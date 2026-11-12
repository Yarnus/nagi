package dev.nagi.sdk

import org.junit.Assert.*
import org.junit.Test

class ParserRegressionTest {
    @Test fun protocolDiagnosticsNeverExposeMalformedResponseContent() {
        val marker = "SENSITIVE_RESPONSE_MARKER"
        val errors = listOf(
            "P-2000" to assertThrows(NgaException.Protocol::class.java) {
                NgaParser.data("{\"$marker\":1,\"$marker\":2}")
            },
            "P-1000" to assertThrows(NgaException.Protocol::class.java) {
                NgaXmlParser.posts("<root><$marker></root>", 1)
            },
        )
        for ((code, error) in errors) {
            assertEquals(code, error.code)
            assertFalse(error.stackTraceToString().contains(marker))
            assertNull(error.cause)
            assertTrue(error.suppressed.isEmpty())
        }
    }

    @Test fun protocolReasonChainsPreserveOnlyStableCodes() {
        val error = NgaException.Protocol(ProtocolReason.XML_MALFORMED)
            .then(NgaException.Protocol(ProtocolReason.HTML_PAGINATION_INVALID))
        assertEquals("P-1000>P-3006", error.code)
        assertNull(error.cause)
    }

    @Test fun thirtyRowTopicPaginationDoesNotHideFinalPage() {
        val page = NgaParser.topics("""{"data":{"__T":{"0":{"tid":1,"subject":"Title"}},"__ROWS":65}}""", 2)
        assertTrue(page.hasMore)
    }
    @Test fun jsonPostPagesExposePageSizeAndTotalPagesOnlyWhenRowsAreKnown() {
        val body = """"__T":{"subject":"T","authorid":7},"__R":{"0":{"pid":1,"authorid":7,"lou":0,"content":"x"}}"""
        val known = NgaParser.posts("""{"data":{$body,"__ROWS":65,"__R__ROWS_PAGE":20}}""", 1)
        assertEquals(20, known.pageSize)
        assertEquals(4, known.totalPages)
        assertEquals(2, known.pageOfFloor(20))
        val unknown = NgaParser.posts("""{"data":{$body}}""", 1)
        assertEquals(0, unknown.totalPages)
        assertEquals(0, unknown.pageSize)
        assertNull(unknown.pageOfFloor(20))
        assertNull(PostPage(emptyList(), "", "", 1, 0, false).pageOfFloor(5))
    }
    @Test fun htmlBlocksKeepStructureAndSafeInlineStyles() {
        val blocks = ContentParser.parse("<blockquote>quote</blockquote><table><tr><td>A</td><td>B</td></tr></table><b>bold</b> <a href='javascript:bad'>unsafe</a>")
        assertTrue(blocks[0] is ContentBlock.Quote)
        assertEquals(ContentBlock.Table(listOf(listOf("A", "B"))), blocks[1])
        val text = blocks[2] as ContentBlock.Text
        assertTrue(text.spans.first().bold)
        assertTrue(text.spans.all { it.url == null })
    }
    @Test fun envelopeCleanupNeverTruncatesCommentMarkersInsidePostText() {
        val page = NgaParser.topics("""{"data":{"__T":{"0":{"tid":1,"subject":"literal /*error fill content stays intact"}}}}""", 1)
        assertEquals("literal /*error fill content stays intact", page.items.single().title)
    }
    @Test fun nullErrorsAreNotFailuresAndInvalidRowsAreNotEmptySuccess() {
        val valid = NgaParser.topics("""{"error":null,"data":{"__T":{}}}""", 1)
        assertTrue(valid.items.isEmpty())
        assertTrue(runCatching { NgaParser.topics("""{"data":{"__T":{"0":"bad"}}}""", 1) }.exceptionOrNull() is NgaException.Protocol)
    }
    @Test fun knownSmilesUseRuntimeUrlsAndUnknownSmilesRemainReadable() {
        val blocks = ContentParser.parse("[s:ac:哭笑][s:unknown:name]")
        val text = blocks.single() as ContentBlock.Text
        assertEquals("[s:ac:哭笑][s:unknown:name]", text.value)
        assertEquals(listOf(InlineSmile(0, 9, NgaSmiles.resolve("ac:哭笑")!!)), text.smiles)
    }
    @Test fun ngaQuoteHeadersBecomeAttributionInsteadOfRawReferenceTags() {
        val quote = ContentParser.parse("[quote][b]Reply to [pid=101,202,1]Reply[/pid] Post by [uid=7]测试作者[/uid] (2026-11-04 10:15)[/b]引用正文[/quote]").single() as ContentBlock.Quote
        assertEquals("测试作者", quote.author)
        assertEquals("2026-11-04 10:15", quote.date)
        assertEquals(listOf(ContentBlock.Text("引用正文")), quote.children)
    }
    @Test fun legacyQuoteHeadersAndNestedReferencesPreserveText() {
        val quote = ContentParser.parse("[quote][pid=101,tid=202,reply=3]Reply[/pid][b]Post by [uid=7]测试作者[/b]外层[quote]回复 [uid=8]另一作者[/uid]：内层[/quote][/quote]").single() as ContentBlock.Quote
        assertEquals("测试作者", quote.author)
        assertEquals(ContentBlock.Text("外层"), quote.children[0])
        val nested = quote.children[1] as ContentBlock.Quote
        assertEquals(listOf(ContentBlock.Text("回复 另一作者：内层")), nested.children)
    }
    @Test fun malformedAttributionCannotConsumeNestedQuoteContent() {
        val quote = ContentParser.parse("[quote][b]Post by [uid=7]未闭合[quote][b]内部文本[/b][/quote]尾部[/quote]").single() as ContentBlock.Quote
        assertNull(quote.author)
        assertTrue((quote.children[0] as ContentBlock.Text).value.contains("未闭合"))
        val nested = quote.children[1] as ContentBlock.Quote
        assertEquals("内部文本", (nested.children.single() as ContentBlock.Text).value)
        assertEquals("尾部", (quote.children[2] as ContentBlock.Text).value)
    }
    @Test fun referenceCleanupDoesNotAlterCodeOrRemoveUnknownMarkup() {
        val blocks = ContentParser.parse("[code][uid=7]literal[/uid][/code]<pre>[pid=1,2,3]literal[/pid]</pre>[custom]保留[/custom] [uid=9]用户名[/uid]")
        assertEquals(ContentBlock.Code("[uid=7]literal[/uid]"), blocks[0])
        assertEquals(ContentBlock.Code("[pid=1,2,3]literal[/pid]"), blocks[1])
        assertEquals(ContentBlock.Text("[custom]保留[/custom] 用户名"), blocks[2])
    }
    @Test fun nestedQuotesAndCollapsesKeepTheirBoundaries() {
        val blocks = ContentParser.parse("[quote]outer[quote]inner[/quote]end[/quote]after")
        val outer = blocks[0] as ContentBlock.Quote
        assertEquals(listOf(ContentBlock.Text("outer"), ContentBlock.Quote(listOf(ContentBlock.Text("inner"))), ContentBlock.Text("end")), outer.children)
        assertEquals(ContentBlock.Text("after"), blocks[1])
    }
    @Test fun rawControlCharactersInsideJsonStringsAreEscapedSafely() {
        val raw = "{\"data\":{\"__T\":{\"0\":{\"tid\":1,\"subject\":\"标题\t含制表符\n换行\u0007\"}}}}"
        val page = NgaParser.topics(raw, 1)
        assertTrue(page.items.single().title.contains("标题"))
        assertTrue(page.items.single().title.contains("含制表符"))
    }
    @Test fun xmlPostsAndTopicsIgnoreNestedItemsAndRejectDoctype() {
        val xml = """
            <?xml version="1.0" encoding="GB18030"?>
            <root>
              <__GLOBAL><_ATTACH_BASE_VIEW>img.nga.cn/attachments</_ATTACH_BASE_VIEW></__GLOBAL>
              <__U><item i="0"><uid>7</uid><username>作者A</username><avatar>//img.nga.cn/avatars/7.png</avatar></item></__U>
              <__T><tid>123</tid><subject>XML 标题 &amp; 测试&#x07;</subject><authorid>7</authorid></__T>
              <__R>
                <item i="0">
                  <pid>0</pid><lou>0</lou><authorid>7</authorid><postdate>2026-11-06 12:00</postdate>
                  <content>正文[img]./mon_202611/x.png[/img]</content>
                  <attachs><item><attachurl>mon_202611/x.png</attachurl></item></attachs>
                </item>
              </__R>
              <__ROWS>45</__ROWS><__R__ROWS_PAGE>20</__R__ROWS_PAGE>
            </root>
        """.trimIndent()
        val page = NgaXmlParser.posts(xml, 1)
        assertEquals("XML 标题 & 测试", page.title)
        assertEquals(1, page.items.size)
        assertEquals("作者A", page.items.single().author)
        assertEquals("https://img.nga.cn/avatars/7.png", page.items.single().avatar)
        assertEquals(3, page.totalPages)
        assertTrue(page.hasMore)
        assertTrue(page.items.single().content.any { it == ContentBlock.Image("https://img.nga.cn/attachments/mon_202611/x.png") })

        val dtdXml = """<?xml version="1.0"?><!DOCTYPE root [<!ENTITY xxe "secret">]><root/>"""
        assertTrue(runCatching { NgaXmlParser.posts(dtdXml, 1) }.exceptionOrNull() is NgaException.Protocol)
    }

