package dev.nagi.sdk

import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class NgaClientTest {
    private val topic = """{"data":{"__T":{"0":{"tid":123,"subject":"测试标题","author":"作者","authorid":7}},"__ROWS":71}}"""

    @Test fun directoryAndCollectionMapping() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":{"0":{"name":"Games","groups":{"0":{"name":"Group","forums":{"0":{"fid":-7,"name":"Forum","stid":42}}}}}}}}"""))
            val client = HttpNgaClient(server.url("/"), MemorySessionStore())
            val forum = client.getForums().single()
            assertEquals("collection:42", forum.key)
            assertEquals("Games", forum.category)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("category", request.requestUrl!!.queryParameter("__act"))
        }
    }
    @Test fun gbkTopicsPaginationAndParameters() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(Buffer().write(topic.toByteArray(charset("GBK")))))
            val client = HttpNgaClient(server.url("/"), MemorySessionStore())
            val page = client.getTopics("-7", 2, "42")
            assertEquals("测试标题", page.items.single().title)
            assertTrue(page.hasMore)
            val request = server.takeRequest()
            assertEquals("42", request.requestUrl!!.queryParameter("stid"))
            assertNull(request.requestUrl!!.queryParameter("fid"))
            assertEquals("2", request.requestUrl!!.queryParameter("page"))
            assertEquals("POST", request.method)
            assertEquals("xml", request.requestUrl!!.queryParameter("lite"))
        }
    }
    @Test fun topicSearchMustNotUseForumDirectoryEndpoint() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(topic))
            HttpNgaClient(server.url("/"), MemorySessionStore()).searchTopics("Android", "-7")
            assertEquals("/thread.php", server.takeRequest().requestUrl!!.encodedPath)
        }
    }
    @Test fun newestSortUsesAuditedOrderParameter() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(topic))
            HttpNgaClient(server.url("/"), MemorySessionStore()).getTopics("7", sort = TopicSort.NEWEST)
            assertEquals("postdatedesc", server.takeRequest().requestUrl!!.queryParameter("order_by"))
        }
    }
    @Test fun gbkTopicsWithJavascriptOrMislabeledContentTypeDecodeCleanly() = runBlocking {
        MockWebServer().use { server ->
            val gbk = Buffer().write(topic.toByteArray(charset("GBK")))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/x-javascript").setBody(gbk.clone()))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json; charset=UTF-8").setBody(gbk))
            val client = HttpNgaClient(server.url("/"), MemorySessionStore())
            assertEquals("测试标题", client.getTopics("7").items.single().title)
            assertEquals("测试标题", client.getTopics("7").items.single().title)
        }
    }
    @Test fun utf8AndScriptEnvelope() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody("window.script_muti_get_var_store=$topic;"))
            val page = HttpNgaClient(server.url("/"), MemorySessionStore()).getTopics("1")
            assertEquals("测试标题", page.items.single().title)
        }
    }
    @Test fun postsResolveUsersAttachmentsAndAuthorFilter() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":{"__T":{"subject":"Title","authorid":7},"__U":{"7":{"username":"User","avatar":"//img.nga.cn/avatars/a.png"}},"__R":{"0":{"pid":9,"authorid":7,"lou":0,"content":"Hello[img]./mon_202601/a.png[/img]"}},"__ROWS":21,"__R__ROWS_PAGE":20}}"""))
            val page = HttpNgaClient(server.url("/"), MemorySessionStore()).getPosts("123", authorId = "7")
            assertEquals("User", page.items.single().author)
            assertEquals(ContentBlock.Image("https://img.nga.cn/attachments/mon_202601/a.png"), page.items.single().content.last())
            assertTrue(page.hasMore)
            assertEquals("7", server.takeRequest().requestUrl!!.queryParameter("authorid"))
        }
    }
    @Test fun searchEncodesKeywordsAndPreservesForumAndPagination() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(topic))
            val page = HttpNgaClient(server.url("/"), MemorySessionStore()).searchTopics("测试 + &", "-7", page = 2)
            assertTrue(page.hasMore)
            val url = server.takeRequest().requestUrl!!
            assertEquals("测试 + &", url.queryParameter("key"))
            assertEquals("-7", url.queryParameter("fid"))
            assertEquals("2", url.queryParameter("page"))
            assertEquals("UTF8", url.queryParameter("__inchst"))
        }
    }
    @Test fun userMapping() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":{"0":{"uid":7,"username":"Name","posts":42}}}"""))
            val user = HttpNgaClient(server.url("/"), MemorySessionStore()).getUser("7")
            assertEquals(42, user.posts)
            assertEquals("ucp", server.takeRequest().requestUrl!!.queryParameter("__lib"))
        }
    }
    @Test fun httpBusinessAuthenticationAndMalformedResponses() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), MemorySessionStore())
            val cases = listOf(
                MockResponse().setResponseCode(429) to NgaException.Restricted::class.java,
                MockResponse().setResponseCode(401) to NgaException.Authentication::class.java,
                MockResponse().setBody("""{"error":{"0":"15:访客不能直接访问"}}""") to NgaException.Authentication::class.java,
                MockResponse().setBody("""{"error":{"0":"Topic missing"}}""") to NgaException.Business::class.java,
                MockResponse().setBody("<html>WAF</html>") to NgaException.Protocol::class.java,
                MockResponse().setBody("""{"data":{}}""") to NgaException.Protocol::class.java,
                MockResponse().setBody("{truncated") to NgaException.Protocol::class.java,
            )
            for ((response, type) in cases) {
                server.enqueue(response)
                if (type == NgaException.Protocol::class.java) server.enqueue(response)
                val error = runCatching { client.getTopics("1") }.exceptionOrNull()
                assertTrue("Expected ${type.simpleName}, got $error", type.isInstance(error))
            }
            assertEquals(cases.size + cases.count { it.second == NgaException.Protocol::class.java }, server.requestCount)
        }
    }
    @Test fun staleRoundTripSessionCannotReturnData() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(topic).setBodyDelay(200, TimeUnit.MILLISECONDS))
            val store = MemorySessionStore()
            store.saveSession(NgaSession("7", "token"))
            val client = HttpNgaClient(server.url("/"), store)
            val request = async { runCatching { client.getTopics("1") } }
            withContext(Dispatchers.IO) { server.takeRequest() }
            store.saveSession(NgaSession("8", "other")); store.selectAccount("7")
            assertTrue(request.await().exceptionOrNull() is CancellationException)
        }
    }
    @Test fun cancellationStopsDelayedCall() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(topic).setBodyDelay(1, TimeUnit.SECONDS))
            val request = async { HttpNgaClient(server.url("/"), MemorySessionStore()).getTopics("1") }
            withContext(Dispatchers.IO) { server.takeRequest() }
            request.cancelAndJoin()
            assertTrue(request.isCancelled)
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun noCredentialsToHttpAndNoRedirectFollowing() = runBlocking {
        MockWebServer().use { server ->
            val store = MemorySessionStore().apply { saveSession(NgaSession("7", "secret")) }
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/other")))
            runCatching { HttpNgaClient(server.url("/"), store).getTopics("1") }
            assertNull(server.takeRequest().getHeader("Cookie"))
            assertEquals(1, server.requestCount)
            assertTrue(isCredentialOrigin("https://bbs.nga.cn/".toHttpUrl()))
            listOf("http://bbs.nga.cn/", "https://bbs.nga.cn.evil.test/", "https://bbs.nga.cn:444/", "https://evil.test/").forEach {
                assertFalse(isCredentialOrigin(it.toHttpUrl()))
            }
        }
    }
    @Test fun invalidArgumentsNeverReachNetwork() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), MemorySessionStore())
            assertTrue(runCatching { client.getTopics("1", 0) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { client.searchTopics(" ", "-7") }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { NgaSession("7", "x; injected=yes") }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun logoutClearsSelectedCredentialsAndPreservesOtherAccounts() = runBlocking {
        val store = MemorySessionStore()
        store.saveSession(NgaSession("7", "token7"))
        store.saveSession(NgaSession("8", "token8"))
        val before = store.snapshot().revision
        store.clearSession()
        assertNull(store.snapshot().session)
        assertTrue(store.snapshot().revision > before)
        assertTrue(runCatching { store.selectAccount("8") }.isFailure)
        store.selectAccount("7")
        assertEquals("7", store.snapshot().session!!.userId)
    }
    @Test fun accountChangeBeforeTransmissionNeverSendsQueuedRequest() = runBlocking {
        MockWebServer().use { server ->
            val store = MemorySessionStore()
            val snapshots = java.util.concurrent.atomic.AtomicInteger()
            val switching = object : SessionStore by store {
                override suspend fun snapshot(): SessionSnapshot {
                    if (snapshots.incrementAndGet() == 2) store.saveSession(NgaSession("77", "test-token"))
                    return store.snapshot()
                }
            }
            val error = runCatching { HttpNgaClient(server.url("/"), switching).getTopics("7") }.exceptionOrNull()
            assertTrue(error is CancellationException)
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun safeMarkupAndNonstandardNumericValues() {
        val blocks = ContentParser.parse("[quote]Hi[/quote][code]<unsafe>[/code][collapse=More]hidden[/collapse]<img src='javascript:alert(1)'><script>secret</script>")
        assertEquals(3, blocks.size)
        assertEquals(ContentBlock.Code("<unsafe>"), blocks[1])
        val parsed = NgaParser.topics("""{"data":{"__T":{"0":{"tid":1,"subject":+123,"author":01234}}}}""", 1)
        assertEquals("+123", parsed.items.single().title)
        assertEquals("01234", parsed.items.single().author)
        assertEquals("NgaSession(redacted)", NgaSession("7", "secret").toString())
    }
}
