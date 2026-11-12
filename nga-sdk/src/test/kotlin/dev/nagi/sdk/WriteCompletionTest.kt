package dev.nagi.sdk

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class WriteCompletionTest {
    @Test fun allWritesAcceptUnfamiliarSuccessfulResponses() = runBlocking {
        MockWebServer().use { server ->
            val sessions = MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }
            val client = HttpNgaClient(server.url("/"), sessions)
            val responses = listOf(
                "", "<html>Unknown</html>", "{malformed", "{}",
                """{"error":["informational notice"]}""",
                """{"data":{"__MESSAGE":[null,"操作完成"]}}""",
                """{"data":{"action":"reply","__MESSAGE":[1,"陌生提示"]}}""",
            )
            for (raw in responses) {
                for (action in PublishAction.entries) {
                    server.enqueue(MockResponse().setBody(raw))
                    assertTrue("$action: $raw", client.publish(PublishRequest(action, "7", "Private draft", "Title",
                        topicId = "123", postId = "12")) is WriteResult.Confirmed)
                }
                server.enqueue(MockResponse().setBody(raw))
                assertTrue(client.sendMessage(MessageRequest(MessageAction.REPLY, "Private draft", conversationId = "9")) is WriteResult.Confirmed)
                for (rating in PostRating.entries) {
                    server.enqueue(MockResponse().setBody(raw))
                    val result = client.ratePost("123", "12", rating)
                    assertTrue(result is WriteResult.Confirmed)
                    assertNull((result as WriteResult.Confirmed).ratingReceipt)
                }
            }
            assertEquals(responses.size * (PublishAction.entries.size + 3), server.requestCount)
        }
    }

    @Test fun noContentResponsesAreSuccessfulForAllWriteEndpoints() = runBlocking {
        MockWebServer().use { server ->
            val sessions = MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }
            val client = HttpNgaClient(server.url("/"), sessions)
            for (status in listOf(200, 201, 202, 204, 205)) {
                server.enqueue(MockResponse().setResponseCode(status))
                assertTrue("HTTP $status", client.publish(PublishRequest(PublishAction.REPLY, "", "Draft", topicId = "123")) is WriteResult.Confirmed)
                server.enqueue(MockResponse().setResponseCode(status))
                assertTrue("HTTP $status", client.sendMessage(MessageRequest(MessageAction.REPLY, "Draft", conversationId = "9")) is WriteResult.Confirmed)
                server.enqueue(MockResponse().setResponseCode(status))
                assertTrue("HTTP $status", client.ratePost("123", "12", PostRating.UP) is WriteResult.Confirmed)
            }
            assertEquals(15, server.requestCount)
        }
    }

    @Test fun unsuccessfulHttpStatusesNeverBecomeSuccessEvenWithCompletionBody() = runBlocking {
        MockWebServer().use { server ->
            val sessions = MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }
            val client = HttpNgaClient(server.url("/"), sessions)
            for (status in listOf(302, 400, 401, 403, 429, 500, 503)) {
                repeat(3) { server.enqueue(MockResponse().setResponseCode(status).setBody("""{"data":[0,"完成"]}""")) }
                assertFalse(client.publish(PublishRequest(PublishAction.REPLY, "", "Draft", topicId = "123")) is WriteResult.Confirmed)
                assertFalse(client.sendMessage(MessageRequest(MessageAction.REPLY, "Draft", conversationId = "9")) is WriteResult.Confirmed)
                assertFalse(client.ratePost("123", "12", PostRating.UP) is WriteResult.Confirmed)
            }
            assertEquals(21, server.requestCount)
        }
    }

    @Test fun transportFailuresRemainUnconfirmedWithoutReplay() = runBlocking {
        for (operation in 0..3) {
            MockWebServer().use { server ->
                val sessions = MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }
                val client = HttpNgaClient(server.url("/"), sessions, timeoutSeconds = 1)
                val policy = if (operation == 3) okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE
                    else okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST
                server.enqueue(MockResponse().setSocketPolicy(policy))
                val result = when (operation) {
                    0 -> client.publish(PublishRequest(PublishAction.REPLY, "", "Draft", topicId = "123"))
                    1 -> client.sendMessage(MessageRequest(MessageAction.REPLY, "Draft", conversationId = "9"))
                    else -> client.ratePost("123", "12", PostRating.UP)
                }
                assertTrue(result is WriteResult.Unconfirmed)
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test fun knownErrorsOverrideSuccessMarkersForEveryWrite() = runBlocking {
        MockWebServer().use { server ->
            val sessions = MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }
            val client = HttpNgaClient(server.url("/"), sessions)
            val responses = listOf(
                """{"error":{"0":"权限不足"},"data":{"__MESSAGE":[0,"完成"]}}""",
                """{"data":{"__MESSAGE":[1,"字数太短"]}}""",
                """{"__MESSAGE":{"0":0,"1":"操作过于频繁"}}""",
                "<root><error>禁止发言</error></root>",
                "/*\$js\$*/{\"error\":[\"权限不足\"]}/*\$js\$*/",
                "<!--msgcodestart-->15<!--msgcodeend--><!--msginfostart-->请登录<!--msginfoend-->",
            )
            for (raw in responses) {
                server.enqueue(MockResponse().setBody(raw))
                assertTrue(raw, client.publish(PublishRequest(PublishAction.MODIFY, "", "Draft", topicId = "123", postId = "12")) is WriteResult.Rejected)
                server.enqueue(MockResponse().setBody(raw))
                assertTrue(raw, client.sendMessage(MessageRequest(MessageAction.REPLY, "Draft", conversationId = "9")) is WriteResult.Rejected)
                server.enqueue(MockResponse().setBody(raw))
                assertTrue(raw, client.ratePost("123", "12", PostRating.UP) is WriteResult.Rejected)
            }
            assertEquals(responses.size * 3, server.requestCount)
        }
    }
}
