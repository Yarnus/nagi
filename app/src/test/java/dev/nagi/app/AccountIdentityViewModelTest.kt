package dev.nagi.app

import dev.nagi.app.data.AccountManager
import dev.nagi.app.viewmodel.AccountIdentityViewModel
import dev.nagi.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AccountIdentityViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun guestDoesNotFetchAndLogoutClearsIdentity() = runTest(dispatcher) {
        val store = MemorySessionStore()
        val manager = AccountManager(store, FakeBrowserCookies()).also { it.initialize() }
        var calls = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun getUser(userId: String): NgaUser {
                calls++; return NgaUser(userId, "Name $userId", "https://example.org/avatar", 4)
            }
        }
        val vm = AccountIdentityViewModel(manager, client)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.identity.collect() }
        runCurrent(); assertEquals(0, calls); assertNull(vm.identity.value.user)
        store.saveSession(NgaSession("7", "test")); manager.initialize(); runCurrent()
        assertEquals("Name 7", vm.identity.value.user?.name)
        manager.logout(); runCurrent()
        assertNull(vm.identity.value.user); assertNull(vm.identity.value.userId)
    }

    @Test fun switchDropsDelayedProfileAndRetryDoesNotAlterSession() = runTest(dispatcher) {
        val store = MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }
        val manager = AccountManager(store, FakeBrowserCookies()).also { it.initialize() }
        val old = CompletableDeferred<NgaUser>()
        var fail = true
        val client = object : NgaClient by FakeClient() {
            override suspend fun getUser(userId: String): NgaUser {
                if (userId == "7") return old.await()
                if (fail) throw IllegalStateException("private server response")
                return NgaUser(userId, "Current", "", 0)
            }
        }
        val vm = AccountIdentityViewModel(manager, client)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.identity.collect() }
        runCurrent(); assertTrue(vm.identity.value.loading)
        store.saveSession(NgaSession("8", "test")); manager.initialize(); runCurrent()
        old.complete(NgaUser("7", "Old", "old-avatar", 0)); runCurrent()
        assertEquals("8", vm.identity.value.userId); assertNull(vm.identity.value.user)
        assertEquals("无法加载账号资料", vm.identity.value.error)
        val revision = manager.state.value.revision
        fail = false; vm.retry(); runCurrent()
        assertEquals("Current", vm.identity.value.user?.name)
        assertEquals(revision, manager.state.value.revision)
    }
}
