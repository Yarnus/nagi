package dev.nagi.app

import dev.nagi.app.data.AccountManager
import dev.nagi.app.data.LocalFavorite
import dev.nagi.app.data.PreferenceStore
import dev.nagi.app.viewmodel.LibraryViewModel
import dev.nagi.app.viewmodel.userMessage
import dev.nagi.sdk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    @Test fun historyRemovalAndClearRespectFavoritedPositions() = runTest(dispatcher) {
        val sessions = MemorySessionStore()
        val accounts = AccountManager(sessions, FakeBrowserCookies()).also { it.initialize() }
        val preferences = PreferenceStore(RuntimeEnvironment.getApplication())
        val daos = FakeDaoProvider()
        val dao = daos.forUser(null)
        dao.saveFavorite(LocalFavorite("fav", "Fav"))
        dao.savePosition(dev.nagi.app.data.ReadingPosition("fav", 2, 1, 0, updatedAt = 10L))
        dao.savePosition(dev.nagi.app.data.ReadingPosition("other", 3, 2, 0, updatedAt = 20L))
        dao.saveHistory(dev.nagi.app.data.ReadingHistory("fav", "Fav", 1000L))
        dao.saveHistory(dev.nagi.app.data.ReadingHistory("other", "Other", 2000L))

        val vm = LibraryViewModel(FakeClient(), daos, sessions, preferences, accounts)
        runCurrent()
        vm.removeHistory("other"); runCurrent()
        assertEquals(listOf("fav"), dao.history.value.map { it.topicId })
        assertNotNull(dao.position("other"))

        vm.clearHistory(clearUnfavoritedPositions = true); runCurrent()
        assertTrue(dao.history.value.isEmpty())
        assertNotNull(dao.position("fav"))
        assertNull(dao.position("other"))
    }

    @Test fun guestDoesNotRequestOrWriteCloudFavoriteForums() = runTest(dispatcher) {
        val sessions = MemorySessionStore()
        val accounts = AccountManager(sessions, FakeBrowserCookies()).also { it.initialize() }
        val preferences = PreferenceStore(RuntimeEnvironment.getApplication())
        var readCalls = 0
        var writeCalls = 0
        val delegate = FakeClient()
        val client = object : NgaClient by delegate {
            override suspend fun getFavoriteForums(): List<Forum> {
                readCalls++
                return emptyList()
            }
            override suspend fun setForumFavorite(forum: Forum, favorite: Boolean): WriteResult {
                writeCalls++
                return WriteResult.Confirmed("ok")
            }
        }
        val vm = LibraryViewModel(client, FakeDaoProvider(), sessions, preferences, accounts)
        runCurrent()
        assertFalse(vm.isSignedIn)
        assertEquals(0, readCalls)
        vm.loadCloudFavoriteForums(); runCurrent()
        vm.setCloudForumFavorite(Forum("7", "Forum", "", ""), true); runCurrent()
        assertEquals(0, readCalls)
        assertEquals(0, writeCalls)
        assertTrue(vm.cloudFavoriteForums.value.items.isEmpty())
        assertEquals("请先登录 NGA 后再管理 NGA 收藏版块", vm.cloudForumNotice.value)
    }

    @Test fun signedInCloudFavoriteForumsHandleConfirmRejectAndUnconfirmedLocks() = runTest(dispatcher) {
        val sessions = MemorySessionStore().apply { saveSession(NgaSession("7", "test-token")) }
        val accounts = AccountManager(sessions, FakeBrowserCookies()).also { it.initialize() }
        val preferences = PreferenceStore(RuntimeEnvironment.getApplication())
        val serverForums = mutableListOf(Forum("7", "Forum", "", ""))
        var readFailure: Throwable? = null
        var nextWriteResult: WriteResult = WriteResult.Confirmed("已加入")
        var writeCount = 0
        val delegate = FakeClient()
        val client = object : NgaClient by delegate {
            override suspend fun getFavoriteForums(): List<Forum> {
                readFailure?.let { throw it }
                return serverForums.toList()
            }
            override suspend fun setForumFavorite(forum: Forum, favorite: Boolean): WriteResult {
                writeCount++
                val result = nextWriteResult
                if (result is WriteResult.Confirmed) {
                    if (favorite) {
                        if (serverForums.none { it.key == forum.key }) serverForums += forum
                    } else {
                        serverForums.removeAll { it.key == forum.key }
                    }
                }
                return result
            }
        }
        val vm = LibraryViewModel(client, FakeDaoProvider(), sessions, preferences, accounts)
        val feedback = mutableListOf<String>()
        val feedbackJob = backgroundScope.launch { vm.cloudForumFeedback.collect { feedback += it } }
        runCurrent()
        assertEquals(listOf("forum:7"), vm.cloudFavoriteForums.value.items.map { it.key })

        // Confirmed write updates list immediately without triggering a stale re-read
        val col = Forum("0", "Collection", "", "", collectionId = "99")
        vm.setCloudForumFavorite(col, true); runCurrent()
        assertEquals(listOf("forum:7", "collection:99"), vm.cloudFavoriteForums.value.items.map { it.key })
        assertTrue(feedback.contains("已加入 NGA 收藏版块"))

        vm.setCloudForumFavorite(col, false); runCurrent()
        assertEquals(listOf("forum:7"), vm.cloudFavoriteForums.value.items.map { it.key })
        assertTrue(feedback.contains("已取消 NGA 收藏版块"))

        // Explicit refresh failure preserves current items and surfaces error
        readFailure = NgaException.Network()
        vm.loadCloudFavoriteForums(); runCurrent()
        assertEquals(listOf("forum:7"), vm.cloudFavoriteForums.value.items.map { it.key })
        assertNotNull(vm.cloudFavoriteForums.value.error)
        readFailure = null
        vm.loadCloudFavoriteForums(); runCurrent()
        assertNull(vm.cloudFavoriteForums.value.error)
        assertEquals(listOf("forum:7"), vm.cloudFavoriteForums.value.items.map { it.key })

        // Rejected write keeps list unchanged and does not lock key
        nextWriteResult = WriteResult.Rejected(NgaException.Restricted(NgaRejectionReason.PERMISSION))
        vm.setCloudForumFavorite(col, true); runCurrent()
        assertFalse(col.key in vm.cloudForumUncertainKeys.value)
        assertEquals(listOf("forum:7"), vm.cloudFavoriteForums.value.items.map { it.key })

        // Unconfirmed write locks key until loadCloudFavoriteForums succeeds
        val writesBeforeUnconfirmed = writeCount
        nextWriteResult = WriteResult.Unconfirmed()
        vm.setCloudForumFavorite(col, true); runCurrent()
        assertEquals(writesBeforeUnconfirmed + 1, writeCount)
        assertTrue(col.key in vm.cloudForumUncertainKeys.value)
        nextWriteResult = WriteResult.Confirmed("ok")
        vm.setCloudForumFavorite(col, true); runCurrent()
        assertEquals(writesBeforeUnconfirmed + 1, writeCount) // Blocked while locked

        vm.loadCloudFavoriteForums(); runCurrent()
        assertFalse(col.key in vm.cloudForumUncertainKeys.value)
        vm.setCloudForumFavorite(col, true); runCurrent()
        assertEquals(writesBeforeUnconfirmed + 2, writeCount)
        feedbackJob.cancel()
    }
}
