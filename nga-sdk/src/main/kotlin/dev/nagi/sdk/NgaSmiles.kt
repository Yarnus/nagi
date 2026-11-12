package dev.nagi.sdk

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class NgaSmile(val groupId: String, val name: String, val url: String) {
    val key: String get() = "$groupId:$name"
    val code: String get() = "[s:$key]"
}

data class NgaSmileGroup(val id: String, val name: String, val smiles: List<NgaSmile>)

object NgaSmiles {
    private val byKey = SMILE_FILES.mapValues { (key, file) ->
        NgaSmile(key.substringBefore(':'), key.substringAfter(':'), SMILE_BASE + file)
    }
    val groups: List<NgaSmileGroup> = listOf(
        "ac" to "AC娘", "a2" to "AC娘Ⅱ", "ng" to "NG娘", "pst" to "潘斯特",
        "dt" to "外域三人组", "pg" to "企鹅", "0" to "默认",
    ).map { (id, name) -> NgaSmileGroup(id, name, byKey.values.filter { it.groupId == id }) }
    private val byUrl = byKey.values.associateBy { it.url }

    fun resolve(name: String): NgaSmile? = byKey[when {
        name.all(Char::isDigit) && name.isNotEmpty() -> "0:$name"
        name.startsWith(':') -> "0$name"
        else -> name
    }]

    fun fromUrl(url: String): NgaSmile? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (!parsed.isHttps || parsed.port != 443 || parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return null
        return byUrl[parsed.toString()]
    }
}
