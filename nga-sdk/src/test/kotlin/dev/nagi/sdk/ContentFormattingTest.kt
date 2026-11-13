package dev.nagi.sdk

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class ContentFormattingTest {
    @Test fun headingsAndDividersAreBlocks() {
        val blocks = ContentParser.parse("[h]天气查询[/h]正文\n===前言===\n======")
        assertTrue(blocks.first() is ContentBlock.Heading)
        assertEquals(2, blocks.count { it is ContentBlock.Divider })
        val styled = ContentParser.parse("===[size=150%][color=blue]前言[/color][/size]===").single() as ContentBlock.Divider
        val text = styled.children.single() as ContentBlock.Text
        assertEquals("前言", text.value)
        assertTrue(text.spans.any { it.color == "blue" && it.scale == 1.5f })
    }

    @Test fun inlineFormattingAndReferencesKeepTheirTargets() {
        val text = ContentParser.parse("[color=red][size=150%][b]文字[/b][/size][/color] [uid=7]作者[/uid] [tid=123]主题[/tid] [pid=9,123,2]楼层[/pid] [@名字]").single() as ContentBlock.Text
        assertEquals("文字 作者 主题 楼层 @名字", text.value)
        assertTrue(text.spans.any { it.bold && it.color == "red" && it.scale == 1.5f })
        assertTrue(text.spans.any { it.url == "https://bbs.nga.cn/nuke.php?__lib=ucp&__act=get&uid=7" })
        assertTrue(text.spans.any { it.url == "https://bbs.nga.cn/read.php?tid=123" })
        assertTrue(text.spans.any { it.url == "https://bbs.nga.cn/read.php?pid=9&tid=123&page=2" })
        assertTrue(text.spans.any { it.url?.contains("username=") == true })
    }

    @Test fun invalidFormattingAndLinksDoNotBecomeActive() {
        val text = ContentParser.parse("[color=notacolor]A[/color][size=NaN]B[/size][size=999%]C[/size][uid=-1]匿名[/uid][tid=bad]D[/tid][url=javascript:bad]E[/url]").single() as ContentBlock.Text
        assertEquals("ABC匿名DE", text.value)
        assertTrue(text.spans.all { it.color == null && it.scale == null && it.url == null })
    }

    @Test fun listsAlignmentsAndTablesRetainRichChildren() {
        val aligned = ContentParser.parse("[align=right][list=1][*][b]第一[/b][*]第二[list][*]嵌套[/list][/list][/align]").single() as ContentBlock.Aligned
        val list = aligned.children.single() as ContentBlock.ListBlock
        assertTrue(list.ordered)
        assertEquals(2, list.items.size)
        assertTrue(list.items[1].any { it is ContentBlock.ListBlock })
        val table = ContentParser.parse("[table][tr][td1][b]粗体[/b][/td][td width=20][url=https://example.com]链接[/url][s:ac:哭笑][/td][/tr][/table]").single() as ContentBlock.Table
        assertTrue((table.rows[0][0].single() as ContentBlock.Text).spans.single().bold)
        assertTrue((table.rows[0][1].single() as ContentBlock.Text).smiles.isNotEmpty())
        assertTrue((table.rows[0][1].single() as ContentBlock.Text).spans.any { it.url != null })
    }

    @Test fun mediaLegacyImagesAndSpecialBreaks() {
        val context = ContentContext(postDate = "2026-11-11 10:47")
        val blocks = ContentParser.parse("[noimg]image.png[/noimg][album]./mon_202611/11/a.png\n./mon_202611/11/b.png[/album][flash=audio]https://example.com/a.mp3[/flash][attach]https://example.com/a.zip[/attach][img]https://example.com/a.mp4?x=1[/img]前[stripbr]后", context = context)
        assertEquals("https://img.nga.cn/attachments/mon_202611/11/image.png", (blocks[0] as ContentBlock.Image).url)
        assertEquals(3, blocks.count { it is ContentBlock.Image })
        assertEquals(listOf(MediaKind.AUDIO, MediaKind.ATTACHMENT, MediaKind.VIDEO), blocks.filterIsInstance<ContentBlock.Media>().map { it.kind })
        assertEquals("前\n后", (blocks.last() as ContentBlock.Text).value)
        val unsafe = ContentParser.parse("[flash]javascript:bad[/flash][attach]data:text/html,bad[/attach]")
        assertTrue(unsafe.none { it is ContentBlock.Media })
    }

    @Test fun diceIsDeterministicAndDoesNotInventPreviewResults() {
        val raw = "[dice]2d6+3[/dice][dice]d20[/dice]"
        val context = ContentContext("7", "123", "9")
        assertEquals(ContentParser.parse(raw, context = context), ContentParser.parse(raw, context = context))
        assertTrue(ContentParser.parse(raw, context = context).filterIsInstance<ContentBlock.Dice>().all { it.total != null })
        assertEquals(null, (ContentParser.parse(raw).first() as ContentBlock.Dice).total)
        assertEquals(null, (ContentParser.parse("[dice]100000d6[/dice]", context = context).single() as ContentBlock.Dice).total)
        val dice = ContentParser.parse("[dice]2d6+3[/dice]", context = context).single() as ContentBlock.Dice
        assertEquals(listOf(5, 6), dice.rolls)
        assertEquals(14L, dice.total)
    }

    @Test fun nestedTablesAndCodeKeepTheirBoundaries() {
        val table = ContentParser.parse("[table][tr][td][code]<unsafe>[/td][/code][table][tr][td]内层[/td][/tr][/table][/td][/tr][/table]").single() as ContentBlock.Table
        assertEquals(ContentBlock.Code("<unsafe>[/td]"), table.rows[0][0][0])
        assertTrue(table.rows[0][0][1] is ContentBlock.Table)
        val html = ContentParser.parse("<table><tr><td><pre>[h]原文[/h]</pre></td><td>正文</td></tr></table>").single() as ContentBlock.Table
        assertEquals(listOf(ContentBlock.Code("[h]原文[/h]")), html.rows[0][0])
        val quote = ContentParser.parse("[quote][code][/quote][/code]正文[/quote]").single() as ContentBlock.Quote
        assertEquals(ContentBlock.Code("[/quote]"), quote.children[0])
        assertEquals(ContentBlock.Text("正文"), quote.children[1])
        val list = ContentParser.parse("[list][*]<pre>[*][h]原文[/h]</pre>[*]第二项[/list]").single() as ContentBlock.ListBlock
        assertEquals(2, list.items.size)
        assertEquals(ContentBlock.Code("[*][h]原文[/h]"), list.items[0].single())
    }

    @Test fun locationParametersAndAlbumBreaksArePreserved() {
        val text = ContentParser.parse("[pid=0,tid=123,reply=0]主楼[/pid]").single() as ContentBlock.Text
        assertEquals("https://bbs.nga.cn/read.php?pid=0&tid=123&reply=0", text.spans.single().url)
        val album = ContentParser.parse("[album]./a.png<br/>./b.png[/album]")
        assertEquals(2, album.filterIsInstance<ContentBlock.Image>().size)
    }

    @Test fun codeAndUnknownMarkupStayLiteral() {
        assertEquals(ContentBlock.Code("[h]标题[/h][color=red]红[/color]======"), ContentParser.parse("[code][h]标题[/h][color=red]红[/color]======[/code]").single())
        assertEquals(ContentBlock.Code("[h]标题[/h]======"), ContentParser.parse("<pre>[h]标题[/h]======</pre>").single())
        assertEquals(ContentBlock.Text("[custom]原文[/custom]"), ContentParser.parse("[custom]原文[/custom]").single())
    }
}