    @Test fun postMetadataAndStrikethroughSpansAreParsedAcrossJsonXmlAndHtml() {
        val blocks = ContentParser.parse("[del]别骂了别骂了[/del] 新客户端根本就不是网页版套皮，[del]我告你们毁谤啊！[/del]")
        val textBlock = blocks.single() as ContentBlock.Text
        assertEquals("别骂了别骂了 新客户端根本就不是网页版套皮，我告你们毁谤啊！", textBlock.value)
        assertEquals(2, textBlock.spans.count { it.strikethrough })
        assertEquals("别骂了别骂了", textBlock.value.substring(textBlock.spans[0].start, textBlock.spans[0].end))
        assertEquals("我告你们毁谤啊！", textBlock.value.substring(textBlock.spans[1].start, textBlock.spans[1].end))

        val jsonRaw = """
            {
              "data": {
                "__U": {
                  "__GROUPS": { "39": { "0": "学徒", "1": 100, "2": 39 }, "52": { "0": "Warden", "1": 500, "2": 52 } },
                  "7": { "uid": 7, "username": "gerraerd", "memberid": 52, "rvrc": 10, "postnum": 496 },
                  "8": { "uid": 8, "username": "铜镜印無邪", "memberid": 39, "rvrc": 13, "postnum": 560 }
                },
                "__T": { "subject": "iOS客户端闪退问题", "authorid": 7 },
                "__R": {
                  "0": { "pid": 0, "lou": 0, "authorid": 7, "postdate": "2025-05-26 17:27", "content": "[del]别骂了[/del]", "score": 0, "from_client": "7 iOS" },
                  "1": { "pid": 101, "lou": 1, "authorid": 8, "postdate": "2025-05-26 17:32", "content": "安卓版还没做好吗", "score": 108, "from_client": "8 Android" }
                }
              }
            }
        """.trimIndent()
        val jsonPage = NgaParser.posts(jsonRaw, 1)
        val p0 = jsonPage.items[0]
        assertEquals(0, p0.score)
        assertEquals("Warden", p0.authorLevel)
        assertEquals("1.0", p0.authorReputation)
        assertEquals(496, p0.authorPosts)
        assertEquals("iOS", p0.clientPlatform)
        val p1 = jsonPage.items[1]
        assertEquals(108, p1.score)
        assertEquals("学徒", p1.authorLevel)
        assertEquals("1.3", p1.authorReputation)
        assertEquals(560, p1.authorPosts)
        assertEquals("Android", p1.clientPlatform)

        val xmlRaw = """
            <?xml version="1.0" encoding="GB18030"?>
            <root>
              <__U>
                <__GROUPS>
                  <item><item>Warden</item><item>500</item><item>52</item></item>
                </__GROUPS>
                <item i="0"><uid>7</uid><username>gerraerd</username><memberid>52</memberid><rvrc>10</rvrc><postnum>496</postnum></item>
              </__U>
              <__T><tid>123</tid><subject>iOS客户端闪退问题</subject><authorid>7</authorid></__T>
              <__R>
                <item i="0">
                  <pid>0</pid><lou>0</lou><authorid>7</authorid><postdate>2025-05-26 17:27</postdate>
                  <content>[del]别骂了别骂了[/del]</content><score>108</score><from_client>7 iOS</from_client>
                </item>
              </__R>
            </root>
        """.trimIndent()
        val xmlPost = NgaXmlParser.posts(xmlRaw, 1).items.single()
        assertEquals(108, xmlPost.score)
        assertEquals("Warden", xmlPost.authorLevel)
        assertEquals("1.0", xmlPost.authorReputation)
        assertEquals(496, xmlPost.authorPosts)
        assertEquals("iOS", xmlPost.clientPlatform)
        assertTrue((xmlPost.content.single() as ContentBlock.Text).spans.single().strikethrough)
    }
}
