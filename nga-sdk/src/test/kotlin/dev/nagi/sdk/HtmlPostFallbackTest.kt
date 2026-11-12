package dev.nagi.sdk

import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

class HtmlPostFallbackTest {
    private fun xml(page: Int = 1, floor: Int = 0, authorId: String = "7"): String = """
        <?xml version="1.0" encoding="GB18030"?>
        <root>
          <__GLOBAL><_ATTACH_BASE_VIEW>//img.nga.cn/attachments/</_ATTACH_BASE_VIEW></__GLOBAL>
          <__U><item i="0"><uid>$authorId</uid><username>Reader</username><avatar>//img.nga.cn/avatars/a.png</avatar></item></__U>
          <__T><tid>123</tid><subject>Title &amp; more</subject><authorid>$authorId</authorid></__T>
          <__R>
            <item i="0">
              <pid>${if (floor == 0) 0 else 99}</pid>
              <lou>$floor</lou>
              <authorid>$authorId</authorid>
              <postdate>2026-11-06 10:00</postdate>
              <content>Hello&lt;br/&gt;[b]bold[/b][img]./mon_202611/a.png[/img]</content>
            </item>
          </__R>
          <__ROWS>41</__ROWS>
          <__R__ROWS_PAGE>20</__R__ROWS_PAGE>
        </root>
    """.trimIndent()

    private fun html(page: Int = 1, floor: Int = 0, authorId: String = "0"): String = """
        <!doctype html><html><head><script>
        var __CURRENT_TID=123, __CURRENT_PAGE=$page, __CURRENT_AUTHORID=$authorId;
        var __ATTACH_BASE_VIEW='//img.nga.cn/attachments/';
        </script></head><body>
        <h1 id="currentTopicName">Title &amp; more</h1>
        <div id="m_posts_c">
          <a id="postauthor$floor" href="nuke.php?func=ucp&amp;uid=7">UID</a>
          <div id="postcontainer$floor"><a id="pid${if (floor == 0) 0 else 99}Anchor"></a>
            <span id="postdate$floor">2026-11-06 10:00</span>
            <span id="postcontent$floor">Hello<br>[b]bold[/b][img]./mon_202611/a.png[/img]</span>
          </div>
        </div>
        <script>
        commonui.userInfo.setAll({"7":{"username":"Reader","avatar":"//img.nga.cn/avatars/a.png"}});
        commonui.postArg.setDefault(-7,0,123,7,0,'Title (with parentheses)','',null,'',null,0,40,1234,20);
        </script>
        <script>var __PAGE = {0:'/read.php?tid=123',1:3,2:$page,3:20};</script>
        </body></html>
    """.trimIndent()

