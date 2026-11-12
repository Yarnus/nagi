package dev.nagi.sdk

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.parser.Parser

internal object NgaInteractionParser {
    private fun records(value: Any?): List<JSONObject> = when (value) {
        is JSONObject -> value.keys().asSequence().toList()
            .sortedWith(compareBy<String> { it.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it })
            .map { value.optJSONObject(it) ?: throw NgaException.Protocol(ProtocolReason.MESSAGE_RECORD_INVALID) }
        is JSONArray -> (0 until value.length()).map { value.optJSONObject(it) ?: throw NgaException.Protocol(ProtocolReason.MESSAGE_RECORD_INVALID) }
        else -> throw NgaException.Protocol(ProtocolReason.MESSAGE_RECORDS_SHAPE)
    }

    private fun JSONObject.identity(key: String): String = optString(key).takeIf { it.isNotBlank() && it != "null" }
        ?: throw NgaException.Protocol(ProtocolReason.MESSAGE_IDENTITY)

    private fun JSONObject.hasNext(): Boolean {
        val value = opt("nextPage")
        if (value == null || value == JSONObject.NULL) return false
        if (value !is String && value !is Number) throw NgaException.Protocol(ProtocolReason.MESSAGE_NEXT_PAGE)
        return value.toString().isNotBlank() && value.toString() != "0"
    }

    private fun participants(raw: String): List<MessageParticipant> {
        if (raw.isBlank()) return emptyList()
        val parts = raw.split('\t')
        return parts.chunked(2).mapNotNull {
            if (it.size == 2 && it[0].isNotBlank() && it[1].isNotBlank()) MessageParticipant(it[0], plain(it[1])) else null
        }
    }

    fun conversations(raw: String, page: Int): MessageConversationPage {
        val data = NgaParser.data(raw)
        val listing = data.opt("0")
        val conversations = if (listing is JSONObject) JSONObject().apply {
            listing.keys().asSequence()
                .filterNot { it in setOf("currentPage", "rowsPerPage", "nextPage") }
                .forEach { put(it, listing.get(it)) }
        } else listing
        val items = records(conversations).map { item ->
            MessageConversation(
                id = item.identity("mid"), title = plain(item.optString("subject")),
                senderId = item.identity("from"), senderName = plain(item.optString("from_username")),
                createdAt = item.optLong("time"), lastActiveAt = item.optLong("last_modify"),
                postCount = item.optInt("posts"), participants = participants(item.optString("all_user")),
            )
        }
        val pagination = (listing as? JSONObject)?.takeIf { it.has("nextPage") } ?: data
        return MessageConversationPage(items, page, pagination.hasNext())
    }

    fun messagePosts(raw: String, page: Int): MessagePostPage {
        val data = NgaParser.data(raw).optJSONObject("0") ?: throw NgaException.Protocol(ProtocolReason.MESSAGE_DATA_NODE)
        val userInfo = data.opt("userInfo")
        val userRecords = if (userInfo is JSONObject) JSONObject().apply {
            userInfo.keys().asSequence().filterNot { it == "__GROUPS" }
                .forEach { put(it, userInfo.get(it)) }
        } else userInfo
        val users = if (data.has("userInfo")) records(userRecords).map {
            MessageParticipant(it.identity("uid"), plain(it.optString("username")))
        } else participants(data.optString("allUsers"))
        val posts = records(data.opt("allmsgs")).map { item ->
            if (!item.has("content") || item.isNull("content")) throw NgaException.Protocol(ProtocolReason.MESSAGE_CONTENT)
            MessagePost(item.identity("id"), item.identity("from"), plain(item.optString("subject")),
                ContentParser.parse(item.getString("content")), item.optLong("time"))
        }
        return MessagePostPage(posts, users, page, data.hasNext())
    }
}

internal fun decodeNgaEditor(value: String): String {
    val decodedAmpersands = value.replace("&amp;", "&")
    val units = Regex("&#(\\d{1,5});").replace(decodedAmpersands) { match ->
        val unit = match.groupValues[1].toInt()
        if (unit in 0xD800..0xDFFF) unit.toChar().toString() else match.value
    }
    return Parser.unescapeEntities(units, false)
}

internal fun escapeNgaSubmission(value: String): String {
    val encoder = charset("GBK").newEncoder()
    return buildString {
        var offset = 0
        while (offset < value.length) {
            val codePoint = value.codePointAt(offset)
            val text = String(Character.toChars(codePoint))
            if (encoder.canEncode(text)) append(text)
            else text.forEach { append("&#").append(it.code).append(';') }
            offset += text.length
        }
    }
}
