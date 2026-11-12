package dev.nagi.app

import dev.nagi.app.data.AccountManager
import dev.nagi.app.data.PreferenceStore
import dev.nagi.app.viewmodel.LibraryViewModel
import dev.nagi.app.viewmodel.userMessage
import dev.nagi.sdk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun sdkFailuresPreserveDirectoryAndAllowRefreshRetry() = runTest(dispatcher) {
        val sessions = MemorySessionStore()
        val accounts = AccountManager(sessions, FakeBrowserCookies()).also { it.initialize() }
        val preferences = PreferenceStore(RuntimeEnvironment.getApplication())
        for (error in listOf(NgaException.Network(java.net.SocketTimeoutException()), NgaException.Authentication(),
            NgaException.Restricted(), NgaException.Http(503))) {
            var failure: NgaException? = null
            val delegate = FakeClient()
            val client = object : NgaClient by delegate {
                override suspend fun getForums(): List<Forum> {
                    failure?.let { throw it }
                    return delegate.getForums()
                }
            }
            val vm = LibraryViewModel(client, FakeDaoProvider(), sessions, preferences, accounts)
            runCurrent()
            val loaded = vm.forums.value.items
            assertTrue(loaded.isNotEmpty())
            failure = error
            vm.loadForums(); runCurrent()
            assertFalse(vm.forums.value.loading)
            assertEquals(loaded, vm.forums.value.items)
            assertEquals(userMessage(error), vm.forums.value.error)
            failure = null
            vm.loadForums(); runCurrent()
            assertNull(vm.forums.value.error)
            assertEquals(loaded, vm.forums.value.items)
        }
    }
}
