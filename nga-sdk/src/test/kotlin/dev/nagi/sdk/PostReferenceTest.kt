package dev.nagi.sdk

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class PostReferenceTest {
    @Test fun quoteKeepsTargetAndOriginalText() {
        val quote = ContentParser.parse("[quote][b]Reply to [pid=101,202,3]Reply[/pid] Post by [uid=7]Author[/uid] (2026-11-04 10:15)[/b]Original[/quote]Reply").first() as ContentBlock.Quote
        assertEquals(PostReference("202", "101", 3), quote.reference)
        assertEquals("Author", quote.author)
        assertEquals(listOf(ContentBlock.Text("Original")), quote.children)
    }

    @Test fun quoteAndReplyHeadersStripTrailingColonAndLeadingBreaks() {
        val blocks = ContentParser.parse(
            "[quote][pid=101,202,3]Reply[/pid] [b]Post by [uid=7]Author[/uid] (2026-11-04 10:15):[/b]<br/><br/>Original[/quote]<br/><br/>Reply",
        )
        assertEquals(2, blocks.size)
        val quote = blocks[0] as ContentBlock.Quote
        assertEquals("Author", quote.author)
        assertEquals("2026-11-04 10:15", quote.date)
        assertEquals(PostReference("202", "101", 3), quote.reference)
        assertEquals(listOf(ContentBlock.Text("Original")), quote.children)
        assertEquals(ContentBlock.Text("Reply"), blocks[1])

        val fullWidthColon = ContentParser.parse(
            "[quote][b]Reply to [pid=101,202,3]Reply[/pid] Post by [uid=7]Author[/uid] (2026-11-04 10:15)[/b]：<br/><br/>Original[/quote]",
        ).single() as ContentBlock.Quote
        assertEquals(listOf(ContentBlock.Text("Original")), fullWidthColon.children)

        val bare = ContentParser.parse(
            "[b]Reply to [pid=0,202,1]Reply[/pid] Post by [uid=7]Author[/uid] (2026-11-04 10:15):[/b]<br/><br/>My reply",
        )
        assertEquals(2, bare.size)
        assertEquals(ContentBlock.Text("My reply"), bare[1])
    }

    @Test fun bareReplyBecomesReferenceWithoutConsumingReplyBody() {
        val blocks = ContentParser.parse("[b]Reply to [pid=0,202,1]Reply[/pid] Post by [uid=7]Author[/uid][/b]My reply")
        val quote = blocks.first() as ContentBlock.Quote
        assertEquals(PostReference("202", "0"), quote.reference)
        assertEquals("Author", quote.author)
        assertTrue(quote.children.isEmpty())
        assertEquals(ContentBlock.Text("My reply"), blocks.last())
    }

    @Test fun consecutiveReplyHeadersDoNotOverflowOrConsumeBody() {
        val header = "[b]Reply to [pid=0,202,1]Reply[/pid][/b]"
        val blocks = ContentParser.parse(header.repeat(5000) + "Final body")
        assertEquals(5001, blocks.size)
        assertTrue(blocks.dropLast(1).all { it is ContentBlock.Quote && it.reference == PostReference("202", "0") && it.children.isEmpty() })
        assertEquals(ContentBlock.Text("Final body"), blocks.last())
    }

    @Test fun legacyTargetsAndMissingAuthorAreSupported() {
        val legacy = ContentParser.parse("[quote][pid=101,tid=202,reply=3]Reply[/pid][b]Post by [uid=7]Author[/b]Original[/quote]").single() as ContentBlock.Quote
        assertEquals(PostReference("202", "101"), legacy.reference)
        val bare = ContentParser.parse("[b]Reply to [pid=101,202,2]Reply[/pid][/b]Body").first() as ContentBlock.Quote
        assertEquals(PostReference("202", "101", 2), bare.reference)
    }

    @Test fun invalidTargetsAndCodeAreNotResolved() {
        val invalid = ContentParser.parse("[quote][b]Reply to [pid=101,evil,1]Reply[/pid] Post by [uid=7]Author[/uid][/b]Original[/quote]").single() as ContentBlock.Quote
        assertNull(invalid.reference)
        assertTrue(ContentParser.parse("[code][b]Reply to [pid=101,202,1]Reply[/pid][/b][/code]").single() is ContentBlock.Code)
    }

    @Test fun explicitInvalidTopicsNeverFallBackToContext() {
        val context = ContentContext(topicId = "123")
        for (topic in listOf("evil", "", "0", "-1", "tid=evil")) {
            val quote = ContentParser.parse(
                "[quote][b]Reply to [pid=0,$topic,1]Reply[/pid][/b]Body[/quote]",
                context = context,
            ).single() as ContentBlock.Quote
            assertNull("Invalid topic: $topic", quote.reference)
            assertEquals(listOf(ContentBlock.Text("Body")), quote.children)
        }
        val missing = ContentParser.parse("[b]Reply to [pid=101]Reply[/pid][/b]Body", context = context)
        assertEquals(PostReference("123", "101"), (missing.first() as ContentBlock.Quote).reference)
        val explicit = ContentParser.parse("[b]Reply to [pid=101,202,2]Reply[/pid][/b]Body", context = context)
        assertEquals(PostReference("202", "101", 2), (explicit.first() as ContentBlock.Quote).reference)
    }

    @Test fun targetedReadSendsPidAndNormalizesPageFromServerPageSize() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":{"__T":{"tid":202,"subject":"Title","authorid":7},"__R":{"0":{"pid":101,"authorid":7,"lou":43,"content":"Original"}},"__ROWS":100,"__R__ROWS_PAGE":20}}"""))
            val result = HttpNgaClient(server.url("/"), MemorySessionStore()).getPostPage(PostReference("202", "101"))
            assertEquals(3, result.page)
            assertEquals("101", result.items.single().id)
            val url = server.takeRequest().requestUrl!!
            assertEquals("101", url.queryParameter("pid"))
            assertEquals("202", url.queryParameter("tid"))
            assertNull(url.queryParameter("authorid"))
        }
    }

    @Test fun stalePageHintsDoNotInflateKnownPagination() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), MemorySessionStore())
            for ((floor, page) in listOf(43 to 3, 99 to 5)) {
                server.enqueue(MockResponse().setBody("""{"data":{"__T":{"tid":202,"subject":"Title"},"__R":{"0":{"pid":101,"lou":$floor,"content":"Original"}},"__ROWS":100,"__R__ROWS_PAGE":20}}"""))
                val result = client.getPostPage(PostReference("202", "101", 99))
                assertEquals(page, result.page)
                assertEquals(5, result.totalPages)
                assertEquals(page < 5, result.hasMore)
            }
        }
    }

    @Test fun targetedReadKeepsUnknownPaginationUnknown() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), MemorySessionStore())
            server.enqueue(MockResponse().setBody("""{"data":{"__T":{"tid":202,"subject":"Title"},"__R":{"0":{"pid":101,"lou":43,"content":"Original"}},"__R__ROWS_PAGE":20}}"""))
            val unknownTotal = client.getPostPage(PostReference("202", "101", 99))
            assertEquals(3, unknownTotal.page)
            assertEquals(0, unknownTotal.totalPages)
            server.enqueue(MockResponse().setBody("""{"data":{"__T":{"tid":202,"subject":"Title"},"__R":{"0":{"pid":101,"lou":43,"content":"Original"}},"__ROWS":100}}"""))
            val unknownSize = client.getPostPage(PostReference("202", "101", 99))
            assertEquals(99, unknownSize.page)
            assertEquals(0, unknownSize.pageSize)
            assertEquals(0, unknownSize.totalPages)
        }
    }

    @Test fun targetedReadNeverSubstitutesAnotherPost() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":{"__T":{"tid":202,"subject":"Title"},"__R":{"0":{"pid":999,"lou":0,"content":"Wrong post"}}}}"""))
            try {
                HttpNgaClient(server.url("/"), MemorySessionStore()).getPostPage(PostReference("202", "101"))
                fail("Expected missing post")
            } catch (_: NgaException.NotFound) { }
        }
    }
}
