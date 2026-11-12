package dev.nagi.app

import androidx.lifecycle.SavedStateHandle
import dev.nagi.app.data.AccountManager
import dev.nagi.app.viewmodel.UserProfileViewModel
import dev.nagi.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun refreshFailureRetainsProfileAndRetryRecovers() = runTest(dispatcher) {
        val accounts = AccountManager(MemorySessionStore(), FakeBrowserCookies()).also { it.initialize() }
        var fail = false
        val client = object : NgaClient by FakeClient() {
            override suspend fun getUser(userId: String): NgaUser {
                if (fail) throw NgaException.Network()
                return NgaUser(userId, "用户", "", 10, "上海", 1000000000L)
            }
        }
        val vm = UserProfileViewModel(client, accounts, SavedStateHandle(mapOf("uid" to "7")))
        runCurrent(); fail = true; vm.refresh(); runCurrent()
        assertEquals("上海", vm.state.value.user!!.ipLocation)
        assertNotNull(vm.state.value.error)
        fail = false; vm.refresh(); runCurrent()
        assertNull(vm.state.value.error)
    }

    @Test fun staleAndMismatchedProfilesNeverAppear() = runTest(dispatcher) {
        val accounts = AccountManager(MemorySessionStore(), FakeBrowserCookies()).also { it.initialize() }
        val gate = CompletableDeferred<Unit>()
        val client = object : NgaClient by FakeClient() {
            override suspend fun getUser(userId: String): NgaUser { gate.await(); return NgaUser(userId, "Old", "", 1) }
        }
        val vm = UserProfileViewModel(client, accounts, SavedStateHandle(mapOf("uid" to "7")))
        runCurrent(); accounts.logout(); gate.complete(Unit); runCurrent()
        assertNull(vm.state.value.user)
        val wrong = UserProfileViewModel(object : NgaClient by FakeClient() {
            override suspend fun getUser(userId: String) = NgaUser("8", "Wrong", "", 0)
        }, accounts, SavedStateHandle(mapOf("uid" to "7")))
        runCurrent()
        assertNull(wrong.state.value.user)
        assertNotNull(wrong.state.value.error)
    }
}
