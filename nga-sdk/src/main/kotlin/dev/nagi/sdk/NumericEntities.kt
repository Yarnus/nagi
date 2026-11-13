package dev.nagi.sdk

import org.jsoup.parser.Parser

private val numericEntity = Regex("""&(?:amp;)*#(?:[xX][0-9a-fA-F]+|[0-9]+);""", RegexOption.IGNORE_CASE)

/** Decode residual numeric entities without reparsing escaped HTML or BBCode. */
internal fun decodeNumericEntities(value: String): String = numericEntity.replace(value) {
    Parser.unescapeEntities("&#" + it.value.substringAfterLast('#'), false)
}
