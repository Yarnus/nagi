package dev.nagi.sdk

import org.junit.Assert.*
import org.junit.Test

class ContentLinkTest {
    @Test fun unnamedLinksIncludeLegacyBraceForm() {
        val url = "https://ngabbs.com/read.php?tid=45905669"
        listOf("[url]$url[/url]", "{[url]$url}[/url]", "[URL] $url [/URL]").forEach { raw ->
            val block = ContentParser.parse("before $raw after").single() as ContentBlock.Text
            assertEquals("before $url after", block.value)
            val span = block.spans.single()
            assertEquals(url, span.url)
            assertEquals(url, block.value.substring(span.start, span.end))
        }
    }

    @Test fun namedLinksKeepFormattingAndCodeRemainsLiteral() {
        val block = ContentParser.parse("[url=https://ngabbs.com/read.php?tid=1][b]topic[/b][/url]").single() as ContentBlock.Text
        assertEquals("topic", block.value)
        assertTrue(block.spans.single().bold)
        assertEquals("https://ngabbs.com/read.php?tid=1", block.spans.single().url)
        assertEquals(ContentBlock.Code("[url]https://example.com[/url]"), ContentParser.parse("[code][url]https://example.com[/url][/code]").single())
    }

    @Test fun unsafeUnnamedLinksAreNotClickable() {
        listOf("javascript:alert(1)", "https://user:secret@ngabbs.com/read.php?tid=1", "http://ngabbs.com/read.php?tid=1").forEach { url ->
            val block = ContentParser.parse("[url]$url[/url]").single() as ContentBlock.Text
            assertTrue(block.spans.none { it.url != null })
        }
    }
}
