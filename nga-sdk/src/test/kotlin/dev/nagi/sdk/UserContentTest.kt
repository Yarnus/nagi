package dev.nagi.sdk

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class UserContentTest {
    @Test fun xmlUserTopicsUseReferenceDefaultPageSize() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""<root><__T><item><tid>123</tid><subject>Title</subject></item></__T><__ROWS>70</__ROWS></root>"""))
            val result = HttpNgaClient(server.url("/"), MemorySessionStore()).getUserTopics("7")
            assertTrue(result.hasMore); assertEquals(70, result.total)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/thread.php", request.requestUrl!!.encodedPath)
            assertEquals("7", request.requestUrl!!.queryParameter("authorid"))
            assertEquals("1", request.requestUrl!!.queryParameter("page"))
        }
    }
    @Test fun userRepliesHaveTypedContentAndUnknownPagination() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":{"__T":{"0":{"tid":123,"subject":"Title","__P":{"tid":123,"pid":9,"authorid":7,"content":"Reply","postdate":42}}}}}"""))
            val result = HttpNgaClient(server.url("/"), MemorySessionStore()).getUserReplies("7", 2)
            assertEquals("9", result.items.single().postId)
            assertEquals(listOf(ContentBlock.Text("Reply")), result.items.single().content)
            assertNull(result.hasMore)
            val url = server.takeRequest().requestUrl!!
            assertEquals("/thread.php", url.encodedPath)
            assertEquals("1", url.queryParameter("searchpost"))
            assertEquals("7", url.queryParameter("authorid"))
            assertEquals("2", url.queryParameter("page"))
        }
    }
    @Test fun xmlRepliesPreserveIdentityAndKnownPagination() {
        val raw = """<root><__T><item><tid>123</tid><subject>Title</subject><__P><tid>123</tid><pid>9</pid><authorid>7</authorid><content>Reply</content></__P></item></__T><__ROWS>2</__ROWS><__T__ROWS_PAGE>1</__T__ROWS_PAGE></root>"""
        assertEquals(true, NgaXmlParser.userReplies(raw, 1).hasMore)
        assertEquals(false, NgaXmlParser.userReplies(raw, 2).hasMore)
        assertTrue(runCatching { NgaXmlParser.userReplies(raw.replace("<pid>9</pid>", ""), 1) }.exceptionOrNull() is NgaException.Protocol)
    }
    @Test fun emptyAndMalformedRepliesAreDifferent() {
        assertEquals(false, NgaParser.userReplies("""{"data":{"__T":[]}}""", 1).hasMore)
        assertTrue(runCatching { NgaParser.userReplies("""{"data":{"unexpected":[]}}""", 1) }.exceptionOrNull() is NgaException.Protocol)
        assertTrue(runCatching { NgaParser.userReplies("""{"data":{"__T":[{"tid":123}]}}""", 1) }.exceptionOrNull() is NgaException.Protocol)
    }
    @Test fun invalidUserAndPageNeverReachNetwork() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), MemorySessionStore())
            for (uid in listOf("", "-1", "0", "x", "7&x=1")) {
                assertTrue(runCatching { client.getUserTopics(uid) }.exceptionOrNull() is IllegalArgumentException)
                assertTrue(runCatching { client.getUserReplies(uid) }.exceptionOrNull() is IllegalArgumentException)
            }
            assertTrue(runCatching { client.getUserTopics("7", 0) }.isFailure)
            assertTrue(runCatching { client.getUserReplies("7", -1) }.isFailure)
            assertEquals(0, server.requestCount)
        }
    }
}
