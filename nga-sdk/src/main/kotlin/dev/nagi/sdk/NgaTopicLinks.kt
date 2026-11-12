package dev.nagi.sdk

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Recognizes plain topic links; links with page, post or action targets stay in the browser. */
object NgaTopicLinks {
    fun topicId(raw: String): String? {
        val url = raw.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        if (NgaDomain.entries.none { it.baseUrl.toHttpUrlOrNull()?.host == url.host }) return null
        if (url.encodedPath != "/read.php" || url.fragment != null || url.queryParameterNames != setOf("tid")) return null
        return url.queryParameterValues("tid").singleOrNull()?.takeIf { it.matches(Regex("[1-9][0-9]*")) }
    }
}
