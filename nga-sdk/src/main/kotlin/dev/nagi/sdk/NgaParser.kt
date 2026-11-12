package dev.nagi.sdk

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

internal object NgaParser {
    private fun normalizeEnvelope(raw: String): String {
        val initial = raw.trim().removePrefix("\uFEFF").trim()
            .removePrefix("window.script_muti_get_var_store=").trim().removePrefix("/*\$js\$*/").trim()
        val cleaned = StringBuilder(initial.length)
        var quote: Char? = null
        var escaped = false
        var numericKeyEnd = -1
        for (index in initial.indices) {
            val char = initial[index]
            if (quote != null) {
                if (escaped) {
                    escaped = false
                    cleaned.append(char)
                } else if (char == '\\') {
                    escaped = true
                    cleaned.append(char)
                } else if (char == quote) {
                    quote = null
                    cleaned.append(char)
                } else if (char.code < 0x20) {
                    when (char) {
                        '\t' -> cleaned.append("\\t")
                        '\n' -> cleaned.append("\\n")
                        '\r' -> cleaned.append("\\r")
                    }
                } else {
                    cleaned.append(char)
                }
            } else {
                if (initial.startsWith("/*error fill content", index)) break
                if (char in '0'..'9' && index >= numericKeyEnd) {
                    var before = index - 1
                    while (before >= 0 && initial[before].isWhitespace()) before--
                    if (before >= 0 && initial[before] in "{,") {
                        var end = index + 1
                        while (end < initial.length && initial[end] in '0'..'9') end++
                        var after = end
                        while (after < initial.length && initial[after].isWhitespace()) after++
                        if (after < initial.length && initial[after] == ':') {
                            cleaned.append('"')
                            numericKeyEnd = end
                        }
                    }
                }
                if (char == '"' || char == '\'') quote = char
                cleaned.append(char)
                if (index + 1 == numericKeyEnd) cleaned.append('"')
            }
        }
        val text = cleaned.toString().trimEnd().removeSuffix("/*\$js\$*/").trimEnd().removeSuffix(";").trimEnd()
        val outsideStrings = BooleanArray(text.length)
        quote = null
        escaped = false
        for (index in text.indices) {
            val char = text[index]
            outsideStrings[index] = quote == null
            if (quote != null) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == quote) quote = null
            } else if (char == '"' || char == '\'') quote = char
        }
        return text.replace(Regex("""([{,]\s*)"(content|subject|author)"\s*:\s*(\+\d+|0\d+)(\s*[,}])""")) {
            if (!outsideStrings[it.range.first]) it.value
            else "${it.groupValues[1]}\"${it.groupValues[2]}\":\"${it.groupValues[3]}\"${it.groupValues[4]}"
        }
    }
    fun extractErrorOrNull(raw: String): NgaException? {
        val trimmed = raw.trimStart().removePrefix("\uFEFF").trimStart()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("window.script_muti_get_var_store=")) return null
        return try {
            val value = JSONObject(normalizeEnvelope(raw))
            if (value.has("error") && !value.isNull("error")) classifyNgaError(value.get("error").toString()) else null
        } catch (_: Exception) {
            null
        }
    }
    private fun root(raw: String): JSONObject {
        try {
            val value = JSONObject(normalizeEnvelope(raw))
            if (value.has("error") && !value.isNull("error")) {
                throw classifyNgaError(value.get("error").toString())
            }
            return value
        } catch (error: NgaException) { throw error }
        catch (_: Exception) { throw NgaException.Protocol(ProtocolReason.JSON_MALFORMED) }
    }
    fun data(raw: String): JSONObject = root(raw).optJSONObject("data") ?: throw NgaException.Protocol(ProtocolReason.JSON_DATA_MISSING)
    private fun records(value: Any?): List<JSONObject> = when (value) {
        is JSONObject -> value.keys().asSequence().toList().sortedWith(compareBy<String> { it.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it })
            .map { value.optJSONObject(it) ?: throw NgaException.Protocol(ProtocolReason.JSON_RECORD_INVALID) }
        is JSONArray -> (0 until value.length()).map { value.optJSONObject(it) ?: throw NgaException.Protocol(ProtocolReason.JSON_RECORD_INVALID) }
        else -> throw NgaException.Protocol(ProtocolReason.JSON_RECORDS_SHAPE)
    }
    fun forums(raw: String): List<Forum> {
        val root = root(raw)
        val icons = root.optJSONObject("other")?.optJSONObject("forum_icon_list")
        val forumIcons = directoryIcons(icons, "f")
        val collectionIcons = directoryIcons(icons, "s")
        return records(root.optJSONObject("data") ?: throw NgaException.Protocol(ProtocolReason.JSON_DATA_MISSING)).flatMap { category ->
            records(category.opt("groups")).flatMap { group ->
                records(group.opt("forums")).map { forum ->
                    val id = forum.optString("fid")
                    val collection = forum.optString("stid").takeIf { it.isNotBlank() && it != "0" }
                    val name = plain(forum.optString("name"))
                    if ((id.isBlank() && collection == null) || name.isBlank()) throw NgaException.Protocol(ProtocolReason.JSON_FORUM_INVALID)
                    Forum(id, name, plain(category.optString("name")), plain(group.optString("name")), collection,
                        description = if (forum.isNull("info")) "" else plain(forum.optString("info")),
                        iconUrl = if (collection != null) collectionIcons[collection] else forumIcons[id])
                }
            }
        }.distinctBy { it.key }
    }

    private fun directoryIcons(metadata: JSONObject?, kind: String): Map<String, String> {
        metadata ?: return emptyMap()
        val prefix = metadata.optString("${kind}_px_l").toHttpUrlOrNull() ?: return emptyMap()
        if (!prefix.isHttps || prefix.host != "img4.nga.cn" || prefix.port != 443 ||
            prefix.username.isNotEmpty() || prefix.password.isNotEmpty() ||
            prefix.query != null || prefix.fragment != null || !prefix.encodedPath.endsWith("/")) return emptyMap()
        val suffix = metadata.optString("${kind}_sx_l")
        if (!suffix.matches(Regex("\\.[a-zA-Z0-9]+"))) return emptyMap()
        return metadata.optString(kind).split(',').chunked(2).mapNotNull { pair ->
            val id = pair[0].trim()
            val version = pair.getOrNull(1)?.trim()
            if (id.toLongOrNull() == null || version == null || !version.matches(Regex("\\?[0-9]+"))) null
            else id to "${prefix}$id$suffix$version"
        }.toMap()
    }

    fun topics(raw: String, page: Int, defaultPageSize: Int = 30): TopicPage {
        val data = data(raw)
        if (!data.has("__T")) throw NgaException.Protocol(ProtocolReason.JSON_TOPICS_NODE)
        val items = records(data.opt("__T")).map { item ->
            val id = item.optString("tid")
            if (id.isBlank()) throw NgaException.Protocol(ProtocolReason.JSON_TOPIC_ID)
            Topic(id, plain(item.optString("subject")), plain(item.optString("author")), item.optString("authorid"),
                item.optLong("postdate"), item.optLong("lastpost"), item.optInt("replies"), item.optInt("recommend") > 0)
        }
        val total = data.optInt("__ROWS", items.size).coerceAtLeast(0)
        val pageSize = data.optInt("__T__ROWS_PAGE", defaultPageSize).coerceAtLeast(1)
        return TopicPage(items, page, total, items.isNotEmpty() && page.toLong() * pageSize < total)
    }
    fun posts(raw: String, page: Int): PostPage {
        val data = data(raw)
        val topic = data.optJSONObject("__T") ?: throw NgaException.Protocol(ProtocolReason.JSON_THREAD_NODE)
        val users = data.optJSONObject("__U")
        val groups = parseJsonGroups(users?.optJSONObject("__GROUPS") ?: data.optJSONObject("__GROUPS"))
        val base = data.optJSONObject("__GLOBAL")?.optString("_ATTACH_BASE_VIEW")
            ?.takeIf { it.isNotBlank() } ?: "https://img.nga.cn/attachments/"
        fun parsePost(item: JSONObject, fallbackFloor: Int, depth: Int): Post {
            if (depth > 8) throw NgaException.Protocol(ProtocolReason.JSON_COMMENT_DEPTH)
            val uid = item.optString("authorid")
            val user = users?.optJSONObject(uid)
            val floor = item.optInt("lou", fallbackFloor)
            val pid = item.optString("pid").ifBlank { "floor:$floor" }
            val memberId = user?.optString("memberid").orEmpty().ifBlank { user?.optString("groupid").orEmpty() }
            val level = groups[memberId] ?: user?.optString("group")?.takeIf { it.isNotBlank() && it != "null" }?.let(::plain)
            val reputation = formatReputation(user?.optString("rvrc").orEmpty().ifBlank { user?.optString("fame").orEmpty() })
            val postCount = parsePostCount(user?.optString("postnum").orEmpty().ifBlank { user?.optString("posts").orEmpty() })
            val clientPlatform = normalizeClientPlatform(item.optString("from_client"))
            return Post(
                id = pid,
                floor = floor,
                author = plain(item.optString("author").ifBlank { user?.optString("username") ?: uid }),
                authorId = uid,
                avatar = resourceUrl(user?.optString("avatar").orEmpty(), "https://img.nga.cn/"),
                date = item.optString("postdate"),
                content = ContentParser.parse(item.optString("content", item.optString("subject")), base),
                comments = if (item.has("comment")) records(item.opt("comment")).mapIndexed { index, comment ->
                    parsePost(comment, index, depth + 1)
                } else emptyList(),
                score = item.optInt("score", 0),
                authorLevel = level,
                authorReputation = reputation,
                authorPosts = postCount,
                clientPlatform = clientPlatform,
            )
        }
        val items = records(data.opt("__R")).mapIndexed { index, item -> parsePost(item, (page - 1) * 30 + index, 0) }
        val hasTotal = data.has("__ROWS")
        val total = data.optInt("__ROWS", items.size).coerceAtLeast(0)
        val size = data.optInt("__R__ROWS_PAGE", 0).coerceAtLeast(0)
        val paginationSize = size.takeIf { it > 0 } ?: 30
        return PostPage(items, plain(topic.optString("subject")), topic.optString("authorid"), page, total,
            items.isNotEmpty() && page.toLong() * paginationSize < total, size,
            if (hasTotal && size > 0) ((total + size - 1L) / size).toInt().coerceAtLeast(page) else 0)
    }
    private fun collect(value: Any?, depth: Int = 0): List<JSONObject> {
        if (depth > 12) throw NgaException.Protocol(ProtocolReason.JSON_COLLECT_DEPTH)
        return when (value) {
            is JSONObject -> {
                val identified = value.has("tid") || value.has("mid") || value.has("id")
                if (identified) listOf(value)
                else value.keys().asSequence().flatMap { collect(value.opt(it), depth + 1).asSequence() }.toList()
            }
            is JSONArray -> (0 until value.length()).flatMap { collect(value.opt(it), depth + 1) }
            else -> emptyList()
        }
    }
    private fun identifiedRecords(raw: String): List<JSONObject> {
        val payload = data(raw)
        val items = collect(payload)
        fun hasEmptyCollection(value: Any?): Boolean = when (value) {
            is JSONObject -> value.length() == 0 || value.keys().asSequence().any { hasEmptyCollection(value.opt(it)) }
            is JSONArray -> value.length() == 0 || (0 until value.length()).any { hasEmptyCollection(value.opt(it)) }
            else -> false
        }
        if (items.isEmpty() && !hasEmptyCollection(payload)) throw NgaException.Protocol(ProtocolReason.JSON_COMMUNITY_EMPTY)
        return items
    }
    fun community(raw: String): List<CommunityItem> = identifiedRecords(raw).map { item ->
        val topic = item.optString("tid", item.optString("topic_id"))
        val post = item.optString("pid", item.optString("post_id"))
        val id = item.optString("id", item.optString("mid", "$topic:$post"))
        CommunityItem(id, plain(item.optString("subject", item.optString("title"))),
            plain(item.optString("content", item.optString("message", item.optString("msg")))),
            plain(item.optString("username", item.optString("author", item.optString("from_username")))),
            topic, post, item.optLong("time", item.optLong("postdate")),
            readFlag(item) == 0, unreadKnown = readFlag(item) != null)
    }.distinctBy { it.id }

    private fun readFlag(item: JSONObject): Int? =
        (item.opt("read") ?: item.opt("is_read"))?.toString()?.toIntOrNull()?.takeIf { it in 0..1 }

    fun notifications(raw: String): NotificationFeed {
        val payload = data(raw)
        val envelope = payload.optJSONObject("0")
        if (envelope != null && envelope.has("unread")) {
            val count = envelope.opt("unread")?.toString()?.toIntOrNull()?.takeIf { it >= 0 }
            fun group(key: String, message: Boolean): List<CommunityItem> {
                val value = envelope.opt(key) ?: return emptyList()
                if (value !is JSONArray && value !is JSONObject) throw NgaException.Protocol(ProtocolReason.JSON_COMMUNITY_EMPTY)
                return records(value).map { item ->
                    val type = item.optString("0")
                    val actorId = item.optString("1")
                    val time = item.optLong("9")
                    val topic = if (message) "" else item.optString("6")
                    val post = if (message) "" else item.optString("7", item.optString("8"))
                    if (type.isBlank() || !item.has("2")) throw NgaException.Protocol(ProtocolReason.JSON_COMMUNITY_EMPTY)
                    CommunityItem("$key:$type:$actorId:$topic:$post:$time", plain(item.optString("5")),
                        if (message) "收到短消息" else "社区通知", plain(item.optString("2")),
                        topic, post, time, unread = false, unreadKnown = false, privateMessage = message)
                }
            }
            return NotificationFeed((group("0", false) + group("1", true)).distinctBy { it.id }, count)
        }
        val items = community(raw)
        return NotificationFeed(items, if (items.all { it.unreadKnown }) items.count { it.unread } else null)
    }

    fun userReplies(raw: String, page: Int): UserReplyPage {
        val payload = data(raw)
        if (!payload.has("__T")) throw NgaException.Protocol(ProtocolReason.JSON_USER_TOPICS_NODE)
        val topics = topics(raw, page)
        val items = records(payload.opt("__T")).zip(topics.items).map { (item, topic) ->
            val post = item.optJSONObject("__P") ?: throw NgaException.Protocol(ProtocolReason.JSON_USER_POST_NODE)
            val pid = post.optString("pid")
            if (!pid.matches(Regex("[0-9]+"))) throw NgaException.Protocol(ProtocolReason.JSON_USER_POST_ID)
            if (post.optString("tid") != topic.id) throw NgaException.Protocol(ProtocolReason.JSON_USER_POST_TOPIC)
            if (!post.has("content")) throw NgaException.Protocol(ProtocolReason.JSON_USER_POST_CONTENT)
            UserReply(topic, pid, post.optString("authorid"), post.optLong("postdate"), ContentParser.parse(post.optString("content")))
        }
        val hasMore = if (items.isEmpty()) false else if (payload.has("__ROWS") && payload.optInt("__T__ROWS_PAGE") > 0)
            topics.hasMore else null
        return UserReplyPage(items, page, hasMore)
    }

    fun writeDiagnostic(raw: String, status: Int?): WriteDiagnostic {
        val trimmed = raw.trimStart().removePrefix("\uFEFF").trimStart()
        val format = when {
            trimmed.isEmpty() -> WriteResponseFormat.EMPTY
            trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("window.script_muti_get_var_store=") -> WriteResponseFormat.JSON
            trimmed.startsWith("<?xml") || trimmed.startsWith("<root") -> WriteResponseFormat.XML
            trimmed.startsWith("<") -> WriteResponseFormat.HTML
            else -> WriteResponseFormat.TEXT
        }
        val root = try { JSONObject(normalizeEnvelope(raw)) } catch (_: Exception) { null }
        fun field(node: Any?, index: Int): Any? = when (node) {
            is JSONObject -> node.opt(index.toString())
            is JSONArray -> node.opt(index)
            else -> null
        }
        fun shape(value: Any?): WriteFieldShape = when (value) {
            null -> WriteFieldShape.MISSING
            JSONObject.NULL -> WriteFieldShape.NULL
            is JSONObject -> WriteFieldShape.OBJECT
            is JSONArray -> WriteFieldShape.ARRAY
            is String -> WriteFieldShape.STRING
            is Number -> WriteFieldShape.NUMBER
            is Boolean -> WriteFieldShape.BOOLEAN
            else -> WriteFieldShape.MISSING
        }
        fun boundedInteger(value: Any?, range: IntRange): Int? =
            value?.toString()?.toIntOrNull()?.takeIf { it in range }
        val data = root?.opt("data")
        val error = root?.opt("error")
        val message = (data as? JSONObject)?.opt("__MESSAGE") ?: root?.opt("__MESSAGE")
        val explicitCode = boundedInteger((error as? JSONObject)?.opt("code"), 0..9999)
            ?: boundedInteger((error as? JSONObject)?.opt("error_code"), 0..9999)
            ?: boundedInteger(root?.opt("error_code"), 0..9999)
        val firstError = (field(error, 0) ?: error) as? String
        val prefixedCode = firstError?.let { Regex("^\\s*([0-9]{1,4}):").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        return WriteDiagnostic(
            httpStatus = status?.takeIf { it in 100..599 }, format = format,
            data = shape(data), data0 = shape(field(data, 0)), data1 = shape(field(data, 1)), data2 = shape(field(data, 2)),
            error = shape(error), error0 = shape(field(error, 0)), error1 = shape(field(error, 1)), error2 = shape(field(error, 2)),
            message = shape(message), message0 = shape(field(message, 0)), message1 = shape(field(message, 1)),
            message6 = shape(field(message, 6)), messageStatus = boundedInteger(field(message, 0), 0..9999),
            delta = boundedInteger(field(data, 1), -16..16), flags = boundedInteger(field(data, 2), 0..255),
            errorCode = explicitCode ?: prefixedCode,
        )
    }

    fun writeErrorOrNull(raw: String): NgaException? {
        fun known(error: NgaException?): NgaException? = when (error) {
            is NgaException.Business -> error.takeIf { it.reason != NgaRejectionReason.UNKNOWN }
            else -> error
        }
        known(NgaXmlParser.extractErrorOrNull(raw))?.let { return it }
        known(NgaHtmlPostParser.extractErrorOrNull(raw))?.let { return it }
        val envelope = try { JSONObject(normalizeEnvelope(raw)) } catch (_: Exception) { return null }
        if (envelope.has("error") && !envelope.isNull("error")) {
            known(classifyNgaError(envelope.get("error").toString()))?.let { return it }
        }
        val payload = envelope.opt("data")
        val completion = (payload as? JSONObject)?.opt("__MESSAGE") ?: envelope.opt("__MESSAGE") ?: payload
        val message = when (completion) {
            is JSONObject -> completion.opt("1")
            is JSONArray -> completion.opt(1)
            else -> null
        }
        return (message as? String)?.let { known(classifyNgaError(it)) }
    }

    fun rating(raw: String, requested: PostRating): WriteResult {
        writeErrorOrNull(raw)?.let { throw it }
        return WriteResult.Confirmed("评价已提交", ratingReceipt(raw, requested))
    }

    private fun ratingReceipt(raw: String, requested: PostRating): PostRatingReceipt? {
        val envelope = try { JSONObject(normalizeEnvelope(raw)).opt("data") } catch (_: Exception) { return null }
        fun field(index: Int): Any? = when (envelope) {
            is JSONObject -> envelope.opt(index.toString())
            is JSONArray -> envelope.opt(index)
            else -> null
        }
        val delta = field(1)?.toString()?.toIntOrNull() ?: return null
        val flags = field(2)?.toString()?.toIntOrNull() ?: return null
        if (flags < 0) return null
        val previous = when (flags and 3) {
            0 -> null
            1 -> PostRating.UP
            2 -> PostRating.DOWN
            else -> return null
        }
        val current = when (flags and 12) {
            0 -> null
            4 -> PostRating.UP
            8 -> PostRating.DOWN
            else -> return null
        }
        val matchesAction = if (previous == requested) current == null else current == requested
        if (!matchesAction || delta != (current?.value ?: 0) - (previous?.value ?: 0)) return null
        return PostRatingReceipt(delta, current)
    }

    fun publish(raw: String, target: PublishTarget): WriteResult {
        writeErrorOrNull(raw)?.let { throw it }
        return WriteResult.Confirmed(when (target.action) {
            PublishAction.NEW_TOPIC -> "主题已发布"
            PublishAction.MODIFY -> "修改已保存"
            else -> "回复已发送"
        })
    }

    fun user(raw: String, id: String): NgaUser {
        val payload = root(raw).opt("data")
        val user = when (payload) {
            is JSONArray -> if (payload.length() == 1) payload.optJSONObject(0) else null
            is JSONObject -> payload.optJSONObject("0") ?: payload
            else -> null
        } ?: throw NgaException.Protocol(ProtocolReason.JSON_USER_NODE)
        val uid = user.optString("uid", id)
        if (uid.isBlank() || !user.has("username")) throw NgaException.Protocol(ProtocolReason.JSON_USER_FIELDS)
        if (id.isNotBlank() && uid != id) throw NgaException.Protocol(ProtocolReason.JSON_USER_FIELDS)
        val location = (user.opt("ipLoc") as? String)?.let(::plain)?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
        val registered = user.opt("regdate")?.toString()?.toLongOrNull()?.takeIf { it > 0 && it <= 253402300799L }
        val signature = (user.opt("sign") as? String)?.takeIf { it.isNotBlank() }?.let(ContentParser::parse).orEmpty()
        return NgaUser(uid, plain(user.optString("username")),
            resourceUrl(user.optString("avatar"), "https://img.nga.cn/"), user.optInt("posts"), location, registered, signature)
    }
}

