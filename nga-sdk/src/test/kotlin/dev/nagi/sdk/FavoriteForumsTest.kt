package dev.nagi.sdk

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class FavoriteForumsTest {
    private suspend fun signedIn() = MemorySessionStore().apply { saveSession(NgaSession("7", "test-token")) }

    @Test fun parsesForumsAndCollectionsWithNumericKeys() {
        val forums = NgaParser.favoriteForums("""{"data":{0:{
            0:{"fid":-7,"name":"艾泽拉斯议事厅","info":"魔兽 &amp; 讨论"},
            1:{"fid":"0","stid":16667422,"name":"主题合集"},
            2:{"fid":"0","stid":"0","name":"无效"},
            3:{"fid":"8","name":""},
            4:{"fid":-7,"name":"重复"}
        }},"encode":"utf8"}""")
        assertEquals(2, forums.size)
        assertEquals(Forum("-7", "艾泽拉斯议事厅", "", "", null, description = "魔兽 & 讨论"), forums[0])
        assertEquals("", forums[1].id)
        assertEquals("16667422", forums[1].collectionId)
        assertEquals("collection:16667422", forums[1].key)
    }

    @Test fun acceptsArrayRecordsAndExactEmptyShapes() {
        assertEquals(listOf("7"), NgaParser.favoriteForums("""{"data":[[{"fid":"7","name":"Forum"}]]}""").map { it.id })
        for (empty in listOf("""{"data":{"0":""}}""", """{"data":{"0":{}}}""", """{"data":{"0":[]}}""", """{"data":[[]]}""")) {
            assertTrue(empty, NgaParser.favoriteForums(empty).isEmpty())
        }
    }

    @Test fun rejectsMalformedShapesAndSurfacesNgaErrors() {
        for (raw in listOf("""{"data":{}}""", """{"data":{"0":"x"}}""", """{"data":{"0":5}}""", """{"data":1}""", """{}""", "oops")) {
            assertThrows(raw, NgaException.Protocol::class.java) { NgaParser.favoriteForums(raw) }
        }
        assertThrows(NgaException::class.java) { NgaParser.favoriteForums("""{"error":{"0":"你需要登录"}}""") }
    }

    @Test fun readRequiresLoginAndSendsAuthenticatedJsonRequest() = runBlocking {
        MockWebServer().use { server ->
            val anonymous = HttpNgaClient(server.url("/"), MemorySessionStore())
            assertThrows(NgaException.Authentication::class.java) { runBlocking { anonymous.getFavoriteForums() } }
            assertEquals(0, server.requestCount)

            server.enqueue(MockResponse().setBody("""{"data":{"0":{"0":{"fid":"7","name":"Forum"}}}}"""))
            val forums = HttpNgaClient(server.url("/"), signedIn()).getFavoriteForums()
            assertEquals(listOf("forum:7"), forums.map { it.key })
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/nuke.php", request.requestUrl!!.encodedPath)
            assertEquals("forum_favor2", request.requestUrl!!.queryParameter("__lib"))
            assertEquals("forum_favor", request.requestUrl!!.queryParameter("__act"))
            assertEquals("8", request.requestUrl!!.queryParameter("__output"))
            val body = request.body.readUtf8()
            assertTrue(body.contains("action=get"))
            assertNull(request.getHeader("Cookie"))
            assertFalse(body.contains("access_uid"))
            val trusted = okhttp3.Request.Builder()
                .url("https://bbs.nga.cn/nuke.php?__lib=forum_favor2&__act=forum_favor&__output=8")
                .post(okhttp3.FormBody.Builder().add("action", "get").build())
                .build()
                .withNgaSession(NgaSession("7", "test-token"))
            assertEquals("ngaPassportUid=7; ngaPassportCid=test-token", trusted.header("Cookie"))
            val form = trusted.body as okhttp3.FormBody
            assertEquals(listOf("action", "access_uid", "access_token"), (0 until form.size).map { form.name(it) })
        }
    }

    @Test fun writesUseForumOrCollectionIdOnceAndFollowWriteOutcomes() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), signedIn())
            server.enqueue(MockResponse().setBody("unfamiliar success"))
            assertTrue(client.setForumFavorite(Forum("-7", "Forum", "", ""), true) is WriteResult.Confirmed)
            server.takeRequest().let { request ->
                assertEquals("/nuke.php", request.requestUrl!!.encodedPath)
                assertEquals("forum_favor2", request.requestUrl!!.queryParameter("__lib"))
                assertEquals("forum_favor", request.requestUrl!!.queryParameter("__act"))
                val body = request.body.readUtf8()
                assertTrue(body.contains("action=add"))
                assertTrue(body.contains("fid=-7"))
            }

            server.enqueue(MockResponse().setBody("{}"))
            assertTrue(client.setForumFavorite(Forum("7", "Collection", "", "", collectionId = "99"), false) is WriteResult.Confirmed)
            server.takeRequest().body.readUtf8().let { body ->
                assertTrue(body.contains("action=del"))
                assertTrue(body.contains("fid=99"))
                assertFalse(body.contains("fid=7&"))
            }

            server.enqueue(MockResponse().setBody("""{"error":{"0":"你没有权限"}}"""))
            assertTrue(client.setForumFavorite(Forum("7", "Forum", "", ""), true) is WriteResult.Rejected)
            server.takeRequest()

            server.enqueue(MockResponse().setResponseCode(503))
            assertTrue(client.setForumFavorite(Forum("7", "Forum", "", ""), true) is WriteResult.Unconfirmed)
            server.takeRequest()
            assertEquals(4, server.requestCount)

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { client.setForumFavorite(Forum("0", "Bad", "", ""), true) }
            }
            assertEquals(4, server.requestCount)
        }
    }

    @Test fun anonymousWriteIsRejectedWithoutTransmission() = runBlocking {
        MockWebServer().use { server ->
            val result = HttpNgaClient(server.url("/"), MemorySessionStore()).setForumFavorite(Forum("7", "Forum", "", ""), true)
            assertTrue(result is WriteResult.Rejected && result.reason is NgaException.Authentication)
            assertEquals(0, server.requestCount)
        }
    }
}
