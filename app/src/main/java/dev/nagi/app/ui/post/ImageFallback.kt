package dev.nagi.app.ui.post

import coil.network.HttpException

private val missingAttachmentsRegex = Regex("^(https://img\\d*\\.nga\\.cn)/(mon_[^?#]+.*)$")
private val thumbSuffixRegex = Regex("""\.(?:thumb(?:_ss|_s)?|medium)\.jpg(?=[?#]|$)""", RegexOption.IGNORE_CASE)
private val primaryNgaImgRegex = Regex("^https://img\\.nga\\.cn(/.*)$")
private val mirrorNgaImgRegex = Regex("^https://img\\d+\\.nga\\.cn(/.*)$")

/**
 * Returns an ordered, deduplicated list of fallback URLs to try when loading an NGA image.
 * Covers missing `/attachments/` segments, unavailable thumbnail variants, and CDN host mirrors.
 */
internal fun imageFallbackCandidates(url: String): List<String> {
    if (url.isBlank()) return emptyList()
    val candidates = LinkedHashSet<String>()
    fun addWithThumbAndMirror(candidate: String) {
        candidates += candidate
        val original = thumbSuffixRegex.replace(candidate, "")
        if (original != candidate) candidates += original
    }

    addWithThumbAndMirror(url)
    missingAttachmentsRegex.matchEntire(url)?.let { match ->
        addWithThumbAndMirror("${match.groupValues[1]}/attachments/${match.groupValues[2]}")
    }

    val snapshot = candidates.toList()
    for (candidate in snapshot) {
        primaryNgaImgRegex.matchEntire(candidate)?.let {
            candidates += "https://img8.nga.cn${it.groupValues[1]}"
        } ?: mirrorNgaImgRegex.matchEntire(candidate)?.let {
            candidates += "https://img.nga.cn${it.groupValues[1]}"
        }
    }
    return candidates.toList()
}

internal fun isHttp404Error(throwable: Throwable?): Boolean =
    (throwable as? HttpException)?.response?.code == 404
