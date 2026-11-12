package dev.nagi.app

import androidx.lifecycle.SavedStateHandle
import androidx.compose.ui.text.input.TextFieldValue
import dev.nagi.app.data.AccountManager
import dev.nagi.app.viewmodel.*
import dev.nagi.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class QueuedWriteAccountTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun queuedWritesNeverUseTheReplacementAccount() = runTest(dispatcher) {
        val store = MemorySessionStore().apply {
            saveSession(NgaSession("8", "test-b")); saveSession(NgaSession("7", "test-a"))
        }
        val accounts = AccountManager(store, FakeBrowserCookies()).also { it.initialize() }
        var writes = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun preparePublish(target: PublishTarget) = PublishPreparation("", "")
            override suspend fun publish(request: PublishRequest): WriteResult { writes++; return WriteResult.Unconfirmed() }
            override suspend fun sendMessage(request: MessageRequest): WriteResult { writes++; return WriteResult.Unconfirmed() }
            override suspend fun ratePost(topicId: String, postId: String, rating: PostRating): WriteResult { writes++; return WriteResult.Unconfirmed() }
        }
        val reply = ReplySessionViewModel(client, accounts, SavedStateHandle())
        reply.open(PublishTarget(PublishAction.REPLY, topicId = "123"), "Reply")
        runCurrent(); reply.edit(TextFieldValue("Reply"))
        val messages = MessagesViewModel(client, accounts)
        messages.compose(true); messages.edit("Body", 4); messages.details("Title", "Alice")
        val rating = PostRatingViewModel(client, accounts, SavedStateHandle())
        reply.send(); messages.send(); rating.rate("123", "0", PostRating.UP)
        accounts.switchAccount("8")
        runCurrent()
        assertEquals(0, writes)
    }
}
