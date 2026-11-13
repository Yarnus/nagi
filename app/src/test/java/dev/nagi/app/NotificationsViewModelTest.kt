package dev.nagi.app

import dev.nagi.app.data.AccountManager
import dev.nagi.app.viewmodel.NotificationsViewModel
import dev.nagi.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun refreshRetainsHistoryReadStateAndNewItemsAcrossRecreationAndAccounts() = runTest(dispatcher) {
        val store = MemorySessionStore().apply { saveSession(NgaSession("7", "test")); saveSession(NgaSession("8", "other")); selectAccount("7") }
        val accounts = AccountManager(store, FakeBrowserCookies()).also { it.initialize() }
        val databases = FakeDaoProvider()
        val first = CommunityItem("first", "Title", "", "Actor", "2", "3", 42, false, unreadKnown = false)
        val second = first.copy(id = "second", createdAt = 50)
        var items = listOf(first)
        val client = object : NgaClient by FakeClient() {
            override suspend fun getNotificationFeed() = NotificationFeed(items, 99)
        }
        val vm = NotificationsViewModel(client, accounts, databases)
        vm.setForeground(true); runCurrent()
        assertEquals(1, vm.state.value.unreadCount)
        vm.markRead(vm.state.value.items.single()); runCurrent()
        assertEquals(0, vm.state.value.unreadCount)
        items = listOf(first, second, second)
        vm.refresh(); runCurrent()
        assertEquals(listOf("second", "first"), vm.state.value.items.map { it.id })
        assertEquals(1, vm.state.value.unreadCount)
        vm.markAllRead(); runCurrent()
        items = emptyList(); vm.refresh(); runCurrent()
        assertEquals(2, vm.state.value.items.size)
        assertEquals(0, vm.state.value.unreadCount)
        val restored = NotificationsViewModel(client, accounts, databases)
        runCurrent()
        assertEquals(vm.state.value.items, restored.state.value.items)
        accounts.switchAccount("8"); runCurrent()
        assertTrue(vm.state.value.items.isEmpty())
        accounts.switchAccount("7"); runCurrent()
        assertEquals(2, vm.state.value.items.size)
        assertEquals(0, vm.state.value.unreadCount)
    }

    @Test fun guestsNeverFetchAndForegroundRequestsCoalesce() = runTest(dispatcher) {
        val store = MemorySessionStore()
        val accounts = AccountManager(store, FakeBrowserCookies()).also { it.initialize() }
        var calls = 0
        val gate = CompletableDeferred<NotificationFeed>()
        val client = object : NgaClient by FakeClient() {
            override suspend fun getNotificationFeed(): NotificationFeed { calls++; return gate.await() }
        }
        val vm = NotificationsViewModel(client, accounts, FakeDaoProvider())
        vm.setForeground(true); runCurrent()
        assertEquals(0, calls)
        store.saveSession(NgaSession("7", "test")); accounts.initialize(); runCurrent()
        vm.refresh(); vm.setForeground(true); runCurrent()
        assertEquals(1, calls)
        gate.complete(NotificationFeed(emptyList(), 3)); runCurrent()
        assertEquals(0, vm.state.value.unreadCount)
        vm.setForeground(false); vm.setForeground(true); runCurrent()
        assertEquals(2, calls)
    }

    @Test fun failuresRetainCountAndLogoutClearsOldAccount() = runTest(dispatcher) {
        val store = MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }
        val accounts = AccountManager(store, FakeBrowserCookies()).also { it.initialize() }
        var fail = false
        val client = object : NgaClient by FakeClient() {
            override suspend fun getNotificationFeed(): NotificationFeed {
                if (fail) throw NgaException.Network()
                return NotificationFeed(listOf(CommunityItem("1", "Title", "", "User", "2", "3", 42, true)), 1)
            }
        }
        val vm = NotificationsViewModel(client, accounts, FakeDaoProvider())
        vm.setForeground(true); runCurrent()
        fail = true; vm.refresh(); runCurrent()
        assertEquals(1, vm.state.value.unreadCount)
        assertEquals(1, vm.state.value.items.size)
        assertNotNull(vm.state.value.error)
        accounts.logout(); runCurrent()
        assertNull(vm.state.value.unreadCount)
        assertTrue(vm.state.value.items.isEmpty())
    }

    @Test fun pendingOldAccountCannotDeliverAfterSwitch() = runTest(dispatcher) {
        val store = MemorySessionStore().apply { saveSession(NgaSession("7", "test")); saveSession(NgaSession("8", "other")); selectAccount("7") }
        val accounts = AccountManager(store, FakeBrowserCookies()).also { it.initialize() }
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun getNotificationFeed(): NotificationFeed {
                val call = ++calls
                if (call == 1) withContext(NonCancellable) { gate.await() }
                return NotificationFeed(emptyList(), if (call == 1) 99 else 2)
            }
        }
        val vm = NotificationsViewModel(client, accounts, FakeDaoProvider())
        vm.setForeground(true); runCurrent()
        accounts.switchAccount("8"); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals("8", vm.state.value.userId)
        assertEquals(0, vm.state.value.unreadCount)
    }
}
