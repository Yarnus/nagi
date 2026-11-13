package dev.nagi.sdk

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object NgaUserLinks {
    fun userId(raw: String): String? {
        val url = raw.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        if (NgaDomain.entries.none { it.baseUrl.toHttpUrlOrNull()?.host == url.host }) return null
        if (url.encodedPath != "/nuke.php" || url.fragment != null || url.queryParameterNames != setOf("__lib", "__act", "uid")) return null
        if (url.queryParameterValues("__lib") != listOf("ucp") || url.queryParameterValues("__act") != listOf("get")) return null
        return url.queryParameterValues("uid").singleOrNull()?.takeIf { it.matches(Regex("[1-9][0-9]*")) }
    }
}
