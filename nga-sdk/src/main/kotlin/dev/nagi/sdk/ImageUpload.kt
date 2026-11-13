package dev.nagi.sdk

import okhttp3.HttpUrl
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class ImageUpload(val bytes: ByteArray) {
    val extension: String = when {
        bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "jpg"
        bytes.take(8) == listOf(137, 80, 78, 71, 13, 10, 26, 10).map { it.toByte() } -> "png"
        bytes.size >= 6 && String(bytes, 0, 6, Charsets.US_ASCII) in setOf("GIF87a", "GIF89a") -> "gif"
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
        else -> throw IllegalArgumentException("Unsupported image")
    }
    val mediaType get() = if (extension == "jpg") "image/jpeg" else "image/$extension"
    init { require(bytes.size in 1..MAX_BYTES) }
    override fun toString() = "ImageUpload(redacted)"
    companion object { const val MAX_BYTES = 10 * 1024 * 1024 }
}

class UploadedAttachment(val name: String, val check: String, val url: String) {
    init {
        require(name.isNotBlank() && check.isNotBlank() && url.isNotBlank())
        require(listOf(name, check, url).none { it.any { char -> char == '\t' || char == '\n' || char == '\r' } })
        require(url.none { it == '[' || it == ']' })
    }
    val bbcode get() = "[img]$url[/img]"
    override fun toString() = "UploadedAttachment(redacted)"
}

class ImageUploadResult(val outcome: WriteResult, val attachment: UploadedAttachment? = null) {
    override fun toString() = "ImageUploadResult(attachment=${attachment != null})"
}

internal class UploadGrant(val url: String, val auth: String, val forumId: String) {
    override fun toString() = "UploadGrant(redacted)"
}

internal fun imageUploadParts(grant: UploadGrant, image: ImageUpload, originHost: String): List<MultipartBody.Part> {
    val name = "${java.util.UUID.randomUUID()}.${image.extension}"
    return mapOf("v2" to "1", "func" to "upload", "auth" to grant.auth, "fid" to grant.forumId,
        "origin_domain" to originHost, "lite" to "xml", "attachment_file1_img" to "1",
        "attachment_file1_dscp" to name, "attachment_file1_url_utf8_name" to name,
        "attachment_file1_auto_size" to "1", "attachment_file1_watermark" to "")
        .map { (key, value) -> MultipartBody.Part.createFormData(key, value) } +
        MultipartBody.Part.createFormData("attachment_file1", name, image.bytes.toRequestBody(image.mediaType.toMediaType()))
}

internal fun isTrustedUploadUrl(url: HttpUrl): Boolean =
    url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
        url.fragment == null && url.encodedPath == "/attach.php" &&
        (isCredentialOrigin(url) || url.host in setOf("img8.nga.cn", "img8.nga.178.com"))
