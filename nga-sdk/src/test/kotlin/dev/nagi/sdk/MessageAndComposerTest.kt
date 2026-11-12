package dev.nagi.sdk

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class MessageAndComposerTest {
    private suspend fun store() = MemorySessionStore().apply { saveSession(NgaSession("7", "test-token")) }
    private fun form(request: okhttp3.mockwebserver.RecordedRequest) =
        request.body.readUtf8().split('&').associate {
            val pair = it.split('=', limit = 2)
            java.net.URLDecoder.decode(pair[0], "UTF-8") to java.net.URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
        }

    @Test fun conversationsHaveOrderedItemsParticipantsAndPagination() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":{"0":{"1":{"mid":10,"from":8,"subject":"Second"},"0":{"mid":9,"from":7,"from_username":"Alice","subject":"Title","posts":3,"all_user":"7\tAlice\t8\tBob","time":12,"last_modify":24}},"nextPage":"page=2"}}"""))
            val result = HttpNgaClient(server.url("/"), store()).getMessageConversations(1)
            assertEquals(listOf("9", "10"), result.items.map { it.id })
            assertEquals(listOf("Alice", "Bob"), result.items.first().participants.map { it.name })
            assertEquals(3, result.items.first().postCount)
            assertEquals(24L, result.items.first().lastActiveAt)
            assertTrue(result.hasMore)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("list", request.requestUrl!!.queryParameter("act"))
            assertNull(request.requestUrl!!.queryParameter("action"))
        }
    }

    @Test fun conversationsSeparateInlinePaginationMetadataFromRecords() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":{"0":{"0":{"mid":9,"from":0,"from_username":"#SYSTEM#","subject":"System","posts":65},"1":{"mid":10,"from":7,"subject":"First","posts":4},"2":{"mid":11,"from":8,"subject":"Second","posts":2},"currentPage":1,"rowsPerPage":20}},"time":123}"""))
            val result = HttpNgaClient(server.url("/"), store()).getMessageConversations()
            assertEquals(listOf("9", "10", "11"), result.items.map { it.id })
            assertEquals(listOf(65, 4, 2), result.items.map { it.postCount })
            assertFalse(result.hasMore)
        }
    }

    @Test fun inlineConversationNextPageIsPreserved() {
        val result = NgaInteractionParser.conversations("""{"data":{"0":{"0":{"mid":9,"from":7},"currentPage":1,"rowsPerPage":1,"nextPage":"page=2"}}}""", 1)
        assertEquals("9", result.items.single().id)
        assertTrue(result.hasMore)
    }

    @Test fun unexpectedScalarConversationEntriesStillFail() {
        val error = runCatching {
            NgaInteractionParser.conversations("""{"data":{"0":{"0":{"mid":9,"from":7},"unexpected":1}}}""", 1)
        }.exceptionOrNull()
        assertTrue(error is NgaException.Protocol)
    }

    @Test fun messageDetailsUseConversationIdAndDedicatedEnvelope() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":{"0":{"allmsgs":{"0":{"id":90,"from":7,"subject":"Re","content":"Hello [b]Bob[/b]","time":33}},"userInfo":{"7":{"uid":7,"username":"Alice"}},"nextPage":""}}}"""))
            val result = HttpNgaClient(server.url("/"), store()).getMessagePosts("9", 2)
            assertEquals("90", result.items.single().id)
            assertEquals("Alice", result.participants.single().name)
            assertTrue(result.items.single().content.isNotEmpty())
            assertFalse(result.hasMore)
            val url = server.takeRequest().requestUrl!!
            assertEquals("read", url.queryParameter("act"))
            assertEquals("9", url.queryParameter("mid"))
            assertEquals("2", url.queryParameter("page"))
        }
    }

    @Test fun messageUserGroupMetadataIsNotAParticipant() {
        val result = NgaInteractionParser.messagePosts("""{"data":{"0":{"allmsgs":{"0":{"id":90,"from":0,"content":"Synthetic system message"}},"userInfo":{"0":{"uid":0,"username":"#SYSTEM#"},"__GROUPS":{"39":{"name":"Members"}}},"nextPage":"page=2"}}}""", 1)
        assertEquals(listOf("0"), result.participants.map { it.userId })
        assertEquals("90", result.items.single().id)
        assertTrue(result.hasMore)
        val error = runCatching {
            NgaInteractionParser.messagePosts("""{"data":{"0":{"allmsgs":{},"userInfo":{"7":{"username":"Missing identity"}}}}}""", 1)
        }.exceptionOrNull()
        assertTrue(error is NgaException.Protocol)
    }

    @Test fun emptyMessagesAreValidButUnknownAndMalformedShapesFail() {
        assertTrue(NgaInteractionParser.conversations("""{"data":{"0":[],"nextPage":""}}""", 1).items.isEmpty())
        assertTrue(NgaInteractionParser.messagePosts("""{"data":{"0":{"allmsgs":{},"nextPage":""}}}""", 1).items.isEmpty())
        listOf("""{"data":{"unexpected":{}}}""", """{"data":{"0":{"0":{"subject":"Missing identity"}}}}""").forEach {
            assertTrue(runCatching { NgaInteractionParser.conversations(it, 1) }.exceptionOrNull() is NgaException.Protocol)
        }
        assertTrue(runCatching { NgaInteractionParser.messagePosts("""{"data":{"0":{"userInfo":{}}}}""", 1) }.exceptionOrNull() is NgaException.Protocol)
    }

    @Test fun messageWritesUseBodyAndAcceptSuccessUnlessKnownError() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), store())
            server.enqueue(MockResponse().setBody("""{"data":{"0":0}}"""))
            assertTrue(client.sendMessage(MessageRequest(MessageAction.NEW, "Private 😄", "Title", listOf("Alice", "Bob"))) is WriteResult.Confirmed)
            val request = server.takeRequest()
            assertEquals("new", request.requestUrl!!.queryParameter("act"))
            assertNull(request.requestUrl!!.queryParameter("content"))
            val fields = form(request)
            assertEquals("Alice Bob", fields["to"])
            assertEquals("Private &#55357;&#56836;", fields["content"])
            assertEquals("Title", fields["subject"])
            server.enqueue(MockResponse().setBody("""{"error":{"0":"Permission denied"}}"""))
            assertTrue(client.sendMessage(MessageRequest(MessageAction.REPLY, "Reply", conversationId = "9")) is WriteResult.Rejected)
            val reply = form(server.takeRequest())
            assertEquals("9", reply["mid"])
            assertFalse(reply.containsKey("to"))
        }
    }

    @Test fun messageWritesDoNotRetryOrFailOver() = runBlocking {
        MockWebServer().use { primary ->
            MockWebServer().use { backup ->
                val client = HttpNgaClient(primary.url("/"), store(), backupOrigins = listOf(backup.url("/")))
                val draft = MessageRequest(MessageAction.REPLY, "Reply", conversationId = "9")
                primary.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
                assertEquals(WriteResult.Unconfirmed(), client.sendMessage(draft))
                primary.enqueue(MockResponse().setResponseCode(307).setHeader("Location", backup.url("/")))
                assertEquals(WriteResult.Unconfirmed(), client.sendMessage(draft))
                assertEquals(2, primary.requestCount)
                assertEquals(0, backup.requestCount)
            }
        }
    }

    @Test fun guestsCannotReadOrSendPrivateDataOrPreparePosts() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), MemorySessionStore())
            assertTrue(runCatching { client.getMessageConversations() }.exceptionOrNull() is NgaException.Authentication)
            assertTrue(runCatching { client.getMessagePosts("9") }.exceptionOrNull() is NgaException.Authentication)
            assertTrue(runCatching { client.preparePublish(PublishTarget(PublishAction.REPLY, topicId = "1")) }.exceptionOrNull() is NgaException.Authentication)
            assertTrue(client.sendMessage(MessageRequest(MessageAction.NEW, "Body", "Title", listOf("Alice"))) is WriteResult.Rejected)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun invalidDestinationsAndRequestsFailBeforeTransmission() = runBlocking {
        listOf<() -> Any>(
            { PublishTarget(PublishAction.COMMENT, topicId = "1") },
            { PublishTarget(PublishAction.NEW_TOPIC) },
            { PublishTarget(PublishAction.QUOTE, topicId = "1", postId = " ") },
            { MessageRequest(MessageAction.NEW, "Body", "Title") },
            { MessageRequest(MessageAction.NEW, "Body", "Title", listOf("Alice Bob")) },
            { MessageRequest(MessageAction.REPLY, "Body") },
            { MessageRequest(MessageAction.REPLY, "Body", recipients = listOf("Alice"), conversationId = "9") },
            { MessageRequest(MessageAction.NEW, " ", "Title", listOf("Alice")) },
        ).forEach { assertTrue(runCatching(it).exceptionOrNull() is IllegalArgumentException) }
        val client = HttpNgaClient("http://localhost/".toHttpUrl(), store())
        assertTrue(runCatching { client.getMessageConversations(0) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { client.getMessagePosts(" ") }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun preparationFetchesServerReplyTextWithoutSubmitting() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""<root><action>quote</action><tid>123</tid><pid>0</pid><auth>test</auth><__CU><uid>7</uid></__CU><content>[quote]Hello &amp;amp;#55357;&amp;amp;#56836; &amp;lt;raw&amp;gt;[/quote]
Next line</content><subject>Title</subject></root>"""))
            val result = HttpNgaClient(server.url("/"), store()).preparePublish(PublishTarget(PublishAction.QUOTE, topicId = "123", postId = "0"))
            assertEquals("[quote]Hello 😄 <raw>[/quote]\nNext line", result.content)
            val request = server.takeRequest()
            assertEquals("quote", request.requestUrl!!.queryParameter("action"))
            assertEquals("0", request.requestUrl!!.queryParameter("pid"))
            assertEquals("xml", request.requestUrl!!.queryParameter("lite"))
            assertFalse(form(request).containsKey("step"))
        }
    }

    @Test fun mainAndTopicReplyPreparationAllowValidatedEmptyEditorEnvelope() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), store())
            for (pid in listOf("0", null)) {
                server.enqueue(MockResponse().setBody("""<root><action>reply</action><__T><authorid>8</authorid></__T><pid></pid><auth>test</auth><tid>123</tid><__CU><uid>7</uid></__CU><__F/></root>"""))
                val result = client.preparePublish(PublishTarget(PublishAction.REPLY, topicId = "123", postId = pid))
                assertEquals("", result.content)
                val request = server.takeRequest()
                assertEquals(pid, request.requestUrl!!.queryParameter("pid"))
                assertFalse(form(request).containsKey("step"))
            }
        }
    }

    @Test fun preparationRejectsUnknownErrorsAndExternalEntities() = runBlocking {
        assertTrue(runCatching { NgaXmlParser.publishPreparation("<root><unknown/></root>") }.exceptionOrNull() is NgaException.Protocol)
        assertTrue(runCatching { NgaXmlParser.publishPreparation("<root><error>请登录</error></root>") }.exceptionOrNull() is NgaException.Authentication)
        assertTrue(runCatching { NgaXmlParser.publishPreparation("""<!DOCTYPE root [<!ENTITY x SYSTEM "file:///etc/passwd">]><root><content>&x;</content></root>""") }.exceptionOrNull() is NgaException.Protocol)
    }

    @Test fun commentsTargetThePostWhileCollectionPublishingUsesOnlyStid() = runBlocking {
        MockWebServer().use { server ->
            val client = HttpNgaClient(server.url("/"), store())
            server.enqueue(MockResponse().setBody("""{"data":{"0":0,"1":"回复成功"}}"""))
            assertTrue(client.publish(PublishRequest(PublishAction.COMMENT, "", "Comment", topicId = "123", postId = "0")) is WriteResult.Confirmed)
            val request = server.takeRequest()
            assertEquals("reply", request.requestUrl!!.queryParameter("action"))
            assertEquals("1", request.requestUrl!!.queryParameter("comment"))
            assertEquals("123", request.requestUrl!!.queryParameter("tid"))
            assertEquals("0", request.requestUrl!!.queryParameter("pid"))
            assertEquals("2", form(request)["step"])
            server.enqueue(MockResponse().setBody("""{"data":{"0":0,"1":"发帖成功"}}"""))
            client.publish(PublishRequest(PublishAction.NEW_TOPIC, "7", "Body", "Title", collectionId = "99"))
            val url = server.takeRequest().requestUrl!!
            assertEquals("99", url.queryParameter("stid"))
            assertNull(url.queryParameter("fid"))
        }
    }

    @Test fun structuredPostsPreserveInlineComments() {
        val json = """{"data":{"__T":{"subject":"Title"},"__R":{"0":{"pid":0,"content":"Body","comment":{"0":{"pid":42,"authorid":8,"content":"Comment"}}}}}}"""
        assertEquals("42", NgaParser.posts(json, 1).items.single().comments.single().id)
        val xml = """<root><__T/><__R><item pid="0"><content>Body</content><comment><item pid="42" authorid="8"><content>Comment</content></item></comment></item></__R></root>"""
        assertEquals("42", NgaXmlParser.posts(xml, 1).items.single().comments.single().id)
    }

    @Test fun submissionPreservesBbcodeAndEscapesLegacyUnsupportedCharacters() {
        assertEquals("[b]中文 & text[/b]\n&#55357;&#56836;", escapeNgaSubmission("[b]中文 & text[/b]\n😄"))
        assertFalse(MessageRequest(MessageAction.REPLY, "secret", conversationId = "9").toString().contains("secret"))
    }

    @Test fun accountSwitchDiscardsMessageResponse() = runBlocking {
        MockWebServer().use { server ->
            val sessions = store()
            server.enqueue(MockResponse().setBody("""{"data":{"0":[],"nextPage":""}}""").setBodyDelay(300, TimeUnit.MILLISECONDS))
            val call = async(kotlinx.coroutines.Dispatchers.Default) { HttpNgaClient(server.url("/"), sessions).getMessageConversations() }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            sessions.saveSession(NgaSession("8", "another-test-token"))
            assertTrue(runCatching { call.await() }.exceptionOrNull() is CancellationException)
        }
    }
}
