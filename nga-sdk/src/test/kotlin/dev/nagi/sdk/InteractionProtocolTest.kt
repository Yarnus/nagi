package dev.nagi.sdk

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class InteractionProtocolTest {
    private suspend fun store() = MemorySessionStore().apply { saveSession(NgaSession("7", "test-token")) }

    @Test fun notificationsAndMessagesUseAuditedParameters() = runBlocking {
        MockWebServer().use { server ->
            val body = """{"data":{"0":{"mid":9,"tid":123,"subject":"Title","content":"Message","username":"User","time":42,"read":0}}}"""
            server.enqueue(MockResponse().setBody(body))
            server.enqueue(MockResponse().setBody("""{"data":{"0":{"0":{"mid":9,"subject":"Title","from":7,"from_username":"User","time":42}},"nextPage":""}}"""))
            val client = HttpNgaClient(server.url("/"), store())
            assertEquals("Message", client.getNotifications(10).single().summary)
            val notifications = server.takeRequest().requestUrl!!
            assertEquals("noti", notifications.queryParameter("__lib"))
            assertEquals("10", notifications.queryParameter("time_limit"))
            assertEquals("9", client.getPrivateMessages(2).single().id)
            val messages = server.takeRequest().requestUrl!!
            assertEquals("message", messages.queryParameter("__lib"))
            assertEquals("list", messages.queryParameter("act"))
            assertEquals("2", messages.queryParameter("page"))
        }
    }
    @Test fun userTopicsMapTypedResults() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":{"__T":{"0":{"tid":123,"subject":"Title","author":"Name"}},"__ROWS":70,"__T__ROWS_PAGE":35}}"""))
            val topics = HttpNgaClient(server.url("/"), store()).getUserTopics("7", 2)
            assertEquals("123", topics.items.single().id)
            assertEquals(70, topics.total)
            assertFalse(topics.hasMore)
            val request = server.takeRequest().requestUrl!!
            assertEquals("/thread.php", request.encodedPath)
            assertEquals("7", request.queryParameter("authorid"))
            assertNull(request.queryParameter("__act"))
            assertEquals("2", request.queryParameter("page"))
        }
    }
    @Test fun publishSendsTheRequestedActionOnce() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), store())
            server.enqueue(MockResponse().setBody("""{"data":{"0":0,"1":"发帖成功"}}"""))
            assertTrue(client.publish(PublishRequest(PublishAction.NEW_TOPIC, "7", "Body & text", "Title")) is WriteResult.Confirmed)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("new", request.requestUrl!!.queryParameter("action"))
            assertEquals("UTF8", request.requestUrl!!.queryParameter("__inchst"))
            assertTrue(request.body.readUtf8().contains("post_content=Body%20%26%20text"))
            server.enqueue(MockResponse().setBody("""{"data":{"0":0,"1":"发帖成功"}}"""))
            assertTrue(client.publish(PublishRequest(PublishAction.REPLY, "7", "Reply", topicId = "123")) is WriteResult.Confirmed)
        }
    }
    @Test fun publishSupportsOfficialMessageCompletionWithoutFixedTitle() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), store())
            val responses = listOf(
                """{"data":{"__MESSAGE":[0,"发表完成",null,null,null,null,"/read.php?tid=123&pid=456"]}}""",
                """{"data":{"__MESSAGE":[false,"发表完成",null,null,null,null,"/read.php?tid=123&pid=456"]}}""",
                """{"error":["informational notice"],"data":{"__MESSAGE":{"0":"0","1":"操作完成","6":"https://bbs.nga.cn/read.php?tid=123#pid456"}}}""",
            )
            responses.forEach { raw ->
                server.enqueue(MockResponse().setBody(raw))
                assertTrue(client.publish(PublishRequest(PublishAction.REPLY, "", "Private draft", topicId = "123")) is WriteResult.Confirmed)
            }
            assertEquals(responses.size, server.requestCount)
        }
    }

    @Test fun publishAcceptsMissingAndUnfamiliarCompletionStatus() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), store())
            val messages = listOf(
                """{"1":"操作完成","6":"/read.php?tid=123"}""",
                """{"0":null,"1":"操作完成","6":"/read.php?tid=123"}""",
                """{"0":1,"1":"操作完成"}""",
                """{"0":"unknown","1":"操作完成"}""",
            )
            messages.forEach { message ->
                server.enqueue(MockResponse().setBody("""{"data":{"__MESSAGE":$message}}"""))
                assertTrue(client.publish(PublishRequest(PublishAction.REPLY, "", "Private draft", topicId = "123")) is WriteResult.Confirmed)
            }
            assertEquals(messages.size, server.requestCount)
        }
    }

    @Test fun writesNeverReplayOnRetryAfterOrRedirect() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), store())
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
            server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", server.url("/redirect")))
            val draft = PublishRequest(PublishAction.REPLY, "7", "Reply", topicId = "123")
            assertEquals(WriteResult.Unconfirmed(), client.publish(draft))
            assertEquals(1, server.requestCount)
            assertEquals(WriteResult.Unconfirmed(), client.publish(draft))
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun guestWritesAndPrivateReadsNeverReachNetwork() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), MemorySessionStore())
            assertTrue(client.publish(PublishRequest(PublishAction.REPLY, "7", "Reply", topicId = "123")) is WriteResult.Rejected)
            assertTrue(runCatching { client.getNotifications() }.exceptionOrNull() is NgaException.Authentication)
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun malformedSuccessIsAcceptedButKnownErrorsAreRejected() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), store())
            val draft = PublishRequest(PublishAction.QUOTE, "7", "Reply", topicId = "123", postId = "9")
            server.enqueue(MockResponse().setBody("<html>Unknown</html>"))
            assertTrue(client.publish(draft) is WriteResult.Confirmed)
            server.enqueue(MockResponse().setBody("""{"error":{"0":"Permission denied"}}"""))
            assertTrue(client.publish(draft) is WriteResult.Rejected)
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun unknownCommunitySchemaIsNotEmptySuccess() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":{"unexpected":"message"}}"""))
            assertTrue(runCatching { HttpNgaClient(server.url("/"), store()).getNotifications() }.exceptionOrNull() is NgaException.Protocol)
        }
    }
    @Test fun invalidDraftAndAmbiguousCookiesAreRejected() {
        assertTrue(runCatching { PublishRequest(PublishAction.NEW_TOPIC, "7", "Body") }.isFailure)
        assertTrue(runCatching { PublishRequest(PublishAction.QUOTE, "7", "Body", topicId = "123") }.isFailure)
        assertNull(parsePassportCookies("ngaPassportUid=guest; ngaPassportCid=token"))
        assertNull(parsePassportCookies("ngaPassportUid=7; ngaPassportUid=8; ngaPassportCid=token"))
        assertNull(parsePassportCookies("ngaPassportUid=7; ngaPassportCid=one; ngaPassportCid=two"))
        assertEquals("7", parsePassportCookies("ngaPassportUid=guest; ngaPassportUid=7; ngaPassportCid=token")!!.userId)
        assertFalse(PublishRequest(PublishAction.REPLY, "7", "private-content", topicId = "123").toString().contains("private-content"))
    }
}