internal fun classifyNgaError(error: String): NgaException = when {
    error.contains("15:") || error.contains("登录") || error.contains("访客") -> NgaException.Authentication()
    error.contains("找不到用户") -> NgaException.NotFound()
    error.contains("permission denied", ignoreCase = true) || error.contains("access denied", ignoreCase = true) ->
        NgaException.Restricted(NgaRejectionReason.PERMISSION)
    error.contains("禁言") || error.contains("禁止发言") -> NgaException.Restricted(NgaRejectionReason.MUTED)
    error.contains("频繁") || error.contains("间隔") -> NgaException.Restricted(NgaRejectionReason.RATE_LIMIT)
    error.contains("权限") || error.contains("威望") || error.contains("锁定") || error.contains("禁止回复") ->
        NgaException.Restricted(NgaRejectionReason.PERMISSION)
    error.contains("验证码") || error.contains("验证") -> NgaException.Business(NgaRejectionReason.VERIFICATION)
    error.contains("字数") || error.contains("长度") || error.contains("太短") || error.contains("太长") ->
        NgaException.Business(NgaRejectionReason.CONTENT_LENGTH)
    error.contains("敏感词") || error.contains("禁止词") || error.contains("过滤") -> NgaException.Business(NgaRejectionReason.CONTENT_FILTER)
    error.contains("参数") -> NgaException.Business(NgaRejectionReason.INVALID_PARAMETERS)
    error.contains("重复") -> NgaException.Business(NgaRejectionReason.DUPLICATE)
    error.contains("限制") -> NgaException.Restricted()
    else -> NgaException.Business()
}

