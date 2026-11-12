package dev.nagi.sdk

import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReadExecutionTest {
    private val topics = "<root><__T><item><tid>123</tid><subject>标题😀𠮷</subject></item></__T><__ROWS>41</__ROWS><__T__ROWS_PAGE>20</__T__ROWS_PAGE></root>"

    @Test fun topicFormatsUseTheSamePostPathWithOrWithoutBackups() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<root><broken"))
            server.enqueue(MockResponse().setBody(topics))
            val client = HttpNgaClient(server.url("/"), MemorySessionStore())
            val page = client.getTopics("-7", 2)
            assertEquals("标题😀𠮷", page.items.single().title)
            assertTrue(page.hasMore)
            val first = server.takeRequest()
            val second = server.takeRequest()
            assertEquals("POST", first.method)
            assertEquals("xml", first.requestUrl!!.queryParameter("lite"))
            assertEquals("10", second.requestUrl!!.queryParameter("__output"))
            assertEquals("UTF8", first.requestUrl!!.queryParameter("__inchst"))
            assertEquals(first.getHeader("User-Agent"), first.getHeader("X-User-Agent"))
            server.enqueue(MockResponse().setBody(topics))
            client.getTopics("-7", 3)
            assertEquals("10", server.takeRequest().requestUrl!!.queryParameter("__output"))
        }
    }

    @Test fun successfulBackupIsPreferredOnTheNextRead() = runBlocking {
        MockWebServer().use { primary -> MockWebServer().use { backup ->
            primary.enqueue(MockResponse().setResponseCode(503))
            backup.enqueue(MockResponse().setBody(topics))
            backup.enqueue(MockResponse().setBody(topics))
            val client = HttpNgaClient(primary.url("/"), MemorySessionStore(), backupOrigins = listOf(backup.url("/")))
            client.getTopics("-7")
            client.getTopics("-7", 2)
            assertEquals(1, primary.requestCount)
            assertEquals(2, backup.requestCount)
        } }
    }

    @Test fun backupBusinessErrorsAreNotHiddenByTheFirstTransportFailure() = runBlocking {
        for ((body, expected) in listOf(
            "{\"error\":{\"0\":\"15:请登录\"}}" to NgaException.Authentication::class.java,
            "{\"error\":{\"0\":\"操作频繁\"}}" to NgaException.Restricted::class.java,
        )) {
            MockWebServer().use { primary -> MockWebServer().use { backup ->
                primary.enqueue(MockResponse().setResponseCode(503))
                backup.enqueue(MockResponse().setBody(body))
                val client = HttpNgaClient(primary.url("/"), MemorySessionStore(), backupOrigins = listOf(backup.url("/")))
                val error = runCatching { client.getUser("7") }.exceptionOrNull()
                assertTrue("Expected $expected, got $error", expected.isInstance(error))
                assertEquals(1, primary.requestCount)
                assertEquals(1, backup.requestCount)
            } }
        }
    }

    @Test fun emptyNotFoundTriesXmlFormatsButNotAnotherHost() = runBlocking {
        MockWebServer().use { primary -> MockWebServer().use { backup ->
            repeat(2) { primary.enqueue(MockResponse().setResponseCode(404)) }
            val client = HttpNgaClient(primary.url("/"), MemorySessionStore(), backupOrigins = listOf(backup.url("/")))
            assertTrue(runCatching { client.getPosts("123") }.exceptionOrNull() is NgaException.Http)
            assertEquals(2, primary.requestCount)
            assertEquals(0, backup.requestCount)
        } }
    }

    @Test fun readDeadlineReturnsNetworkFailureNotCoroutineCancellation() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(topics).setBodyDelay(2, TimeUnit.SECONDS))
            val error = runCatching { HttpNgaClient(server.url("/"), MemorySessionStore(), timeoutSeconds = 1).getTopics("-7") }.exceptionOrNull()
            assertTrue("Expected Network, got $error", error is NgaException.Network)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun formCredentialsAreRestrictedToOfficialHttpsOrigins() {
        val session = NgaSession("7", "test-token")
        val form = FormBody.Builder().add("key", "中文 + &").build()
        val trusted = Request.Builder().url("https://bbs.nga.cn/thread.php").post(form).build().withNgaSession(session)
        val authenticated = trusted.body as FormBody
        assertEquals("中文 + &", authenticated.value(0))
        assertEquals("access_uid", authenticated.name(1))
        assertEquals("7", authenticated.value(1))
        assertEquals("access_token", authenticated.name(2))
        assertEquals("test-token", authenticated.value(2))
        assertNotNull(trusted.header("Cookie"))
        for (url in listOf("http://bbs.nga.cn/thread.php", "https://bbs.nga.cn.evil.test/thread.php", "https://bbs.nga.cn:444/thread.php")) {
            val request = Request.Builder().url(url).post(form).build().withNgaSession(session)
            assertNull(request.header("Cookie"))
            assertSame(form, request.body)
        }
        val html = Request.Builder().url("https://bbs.nga.cn/read.php").get().build().withNgaSession(session)
        assertNull(html.body)
        assertNotNull(html.header("Cookie"))
    }

    @Test fun missingXmlPageSizeDoesNotEnableExactFloorJumps() {
        val raw = "<root><__T><subject>T</subject></__T><__R><item><pid>1</pid><lou>20</lou><content>x</content></item></__R><__ROWS>41</__ROWS></root>"
        val page = NgaXmlParser.posts(raw, 2)
        assertEquals(0, page.pageSize)
        assertEquals(0, page.totalPages)
        assertNull(page.pageOfFloor(20))
        assertTrue(page.hasMore)
    }

    @Test fun xmlControlCleanupPreservesSupplementaryCharacters() {
        val raw = "<root><__T><subject>A😀𠮷B</subject><authorid>7</authorid></__T><__R><item><pid>1</pid><lou>0</lou><authorid>7</authorid><content>A😀𠮷B\u0007&#x07;</content></item></__R><__ROWS>1</__ROWS><__R__ROWS_PAGE>20</__R__ROWS_PAGE></root>"
        val page = NgaXmlParser.posts(raw, 1)
        assertEquals("A😀𠮷B", page.title)
        assertEquals(listOf(ContentBlock.Text("A😀𠮷B")), page.items.single().content)
    }
}
