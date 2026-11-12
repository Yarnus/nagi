package dev.nagi.sdk

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class PostRatingTest {
    private suspend fun sessions() = MemorySessionStore().apply { saveSession(NgaSession("7", "test-token")) }
    @Test fun ratingUsesExplicitDirectionAndSingleWriteWithOptionalReceipts() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), sessions())
            for (direction in PostRating.entries) {
                val flags = if (direction == PostRating.UP) 4 else 8
                server.enqueue(MockResponse().setBody("""{"data":["评分成功",${direction.value},$flags]}"""))
                assertTrue(client.ratePost("123", "0", direction) is WriteResult.Confirmed)
                val request = server.takeRequest()
                assertEquals("POST", request.method)
                assertEquals("/nuke.php", request.requestUrl!!.encodedPath)
                assertEquals("topic_recommend", request.requestUrl!!.queryParameter("__lib"))
                assertEquals("add", request.requestUrl!!.queryParameter("__act"))
                val body = request.body.readUtf8()
                assertTrue(body.contains("tid=123")); assertTrue(body.contains("pid=0"))
                assertTrue(body.contains("value=${direction.value}")); assertTrue(body.contains("raw=3"))
            }
            server.enqueue(MockResponse().setBody("""{"data":{"0":"未知响应"}}"""))
            assertEquals(WriteResult.Confirmed("评价已提交"), client.ratePost("123", "12", PostRating.UP))
            server.enqueue(MockResponse().setBody("""{"error":{"0":"Permission denied"}}"""))
            assertTrue(client.ratePost("123", "12", PostRating.DOWN) is WriteResult.Rejected)
            server.enqueue(MockResponse().setResponseCode(503))
            assertEquals(WriteResult.Unconfirmed(), client.ratePost("123", "12", PostRating.UP))
            server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", server.url("/redirected")))
            assertEquals(WriteResult.Unconfirmed(), client.ratePost("123", "12", PostRating.UP))
            assertEquals(6, server.requestCount)
        }
    }
    @Test fun ratingConfirmsNumericReceiptWithoutFixedSuccessWording() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), sessions())
            val cases = listOf(
                Triple(PostRating.UP, """{"data":["已支持",1,4]}""", PostRatingReceipt(1, PostRating.UP)),
                Triple(PostRating.UP, """{"data":{"0":"已支持","1":"2","2":"6"}}""", PostRatingReceipt(2, PostRating.UP)),
                Triple(PostRating.UP, """{"data":["已取消支持",-1,1]}""", PostRatingReceipt(-1, null)),
                Triple(PostRating.DOWN, """{"data":["已反对",-1,8]}""", PostRatingReceipt(-1, PostRating.DOWN)),
                Triple(PostRating.DOWN, """{"data":["已反对",-2,9]}""", PostRatingReceipt(-2, PostRating.DOWN)),
                Triple(PostRating.DOWN, """{"data":["已取消反对",1,2]}""", PostRatingReceipt(1, null)),
            )
            for ((direction, body, expected) in cases) {
                server.enqueue(MockResponse().setBody(body))
                val result = client.ratePost("123", "12", direction)
                assertTrue(body, result is WriteResult.Confirmed)
                assertEquals(expected, (result as WriteResult.Confirmed).ratingReceipt)
            }
            assertEquals(cases.size, server.requestCount)
        }
    }

    @Test fun extensionFlagsDoNotHideConfirmedVoteChanges() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), sessions())
            val cases = listOf(
                Triple(PostRating.UP, 17, PostRatingReceipt(-1, null)),
                Triple(PostRating.DOWN, 18, PostRatingReceipt(1, null)),
                Triple(PostRating.UP, 20, PostRatingReceipt(1, PostRating.UP)),
                Triple(PostRating.DOWN, 24, PostRatingReceipt(-1, PostRating.DOWN)),
                Triple(PostRating.UP, 22, PostRatingReceipt(2, PostRating.UP)),
                Triple(PostRating.DOWN, 25, PostRatingReceipt(-2, PostRating.DOWN)),
            )
            cases.forEach { (direction, flags, expected) ->
                server.enqueue(MockResponse().setBody("""{"data":{"0":"操作完成","1":${expected.delta},"2":$flags}}"""))
                val result = client.ratePost("123", "12", direction)
                assertTrue("flags=$flags", result is WriteResult.Confirmed)
                assertEquals(expected, (result as WriteResult.Confirmed).ratingReceipt)
            }
            assertEquals(cases.size, server.requestCount)
        }
    }

    @Test fun inconsistentReceiptsDoNotRejectSuccessfulWritesOrInventScoreChanges() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), sessions())
            for (body in listOf(
                """{"data":["评分成功",-1,8]}""",
                """{"data":["评分成功",2,4]}""",
                """{"data":["评分成功",1,12]}""",
                """{"data":["评分成功",0,0]}""",
                """{"data":["评分成功",1]}""",
                """{"data":["评分成功","1.0",4]}""",
                """{"data":["评分成功",1,16]}""",
            )) {
                server.enqueue(MockResponse().setBody(body))
                assertEquals(body, WriteResult.Confirmed("评价已提交"), client.ratePost("123", "12", PostRating.UP))
            }
            assertEquals(7, server.requestCount)
        }
    }

    @Test fun invalidTargetsAndGuestsNeverTransmit() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), sessions())
            for ((tid, pid) in listOf("0" to "0", "1" to "-1", "1" to "floor:1", "" to "0")) {
                assertTrue(runCatching { client.ratePost(tid, pid, PostRating.UP) }.exceptionOrNull() is IllegalArgumentException)
            }
            assertTrue(HttpNgaClient(server.url("/"), MemorySessionStore()).ratePost("123", "0", PostRating.UP) is WriteResult.Rejected)
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun missingReplyPrefillRequiresMatchingMainTopicAndAuthenticatedEnvelope() {
        val raw = """<root><action>reply</action><tid>123</tid><pid>0</pid><auth>test</auth><__CU><uid>7</uid></__CU></root>"""
        for (target in listOf(PublishTarget(PublishAction.QUOTE, topicId = "123", postId = "0"),
            PublishTarget(PublishAction.COMMENT, topicId = "123", postId = "0"),
            PublishTarget(PublishAction.REPLY, topicId = "123", postId = "12"),
            PublishTarget(PublishAction.REPLY, topicId = "999", postId = "0"))) {
            assertTrue(runCatching { NgaXmlParser.publishPreparation(raw, target) }.exceptionOrNull() is NgaException.Protocol)
        }
        val target = PublishTarget(PublishAction.REPLY, topicId = "123", postId = "0")
        for (invalid in listOf(raw.replace("<auth>test</auth>", ""), raw.replace("<uid>7</uid>", "<uid>0</uid>"), raw.replace("<action>reply</action>", "<action>quote</action>"))) {
            assertTrue(runCatching { NgaXmlParser.publishPreparation(invalid, target) }.exceptionOrNull() is NgaException.Protocol)
        }
    }
}