internal fun plain(value: String): String = Jsoup.parse(value.replace(Regex("(?i)<br\\s*/?>"), "\n")).text()

internal fun parseJsonGroups(groupsObj: JSONObject?): Map<String, String> {
    if (groupsObj == null) return emptyMap()
    val result = mutableMapOf<String, String>()
    for (key in groupsObj.keys()) {
        val entry = groupsObj.opt(key)
        val name = when (entry) {
            is JSONObject -> entry.optString("0").ifBlank { entry.optString("name") }
            is JSONArray -> entry.optString(0)
            is String -> entry
            else -> ""
        }.let(::plain).trim()
        if (key.isNotBlank() && name.isNotBlank()) {
            result[key] = name
        }
    }
    return result
}

internal fun formatReputation(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty() || trimmed == "null") return null
    val intVal = trimmed.toIntOrNull()
    if (intVal != null) {
        return java.lang.String.format(java.util.Locale.US, "%.1f", intVal / 10.0)
    }
    val doubleVal = trimmed.toDoubleOrNull() ?: return null
    return java.lang.String.format(java.util.Locale.US, "%.1f", doubleVal)
}

internal fun parsePostCount(raw: String?): Int? {
    val count = raw?.trim()?.toIntOrNull() ?: return null
    return count.takeIf { it >= 0 }
}

