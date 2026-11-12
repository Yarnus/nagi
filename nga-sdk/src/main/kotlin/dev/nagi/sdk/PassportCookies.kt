package dev.nagi.sdk

fun parsePassportCookies(header: String): NgaSession? {
    val values = header.split(';').mapNotNull {
        val pair = it.trim().split('=', limit = 2)
        if (pair.size == 2 && pair[1].isNotBlank()) pair[0] to pair[1] else null
    }
    val users = values.filter { it.first == "ngaPassportUid" }.map { it.second }
        .filter { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }.distinct()
    val tokens = values.filter { it.first == "ngaPassportCid" }.map { it.second }.distinct()
    return if (users.size == 1 && tokens.size == 1) runCatching { NgaSession(users.single(), tokens.single()) }.getOrNull() else null
}
