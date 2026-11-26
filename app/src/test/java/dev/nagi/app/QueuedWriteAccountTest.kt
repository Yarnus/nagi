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

    @Test fun concurrentWithCurrentRevisionOperationsRunWithoutBlockingAndWriterWaits() = runTest(dispatcher) {
        val store = MemorySessionStore().apply {
            saveSession(NgaSession("7", "test-a"))
            saveSession(NgaSession("8", "test-b"))
        }
        val accounts = AccountManager(store, FakeBrowserCookies()).also { it.initialize() }
        val rev = accounts.state.value.revision
        val slowGate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val slowJob = launch {
            accounts.withCurrentRevision(rev) {
                events += "slow:start"
                slowGate.await()
                events += "slow:end"
            }
        }
        runCurrent()
        assertEquals(listOf("slow:start"), events)

        val fastJob = launch {
            accounts.withCurrentRevision(rev) {
                events += "fast:ran"
            }
        }
        runCurrent()
        assertEquals(listOf("slow:start", "fast:ran"), events)

        val switchJob = launch {
            accounts.switchAccount("8")
            events += "switch:done"
        }
        runCurrent()
        assertEquals(listOf("slow:start", "fast:ran"), events)

        slowGate.complete(Unit)
        runCurrent()
        assertEquals(listOf("slow:start", "fast:ran", "slow:end", "switch:done"), events)
        assertTrue(slowJob.isCompleted && fastJob.isCompleted && switchJob.isCompleted)

        val rejected = runCatching { accounts.withCurrentRevision(rev) { "stale" } }.exceptionOrNull()
        assertTrue(rejected is CancellationException)
    }
}
