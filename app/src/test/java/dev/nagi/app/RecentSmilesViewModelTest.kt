package dev.nagi.app

import dev.nagi.app.data.AccountManager
import dev.nagi.app.viewmodel.RecentSmilesViewModel
import dev.nagi.sdk.MemorySessionStore
import dev.nagi.sdk.NgaSession
import dev.nagi.sdk.NgaSmiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecentSmilesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun recentSmilesAreIsolatedPerAccountAndOrderedMostRecentFirst() = runTest(dispatcher) {
        val sessions = MemorySessionStore().apply { saveSession(NgaSession("7", "t1")) }
        val accounts = AccountManager(sessions, FakeBrowserCookies()).also { it.initialize() }
        val daos = FakeDaoProvider()
        val vm = RecentSmilesViewModel(daos, accounts)
        runCurrent()

        val first = NgaSmiles.resolve("ac:blink")!!
        val second = NgaSmiles.resolve("ng:吃瓜")!!
        vm.record(first); runCurrent()
        // Advance clock or ensure deterministic overwrite by calling record again with distinct timestamp if needed.
        daos.forUser("7").recordRecentSmile(second.key, System.currentTimeMillis() + 10L)
        runCurrent()
        assertEquals(listOf(second, first), vm.state.value.smiles)

        // Switching to another account isolates recent smiles.
        sessions.saveSession(NgaSession("8", "t2"))
        accounts.initialize(); runCurrent()
        assertTrue(vm.state.value.smiles.isEmpty())

        vm.record(first); runCurrent()
        assertEquals(listOf(first), vm.state.value.smiles)

        // Switching back preserves account 7's recent smiles.
        sessions.saveSession(NgaSession("7", "t1"))
        accounts.initialize(); runCurrent()
        assertEquals(listOf(second, first), vm.state.value.smiles)
    }
}
