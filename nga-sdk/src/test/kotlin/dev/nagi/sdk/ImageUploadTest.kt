package dev.nagi.sdk

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class ImageUploadTest {
    private val target = PublishTarget(PublishAction.REPLY, topicId = "123")
    private val raw = """<root><action>reply</action><tid>123</tid><pid>0</pid><fid>7</fid><__CU><uid>8</uid></__CU><auth>UPLOAD_SECRET</auth><attach_url>https://img8.nga.cn/attach.php</attach_url></root>"""
    private fun image() = ImageUpload(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0))

    @Test fun preparationBindsGrantToAccountAndTargetWithoutLoggingAuthorization() {
        val grant = NgaXmlParser.uploadGrant(raw, target, "8")
        assertEquals("7", grant.forumId)
        assertEquals("UPLOAD_SECRET", grant.auth)
        assertFalse(grant.toString().contains("UPLOAD_SECRET"))
        for (bad in listOf(raw.replace("<uid>8", "<uid>9"), raw.replace("<tid>123", "<tid>456"),
            raw.replace("<auth>UPLOAD_SECRET</auth>", ""), raw.replace("<fid>7</fid>", ""))) {
            assertThrows(NgaException.Protocol::class.java) { NgaXmlParser.uploadGrant(bad, target, "8") }
        }
    }

    @Test fun onlyExactHttpsUploadEndpointsAcceptAuthorization() {
        assertTrue(isTrustedUploadUrl("https://img8.nga.cn/attach.php".toHttpUrl()))
        listOf("http://img8.nga.cn/attach.php", "https://img8.nga.cn.evil.test/attach.php",
            "https://img8.nga.cn:8443/attach.php", "https://secret@img8.nga.cn/attach.php",
            "https://img8.nga.cn/other.php", "https://evil.test/attach.php").forEach {
            assertFalse(it, isTrustedUploadUrl(it.toHttpUrl()))
        }
    }

    @Test fun untrustedUploadUrlIsRejectedBeforeAnyUpload() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(raw.replace("https://img8.nga.cn/attach.php", "https://evil.test/attach.php")))
            val store = MemorySessionStore().apply { saveSession(NgaSession("8", "test")) }
            val error = runCatching { HttpNgaClient(server.url("/"), store).uploadImage(target, image()) }.exceptionOrNull()
            assertTrue(error is NgaException.Restricted)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun multipartIncludesAuthorizationAndImageButNoAccountTokenForImageHost() {
        val parts = imageUploadParts(NgaXmlParser.uploadGrant(raw, target, "8"), image(), "bbs.nga.cn")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply { parts.forEach(::addPart) }.build()
        val buffer = Buffer(); body.writeTo(buffer)
        val text = buffer.readUtf8()
        assertTrue(text.contains("name=\"auth\""))
        assertTrue(text.contains("\r\n\r\nUPLOAD_SECRET\r\n"))
        assertTrue(text.contains("name=\"attachment_file1\"; filename=\""))
        assertTrue(text.contains("Content-Type: image/jpeg"))
        assertTrue(text.contains("name=\"attachment_file1_auto_size\""))
        assertTrue(parts.any { it.headers?.get("Content-Disposition")?.contains("attachment_file1_auto_size") == true &&
            Buffer().also { buffer -> it.body.writeTo(buffer) }.readUtf8() == "1" })
        val request = Request.Builder().url("https://img8.nga.cn/attach.php").post(body).build()
            .withNgaSession(NgaSession("8", "ACCOUNT_SECRET"))
        assertNull(request.header("Cookie"))
        assertFalse(text.contains("ACCOUNT_SECRET"))
    }

    @Test fun invalidImagesAndOversizeInputsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ImageUpload(byteArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { ImageUpload("not an image".toByteArray()) }
        val bytes = ByteArray(ImageUpload.MAX_BYTES + 1); image().bytes.copyInto(bytes)
        assertThrows(IllegalArgumentException::class.java) { ImageUpload(bytes) }
    }

    @Test fun receiptRequiresUsableAttachmentAndPublicationIncludesChecks() = runBlocking {
        val receipt = """<root><attachments>aid~one</attachments><attachments_check>CHECK_SECRET</attachments_check><url>./mon_202611/one.jpg</url></root>"""
        val attachment = NgaXmlParser.uploadedAttachment(receipt)!!
        assertEquals("[img]./mon_202611/one.jpg[/img]", attachment.bbcode)
        assertFalse(attachment.toString().contains("CHECK_SECRET"))
        assertNull(NgaXmlParser.uploadedAttachment("<root/>"))
        assertNull(NgaXmlParser.uploadedAttachment(receipt.replace("./mon_202611/one.jpg", "https://evil.test/a.jpg")))
        assertNull(NgaXmlParser.uploadedAttachment(receipt.replace("aid~one", "aid~one\tINJECT")))
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(""))
            val store = MemorySessionStore().apply { saveSession(NgaSession("8", "test")) }
            val result = HttpNgaClient(server.url("/"), store).publish(PublishRequest(PublishAction.REPLY, "", attachment.bbcode,
                topicId = "123", attachments = listOf(attachment)))
            assertTrue(result is WriteResult.Confirmed)
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("attachments=aid%7Eone"))
            assertTrue(body.contains("attachments_check=CHECK_SECRET"))
        }
    }
}
