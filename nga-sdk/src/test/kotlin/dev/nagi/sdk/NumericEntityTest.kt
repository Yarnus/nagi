package dev.nagi.sdk

import org.junit.Assert.*
import org.junit.Test

class NumericEntityTest {
    private val title = "[其他] &amp;#128227; 20万奖池！腾讯光子AI游戏创作大赛来了|参赛 · 转发 · 推荐好友都有机会拿奖！"
    private val expectedTitle = "[其他] 📣 20万奖池！腾讯光子AI游戏创作大赛来了|参赛 · 转发 · 推荐好友都有机会拿奖！"

    @Test fun topicAndReaderTitlesDecodeTheOriginalPostEntities() {
        val topics = NgaParser.topics("""{"data":{"__T":{"0":{"tid":47841712,"subject":"$title"}}}}""", 1)
        assertEquals(expectedTitle, topics.items.single().title)
        val page = NgaParser.posts("""{"data":{"__T":{"tid":47841712,"subject":"$title"},"__R":{}}}""", 1)
        assertEquals(expectedTitle, page.title)
    }

    @Test fun originalBodyDecodesEmojiAndNonBreakingSpacesWithoutLosingStyles() {
        val blocks = ContentParser.parse("有创意就能开工，一句想法，轻松做出一个能跑、能玩、能对战的&amp;#160;[b]3D[/b]&amp;#160;体素世界&amp;#127918;<br/>&amp;#128073;点击报名")
        val text = blocks.single() as ContentBlock.Text
        assertEquals("有创意就能开工，一句想法，轻松做出一个能跑、能玩、能对战的\u00a03D\u00a0体素世界🎮\n👉点击报名", text.value)
        val bold = text.spans.single { it.bold }
        assertEquals("3D", text.value.substring(bold.start, bold.end))
    }

    @Test fun encodedWhitespaceIsNotPartOfTheOriginalQuestionnaireUrl() {
        val text = ContentParser.parse("[url]https://wj.qq.com/s2/28259413/jd78/&amp;#160;[/url]").single() as ContentBlock.Text
        assertEquals("https://wj.qq.com/s2/28259413/jd78/", text.spans.single().url)
        assertEquals("https://wj.qq.com/s2/28259413/jd78/", text.value)
        val named = ContentParser.parse("[url=https://example.com/?a=1&amp;b=2&amp;#160;]问卷[/url]").single() as ContentBlock.Text
        assertEquals("https://example.com/?a=1&b=2", named.spans.single().url)
        assertFalse(ContentParser.parse("[url=javascript&amp;#58;alert(1)]bad[/url]").filterIsInstance<ContentBlock.Text>().any { block -> block.spans.any { it.url != null } })
    }

    @Test fun numericDecodingDoesNotReparseEscapedMarkupOrCode() {
        val text = ContentParser.parse("&amp;#60;b&amp;#62;文字&amp;#60;/b&amp;#62; &amp;#91;b&amp;#93;原文&amp;#91;/b&amp;#93;").single() as ContentBlock.Text
        assertEquals("<b>文字</b> [b]原文[/b]", text.value)
        assertTrue(text.spans.isEmpty())
        val raw = "&amp;#128227; &#160; &amp;lt;b&amp;gt;"
        assertEquals(ContentBlock.Code(raw), ContentParser.parse("[code]$raw[/code]").single())
        assertEquals(ContentBlock.Code("&#128227; &#160;"), ContentParser.parse("<pre>&amp;#128227; &amp;#160;</pre>").single())
    }

    @Test fun nestedDecimalAndHexEntitiesDecodeWithoutChangingNamedEntityLiterals() {
        assertEquals("📣 🎮 \u00a0", plain("&amp;amp;#128227; &amp;#x1F3AE; &amp;#160;"))
        val text = ContentParser.parse("&amp;amp;#128227; &amp;#X1F3AE; &amp;lt;literal&amp;gt; &amp;#unknown;").single() as ContentBlock.Text
        assertEquals("📣 🎮 &lt;literal&gt; &#unknown;", text.value)
        assertEquals("\uFFFD", (ContentParser.parse("&amp;#x110000;").single() as ContentBlock.Text).value)
    }
}
