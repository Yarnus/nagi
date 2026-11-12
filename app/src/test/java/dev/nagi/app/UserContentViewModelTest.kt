package dev.nagi.app

import androidx.lifecycle.SavedStateHandle
import dev.nagi.app.data.AccountManager
import dev.nagi.app.viewmodel.UserContentViewModel
import dev.nagi.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class UserContentViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()
    @Test fun failedNextPageKeepsContentAndRetryRequestsFailedPage() = runTest(dispatcher) {
        val manager = AccountManager(MemorySessionStore(), FakeBrowserCookies()).also { it.initialize() }
        val calls = mutableListOf<Int>()
        var fail = true
        val client = object : NgaClient by FakeClient() {
            override suspend fun getUserTopics(userId: String, page: Int): TopicPage {
                calls += page
                if (page == 2 && fail) throw NgaException.Network()
                return TopicPage(listOf(Topic("$page", "Page $page", "Name", userId)), page, 2, page == 1)
            }
        }
        val saved = SavedStateHandle(mapOf("uid" to "7", "kind" to "topics"))
        val vm = UserContentViewModel(client, manager, saved)
        runCurrent(); vm.next(); runCurrent()
        assertEquals("1", vm.state.value.topics.single().id); assertEquals(1, vm.state.value.page)
        assertNotNull(vm.state.value.error)
        vm.next(); runCurrent()
        assertEquals(listOf(1, 2), calls)
        fail = false; vm.retry(); runCurrent()
        assertEquals(listOf(1, 2, 2), calls); assertEquals(2, saved.get<Int>("page"))
        assertEquals(false, vm.state.value.hasMore)
        assertEquals(listOf("1", "2"), vm.state.value.topics.map { it.id })
        vm.next(); runCurrent()
        assertEquals(listOf(1, 2, 2), calls)
    }
    @Test fun tabsRetainIndependentPagesWithoutRefetching() = runTest(dispatcher) {
        val manager = AccountManager(MemorySessionStore(), FakeBrowserCookies()).also { it.initialize() }
        val topics = mutableListOf<Int>()
        val replies = mutableListOf<Int>()
        val client = object : NgaClient by FakeClient() {
            override suspend fun getUserTopics(userId: String, page: Int): TopicPage {
                topics += page
                return TopicPage(listOf(Topic("t$page", "Topic", "User", userId)), page, 100, true)
            }
            override suspend fun getUserReplies(userId: String, page: Int): UserReplyPage {
                replies += page
                return UserReplyPage(listOf(UserReply(Topic("1", "Topic", "User", userId), "p$page", userId, 42, emptyList())), page, null)
            }
        }
        val vm = UserContentViewModel(client, manager, SavedStateHandle(mapOf("uid" to "7")))
        runCurrent(); vm.next(); vm.next(); runCurrent()
        vm.selectReplies(true); runCurrent(); vm.next(); runCurrent()
        vm.selectReplies(false); runCurrent()
        assertEquals(2, vm.state.value.page)
        assertEquals(listOf("t1", "t2"), vm.state.value.topics.map { it.id })
        vm.selectReplies(true); runCurrent()
        assertEquals(listOf("p1", "p2"), vm.state.value.replies.map { it.postId })
        assertNull(vm.state.value.hasMore)
        assertEquals(listOf(1, 2), topics)
        assertEquals(listOf(1, 2), replies)
    }

    @Test fun accountChangeDiscardsPendingResult() = runTest(dispatcher) {
        val manager = AccountManager(MemorySessionStore(), FakeBrowserCookies()).also { it.initialize() }
        val gate = CompletableDeferred<TopicPage>()
        val client = object : NgaClient by FakeClient() {
            override suspend fun getUserTopics(userId: String, page: Int) = gate.await()
        }
        val vm = UserContentViewModel(client, manager, SavedStateHandle(mapOf("uid" to "7")))
        runCurrent(); manager.logout()
        gate.complete(TopicPage(listOf(Topic("1", "Old", "Name", "7")), 1, 1, false)); runCurrent()
        assertTrue(vm.state.value.topics.isEmpty())
    }
}
