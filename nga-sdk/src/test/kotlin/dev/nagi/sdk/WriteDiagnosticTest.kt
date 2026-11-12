package dev.nagi.sdk

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class WriteDiagnosticTest {
    private suspend fun sessions() = MemorySessionStore().apply { saveSession(NgaSession("7", "PRIVATE_TOKEN")) }

    @Test fun optionalReceiptFailureDoesNotRejectWriteAndDiagnosticsStayBounded() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":["PRIVATE_RESPONSE",-1,0],"PRIVATE_KEY":"PRIVATE_TOKEN"}"""))
            val client = HttpNgaClient(server.url("/"), sessions(), writeDiagnostics = true)
            val result = client.ratePost("123", "12", PostRating.UP)
            assertTrue(result is WriteResult.Confirmed)
            assertNull((result as WriteResult.Confirmed).ratingReceipt)
            assertNull(result.diagnostic)
            val diagnostic = NgaParser.writeDiagnostic("""{"data":["PRIVATE_RESPONSE",-1,0],"PRIVATE_KEY":"PRIVATE_TOKEN"}""", 200)
            assertEquals(200, diagnostic.httpStatus)
            assertEquals(WriteResponseFormat.JSON, diagnostic.format)
            assertEquals(WriteFieldShape.ARRAY, diagnostic.data)
            assertEquals(WriteFieldShape.STRING, diagnostic.data0)
            assertEquals(WriteFieldShape.NUMBER, diagnostic.data1)
            assertEquals(-1, diagnostic.delta)
            assertEquals(0, diagnostic.flags)
            assertFalse(diagnostic.toString().contains("PRIVATE"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun unknownReplyIsAcceptedWithoutExposingResponseText() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"error":{"0":"39:PRIVATE_RESPONSE","1":"PRIVATE_DRAFT","code":"PRIVATE_TOKEN"}}"""))
            val result = HttpNgaClient(server.url("/"), sessions(), writeDiagnostics = true)
                .publish(PublishRequest(PublishAction.REPLY, "", "PRIVATE_DRAFT", topicId = "123"))
            assertTrue(result is WriteResult.Confirmed)
            assertNull(result.diagnostic)
            val diagnostic = NgaParser.writeDiagnostic("""{"error":{"0":"39:PRIVATE_RESPONSE","1":"PRIVATE_DRAFT","code":"PRIVATE_TOKEN"}}""", 200)
            assertEquals(WriteFieldShape.OBJECT, diagnostic.error)
            assertEquals(WriteFieldShape.STRING, diagnostic.error0)
            assertEquals(39, diagnostic.errorCode)
            assertFalse(result.toString().contains("PRIVATE"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun disabledDiagnosticsLeaveExistingOutcomesUnchanged() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), sessions())
            server.enqueue(MockResponse().setBody("""{"data":["PRIVATE_RESPONSE",-1,0]}"""))
            assertEquals(WriteResult.Confirmed("评价已提交"), client.ratePost("123", "12", PostRating.UP))
            server.enqueue(MockResponse().setBody("""{"error":["PRIVATE_RESPONSE"]}"""))
            assertNull(client.publish(PublishRequest(PublishAction.REPLY, "", "PRIVATE_DRAFT", topicId = "123")).diagnostic)
        }
    }

    @Test fun httpFailuresNeverFollowRedirectsAndKeepSafeBodyShapes() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", server.url("/PRIVATE_TOKEN"))
                .setBody("<html>PRIVATE_RESPONSE</html>"))
            val result = HttpNgaClient(server.url("/"), sessions(), writeDiagnostics = true).ratePost("123", "12", PostRating.UP)
            assertTrue(result is WriteResult.Unconfirmed)
            assertEquals(307, result.diagnostic!!.httpStatus)
            assertEquals(WriteResponseFormat.HTML, result.diagnostic!!.format)
            assertEquals(WriteFailure.HTTP, result.diagnostic!!.failure)
            assertFalse(result.toString().contains("PRIVATE"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun officialMessageDiagnosticExposesTypesAndStatusButNotMessageOrLocation() {
        val raw = """{"data":{"__MESSAGE":[0,"PRIVATE_RESPONSE",null,null,null,null,"https://PRIVATE_TOKEN"]}}"""
        val diagnostic = NgaParser.writeDiagnostic(raw, 200)
        assertEquals(WriteFieldShape.ARRAY, diagnostic.message)
        assertEquals(WriteFieldShape.NUMBER, diagnostic.message0)
        assertEquals(WriteFieldShape.STRING, diagnostic.message1)
        assertEquals(0, diagnostic.messageStatus)
        assertFalse(diagnostic.toString().contains("PRIVATE"))
    }

    @Test fun malformedAndHostileMetadataNeverBecomesDiagnosticText() {
        val raw = """{"data":["PRIVATE_RESPONSE",123456789,123456789],"error":{"code":"https://PRIVATE_TOKEN","0":"123456789:PRIVATE_DRAFT"}}"""
        val diagnostic = NgaParser.writeDiagnostic(raw, 200)
        assertNull(diagnostic.delta)
        assertNull(diagnostic.flags)
        assertNull(diagnostic.errorCode)
        assertFalse(diagnostic.toString().contains("PRIVATE"))
        assertEquals(WriteFieldShape.MISSING, NgaParser.writeDiagnostic("{PRIVATE_RESPONSE", 200).data)
        assertEquals(WriteResponseFormat.EMPTY, NgaParser.writeDiagnostic("", 200).format)
    }
}
