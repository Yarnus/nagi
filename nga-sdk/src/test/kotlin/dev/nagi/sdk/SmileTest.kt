package dev.nagi.sdk

import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.net.URLDecoder
import org.junit.Test

class SmileTest {
    @Test fun catalogContainsAllGroupsAndOnlyTrustedRemoteResources() {
        assertEquals(listOf("ac", "a2", "ng", "pst", "dt", "pg", "0"), NgaSmiles.groups.map { it.id })
        val smiles = NgaSmiles.groups.flatMap { it.smiles }
        assertEquals(265, smiles.size)
        assertEquals(265, smiles.map { it.key }.distinct().size)
        smiles.forEach {
            assertEquals(it, NgaSmiles.resolve(it.key))
            assertEquals(it, NgaSmiles.fromUrl(it.url))
            assertTrue(it.url.startsWith("https://img4.nga.cn/ngabbs/post/smile/"))
            assertEquals("[s:${it.key}]", it.code)
        }
    }

    @Test fun legacyDefaultAliasesShareOneIdentityAndUnknownNamesStayReadable() {
        val expected = NgaSmiles.resolve("0:1")!!
        listOf("1", ":1", "0:1").forEach { assertEquals(expected, NgaSmiles.resolve(it)) }
        val raw = "[s:1][s:0:1][s::1][s:unknown:name][s:999]"
        val block = ContentParser.parse(raw).single() as ContentBlock.Text
        assertEquals(raw, block.value)
        assertEquals(listOf(expected, expected, expected), block.smiles.map { it.smile })
        assertEquals(listOf("[s:1]", "[s:0:1]", "[s::1]"), block.smiles.map { raw.substring(it.start, it.end) })
    }

    @Test fun smilesStayInsideStyledParagraphsWithUnicodeAndLineBreaks() {
        val raw = "你好😀[b][s:ac:哭笑]加粗[/b]<br/>[url=https://bbs.nga.cn/][s:a2:doge]链接[/url]结束"
        val block = ContentParser.parse(raw).single() as ContentBlock.Text
        assertEquals("你好😀[s:ac:哭笑]加粗\n[s:a2:doge]链接结束", block.value)
        assertEquals(2, block.smiles.size)
        block.smiles.forEach { inline ->
            assertEquals(inline.smile.code, block.value.substring(inline.start, inline.end))
            assertTrue(block.spans.any { it.start <= inline.start && it.end >= inline.end })
        }
        assertTrue(block.spans.any { it.bold })
        assertTrue(block.spans.any { it.url == "https://bbs.nga.cn/" })
    }

    @Test fun quotesAndCollapsesKeepInlineSmilesButCodeStaysLiteral() {
        val blocks = ContentParser.parse("[quote]前[s:ng:doge]后[/quote][collapse=更多][s:pg:吃瓜][/collapse][code][s:ac:哭笑][/code]")
        assertEquals(1, ((blocks[0] as ContentBlock.Quote).children.single() as ContentBlock.Text).smiles.size)
        assertEquals(1, ((blocks[1] as ContentBlock.Collapse).children.single() as ContentBlock.Text).smiles.size)
        assertEquals(ContentBlock.Code("[s:ac:哭笑]"), blocks[2])
    }

    @Test fun renderedHtmlSmilesStayInlineButAttachmentsAndLookalikesDoNot() {
        val base = "https://img.nga.cn/attachments/"
        val url = NgaSmiles.resolve("ac:哭笑")!!.url
        val block = ContentParser.parseHtml("前<img src='$url'/>后", base).single() as ContentBlock.Text
        assertEquals("前[s:ac:哭笑]后", block.value)
        assertEquals(1, block.smiles.size)
        assertEquals(block, ContentParser.parseHtml("前<img src='http://img4.ngacn.cc/ngabbs/post/smile/ac15.png'/>后", base).single())
        listOf(
            "https://evil.example/ngabbs/post/smile/ac15.png",
            "https://img4.nga.cn.evil.example/ngabbs/post/smile/ac15.png",
            "https://img4.nga.cn:8443/ngabbs/post/smile/ac15.png",
            "https://img.nga.cn/attachments/ac15.png",
        ).forEach { untrusted ->
            assertNull(NgaSmiles.fromUrl(untrusted))
            val image = ContentParser.parseHtml("<img src='$untrusted' data-nagi-emoji='ac:哭笑'/>", base).single()
            assertEquals(ContentBlock.Image(untrusted), image)
        }
        assertNull(NgaSmiles.fromUrl("https://user:secret@img4.nga.cn/ngabbs/post/smile/ac15.png"))
    }

    @Test fun writesSendCanonicalCodesWithoutUploadingOrSubstitutingImages() = runBlocking {
        MockWebServer().use { server ->
            val sessions = MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }
            val client = HttpNgaClient(server.url("/"), sessions)
            val body = "前${NgaSmiles.resolve("ac:哭笑")!!.code}${NgaSmiles.resolve("1")!!.code}后"
            server.enqueue(MockResponse().setBody("{}"))
            assertTrue(client.publish(PublishRequest(PublishAction.REPLY, "", body, topicId = "123")) is WriteResult.Confirmed)
            server.enqueue(MockResponse().setBody("{}"))
            assertTrue(client.sendMessage(MessageRequest(MessageAction.REPLY, body, conversationId = "9")) is WriteResult.Confirmed)
            listOf("post_content", "content").forEach { field ->
                val fields = server.takeRequest().body.readUtf8().split('&').associate {
                    URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
                }
                assertEquals(body, fields[field])
                assertFalse(fields.values.any { it.contains("/post/smile/") })
            }
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun adjacentSmilesShareAParagraphAndDoNotChangeAttachmentBehavior() {
        val blocks = ContentParser.parse("[s:ac:哭笑][s:ac:哭笑][img]https://img.nga.cn/attachments/a.png[/img]结尾[s:dt:哭]")
        assertEquals(2, (blocks[0] as ContentBlock.Text).smiles.size)
        assertEquals(ContentBlock.Image("https://img.nga.cn/attachments/a.png"), blocks[1])
        assertEquals(1, (blocks[2] as ContentBlock.Text).smiles.size)
    }
}
