package dev.nagi.app.ui.components

private const val ANONYMOUS_PREFIX = "#anony_"

/**
 * Formats an author handle for UI display.
 * Collapses NGA's 39-char anonymous handles (`#anony_<hash>`) into `匿名 · <4-char prefix>`
 * while keeping empty handles as `匿名` and preserving regular usernames.
 */
internal fun formatAuthorDisplayName(author: String?): String {
    val trimmed = author?.trim().orEmpty()
    if (trimmed.isEmpty()) return "匿名"
    if (!trimmed.startsWith(ANONYMOUS_PREFIX)) return trimmed
    val hash = trimmed.removePrefix(ANONYMOUS_PREFIX).trim()
    if (hash.isEmpty()) return "匿名"
    val shortCode = if (hash.length <= 4) hash else hash.take(4)
    return "匿名 · $shortCode"
}

/** Returns true when [author] is an NGA anonymous handle (`#anony_...`). */
internal fun isAnonymousHandle(author: String?): Boolean =
    author?.trim()?.startsWith(ANONYMOUS_PREFIX) == true