internal fun normalizeClientPlatform(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty() || trimmed == "null" || trimmed == "0") return null
    val lower = trimmed.lowercase(java.util.Locale.ROOT)
    return when {
        lower.contains("ios") || lower.contains("iphone") || lower.contains("ipad") || trimmed == "7" -> "iOS"
        lower.contains("android") || trimmed == "8" -> "Android"
        lower.contains("wp") || lower.contains("windows") || trimmed == "9" -> "Windows"
        else -> {
            val stripped = trimmed.replace(Regex("^\\d+\\s*"), "").trim()
            stripped.takeIf { it.isNotBlank() }
        }
    }
}

internal fun resourceUrl(raw: String, base: String): String {
    var value = raw.trim().replace("&amp;", "&").substringBefore("|.a")
    if (value.isBlank() || value == "null") return ""
    value = value.replace(Regex("https?://img\\d*\\.(nga\\.178\\.com|ngacn\\.cc|ngabbs\\.com)/attachments/"), "https://img.nga.cn/attachments/")
        .replace(Regex("https?://img(\\d*)\\.(nga\\.178\\.com|ngacn\\.cc|ngabbs\\.com)/"), "https://img$1.nga.cn/")
    val origin = when {
        base.startsWith("//") -> "https:$base"
        base.startsWith("http") -> base
        else -> "https://$base"
    }.trimEnd('/') + "/"
    value = when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("img") && value.contains(".nga.cn/") -> "https://$value"
        value.startsWith("./") -> origin + value.removePrefix("./").removePrefix("attachments/")
        else -> value
    }
    val resolved = runCatching { java.net.URI(origin).resolve(value).toString() }.getOrDefault("")
    val uri = runCatching { java.net.URI(resolved) }.getOrNull() ?: return ""
    if (uri.scheme !in setOf("https", "http") || uri.host == null || uri.userInfo != null) return ""
    return if (uri.scheme == "http" && uri.host.matches(Regex("img\\d*\\.nga\\.cn"))) resolved.replaceFirst("http:", "https:")
    else resolved.takeIf { uri.scheme == "https" }.orEmpty()
}
