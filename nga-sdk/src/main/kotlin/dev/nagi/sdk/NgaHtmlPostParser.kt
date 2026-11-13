package dev.nagi.sdk

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup

internal object NgaHtmlPostParser {
    fun posts(raw: String, topicId: String, page: Int, authorId: String?): PostPage {
        try {
            NgaXmlParser.extractErrorOrNull(raw)?.let { throw it }
            NgaParser.extractErrorOrNull(raw)?.let { throw it }
            extractErrorOrNull(raw)?.let { throw it }
            need(raw.trimEnd().endsWith("</html>", ignoreCase = true), ProtocolReason.HTML_INCOMPLETE)
            val document = Jsoup.parse(raw).apply { outputSettings().prettyPrint(false) }
            val scripts = document.select("script:not([src])").joinToString("\n") { it.data() }
            fun variable(name: String): String? = Regex("""\b${name}\s*=\s*(-?\d+)\b""").find(scripts)?.groupValues?.get(1)
            need(variable("__CURRENT_TID") == topicId && variable("__CURRENT_PAGE") == page.toString(), ProtocolReason.HTML_TOPIC_MISMATCH)
            need(variable("__CURRENT_AUTHORID") == (authorId ?: "0"), ProtocolReason.HTML_AUTHOR_FILTER_MISMATCH)
            val defaults = guard(ProtocolReason.HTML_DEFAULTS_MISSING) {
                JSONArray("[" + literal(scripts, "commonui.postArg.setDefault(", '(', ')') + "]")
            }
            val (topicAuthor, replies) = guard(ProtocolReason.HTML_DEFAULTS_INVALID) {
                need(defaults.get(2).toString() == topicId && !defaults.isNull(3), ProtocolReason.HTML_DEFAULTS_INVALID)
                val author = defaults.get(3).toString()
                need(author.matches(Regex("-?[1-9]\\d*")) && author.toLongOrNull() != null, ProtocolReason.HTML_DEFAULTS_INVALID)
                val count = defaults.getLong(11)
                need(count in 0 until Int.MAX_VALUE.toLong(), ProtocolReason.HTML_DEFAULTS_INVALID)
                author to count
            }
            val total = replies.toInt() + 1
            val metadataStart = Regex("""\bvar\s+__PAGE\s*=\s*""").find(scripts)?.range?.last?.plus(1)
            val (pages, pageSize) = if (metadataStart == null) {
                need(page == 1 && !Regex("""\b__PAGE\b""").containsMatchIn(scripts), ProtocolReason.HTML_PAGINATION_INVALID)
                1 to 0
            } else guard(ProtocolReason.HTML_PAGINATION_INVALID) {
                val pagination = javascriptObject(scripts.substring(metadataStart))
                val pageCount = pagination.getInt("1")
                val size = pagination.getInt("3")
                need(pagination.getInt("2") == page && pageCount >= page && size > 0, ProtocolReason.HTML_PAGINATION_INVALID)
                pageCount to size
            }
            val users = guard(ProtocolReason.HTML_USERS_INVALID) {
                javascriptObject(scripts.substringAfter("commonui.userInfo.setAll(", ""))
            }
            val groups = parseJsonGroups(users.optJSONObject("__GROUPS"))
            val title = decodeNumericEntities(document.getElementById("currentTopicName")?.text().orEmpty())
            need(title.isNotBlank(), ProtocolReason.HTML_TITLE_MISSING)
            val attachmentBase = Regex("""\b__ATTACH_BASE_VIEW\s*=\s*""").find(scripts)?.let {
                JSONTokener(scripts.substring(it.range.last + 1)).nextValue() as? String
            } ?: "https://img.nga.cn/attachments/"
            val container = document.getElementById("m_posts_c") ?: throw NgaException.Protocol(ProtocolReason.HTML_POSTS_CONTAINER)
            val postContainers = container.select("[id]").filter { it.id().matches(Regex("postcontainer\\d+")) }
            val contents = container.select("[id]").filter { it.id().matches(Regex("postcontent\\d+")) }
            need(postContainers.size == contents.size, ProtocolReason.HTML_POST_COUNT_MISMATCH)
            val items = postContainers.map { postContainer ->
                val suffix = postContainer.id().removePrefix("postcontainer")
                val content = postContainer.getElementById("postcontent$suffix")
                    ?: throw NgaException.Protocol(ProtocolReason.HTML_POST_CONTENT)
                val floor = suffix.toInt()
                val author = document.getElementById("postauthor$suffix") ?: throw NgaException.Protocol(ProtocolReason.HTML_POST_AUTHOR)
                val uid = Regex("""(?:[?&])uid=(-?\d+)(?:&|$)""").find(author.attr("href"))?.groupValues?.get(1)
                    ?: author.text().trim().takeIf { it.startsWith("#anony_") }
                    ?: throw NgaException.Protocol(ProtocolReason.HTML_POST_AUTHOR)
                need(authorId == null || uid == authorId, ProtocolReason.HTML_POST_AUTHOR_FILTER)
                val user = users.optJSONObject(uid)
                val anchor = postContainer.select("[id]").firstOrNull { it.id().matches(Regex("pid\\d+Anchor")) }
                    ?: throw NgaException.Protocol(ProtocolReason.HTML_POST_ANCHOR)
                val pid = anchor.id().removePrefix("pid").removeSuffix("Anchor")
                val date = document.getElementById("postdate$suffix")?.text() ?: throw NgaException.Protocol(ProtocolReason.HTML_POST_DATE)
                val memberId = user?.optString("memberid").orEmpty().ifBlank { user?.optString("groupid").orEmpty() }
                val level = groups[memberId] ?: user?.optString("group")?.takeIf { it.isNotBlank() && it != "null" }?.let(::plain)
                val reputation = formatReputation(user?.optString("rvrc").orEmpty().ifBlank { user?.optString("fame").orEmpty() })
                val postCount = parsePostCount(user?.optString("postnum").orEmpty().ifBlank { user?.optString("posts").orEmpty() })
                val (score, clientPlatform) = parseHtmlPostArg(scripts, floor)
                Post(
                    id = pid,
                    floor = floor,
                    author = plain(user?.optString("username").orEmpty().ifBlank { author.text().ifBlank { uid } }),
                    authorId = uid,
                    avatar = resourceUrl(user?.optString("avatar").orEmpty(), "https://img.nga.cn/"),
                    date = date,
                    content = ContentParser.parseHtml(content.html(), attachmentBase, ContentContext(uid, topicId, pid, date)),
                    score = score,
                    authorLevel = level,
                    authorReputation = reputation,
                    authorPosts = postCount,
                    clientPlatform = clientPlatform,
                )
            }
            need(items.isNotEmpty() && (pageSize == 0 || items.size <= pageSize) && items.map { it.floor }.distinct().size == items.size,
                ProtocolReason.HTML_POSTS_INVALID)
            return PostPage(items, title, topicAuthor, page, total, page < pages, pageSize, pages)
        } catch (error: NgaException) {
            throw error
        } catch (_: Exception) {
            throw NgaException.Protocol(ProtocolReason.HTML_UNEXPECTED)
        }
    }