    @Test fun primaryReadUsesPostLiteXmlAndWindowsPhoneClientHeader() = runBlocking {
        MockWebServer().use { server ->
            val gbkBytes = xml(page = 1, floor = 0).toByteArray(charset("GB18030"))
            server.enqueue(MockResponse().setBody(Buffer().write(gbkBytes)))
            val page = HttpNgaClient(server.url("/"), MemorySessionStore()).getPosts("123", 1)
            assertEquals("Title & more", page.title)
            assertEquals("Reader", page.items.single().author)
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("xml", req.requestUrl!!.queryParameter("lite"))
            assertEquals("UTF8", req.requestUrl!!.queryParameter("__inchst"))
            assertEquals("NGA_WP_JW/(;WINDOWS)", req.getHeader("User-Agent"))
            assertEquals("NGA_WP_JW/(;WINDOWS)", req.getHeader("X-User-Agent"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun brokenLiteXmlFallsBackToCompactXmlAndCachesPreferredFormatForTopic() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<root><broken"))
            server.enqueue(MockResponse().setBody(xml(page = 1, floor = 0)))
            server.enqueue(MockResponse().setBody(xml(page = 2, floor = 20)))

            val client = HttpNgaClient(server.url("/"), MemorySessionStore())
            val first = client.getPosts("123", 1)
            assertEquals(1, first.items.size)
            assertEquals("xml", server.takeRequest().requestUrl!!.queryParameter("lite"))
            val recovered = server.takeRequest()
            assertEquals("10", recovered.requestUrl!!.queryParameter("__output"))
            assertEquals(0, recovered.sequenceNumber)

            val second = client.getPosts("123", 2)
            assertEquals(20, second.items.single().floor)
            val cachedReq = server.takeRequest()
            assertEquals("10", cachedReq.requestUrl!!.queryParameter("__output"))
            assertNull(cachedReq.requestUrl!!.queryParameter("lite"))
            assertEquals(3, server.requestCount)
        }
    }

    @Test fun truncatedGzipAndConnectionCutMidBodyStopWithoutFormatOrHtmlFallback() = runBlocking {
        MockWebServer().use { server ->
            val gzBytes = ByteArrayOutputStream().also { out ->
                GZIPOutputStream(out).use { it.write(xml().toByteArray()) }
            }.toByteArray()
            val responses = listOf(
                MockResponse().setHeader("Content-Encoding", "gzip")
                    .setBody(Buffer().write(gzBytes, 0, gzBytes.size / 2)),
                MockResponse().setBody("x".repeat(100)).setHeader("Content-Length", "8192")
                    .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END),
            )
            for ((index, response) in responses.withIndex()) {
                server.enqueue(response)
                val error = runCatching { HttpNgaClient(server.url("/"), MemorySessionStore()).getPosts("123") }.exceptionOrNull()
                assertTrue("Expected Network, got $error", error is NgaException.Network)
                assertEquals(index + 1, server.requestCount)
            }
        }
    }

    @Test fun brokenXmlFormatsFallBackToNativeHtmlWithWpPostAndSameReadParameters() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<root><broken"))
            server.enqueue(MockResponse().setBody(""))
            server.enqueue(MockResponse().setBody(html(page = 2, floor = 21, authorId = "7")))
            val page = HttpNgaClient(server.url("/"), MemorySessionStore()).getPosts("123", 2, "7")
            assertEquals("Title & more", page.title)
            assertEquals(41, page.total)
            assertTrue(page.hasMore)
            val post = page.items.single()
            assertEquals(21, post.floor)
            assertEquals("99", post.id)
            assertEquals("Reader", post.author)
            assertEquals("7", page.authorId)
            assertEquals("https://img.nga.cn/avatars/a.png", post.avatar)
            assertTrue(post.content.any { it is ContentBlock.Image && it.url == "https://img.nga.cn/attachments/mon_202611/a.png" })
            assertEquals("xml", server.takeRequest().requestUrl!!.queryParameter("lite"))
            assertEquals("10", server.takeRequest().requestUrl!!.queryParameter("__output"))
            val fallback = server.takeRequest()
            assertEquals("POST", fallback.method)
            assertEquals("/read.php", fallback.requestUrl!!.encodedPath)
            assertEquals("123", fallback.requestUrl!!.queryParameter("tid"))
            assertEquals("2", fallback.requestUrl!!.queryParameter("page"))
            assertEquals("7", fallback.requestUrl!!.queryParameter("authorid"))
            assertFalse(fallback.requestUrl!!.queryParameterNames.contains("noBBCode"))
            assertNull(fallback.requestUrl!!.queryParameter("__output"))
            assertNull(fallback.requestUrl!!.queryParameter("lite"))
            assertEquals("NGA_WP_JW/(;WINDOWS)", fallback.getHeader("User-Agent"))
            assertEquals(fallback.getHeader("User-Agent"), fallback.getHeader("X-User-Agent"))
            assertEquals(3, server.requestCount)
        }
    }

    @Test fun postConnectionFailureDoesNotTryAnotherOrigin() = runBlocking {
        MockWebServer().use { backup ->
            val deadUrl = MockWebServer().let { dead ->
                val url = dead.url("/")
                dead.shutdown()
                url
            }
            backup.enqueue(MockResponse().setBody(xml()))
            val client = HttpNgaClient(deadUrl, MemorySessionStore(), timeoutSeconds = 5, backupOrigins = listOf(backup.url("/")))
            assertTrue(runCatching { client.getPosts("123", 1) }.exceptionOrNull() is NgaException.Network)
            assertEquals(0, backup.requestCount)
        }
    }

    @Test fun non2xxStructuredXmlAndJsonErrorsSurfaceDirectlyWithoutFallback() = runBlocking {
        MockWebServer().use { server ->
            val xmlAuthError = """<?xml version="1.0" encoding="GB18030"?><root><__MESSAGE><item>1</item><item>未登录，访客不能直接访问</item></__MESSAGE></root>"""
                .toByteArray(charset("GB18030"))
            server.enqueue(MockResponse().setResponseCode(403).setBody(Buffer().write(xmlAuthError)))
            val client = HttpNgaClient(server.url("/"), MemorySessionStore())
            val err = runCatching { client.getPosts("123") }.exceptionOrNull()
            assertTrue("Expected Authentication, got $err", err is NgaException.Authentication)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun successfulStructuredAndExplicitFailuresNeverRetry() = runBlocking {
        val cases = listOf(
            MockResponse().setBody(xml()),
            MockResponse().setBody("""{"data":{"__T":{"subject":"Title","authorid":7},"__R":{"0":{"pid":0,"authorid":7,"lou":0,"content":"Hello"}},"__ROWS":1}}"""),
            MockResponse().setBody("""<?xml version="1.0" encoding="GB18030"?><root><__MESSAGE><item>1</item><item>15:请登录</item></__MESSAGE></root>"""),
            MockResponse().setBody("""{"error":{"0":"操作频繁"}}"""),
            MockResponse().setBody("""{"error":{"0":"主题已删除"}}"""),
        )
        for (response in cases) MockWebServer().use { server ->
            server.enqueue(response)
            runCatching { HttpNgaClient(server.url("/"), MemorySessionStore()).getPosts("123") }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun statusFailuresTryBothXmlFormatsButDoNotTriggerHtml() = runBlocking {
        for (status in listOf(401, 403, 404, 429, 503)) MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setResponseCode(status)) }
            val error = runCatching { HttpNgaClient(server.url("/"), MemorySessionStore()).getPosts("123") }.exceptionOrNull()
            assertTrue(error is NgaException.Http && error.status == status)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun firstXmlErrorControlsHtmlFallbackAndHtmlIsNotCached() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<broken"))
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setBody(html()))
            server.enqueue(MockResponse().setBody(xml()))
            val client = HttpNgaClient(server.url("/"), MemorySessionStore())
            assertEquals(1, client.getPosts("123").items.size)
            assertEquals(1, client.getPosts("123").items.size)
            repeat(3) { server.takeRequest() }
            assertEquals("xml", server.takeRequest().requestUrl!!.queryParameter("lite"))
        }
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setBody("<broken"))
            val error = runCatching { HttpNgaClient(server.url("/"), MemorySessionStore()).getPosts("123") }.exceptionOrNull()
            assertTrue(error is NgaException.Http && error.status == 503)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun structuredBodyCanSucceedDespiteHttpStatus() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500).setBody(xml()))
            assertEquals(1, HttpNgaClient(server.url("/"), MemorySessionStore()).getPosts("123").items.size)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun htmlBusinessErrorsOnXmlAttemptsStopImmediately() = runBlocking {
        for (status in listOf(200, 403)) {
            for ((message, expected) in listOf(
                "请登录" to NgaException.Authentication::class.java,
                "操作频繁" to NgaException.Restricted::class.java,
                "主题已删除" to NgaException.Business::class.java,
            )) MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(status).setBody(
                    "<!--msgcodestart-->1<!--msgcodeend-->" +
                        "<!--msginfostart--><b>$message</b><!--msginfoend-->",
                ))
                server.enqueue(MockResponse().setBody(xml()))
                val error = runCatching { HttpNgaClient(server.url("/"), MemorySessionStore()).getPosts("123") }.exceptionOrNull()
                assertTrue("Expected $expected, got $error", expected.isInstance(error))
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test fun htmlBusinessErrorsAreNotReportedAsMalformedPages() = runBlocking {
        for ((message, expected) in listOf(
            "请登录" to NgaException.Authentication::class.java,
            "操作频繁" to NgaException.Restricted::class.java,
            "主题已删除" to NgaException.Business::class.java,
        )) MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setBody("<broken")) }
            server.enqueue(MockResponse().setResponseCode(403).setBody(
                "<!--msgcodestart-->1<!--msgcodeend--><!--msginfostart--><!--msginfoend-->" +
                    "<!--msginfostart--><b>$message</b><!--msginfoend-->",
            ))
            val error = runCatching { HttpNgaClient(server.url("/"), MemorySessionStore()).getPosts("123") }.exceptionOrNull()
            assertTrue("Expected $expected, got $error", expected.isInstance(error))
            assertEquals(3, server.requestCount)
        }
    }

    @Test fun lastHtmlPagePreservesMainPostIdentityAndDoesNotLoadPageFour() {
        val result = NgaHtmlPostParser.posts(html(page = 3, floor = 40), "123", 3, null)
        assertFalse(result.hasMore)
        assertEquals(40, result.items.single().floor)
        assertEquals(41, result.total)
        assertEquals(20, result.pageSize)
        assertEquals(3, result.totalPages)
        assertEquals(3, result.pageOfFloor(40))
        assertEquals("0", NgaHtmlPostParser.posts(html(), "123", 1, null).items.single().id)
    }

    @Test fun completeSinglePageHtmlWithoutPaginationDoesNotInventPageSize() {
        val body = html().replace("<script>var __PAGE = {0:'/read.php?tid=123',1:3,2:1,3:20};</script>", "")
            .replace("0,40,1234,20", "0,0,1234,1")
        val result = NgaHtmlPostParser.posts(body, "123", 1, null)
        assertEquals(1, result.items.size)
        assertEquals(1, result.totalPages)
        assertEquals(0, result.pageSize)
        assertFalse(result.hasMore)
        assertNull(result.pageOfFloor(0))
    }

    @Test fun singlePageAuthorFilterDoesNotRequireVisibleCountEquality() {
        val body = html(authorId = "7").replace("<script>var __PAGE = {0:'/read.php?tid=123',1:3,2:1,3:20};</script>", "")
            .replace("0,40,1234,20", "0,40,1234,9")
        val result = NgaHtmlPostParser.posts(body, "123", 1, "7")
        assertEquals(41, result.total)
        assertEquals(1, result.items.size)
        assertFalse(result.hasMore)
        assertEquals(0, result.pageSize)
    }

    @Test fun singlePageCanContainAnOmittedMiddleFloor() {
        val lastPost = """
            <a id="postauthor2" href="nuke.php?uid=7">Reader</a>
            <div id="postcontainer2"><a id="pid102Anchor"></a>
              <span id="postdate2">2026-11-07 10:00</span>
              <span id="postcontent2">Last visible floor</span>
            </div>
        """.trimIndent()
        val body = html().replace("<script>var __PAGE = {0:'/read.php?tid=123',1:3,2:1,3:20};</script>", "")
            .replace("0,40,1234,20", "0,2,1234,3")
            .replace("</div>\n<script>", "$lastPost</div>\n<script>")
        val result = NgaHtmlPostParser.posts(body, "123", 1, null)
        assertEquals(listOf(0, 2), result.items.map { it.floor })
        assertEquals(3, result.total)
        assertEquals(1, result.totalPages)
        assertEquals(0, result.pageSize)
        assertFalse(result.hasMore)
    }

    @Test fun singlePageAllowsMissingBoundaryFloorsAndUnreliableCounts() {
        val noPages = html(floor = 6).replace("<script>var __PAGE = {0:'/read.php?tid=123',1:3,2:1,3:20};</script>", "")
        for (body in listOf(
            noPages,
            noPages.replace("0,40,1234,20", "0,0,1234,0"),
            noPages.replace("0,40,1234,20", "0,2,1234,null"),
        )) {
            val result = NgaHtmlPostParser.posts(body, "123", 1, null)
            assertEquals(listOf(6), result.items.map { it.floor })
            assertEquals(0, result.pageSize)
            assertEquals(1, result.totalPages)
            assertFalse(result.hasMore)
        }
    }

    @Test fun missingPaginationStillRequiresMatchingPageAndIntactPostMarkup() {
        val noPages = html().replace("<script>var __PAGE = {0:'/read.php?tid=123',1:3,2:1,3:20};</script>", "")
        val laterPage = noPages.replace("__CURRENT_PAGE=1", "__CURRENT_PAGE=2")
        val error = assertThrows(NgaException.Protocol::class.java) { NgaHtmlPostParser.posts(laterPage, "123", 2, null) }
        assertEquals("P-3006", error.code)
        for (malformed in listOf(noPages.removeSuffix("</html>"), noPages.replace("postcontent0", "missingcontent0"))) {
            assertThrows(NgaException.Protocol::class.java) { NgaHtmlPostParser.posts(malformed, "123", 1, null) }
        }
    }

    @Test fun wrongAuthorAndInvalidMetadataAreRejected() {
        for (body in listOf(
            html(authorId = "8"),
            html(authorId = "7").replace("uid=7", "uid=8"),
            html(authorId = "7").replace("1:3,2:1,3:20", "1:0,2:1,3:20"),
            html(authorId = "7").replace("1:3,2:1,3:20", "1:3,2:1,3:0"),
        )) assertTrue(runCatching { NgaHtmlPostParser.posts(body, "123", 1, "7") }.exceptionOrNull() is NgaException.Protocol)
    }

    @Test fun literalMetadataNeverExecutesJavascriptAndContentKeepsSafeNativeBlocks() {
        val body = html().replace("Title (with parentheses)", "Title (with parentheses) and \\\"quotes\\\"")
            .replace("Hello<br>", "Hello<br>&lt;literal&gt;[quote]quote[/quote][code]code[/code]<script>unsafe()</script>")
        val result = NgaHtmlPostParser.posts(body, "123", 1, null)
        val content = result.items.single().content
        assertTrue(content.any { it is ContentBlock.Quote })
        assertTrue(content.any { it is ContentBlock.Code && it.value == "code" })
        assertFalse(content.toString().contains("unsafe()"))
        val executable = html().replace("0,40,1234,20", "0,alert('unsafe'),1234,20")
        assertTrue(runCatching { NgaHtmlPostParser.posts(executable, "123", 1, null) }.exceptionOrNull() is NgaException.Protocol)
    }

    @Test fun htmlCodeDecodesEntitiesAndBreaksExactlyOnceIncludingNestedBlocks() {
        val body = html().replace("Hello<br>[b]bold[/b][img]./mon_202611/a.png[/img]", """
            [code]a &lt; b &amp;&amp; c<br>&lt;script&gt;literal&lt;/script&gt;<br>&amp;lt;kept&amp;gt;[/code]
            [quote][code]&lt;div&gt; &amp;[/code][/quote]
            [collapse=more][code]x<br>y[/code][/collapse]
            <pre>&amp;lt;pre&amp;gt;</pre>
        """.trimIndent())
        val blocks = NgaHtmlPostParser.posts(body, "123", 1, null).items.single().content
        assertEquals(ContentBlock.Code("a < b && c\n<script>literal</script>\n&lt;kept&gt;"), blocks[0])
        assertEquals(ContentBlock.Code("<div> &"), (blocks[1] as ContentBlock.Quote).children.single())
        assertEquals(ContentBlock.Code("x\ny"), (blocks[2] as ContentBlock.Collapse).children.single())
        assertEquals(ContentBlock.Code("&lt;pre&gt;"), blocks[3])
        assertEquals(ContentBlock.Code("&lt;div&gt;<br>"), ContentParser.parse("[code]&lt;div&gt;<br>[/code]").single())
    }

    @Test fun missingOrInvalidTopicAuthorsAreRejected() {
        for (author in listOf("null", "''", "'null'", "'unknown'", "0", "7.5")) {
            val body = html().replace("setDefault(-7,0,123,7,", "setDefault(-7,0,123,$author,")
            assertTrue("Author $author must fail", runCatching { NgaHtmlPostParser.posts(body, "123", 1, null) }.exceptionOrNull() is NgaException.Protocol)
        }
    }

    @Test fun aMissingBodyInOneOfSeveralPostsCannotSilentlyDropThatPost() {
        val incompletePost = """
            <a id="postauthor1" href="nuke.php?uid=8">Other author</a>
            <div id="postcontainer1"><a id="pid100Anchor"></a><span id="postdate1">2026-11-06 10:01</span></div>
        """.trimIndent()
        val marker = "</div>\n</div>"
        val withMissingPost = html().replace(marker, "</div>\n$incompletePost\n</div>")
        assertNotEquals(html(), withMissingPost)
        assertTrue(runCatching { NgaHtmlPostParser.posts(withMissingPost, "123", 1, null) }.exceptionOrNull() is NgaException.Protocol)
    }

    @Test fun accountChangesDuringHtmlRequestInvalidateTheResult() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{truncated"))
            server.enqueue(MockResponse().setBody(""))
            server.enqueue(MockResponse().setBody(html()).setBodyDelay(200, TimeUnit.MILLISECONDS))
            val store = MemorySessionStore()
            store.saveSession(NgaSession("7", "test-token"))
            val operation = async { runCatching { HttpNgaClient(server.url("/"), store).getPosts("123") } }
            withContext(Dispatchers.IO) { server.takeRequest(); server.takeRequest(); server.takeRequest() }
            store.saveSession(NgaSession("8", "other-test-token"))
            assertTrue(operation.await().exceptionOrNull() is CancellationException)
            assertEquals(3, server.requestCount)
        }
    }

    @Test fun cancellingHtmlRequestDoesNotRetry() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{truncated"))
            server.enqueue(MockResponse().setBody(""))
            server.enqueue(MockResponse().setBody(html()).setBodyDelay(1, TimeUnit.SECONDS))
            val operation = async { HttpNgaClient(server.url("/"), MemorySessionStore()).getPosts("123") }
            withContext(Dispatchers.IO) { server.takeRequest(); server.takeRequest(); server.takeRequest() }
            operation.cancelAndJoin()
            assertTrue(operation.isCancelled)
            assertEquals(3, server.requestCount)
        }
    }

    @Test fun invalidHtmlNeverBecomesEmptySuccessOrTriggersAnotherRequest() = runBlocking {
        for (body in listOf(
            "<html><body>WAF challenge</body></html>",
            html().removeSuffix("</html>"),
            html().replace("__CURRENT_TID=123", "__CURRENT_TID=456"),
            html().replace("__CURRENT_PAGE=1", "__CURRENT_PAGE=2"),
            html().replace("postcontent0", "postcomment0"),
            html().replace("__CURRENT_AUTHORID=0", "__CURRENT_AUTHORID=7"),
            html().replace("setDefault(-7,0,123", "setDefault(-7,0,456"),
        )) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody("{truncated"))
                server.enqueue(MockResponse().setBody(""))
                server.enqueue(MockResponse().setBody(body))
                val result = runCatching { HttpNgaClient(server.url("/"), MemorySessionStore()).getPosts("123") }
                assertTrue(result.exceptionOrNull() is NgaException.Protocol)
                assertEquals(3, server.requestCount)
            }
        }
    }
}
