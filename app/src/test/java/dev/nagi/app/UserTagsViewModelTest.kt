package dev.nagi.app

import dev.nagi.app.data.*
import dev.nagi.app.viewmodel.UserTagsViewModel
import dev.nagi.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class UserTagsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun saveEditDeleteAndGuestAccountsAreIsolatedAndRestored() = runTest(dispatcher) {
        val sessions = MemorySessionStore().apply { saveSession(NgaSession("7", "test")); saveSession(NgaSession("8", "other")); selectAccount("7") }
        val accounts = AccountManager(sessions, FakeBrowserCookies()).also { it.initialize() }
        val databases = FakeDaoProvider()
        val vm = UserTagsViewModel(databases, accounts)
        runCurrent()
        var completed = 0
        vm.save(vm.state.value, "42", "  攻略作者  ") { completed++ }; runCurrent()
        assertEquals("攻略作者", vm.state.value.tags["42"])
        vm.save(vm.state.value, "42", "朋友") { completed++ }; runCurrent()
        assertEquals("朋友", vm.state.value.tags["42"])
        val previousOwner = vm.state.value
        accounts.switchAccount("8"); runCurrent()
        assertTrue(vm.state.value.tags.isEmpty())
        vm.save(previousOwner, "42", "Stale") { fail("Stale write completed") }; runCurrent()
        assertTrue(databases.forUser("8").userTags().first().isEmpty())
        accounts.logout(); runCurrent()
        vm.save(vm.state.value, "42", "游客标记") {}; runCurrent()
        accounts.switchAccount("7"); runCurrent()
        assertEquals("朋友", vm.state.value.tags["42"])
        val restored = UserTagsViewModel(databases, accounts); runCurrent()
        assertEquals("朋友", restored.state.value.tags["42"])
        vm.save(vm.state.value, "42", " ") { completed++ }; runCurrent()
        assertTrue(vm.state.value.tags.isEmpty())
        assertEquals(3, completed)
        assertEquals("游客标记", databases.forUser(null).userTags().first().single().text)
    }

    @Test fun invalidIdentityAndTextNeverReachStorageAndEmojiLimitUsesCodePoints() = runTest(dispatcher) {
        val accounts = AccountManager(MemorySessionStore(), FakeBrowserCookies()).also { it.initialize() }
        val databases = FakeDaoProvider()
        val vm = UserTagsViewModel(databases, accounts); runCurrent()
        for (uid in listOf("", "0", "-1", "anonymous", "1x")) vm.save(vm.state.value, uid, "tag") { fail() }
        for (text in listOf("a".repeat(25), "first\nsecond", "a\u0000b")) {
            vm.save(vm.state.value, "42", text) { fail() }; runCurrent()
        }
        assertTrue(databases.forUser(null).userTags().first().isEmpty())
        assertNotNull(vm.state.value.error)
        val emoji = "😀".repeat(24)
        vm.save(vm.state.value, "42", emoji) {}; runCurrent()
        assertEquals(emoji, vm.state.value.tags["42"])
        assertNull(vm.state.value.error)
    }

    @Test fun failedSaveKeepsEditorOpenAndDoesNotInventTag() = runTest(dispatcher) {
        val accounts = AccountManager(MemorySessionStore(), FakeBrowserCookies()).also { it.initialize() }
        val dao = object : ReadingDao by FakeDao() {
            override suspend fun saveUserTag(tag: LocalUserTag) { throw IllegalStateException("Storage unavailable") }
        }
        val provider = object : ReadingDaoProvider { override fun forUser(userId: String?) = dao }
        val vm = UserTagsViewModel(provider, accounts); runCurrent()
        vm.save(vm.state.value, "42", "tag") { fail("Failed save closed editor") }; runCurrent()
        assertTrue(vm.state.value.tags.isEmpty())
        assertFalse(vm.state.value.saving)
        assertNotNull(vm.state.value.error)
    }
}
