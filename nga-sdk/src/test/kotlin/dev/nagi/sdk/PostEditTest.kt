package dev.nagi.sdk

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class PostEditTest {
    private val target = PublishTarget(PublishAction.MODIFY, topicId = "123", postId = "12")

    @Test fun editPreparesOriginalMarkupAndSubmitsModifyNotReply() = runBlocking {
        MockWebServer().use { server ->
            val sessions = MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }
            val client = HttpNgaClient(server.url("/"), sessions)
            server.enqueue(MockResponse().setBody("""<root><action>modify</action><tid>123</tid><pid>12</pid><__CU><uid>7</uid></__CU><subject>Original title</subject><content>[b]原文[/b]&amp;lt;tag&amp;gt;
[quote]引用[/quote]</content></root>"""))
            val preparation = client.preparePublish(target)
            assertEquals("Original title", preparation.title)
            assertTrue(preparation.content.startsWith("[b]原文[/b]"))
            assertTrue(preparation.content.contains("[quote]引用[/quote]"))
            val prepare = server.takeRequest()
            assertEquals("modify", prepare.requestUrl!!.queryParameter("action"))
            assertEquals("12", prepare.requestUrl!!.queryParameter("pid"))
            assertFalse(prepare.body.readUtf8().contains("step=2"))
            server.enqueue(MockResponse().setBody("""{"data":{"__MESSAGE":[0,"操作完成",null,null,null,null,"/read.php?tid=123&pid=12"]}}"""))
            val result = client.publish(PublishRequest(PublishAction.MODIFY, "", preparation.content + "\nUpdated", preparation.title,
                topicId = "123", postId = "12"))
            assertTrue(result is WriteResult.Confirmed)
            val write = server.takeRequest()
            assertEquals("modify", write.requestUrl!!.queryParameter("action"))
            assertEquals("123", write.requestUrl!!.queryParameter("tid"))
            assertEquals("12", write.requestUrl!!.queryParameter("pid"))
            assertNull(write.requestUrl!!.queryParameter("comment"))
            assertTrue(write.body.readUtf8().contains("step=2"))
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun preparationRequiresTargetMatchAndOriginalBody() {
        val raw = """<root><action>modify</action><tid>123</tid><pid>12</pid><__CU><uid>7</uid></__CU><content>[b]Original[/b]</content></root>"""
        assertEquals("[b]Original[/b]", NgaXmlParser.publishPreparation(raw, target).content)
        listOf(raw.replace("<pid>12</pid>", "<pid>99</pid>"),
            raw.replace("<action>modify</action>", "<action>reply</action>"),
            raw.replace("<content>[b]Original[/b]</content>", "")).forEach {
            assertTrue(runCatching { NgaXmlParser.publishPreparation(it, target) }.exceptionOrNull() is NgaException.Protocol)
        }
        assertTrue(runCatching { PublishTarget(PublishAction.MODIFY, topicId = "123") }.isFailure)
    }

    @Test fun editsUseTheRequestedActionNotGenericTitlesOrNavigationTargets() {
        val generic = NgaParser.publish("""{"data":{"0":0,"1":"回复成功"}}""", target)
        assertEquals("修改已保存", (generic as WriteResult.Confirmed).message)
        assertTrue(NgaParser.publish("""{"data":{"__MESSAGE":[0,"完成",null,null,null,null,"/read.php?tid=123&pid=99"]}}""", target) is WriteResult.Confirmed)
        assertTrue(NgaParser.publish("""{"data":{"action":"reply","__MESSAGE":[0,"完成"]}}""", target) is WriteResult.Confirmed)
    }
}
