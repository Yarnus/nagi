package dev.nagi.sdk

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface NgaExternalTarget {
    data class Topic(val topicId: String, val postId: String? = null, val page: Int = 1) : NgaExternalTarget
    data class Forum(val forumId: String, val collectionId: String? = null) : NgaExternalTarget
    data class User(val userId: String) : NgaExternalTarget
}

data class ExtractedExternalLink(val url: String, val target: NgaExternalTarget)

/** Parses trusted external NGA URLs into in-app navigation targets. */
object NgaExternalLinks {
    private val positiveId = Regex("[1-9][0-9]*")
    private val forumIdPattern = Regex("-?[1-9][0-9]*")
    private val fragmentPid = Regex("pid([1-9][0-9]*)(?:Anchor)?")
    private val urlCandidate = Regex("""https?://[^\s<>"'\u4E00-\u9FFF\u3000-\u303F\uFF00-\uFFEF]+""", RegexOption.IGNORE_CASE)
    private val trailingPunctuation = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '>', '"', '\'')
    private val blockedTopicParams = setOf("action", "__act", "__lib", "func", "step", "authorid", "searchpost")
    private val blockedForumParams = setOf("key", "authorid", "searchpost", "action", "__act", "__lib", "func", "step")

    fun extractFromText(text: String): ExtractedExternalLink? {
        if (text.isBlank()) return null
        for (match in urlCandidate.findAll(text)) {
            val cleaned = match.value.trimEnd(*trailingPunctuation)
            val target = parse(cleaned)
            if (target != null) return ExtractedExternalLink(cleaned, target)
        }
        return null
    }

    fun parse(raw: String): NgaExternalTarget? {
        val url = raw.trim().toHttpUrlOrNull() ?: return null
        if (url.scheme !in setOf("https", "http") || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        val expectedPort = if (url.scheme == "https") 443 else 80
        if (url.port != expectedPort) return null
        if (NgaDomain.entries.none { it.baseUrl.toHttpUrlOrNull()?.host == url.host }) return null

        return when (url.encodedPath) {
            "/read.php" -> parseTopic(url)
            "/thread.php" -> parseForum(url)
            "/nuke.php" -> parseUser(url)
            else -> null
        }
    }

    private fun parseTopic(url: okhttp3.HttpUrl): NgaExternalTarget.Topic? {
        if (url.queryParameterNames.any { it in blockedTopicParams }) return null
        val tid = url.queryParameterValues("tid").singleOrNull()?.takeIf { it.matches(positiveId) } ?: return null
        val pidParam = url.queryParameterValues("pid")
        if (pidParam.size > 1) return null
        val pidFromQuery = pidParam.singleOrNull()?.also { if (!it.matches(positiveId)) return null }
        val pid = pidFromQuery ?: url.fragment?.let { fragmentPid.matchEntire(it)?.groupValues?.get(1) }
        val pageParam = url.queryParameterValues("page")
        if (pageParam.size > 1) return null
        val page = pageParam.singleOrNull()?.let { raw ->
            if (raw.equals("e", ignoreCase = true)) 1
            else raw.toIntOrNull()?.takeIf { it > 0 } ?: return null
        } ?: 1
        return NgaExternalTarget.Topic(topicId = tid, postId = pid, page = page)
    }

    private fun parseForum(url: okhttp3.HttpUrl): NgaExternalTarget.Forum? {
        if (url.queryParameterNames.any { it in blockedForumParams }) return null
        val stidValues = url.queryParameterValues("stid")
        if (stidValues.size > 1) return null
        val stid = stidValues.singleOrNull()?.also { if (!it.matches(forumIdPattern)) return null }
        val fidValues = url.queryParameterValues("fid")
        if (fidValues.size > 1) return null
        val fid = fidValues.singleOrNull()
        if (stid != null) {
            val resolvedFid = when (fid) {
                null, "0" -> "0"
                else -> fid.takeIf { it.matches(forumIdPattern) } ?: return null
            }
            return NgaExternalTarget.Forum(forumId = resolvedFid, collectionId = stid)
        }
        val validFid = fid?.takeIf { it.matches(forumIdPattern) } ?: return null
        return NgaExternalTarget.Forum(forumId = validFid, collectionId = null)
    }

    private fun parseUser(url: okhttp3.HttpUrl): NgaExternalTarget.User? {
        if (url.queryParameterNames.any { it in setOf("action", "step") }) return null
        val isUcpLib = url.queryParameterValues("__lib") == listOf("ucp") &&
            url.queryParameterValues("__act") == listOf("get")
        val isLegacyUcp = url.queryParameterValues("func") == listOf("ucp") &&
            url.queryParameterValues("__lib").isEmpty() &&
            url.queryParameterValues("__act").isEmpty()
        if (!isUcpLib && !isLegacyUcp) return null
        val uid = url.queryParameterValues("uid").singleOrNull()?.takeIf { it.matches(positiveId) } ?: return null
        return NgaExternalTarget.User(uid)
    }
}
