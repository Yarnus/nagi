package dev.nagi.sdk

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

object ContentParser {
    private val referenceTags = Regex("""\[(?:uid=-?\d+|pid=\d+(?:,(?:\d+|tid=\d+|reply=\d+))*|/(?:uid|pid))\]""", RegexOption.IGNORE_CASE)
    private val quoteHeader = Regex(
        """^\s*(?:\[b\]\s*)?(?:(?:Reply\s+to\s+)?\[pid=(?:\d+,\d+,\d+|\d+,tid=\d+(?:,reply=\d+)?)\](?:Reply)?\[/pid\]\s*)?(?:\[b\]\s*)?Post\s+by\s+\[uid=-?\d+\]([^\[\]\r\n]+)(?:\[/uid\]|\[/b\])\s*(?:\(([^)\r\n]+)\))?\s*(?:\[/b\])?\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val smileTag = Regex("""\[s:([^\]]+)\]""", RegexOption.IGNORE_CASE)
    private val openingTag = Regex("""\[(img|quote|code|collapse|table)(?:=([^\]]*))?\]""", RegexOption.IGNORE_CASE)
    fun parse(raw: String, attachmentBase: String = "https://img.nga.cn/attachments/"): List<ContentBlock> = parse(raw, attachmentBase, 0, false)

    internal fun parseHtml(raw: String, attachmentBase: String): List<ContentBlock> = parse(raw, attachmentBase, 0, true)

    private fun parse(raw: String, base: String, depth: Int, htmlEncodedCode: Boolean): List<ContentBlock> {
        if (depth > 12) return listOf(ContentBlock.Text(raw))
        val blocks = mutableListOf<ContentBlock>()
        var offset = 0
        while (offset < raw.length) {
            val opening = openingTag.find(raw, offset) ?: break
            val tag = opening.groupValues[1].lowercase()
            val tokens = Regex("""\[(/?)$tag(?:=[^\]]*)?\]""", RegexOption.IGNORE_CASE)
            var nesting = 1
            val closing = tokens.findAll(raw, opening.range.last + 1).firstOrNull {
                if (it.groupValues[1] == "/") nesting-- else if (tag !in setOf("img", "code")) nesting++
                nesting == 0
            } ?: break
            blocks += html(raw.substring(offset, opening.range.first), base, depth, htmlEncodedCode)
            val body = raw.substring(opening.range.last + 1, closing.range.first)
            blocks += when (tag) {
                "img" -> resourceUrl(body, base).takeIf(String::isNotBlank)?.let { ContentBlock.Image(it) } ?: ContentBlock.Text("[Image unavailable]")
                "quote" -> quote(body, base, depth + 1, htmlEncodedCode)
                "code" -> ContentBlock.Code(if (htmlEncodedCode) Jsoup.parseBodyFragment(body).body().wholeText() else body)
                "table" -> {
                    val table = "<table>" + body.replace(Regex("""\[(/?)(tr|td|th)\]""", RegexOption.IGNORE_CASE), "<$1$2>") + "</table>"
                    ContentBlock.Table(Jsoup.parseBodyFragment(table).select("tr").map { row -> row.children().map { it.text() } })
                }
                else -> ContentBlock.Collapse(opening.groupValues[2].ifBlank { "Details" }, parse(body, base, depth + 1, htmlEncodedCode))
            }
            offset = closing.range.last + 1
        }
        blocks += html(raw.substring(offset), base, depth, htmlEncodedCode)
        return blocks
    }

    private fun quote(body: String, base: String, depth: Int, htmlEncodedCode: Boolean): ContentBlock.Quote {
        val header = quoteHeader.find(body)
        return ContentBlock.Quote(
            children = parse(if (header == null) body else body.substring(header.range.last + 1), base, depth, htmlEncodedCode),
            author = header?.groupValues?.get(1)?.let(::plain)?.takeIf(String::isNotBlank),
            date = header?.groupValues?.get(2)?.let(::plain)?.takeIf(String::isNotBlank),
        )
    }

    private fun html(raw: String, base: String, depth: Int, htmlEncodedCode: Boolean): List<ContentBlock> {
        if (raw.isBlank()) return emptyList()
        val inline = raw.replace(Regex("""\[(/?)(b|i|u|del)\]""", RegexOption.IGNORE_CASE), "<$1$2>")
            .replace(Regex("""\[url=([^\]]*)\](.*?)\[/url\]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
                val url = resourceUrl(it.groupValues[1], base)
                "<a href=\"${url.replace("\"", "&quot;")}\">${it.groupValues[2]}</a>"
            }
        val linked = inline.replace(Regex("""(\{)?\[url\](.*?)\[/url\]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
            val label = it.groupValues[2].trim().let { value ->
                if (it.groupValues[1].isNotEmpty()) value.removeSuffix("}").trim() else value
            }
            Element("a").attr("href", resourceUrl(label, base)).text(label).outerHtml()
        }
        val document = Jsoup.parseBodyFragment(linked)
        document.select("script,style,iframe,object").remove()
        val blocks = mutableListOf<ContentBlock>()
        val text = StringBuilder()
        val spans = mutableListOf<TextSpan>()
        val smiles = mutableListOf<InlineSmile>()
        fun flush() {
            if (text.isNotBlank()) blocks += ContentBlock.Text(text.toString(), spans.toList(), smiles.toList())
            text.clear(); spans.clear(); smiles.clear()
        }
        fun append(value: String, style: TextSpan) {
            val start = text.length
            text.append(value)
            if (text.length > start && (style.bold || style.italic || style.underline || style.strikethrough || style.url != null)) {
                spans += style.copy(start = start, end = text.length)
            }
        }
        fun visit(node: Node, style: TextSpan = TextSpan(0, 0)) {
            when (node) {
                is TextNode -> {
                    val value = referenceTags.replace(node.wholeText, "")
                    val start = text.length
                    append(value, style)
                    smileTag.findAll(value).forEach { match ->
                        NgaSmiles.resolve(match.groupValues[1])?.let { smile ->
                            smiles += InlineSmile(start + match.range.first, start + match.range.last + 1, smile)
                        }
                    }
                }
                is Element -> when (node.tagName()) {
                    "img" -> {
                        val url = resourceUrl(node.attr("src"), base)
                        val smile = NgaSmiles.fromUrl(url)
                        if (smile != null) {
                            val start = text.length
                            append(smile.code, style)
                            smiles += InlineSmile(start, text.length, smile)
                        } else {
                            flush()
                            if (url.isNotBlank()) blocks += ContentBlock.Image(url)
                        }
                    }
                    "blockquote" -> { flush(); blocks += quote(node.html(), base, depth + 1, htmlEncodedCode) }
                    "pre" -> { flush(); blocks += ContentBlock.Code(node.wholeText()) }
                    "table" -> { flush(); blocks += ContentBlock.Table(node.select("tr").map { row -> row.children().map { it.text() } }) }
                    "br" -> text.append('\n')
                    else -> {
                        val childStyle = when (node.tagName()) {
                            "b", "strong" -> style.copy(bold = true)
                            "i", "em" -> style.copy(italic = true)
                            "u" -> style.copy(underline = true)
                            "del", "s", "strike" -> style.copy(strikethrough = true)
                            "a" -> style.copy(url = resourceUrl(node.attr("href"), base).ifBlank { null }, underline = true)
                            else -> style
                        }
                        node.childNodes().forEach { visit(it, childStyle) }
                        if (node.tagName() in setOf("p", "div", "li")) text.append('\n')
                    }
                }
            }
        }
        document.body().childNodes().forEach { visit(it) }
        flush()
        return blocks
    }
}
