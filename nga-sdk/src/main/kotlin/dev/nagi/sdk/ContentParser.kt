package dev.nagi.sdk

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import okhttp3.HttpUrl.Companion.toHttpUrl
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
    private val htmlCode = Regex("""<pre\b[^>]*>.*?</pre\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val smileTag = Regex("""\[s:([^\]]+)\]""", RegexOption.IGNORE_CASE)
    private val openingTag = Regex("""\[(img|noimg|album|quote|code|collapse|table|h|align|list|flash|attach|dice)(?:=([^\]]*))?\]""", RegexOption.IGNORE_CASE)
    private val inlineTag = Regex("""\[(/?)(b|i|u|del|color|size)(?:=([^\]]*))?\]""", RegexOption.IGNORE_CASE)
    private val positiveId = Regex("[1-9][0-9]*")

    fun parse(raw: String, attachmentBase: String = "https://img.nga.cn/attachments/", context: ContentContext = ContentContext()): List<ContentBlock> =
        Parser(attachmentBase, context, false).parse(raw)

    internal fun parseHtml(raw: String, attachmentBase: String, context: ContentContext = ContentContext()): List<ContentBlock> =
        Parser(attachmentBase, context, true).parse(raw)

    private class Parser(val base: String, val context: ContentContext, val htmlEncodedCode: Boolean) {
        private var diceSeed: Long? = null

        fun parse(raw: String, depth: Int = 0): List<ContentBlock> {
            if (depth > 12) return listOf(ContentBlock.Text(raw))
            val blocks = mutableListOf<ContentBlock>()
            val codeRanges = htmlCode.findAll(raw).map { it.range }.toList()
            var offset = 0
            while (offset < raw.length) {
                val opening = openingTag.findAll(raw, offset).firstOrNull { match -> codeRanges.none { match.range.first in it } } ?: break
                val tag = opening.groupValues[1].lowercase()
                val tagToken = """\[(/?)$tag(?:=[^\]]*)?\]"""
                val protectedCode = """\[code(?:=[^\]]*)?\].*?\[/code\]|<pre\b[^>]*>.*?</pre\s*>"""
                val tokens = Regex(if (tag == "code") tagToken else "$protectedCode|$tagToken",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                var nesting = 1
                val closing = tokens.findAll(raw, opening.range.last + 1).firstOrNull {
                    if (!it.value.startsWith("[$tag", true) && !it.value.startsWith("[/$tag", true)) false else {
                        if (it.groupValues[1] == "/") nesting-- else if (tag !in setOf("img", "noimg", "code")) nesting++
                        nesting == 0
                    }
                } ?: break
                blocks += html(raw.substring(offset, opening.range.first), depth)
                val body = raw.substring(opening.range.last + 1, closing.range.first)
                val attr = opening.groupValues[2]
                blocks += when (tag) {
                    "img", "noimg" -> listOf(image(if (tag == "noimg") legacyImage(body) else body))
                    "album" -> if (body.contains("[img]", true)) parse(body, depth + 1) else
                        body.split(Regex("""<br\s*/?>|\r?\n""", RegexOption.IGNORE_CASE)).map(String::trim).filter(String::isNotBlank).map(::image)
                    "quote" -> listOf(quote(body, depth + 1))
                    "code" -> listOf(ContentBlock.Code(if (htmlEncodedCode) Jsoup.parseBodyFragment(body).body().wholeText() else body))
                    "table" -> listOf(table(body, depth + 1))
                    "h" -> listOf(ContentBlock.Heading(parse(body, depth + 1)))
                    "align" -> listOf(ContentBlock.Aligned(alignment(attr), parse(body, depth + 1)))
                    "list" -> listOf(ContentBlock.ListBlock(listItems(body).map { parse(it, depth + 1) }, attr == "1"))
                    "flash", "attach" -> {
                        val url = resourceUrl(body.trim(), base)
                        if (url.isBlank()) listOf(ContentBlock.Text("[Resource unavailable]"))
                        else listOf(ContentBlock.Media(url, if (tag == "attach") MediaKind.ATTACHMENT else if (attr == "audio") MediaKind.AUDIO else MediaKind.VIDEO))
                    }
                    "dice" -> listOf(dice(body.trim()))
                    else -> listOf(ContentBlock.Collapse(attr.ifBlank { "Details" }, parse(body, depth + 1)))
                }
                offset = closing.range.last + 1
            }
            blocks += html(raw.substring(offset), depth)
            return blocks
        }

        private fun image(raw: String): ContentBlock {
            val url = resourceUrl(raw.trim(), base)
            return when {
                url.isBlank() -> ContentBlock.Text("[Image unavailable]")
                url.substringBefore('?').substringBefore('#').endsWith(".mp4", true) -> ContentBlock.Media(url, MediaKind.VIDEO)
                else -> ContentBlock.Image(url)
            }
        }

        private fun legacyImage(raw: String): String {
            val date = context.postDate.orEmpty()
            val localDate = date.toLongOrNull()?.takeIf { it > 0 }?.let {
                runCatching { Instant.ofEpochSecond(it).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate() }.getOrNull()
            } ?: runCatching { LocalDateTime.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]")).toLocalDate() }.getOrNull()
            return if (localDate == null) raw else "mon_${localDate.format(DateTimeFormatter.ofPattern("yyyyMM/dd"))}/${raw.trim()}"
        }

        private fun quote(body: String, depth: Int): ContentBlock.Quote {
            val header = quoteHeader.find(body)
            return ContentBlock.Quote(
                children = parse(if (header == null) body else body.substring(header.range.last + 1), depth),
                author = header?.groupValues?.get(1)?.let(::plain)?.takeIf(String::isNotBlank),
                date = header?.groupValues?.get(2)?.let(::plain)?.takeIf(String::isNotBlank),
            )
        }

        private fun table(body: String, depth: Int): ContentBlock.Table = ContentBlock.Table(
            tableParts(body, "tr").map { row -> tableParts(row, "td\\d*|th").map { parse(it, depth) } },
        )

        private fun tableParts(raw: String, tags: String): List<String> {
            // Keep raw cell content intact, including code and nested tables.
            val tokens = Regex("""\[code(?:=[^\]]*)?\].*?\[/code\]|<pre\b[^>]*>.*?</pre\s*>|\[(/?)(?:$tags)(?:\s[^\]]*)?\]""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val parts = mutableListOf<String>()
            var nesting = 0
            var start = 0
            for (token in tokens.findAll(raw)) {
                if (token.value.startsWith("[code", true) || token.value.startsWith("<pre", true)) continue
                if (token.groupValues[1].isEmpty()) {
                    if (nesting == 0) start = token.range.last + 1
                    nesting++
                } else if (nesting > 0) {
                    nesting--
                    if (nesting == 0) parts += raw.substring(start, token.range.first)
                }
            }
            return parts
        }

        private fun listItems(body: String): List<String> {
            val items = mutableListOf<String>()
            val tokens = Regex("""\[list(?:=[^\]]*)?\]|\[/list\]|\[\*\]|\[code(?:=[^\]]*)?\].*?\[/code\]|<pre\b[^>]*>.*?</pre\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            var nesting = 0
            var start = 0
            for (token in tokens.findAll(body)) {
                when {
                    token.value.equals("[/list]", true) -> nesting--
                    token.value.startsWith("[list", true) -> nesting++
                    token.value == "[*]" && nesting == 0 -> {
                        body.substring(start, token.range.first).takeIf(String::isNotBlank)?.let(items::add)
                        start = token.range.last + 1
                    }
                }
            }
            body.substring(start).takeIf(String::isNotBlank)?.let(items::add)
            return items
        }

        private fun dice(expression: String): ContentBlock.Dice {
            val parts = expression.split('+')
            if (parts.size > 20) return ContentBlock.Dice(expression)
            val pattern = Regex("""(?:(\d{0,2})[dD](\d{1,6})|(\d{1,6}))""")
            val parsed = parts.map { pattern.matchEntire(it.trim()) ?: return ContentBlock.Dice(expression) }
            val author = context.authorId?.toLongOrNull() ?: return ContentBlock.Dice(expression)
            val topic = context.topicId?.toLongOrNull() ?: return ContentBlock.Dice(expression)
            val post = context.postId?.toLongOrNull() ?: return ContentBlock.Dice(expression)
            if (author < 0 || topic <= 0 || post < 0) return ContentBlock.Dice(expression)
            val rolls = mutableListOf<Int>()
            var total = 0L
            var seed = diceSeed ?: ((author % 233280 + topic % 233280 + post % 233280) % 233280)
            for (part in parsed) {
                if (part.groupValues[3].isNotEmpty()) {
                    total += part.groupValues[3].toLong()
                } else {
                    val count = part.groupValues[1].ifBlank { "1" }.toInt()
                    val faces = part.groupValues[2].toInt()
                    if (count !in 1..20 || faces !in 1..100000 || rolls.size + count > 20) return ContentBlock.Dice(expression)
                    repeat(count) {
                        seed = (seed * 9301 + 49297) % 233280
                        val roll = (seed * faces / 233280 + 1).toInt()
                        rolls += roll
                        total += roll
                    }
                }
            }
            diceSeed = seed
            return ContentBlock.Dice(expression, rolls, total)
        }

        private fun html(raw: String, depth: Int): List<ContentBlock> {
            if (raw.isBlank()) return emptyList()
            val preservedCode = mutableListOf<String>()
            val protected = raw.replace(htmlCode) {
                preservedCode += it.value
                "<nagi-literal-pre index=\"${preservedCode.lastIndex}\"></nagi-literal-pre>"
            }
            fun restoreCode(markup: String): String = markup.replace(Regex("""<nagi-literal-pre index="(\d+)"></nagi-literal-pre>""")) {
                preservedCode.getOrNull(it.groupValues[1].toIntOrNull() ?: -1).orEmpty()
            }
            var inline = protected.replace(inlineTag) {
                val closing = it.groupValues[1]
                val tag = it.groupValues[2].lowercase()
                when (tag) {
                    "color", "size" -> if (closing.isNotEmpty()) "</nagi-$tag>" else Element("nagi-$tag").attr("value", it.groupValues[3]).outerHtml().substringBefore("</nagi-")
                    else -> "<$closing$tag>"
                }
            }
            inline = inline.replace(Regex("""\[url=([^\]]*)\](.*?)\[/url\]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
                link(resourceUrl(it.groupValues[1], base), it.groupValues[2])
            }.replace(Regex("""(\{)?\[url\](.*?)\[/url\]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
                val label = decodeNumericEntities(it.groupValues[2]).trim().let { value -> if (it.groupValues[1].isNotEmpty()) value.removeSuffix("}").trim() else value }
                Element("a").attr("href", resourceUrl(label, base)).text(label).outerHtml()
            }
            inline = inline.replace(Regex("""\[(uid|tid|pid)=([^\]]*)\](.*?)\[/\1\]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
                link(referenceUrl(it.groupValues[1].lowercase(), it.groupValues[2]), it.groupValues[3])
            }.replace(Regex("""\[@\s*([^\]]+)\]""")) {
                val who = it.groupValues[1].trim()
                Element("a").attr("href", userUrl(who)).text("@$who").outerHtml()
            }.replace(Regex("""\[stripbr\]""", RegexOption.IGNORE_CASE), "<br>")
                .replace(Regex("""\[/?(?:style|fixsize|randomblock)(?:\s[^\]]*)?\]""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""={3,}((?:(?!={3,})[^\r\n])*?)={3,}""")) { "<nagi-divider>${it.groupValues[1]}</nagi-divider>" }
                .replace(Regex("={6,}"), "<nagi-divider></nagi-divider>")
            val document = Jsoup.parseBodyFragment(inline).apply { outputSettings().prettyPrint(false) }
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
                if (text.length > start && style != TextSpan(0, 0)) spans += style.copy(start = start, end = text.length)
            }
            fun visit(node: Node, style: TextSpan = TextSpan(0, 0)) {
                when (node) {
                    is TextNode -> {
                        val value = decodeNumericEntities(referenceTags.replace(node.wholeText, ""))
                        val start = text.length
                        append(value, style)
                        smileTag.findAll(value).forEach { match ->
                            NgaSmiles.resolve(match.groupValues[1])?.let { smile -> smiles += InlineSmile(start + match.range.first, start + match.range.last + 1, smile) }
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
                                if (url.isNotBlank()) blocks += image(url)
                            }
                        }
                        "blockquote" -> { flush(); blocks += quote(restoreCode(node.html()), depth + 1) }
                        "pre" -> { flush(); blocks += ContentBlock.Code(node.wholeText()) }
                        "nagi-literal-pre" -> {
                            flush()
                            val original = preservedCode.getOrNull(node.attr("index").toIntOrNull() ?: -1).orEmpty()
                            blocks += ContentBlock.Code(Jsoup.parseBodyFragment(original).body().wholeText())
                        }
                        "table" -> {
                            flush()
                            blocks += ContentBlock.Table(node.children().flatMap { if (it.tagName() == "tr") listOf(it) else it.children().filter { child -> child.tagName() == "tr" } }.map { row ->
                                row.children().filter { it.tagName() in setOf("td", "th") }.map { parse(restoreCode(it.html()), depth + 1) }
                            })
                        }
                        "nagi-divider", "hr" -> { flush(); blocks += ContentBlock.Divider(parse(restoreCode(node.html()), depth + 1)) }
                        "h1", "h2", "h3", "h4", "h5", "h6" -> { flush(); blocks += ContentBlock.Heading(parse(restoreCode(node.html()), depth + 1)) }
                        "ul", "ol" -> { flush(); blocks += ContentBlock.ListBlock(node.children().filter { it.tagName() == "li" }.map { parse(restoreCode(it.html()), depth + 1) }, node.tagName() == "ol") }
                        "br" -> text.append('\n')
                        else -> {
                            val childStyle = when (node.tagName()) {
                                "b", "strong" -> style.copy(bold = true)
                                "i", "em" -> style.copy(italic = true)
                                "u" -> style.copy(underline = true)
                                "del", "s", "strike" -> style.copy(strikethrough = true)
                                "a" -> style.copy(url = resourceUrl(node.attr("href"), base).ifBlank { null }, underline = node.attr("href").isNotBlank())
                                "nagi-color" -> style.copy(color = node.attr("value").lowercase().takeIf { it in ContentColors.rgb } ?: style.color)
                                "nagi-size" -> style.copy(scale = node.attr("value").removeSuffix("%").toFloatOrNull()?.takeIf { it.isFinite() && it in 50f..250f }?.div(100) ?: style.scale)
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

    private fun alignment(raw: String): ContentAlignment = when (raw.lowercase()) {
        "center" -> ContentAlignment.CENTER
        "right" -> ContentAlignment.END
        else -> ContentAlignment.START
    }

    private fun link(url: String, body: String): String = if (url.isBlank()) body else
        Element("a").attr("href", url).outerHtml().substringBefore("</a>") + body + "</a>"

    private fun userUrl(who: String): String {
        if (who.isBlank() || who.startsWith('-') || who.startsWith("#anony_") || (who.all(Char::isDigit) && !who.matches(positiveId))) return ""
        val builder = "https://bbs.nga.cn/nuke.php".toHttpUrl().newBuilder().addQueryParameter("__lib", "ucp").addQueryParameter("__act", "get")
        return builder.addQueryParameter(if (who.matches(positiveId)) "uid" else "username", who).build().toString()
    }

    private fun referenceUrl(tag: String, raw: String): String {
        if (tag == "uid") return if (raw.matches(positiveId)) userUrl(raw) else ""
        if (tag == "tid") return if (raw.matches(positiveId)) "https://bbs.nga.cn/read.php?tid=$raw" else ""
        val parts = raw.split(',')
        if (parts.size !in 1..3) return ""
        val pid = parts[0].takeIf { it == "0" || it.matches(positiveId) } ?: return ""
        val tid = parts.getOrNull(1)?.removePrefix("tid=")
        if (tid != null && !tid.matches(positiveId)) return ""
        if (pid == "0" && tid == null) return ""
        val builder = "https://bbs.nga.cn/read.php".toHttpUrl().newBuilder().addQueryParameter("pid", pid)
        tid?.let { builder.addQueryParameter("tid", it) }
        parts.getOrNull(2)?.let { value ->
            val key = if (value.startsWith("reply=")) "reply" else "page"
            val number = value.removePrefix("reply=")
            if (!number.matches(positiveId) && !(key == "reply" && number == "0")) return ""
            builder.addQueryParameter(key, number)
        }
        return builder.build().toString()
    }
}
