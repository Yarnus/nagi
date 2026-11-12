package dev.nagi.sdk

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class WriteRejectionTest {
    @Test fun publishRetainsSafeRejectionCategoriesWithoutServerTextOrReplay() = runBlocking {
        val cases = listOf(
            "正文长度限制" to NgaRejectionReason.CONTENT_LENGTH,
            "含有敏感词" to NgaRejectionReason.CONTENT_FILTER,
            "请输入验证码" to NgaRejectionReason.VERIFICATION,
            "请求参数错误" to NgaRejectionReason.INVALID_PARAMETERS,
            "重复提交" to NgaRejectionReason.DUPLICATE,
            "您已被禁言" to NgaRejectionReason.MUTED,
            "操作过于频繁" to NgaRejectionReason.RATE_LIMIT,
            "没有权限" to NgaRejectionReason.PERMISSION,
        )
        MockWebServer().use { server ->
            val sessions = MemorySessionStore().apply { saveSession(NgaSession("7", "test-token")) }
            val client = HttpNgaClient(server.url("/"), sessions)
            cases.forEach { (text, expected) ->
                server.enqueue(MockResponse().setBody("""{"error":{"0":"$text PRIVATE_RESPONSE"}}"""))
                val result = client.publish(PublishRequest(PublishAction.REPLY, "", "Private draft", topicId = "123"))
                assertTrue(result is WriteResult.Rejected)
                val error = (result as WriteResult.Rejected).reason
                val reason = when (error) {
                    is NgaException.Restricted -> error.reason
                    is NgaException.Business -> error.reason
                    else -> null
                }
                assertEquals(text, expected, reason)
                assertFalse(error.toString().contains("PRIVATE_RESPONSE"))
                assertFalse(error.toString().contains(text))
                assertNull(error.cause)
            }
            assertEquals(cases.size, server.requestCount)
        }
    }

    @Test fun unknownErrorsStayUnknownAndAuthenticationStaysDistinct() {
        val error = classifyNgaError("Unknown business error PRIVATE_RESPONSE") as NgaException.Business
        assertEquals(NgaRejectionReason.UNKNOWN, error.reason)
        assertFalse(error.toString().contains("PRIVATE_RESPONSE"))
        assertTrue(classifyNgaError("请先登录") is NgaException.Authentication)
        assertTrue(classifyNgaError("15:session expired") is NgaException.Authentication)
        assertEquals(NgaRejectionReason.CONTENT_LENGTH,
            (NgaXmlParser.extractErrorOrNull("<root><error>字数太短</error></root>") as NgaException.Business).reason)
    }
}
