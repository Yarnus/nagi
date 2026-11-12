package dev.nagi.app

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.nagi.app.data.AndroidSessionStore
import dev.nagi.sdk.createNgaClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageListReadTest {
    companion object {
        private val sessions by lazy {
            AndroidSessionStore(InstrumentationRegistry.getInstrumentation().targetContext)
        }
    }
    @Test fun existingAccountConversationIsReadable() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("nagiMessageDetailsRead") == "true")
        assumeTrue("Requires a signed-in account", sessions.snapshot().session != null)
        val client = createNgaClient(sessions = sessions)
        val conversations = client.getMessageConversations().items
        assertTrue("Expected existing conversations", conversations.isNotEmpty())
        for (conversation in conversations) {
            kotlinx.coroutines.delay(1500)
            val details = client.getMessagePosts(conversation.id)
            assertTrue("Expected conversation posts", details.items.isNotEmpty())
            instrumentation.sendStatus(0, Bundle().apply {
                putString("stream", "Conversation page 1 verified: ${details.items.size} posts; more=${details.hasMore}\n")
            })
            if (details.hasMore) {
                kotlinx.coroutines.delay(1500)
                val next = client.getMessagePosts(conversation.id, 2)
                assertTrue("Expected next page posts", next.items.isNotEmpty())
                assertTrue("Pages must not repeat posts", details.items.map { it.id }.intersect(next.items.map { it.id }.toSet()).isEmpty())
            }
        }
        instrumentation.sendStatus(0, Bundle().apply {
            putString("stream", "Details verified: ${conversations.size} conversations\n")
        })
    }

    @Test fun existingAccountInboxIsReadable() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("nagiMessageRead") == "true")
        assumeTrue("Requires an existing signed-in account", sessions.snapshot().session != null)
        val result = createNgaClient(sessions = sessions).getMessageConversations()
        assertTrue("Expected the existing account's inbox to contain conversations", result.items.isNotEmpty())
        instrumentation.sendStatus(0, Bundle().apply {
            putString("stream", "Message inbox verified: ${result.items.size} conversations\n")
        })
    }
}
