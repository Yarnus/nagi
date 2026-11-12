package dev.nagi.sdk

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class SearchProtocolTest {
    @Test fun globalTopicsOmitScopeAndPreserveKeywordAndPage() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":{"__T":{"0":{"tid":123,"subject":"标题"}},"__ROWS":61,"__T__ROWS_PAGE":30}}"""))
            val page = HttpNgaClient(server.url("/"), MemorySessionStore()).searchTopics(" 测试 + & ", page = 2)
            assertTrue(page.hasMore)
            assertEquals(2, page.page)
            val url = server.takeRequest().requestUrl!!
            assertEquals("/thread.php", url.encodedPath)
            assertEquals("测试 + &", url.queryParameter("key"))
            assertEquals("2", url.queryParameter("page"))
            assertEquals("UTF8", url.queryParameter("__inchst"))
            assertNull(url.queryParameter("fid"))
            assertNull(url.queryParameter("stid"))
        }
    }

    @Test fun userLookupAcceptsLiveArrayShapeAndUsesExactUsername() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":[{"uid":7,"username":"测试用户","posts":12}]}"""))
            val user = HttpNgaClient(server.url("/"), MemorySessionStore()).findUser(" 测试用户 ")!!
            assertEquals("7", user.id)
            assertEquals("测试用户", user.name)
            val url = server.takeRequest().requestUrl!!
            assertEquals("/nuke.php", url.encodedPath)
            assertEquals("ucp", url.queryParameter("__lib"))
            assertEquals("get", url.queryParameter("__act"))
            assertEquals("测试用户", url.queryParameter("username"))
            assertEquals("UTF8", url.queryParameter("__inchst"))
            assertNull(url.queryParameter("uid"))
        }
    }

    @Test fun onlyExplicitMissingUsersBecomeEmptyResults() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), MemorySessionStore())
            server.enqueue(MockResponse().setBody("""{"error":["2048:找不到用户"]}"""))
            assertNull(client.findUser("missing"))
            server.enqueue(MockResponse().setBody("""{"error":["2048:注册用户/威望大于0方可使用搜索"]}"""))
            assertTrue(runCatching { client.searchTopics("测试") }.exceptionOrNull() is NgaException.Restricted)
            server.enqueue(MockResponse().setBody("""{"data":[{"username":"missing identity"}]}"""))
            assertTrue(runCatching { client.findUser("测试") }.exceptionOrNull() is NgaException.Protocol)
        }
    }

    @Test fun invalidSearchInputsNeverSendRequests() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), MemorySessionStore())
            assertTrue(runCatching { client.searchTopics(" ") }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { client.searchTopics("x", page = 0) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { client.searchTopics("x", forumId = "") }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { client.findUser(" ") }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, server.requestCount)
        }
    }
}