    private fun javascriptObject(source: String): JSONObject = JSONObject(source.replace(
        Regex("""("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')|([{,]\s*)(\d+)(\s*:)"""),
    ) { match ->
        if (match.groupValues[1].isNotEmpty()) match.value
        else "${match.groupValues[2]}\"${match.groupValues[3]}\"${match.groupValues[4]}"
    })

    private fun need(condition: Boolean, reason: ProtocolReason) {
        if (!condition) throw NgaException.Protocol(reason)
    }

    private inline fun <T> guard(reason: ProtocolReason, block: () -> T): T = try {
        block()
    } catch (error: NgaException) {
        throw error
    } catch (_: Exception) {
        throw NgaException.Protocol(reason)
    }

    fun extractErrorOrNull(raw: String): NgaException? {
        val code = Regex("<!--msgcodestart-->(.*?)<!--msgcodeend-->", RegexOption.DOT_MATCHES_ALL)
            .find(raw)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val message = Regex("<!--msginfostart-->(.*?)<!--msginfoend-->", RegexOption.DOT_MATCHES_ALL)
            .findAll(raw).map { Jsoup.parse(it.groupValues[1]).text() }.firstOrNull { it.isNotBlank() } ?: return null
        return classifyNgaError("$code:$message")
    }

    private fun literal(source: String, marker: String, open: Char, close: Char): String {
        val start = source.indexOf(marker).takeIf { it >= 0 }?.plus(marker.length)
            ?: throw NgaException.Protocol(ProtocolReason.HTML_DEFAULTS_MISSING)
        var quote: Char? = null
        var escaped = false
        var depth = 1
        for (index in start until source.length) {
            val char = source[index]
            if (quote != null) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == quote) quote = null
            } else when (char) {
                '\'', '"' -> quote = char
                open -> depth++
                close -> if (--depth == 0) return source.substring(start, index)
            }
        }
        throw NgaException.Protocol(ProtocolReason.HTML_DEFAULTS_MISSING)
    }

    private fun parseHtmlPostArg(scripts: String, floor: Int): Pair<Int, String?> = runCatching {
        val marker = "commonui.postArg.proc($floor,"
        if (!scripts.contains(marker)) return 0 to null
        val args = JSONArray("[$floor," + literal(scripts, marker, '(', ')') + "]")
        val rawScore = args.optString(16)
        val score = rawScore.substringBefore(',').trim().toIntOrNull() ?: 0
        val client = normalizeClientPlatform(args.optString(19))
        score to client
    }.getOrDefault(0 to null)
}
