package dev.nagi.sdk

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class SubforumTest {
    @Test fun attributesFollowWebRules() {
        val forum = Forum("-7", "Parent", "", "")
        for (value in listOf(7L, 558L, 542L, 2606L, 2590L, 4654L)) {
            assertTrue(Subforum(forum, "123", value).selected)
        }
        assertFalse(Subforum(forum, "123", 40).filterable)
        assertTrue(Subforum(forum, "123", 41).filterable)
        assertFalse(Subforum(forum, "123", 999).selected)
        assertFalse(Subforum(forum, "123", null).filterable)
    }

    @Test fun xmlKeepsFilterIdentitySeparateAndIgnoresWhitespace() {
        val page = NgaXmlParser.topics("""<root><__T/><__F><sub_forums>
            <item><a>-7</a><b>Forum</b><c>Description</c><d>19115466</d><e>558</e></item>
            <collection><a>88</a><b>Collection</b><c/><d>99</d><e>41</e></collection>
            <item><a>bad</a><b>Invalid</b></item>
            <item><a>9</a><b>Missing</b></item>
        </sub_forums></__F></root>""", 1)
        assertEquals(3, page.subforums.size)
        assertEquals("-7", page.subforums[0].forum.id)
        assertEquals("19115466", page.subforums[0].filterId)
        assertTrue(page.subforums[0].selected)
        assertEquals("88", page.subforums[1].forum.collectionId)
        assertFalse(page.subforums[1].selected)
        assertFalse(page.subforums[2].filterable)
    }

    @Test fun settingsUseSingleAuthenticatedWriteAndAcceptUnknownBodies() = runBlocking {
        MockWebServer().use { server ->
            val sessions = MemorySessionStore().apply { saveSession(NgaSession("7", "test-token")) }
            val client = HttpNgaClient(server.url("/"), sessions)
            for (visible in listOf(true, false)) {
                server.enqueue(MockResponse().setBody("unfamiliar success"))
                assertTrue(client.setSubforumVisibility("-7", "19115466", visible) is WriteResult.Confirmed)
                val request = server.takeRequest()
                assertEquals("POST", request.method)
                assertEquals("/nuke.php", request.requestUrl!!.encodedPath)
                assertEquals("user_option", request.requestUrl!!.queryParameter("__lib"))
                assertEquals("set", request.requestUrl!!.queryParameter("__act"))
                assertEquals("19115466", request.requestUrl!!.queryParameter(if (visible) "del" else "add"))
                val body = request.body.readUtf8()
                assertTrue(body.contains("fid=-7"))
                assertTrue(body.contains("info=add_to_block_tids"))
                assertTrue(body.contains("type=1"))
            }
            server.enqueue(MockResponse().setResponseCode(503))
            assertTrue(client.setSubforumVisibility("-7", "19115466", false) is WriteResult.Unconfirmed)
            assertEquals(3, server.requestCount)
        }
    }
}
