package dev.nagi.app

import androidx.lifecycle.SavedStateHandle
import dev.nagi.app.data.*
import dev.nagi.app.viewmodel.*
import dev.nagi.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private suspend fun readerEnv(
        client: NgaClient = FakeClient(),
        daos: FakeDaoProvider = FakeDaoProvider(),
        sessions: SessionStore = MemorySessionStore(),
        browser: FakeBrowserCookies = FakeBrowserCookies(),
    ): Triple<ReaderViewModel, FakeDaoProvider, AccountManager> {
        val manager = AccountManager(sessions, browser).also { it.initialize() }
        return Triple(ReaderViewModel(client, daos, sessions, manager), daos, manager)
    }

    private suspend fun TopicsViewModel(client: NgaClient, saved: SavedStateHandle): dev.nagi.app.viewmodel.TopicsViewModel {
        val sessions = MemorySessionStore().apply { saveSession(NgaSession("7", "test-token")) }
        val accounts = AccountManager(sessions, FakeBrowserCookies()).also { it.initialize() }
        return dev.nagi.app.viewmodel.TopicsViewModel(client, saved, accounts)
    }

    @Test fun queuedSubforumWriteCannotUseSwitchedAccount() = runTest(dispatcher) {
        val sessions = MemorySessionStore().apply { saveSession(NgaSession("7", "test-token")) }
        val accounts = AccountManager(sessions, FakeBrowserCookies()).also { it.initialize() }
        var writes = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun setSubforumVisibility(parentForumId: String, filterId: String, visible: Boolean): WriteResult {
                writes++
                return WriteResult.Confirmed("OK")
            }
        }
        val vm = dev.nagi.app.viewmodel.TopicsViewModel(client, SavedStateHandle(), accounts)
        vm.openForum("-7", null); runCurrent()
        vm.setSubforumVisible(Subforum(Forum("8", "Child", "", ""), "99", 558), false)
        accounts.logout()
        runCurrent()
        assertEquals(0, writes)
        assertNull(vm.filterNotice.value)
    }

    @Test fun subforumWritesRefreshPageOneAndUncertaintyRequiresReadback() = runTest(dispatcher) {
        var writes = 0
        var serverVisible = true
        var outcome: WriteResult = WriteResult.Confirmed("OK")
        val client = object : NgaClient by FakeClient() {
            override suspend fun getTopics(forumId: String, page: Int, collectionId: String?, sort: TopicSort): TopicPage =
                TopicPage(listOf(Topic("$page", "Title", "Author", "7")), page, 60, page < 2,
                    listOf(Subforum(Forum("8", "Child", "", ""), "99", if (serverVisible) 558 else 41)))
            override suspend fun setSubforumVisibility(parentForumId: String, filterId: String, visible: Boolean): WriteResult {
                writes++
                assertEquals("-7", parentForumId)
                assertEquals("99", filterId)
                if (outcome is WriteResult.Confirmed) serverVisible = visible
                return outcome
            }
        }
        val vm = TopicsViewModel(client, SavedStateHandle())
        vm.openForum("-7", null); runCurrent()
        vm.load(); runCurrent()
        assertEquals(2, vm.state.value.page)
        vm.setSubforumVisible(vm.subforums.value.single(), false); runCurrent()
        assertEquals(1, vm.state.value.page)
        assertFalse(vm.subforums.value.single().selected)
        assertFalse(vm.filterLocked.value)
        outcome = WriteResult.Unconfirmed()
        vm.setSubforumVisible(vm.subforums.value.single(), true); runCurrent()
        assertTrue(vm.filterLocked.value)
        vm.setSubforumVisible(vm.subforums.value.single(), true); runCurrent()
        assertEquals(2, writes)
        vm.load(true); runCurrent()
        assertFalse(vm.filterLocked.value)
        assertFalse(vm.subforums.value.single().selected)
    }

    @Test fun uncertainSubforumWriteStaysLockedAfterForumSwitchAndFailedReadback() = runTest(dispatcher) {
        var failRead = false
        var writes = 0
        val sub = Subforum(Forum("8", "Child", "", ""), "99", 558)
        val client = object : NgaClient by FakeClient() {
            override suspend fun getTopics(forumId: String, page: Int, collectionId: String?, sort: TopicSort): TopicPage {
                if (failRead && forumId == "-7") throw NgaException.Network()
                return TopicPage(emptyList(), 1, 0, false, listOf(sub))
            }
            override suspend fun setSubforumVisibility(parentForumId: String, filterId: String, visible: Boolean): WriteResult {
                writes++
                return WriteResult.Unconfirmed()
            }
        }
        val vm = TopicsViewModel(client, SavedStateHandle())
        vm.openForum("-7", null); runCurrent()
        vm.setSubforumVisible(sub, false); runCurrent()
        vm.openForum("9", null); runCurrent()
        failRead = true
        vm.openForum("-7", null); runCurrent()
        assertTrue(vm.filterLocked.value)
        vm.setSubforumVisible(sub, false); runCurrent()
        assertEquals(1, writes)
        failRead = false
        vm.load(true); runCurrent()
        assertFalse(vm.filterLocked.value)
    }

    @Test fun acceptedSubforumWriteWithFailedReadbackStaysLockedWithoutReplay() = runTest(dispatcher) {
        var failRead = false
        var writes = 0
        val sub = Subforum(Forum("8", "Child", "", ""), "99", 558)
        val client = object : NgaClient by FakeClient() {
            override suspend fun getTopics(forumId: String, page: Int, collectionId: String?, sort: TopicSort): TopicPage {
                if (failRead) throw NgaException.Network()
                return TopicPage(emptyList(), 1, 0, false, listOf(sub))
            }
            override suspend fun setSubforumVisibility(parentForumId: String, filterId: String, visible: Boolean): WriteResult {
                writes++
                return WriteResult.Confirmed("OK")
            }
        }
        val vm = TopicsViewModel(client, SavedStateHandle())
        vm.openForum("-7", null); runCurrent()
        failRead = true
        vm.setSubforumVisible(sub, false); runCurrent()
        assertTrue(vm.filterLocked.value)
        assertNotNull(vm.state.value.error)
        assertTrue(vm.filterNotice.value!!.contains("设置已保存"))
        vm.setSubforumVisible(sub, false); runCurrent()
        assertEquals(1, writes)
        failRead = false
        vm.load(true); runCurrent()
        assertFalse(vm.filterLocked.value)
    }

    @Test fun subforumPendingWriteCoalescesAndOldForumResultCannotRefreshNewForum() = runTest(dispatcher) {
        val gate = CompletableDeferred<WriteResult>()
        var writes = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun setSubforumVisibility(parentForumId: String, filterId: String, visible: Boolean): WriteResult {
                writes++
                return gate.await()
            }
        }
        val vm = TopicsViewModel(client, SavedStateHandle())
        val sub = Subforum(Forum("8", "Child", "", ""), "99", 558)
        vm.openForum("-7", null); runCurrent()
        vm.setSubforumVisible(sub, false)
        vm.setSubforumVisible(sub, false); runCurrent()
        assertEquals(1, writes)
        vm.openForum("9", null); runCurrent()
        gate.complete(WriteResult.Confirmed("OK")); runCurrent()
        assertEquals(0, vm.filterEpoch.value)
        assertNull(vm.filterNotice.value)
    }

    @Test fun historyFailureDoesNotDiscardSuccessfulRead() = runTest(dispatcher) {
        val sessions = MemorySessionStore()
        val accounts = AccountManager(sessions, FakeBrowserCookies()).also { it.initialize() }
        val dao = object : ReadingDao by FakeDao() {
            override suspend fun saveHistory(item: ReadingHistory) { throw java.io.IOException("Storage full") }
        }
        val provider = object : ReadingDaoProvider { override fun forUser(userId: String?) = dao }
        val vm = ReaderViewModel(FakeClient(), provider, sessions, accounts)
        vm.open("1"); runCurrent()
        assertNotNull(vm.state.value.data)
        assertNull(vm.state.value.error)
    }

    @Test fun unknownPaginationDisablesFloorJumpsButAllowsPageNavigation() = runTest(dispatcher) {
        for ((size, pages) in listOf(0 to 4, 30 to 0, 0 to 0)) {
            val delegate = FakeClient()
            val client = object : NgaClient by delegate {
                override suspend fun getPosts(topicId: String, page: Int, authorId: String?): PostPage =
                    delegate.getPosts(topicId, page, authorId).copy(pageSize = size, totalPages = pages)
            }
            val (vm, _, _) = readerEnv(client)
            vm.open("1"); runCurrent()
            vm.jumpToFloor(5); runCurrent()
            assertNull(vm.state.value.targetFloor)
            vm.jumpToFloor(65); runCurrent()
            assertEquals(listOf(1), delegate.postRequests.map { it.first })
            vm.jumpToPage(2); runCurrent()
            assertEquals(2, vm.state.value.data!!.page)
            vm.loadNext(); runCurrent()
            assertEquals(3, vm.state.value.endPage)
        }
    }

    @Test fun sdkFailuresKeepReaderAndTopicPagesRetryable() = runTest(dispatcher) {
        for (error in listOf(NgaException.Network(java.net.SocketTimeoutException()), NgaException.Authentication(),
            NgaException.Restricted(), NgaException.Http(503))) {
            var failure: NgaException? = null
            val delegate = FakeClient()
            val client = object : NgaClient by delegate {
                override suspend fun getPosts(topicId: String, page: Int, authorId: String?): PostPage {
                    failure?.let { throw it }
                    return delegate.getPosts(topicId, page, authorId)
                }
                override suspend fun getTopics(forumId: String, page: Int, collectionId: String?, sort: TopicSort): TopicPage {
                    failure?.let { throw it }
                    return delegate.getTopics(forumId, page, collectionId, sort)
                }
            }
            val (reader, _, _) = readerEnv(client)
            val topics = TopicsViewModel(client, SavedStateHandle())
            reader.open("1"); topics.openForum("7", null); runCurrent()
            failure = error
            reader.loadNext(); topics.load(); runCurrent()
            assertEquals(10, reader.state.value.items.size)
            assertFalse(reader.state.value.loadingMore)
            assertEquals(userMessage(error), reader.state.value.moreError)
            assertEquals(1, topics.state.value.page)
            assertFalse(topics.state.value.loading)
            assertEquals(userMessage(error), topics.state.value.error)
            failure = null
            reader.retryMore(); topics.load(); runCurrent()
            assertEquals(2, reader.state.value.endPage)
            assertEquals(2, topics.state.value.page)
            assertNull(reader.state.value.moreError)
            assertNull(topics.state.value.error)
        }
    }

    @Test fun onlySuccessfulNetworkReadsPublishFreshRatingBaselines() = runTest(dispatcher) {
        val client = FakeClient()
        val (vm, _, _) = readerEnv(client = client)
        vm.open("1"); runCurrent()
        val first = vm.state.value
        assertEquals(1L, first.loadedRevision)
        assertEquals(first.items.map { it.id }, first.loadedPostIds)
        vm.jumpToFloor(first.items[1].floor); runCurrent()
        assertEquals(first.loadedRevision, vm.state.value.loadedRevision)
        client.failPosts = true
        vm.load(); runCurrent()
        assertEquals(first.loadedRevision, vm.state.value.loadedRevision)
        client.failPosts = false
        vm.load(); runCurrent()
        assertEquals(2L, vm.state.value.loadedRevision)
        vm.loadNext(); runCurrent()
        assertEquals(3L, vm.state.value.loadedRevision)
        assertEquals(vm.state.value.more.last().items.map { it.id }, vm.state.value.loadedPostIds)
        assertTrue(first.loadedPostIds.none { it in vm.state.value.loadedPostIds })
        vm.open("2"); runCurrent()
        assertEquals(4L, vm.state.value.loadedRevision)
        vm.open("1"); runCurrent()
        assertEquals(5L, vm.state.value.loadedRevision)
    }

    @Test fun readerRefreshStaysOnCurrentPage() = runTest(dispatcher) {
        val client = FakeClient()
        val (vm, _, _) = readerEnv(client = client)
        vm.open("1"); runCurrent()
        vm.load(page = 3); runCurrent()
        vm.load(); runCurrent()
        assertEquals(listOf(1, 3, 3), client.postRequests.map { it.first })
    }
    @Test fun nextPageAppendsWithoutResettingTheListAndStopsAtTheEnd() = runTest(dispatcher) {
        val client = FakeClient()
        val (vm, _, _) = readerEnv(client = client)
        vm.open("1"); runCurrent()
        val epoch = vm.state.value.epoch
        vm.loadNext(); vm.loadNext(); runCurrent()
        assertEquals(listOf(1, 2), client.postRequests.map { it.first })
        assertEquals(1..2, vm.state.value.startPage..vm.state.value.endPage)
        assertEquals(20, vm.state.value.items.size)
        assertEquals(epoch, vm.state.value.epoch)
        vm.loadNext(); runCurrent()
        vm.loadNext(); runCurrent()
        assertFalse(vm.state.value.hasMore)
        vm.loadNext(); runCurrent()
        assertEquals(listOf(1, 2, 3, 4), client.postRequests.map { it.first })
    }
    @Test fun failedAppendKeepsPostsAndWaitsForExplicitRetry() = runTest(dispatcher) {
        val client = FakeClient()
        val (vm, _, _) = readerEnv(client = client)
        vm.open("1"); runCurrent()
        client.failPosts = true
        vm.loadNext(); runCurrent()
        assertNotNull(vm.state.value.moreError)
        assertNull(vm.state.value.error)
        assertEquals(10, vm.state.value.items.size)
        vm.loadNext(); runCurrent()
        assertEquals(2, client.postRequests.size)
        client.failPosts = false
        vm.retryMore(); runCurrent()
        assertNull(vm.state.value.moreError)
        assertEquals(20, vm.state.value.items.size)
    }
    @Test fun previousPageIsPrependedAndLocatedByPage() = runTest(dispatcher) {
        val (vm, _, _) = readerEnv()
        vm.open("1"); runCurrent()
        vm.load(page = 3); runCurrent()
        assertEquals(1, vm.state.value.headerCount)
        vm.loadPrevious(); runCurrent()
        val state = vm.state.value
        assertEquals(2..3, state.startPage..state.endPage)
        assertEquals(20, state.items.size)
        assertEquals(1, state.headerCount)
        assertEquals(2 to 0, state.locate(1))
        assertEquals(3 to 0, state.locate(11))
        assertEquals(12, state.itemIndex(3, 2))
        assertNull(state.itemIndex(4, 0))
    }
    @Test fun jumpToFloorLoadsTheServerPageAndTargetsTheFloor() = runTest(dispatcher) {
        val client = FakeClient()
        val (vm, _, _) = readerEnv(client = client)
        vm.open("1"); runCurrent()
        vm.jumpToFloor(65); runCurrent()
        assertEquals(3, vm.state.value.data!!.page)
        assertEquals(65, vm.state.value.targetFloor)
        vm.jumpToFloor(66); runCurrent()
        assertEquals(listOf(1, 3), client.postRequests.map { it.first })
        assertEquals(66, vm.state.value.targetFloor)
        vm.jumpToFloor(10_000); runCurrent()
        assertEquals(4, vm.state.value.data!!.page)
        vm.jumpToPage(99); runCurrent()
        assertEquals(4, vm.state.value.data!!.page)
    }
    @Test fun jumpToFloorIsIgnoredWhileFilteringByAuthor() = runTest(dispatcher) {
        val client = FakeClient()
        val (vm, _, _) = readerEnv(client = client)
        vm.open("1"); runCurrent()
        vm.load(page = 1, authorOnly = true); runCurrent()
        vm.jumpToFloor(65); runCurrent()
        assertEquals(listOf(1, 1), client.postRequests.map { it.first })
    }
    @Test fun savedPositionUsesTheVisiblePageAcrossAppendedPages() = runTest(dispatcher) {
        val (vm, daos, _) = readerEnv()
        vm.open("1"); runCurrent()
        vm.loadNext(); runCurrent()
        vm.saveVisible(13, 7)
        assertEquals(ReadingPosition("1", 2, 3, 7), daos.forUser(null).position("1"))
    }
    @Test fun returningToTheSameTopicKeepsAppendedPages() = runTest(dispatcher) {
        val client = FakeClient()
        val (vm, _, _) = readerEnv(client = client)
        vm.open("1"); runCurrent()
        vm.loadNext(); runCurrent()
        vm.open("1"); runCurrent()
        assertEquals(20, vm.state.value.items.size)
        assertEquals(2, client.postRequests.size)
    }
    @Test fun readerRetryUsesFailedPage() = runTest(dispatcher) {
        val client = FakeClient()
        val (vm, _, _) = readerEnv(client = client)
        vm.open("1"); runCurrent()
        client.failPosts = true
        vm.load(page = 2); runCurrent()
        assertNotNull(vm.state.value.error)
        client.failPosts = false
        vm.load(); runCurrent()
        assertEquals(2, vm.state.value.data!!.page)
    }
    @Test fun returningToSameSearchDoesNotClearResults() = runTest(dispatcher) {
        val client = FakeClient()
        val vm = TopicsViewModel(client, SavedStateHandle())
        vm.search("query", "7"); runCurrent()
        vm.search("query", "7"); runCurrent()
        assertEquals(1, client.searchRequests)
    }
    @Test fun changingSearchScopeResetsAndRestoresOnlyMatchingPages() = runTest(dispatcher) {
        val client = FakeClient()
        val saved = SavedStateHandle()
        val vm = TopicsViewModel(client, saved)
        vm.search("query", "7"); runCurrent()
        vm.clearSearch()
        assertTrue(vm.state.value.items.isEmpty())
        vm.search("query", "8"); runCurrent()
        vm.load(); runCurrent()
        val restored = TopicsViewModel(client, saved)
        restored.search("query", "8"); runCurrent()
        assertEquals(2, restored.state.value.page)
        assertEquals(2, restored.state.value.items.size)
        assertEquals(listOf("7", "8", "8", "8", "8"), client.searchScopes.map { it.first })
    }
    @Test fun readerPositionAndLocalFavoritesArePersisted() = runTest(dispatcher) {
        val daos = FakeDaoProvider().apply { forUser(null).savePosition(ReadingPosition("1", 3, 2, 10)) }
        val (vm, _, _) = readerEnv(daos = daos)
        vm.open("1"); runCurrent()
        assertEquals(3, vm.state.value.data!!.page)
        assertEquals(2, vm.state.value.position!!.index)
        vm.savePosition(3, 4, 12)
        assertEquals(ReadingPosition("1", 3, 4, 12), daos.forUser(null).position("1"))
        vm.toggleFavorite(); runCurrent()
        assertEquals("1", daos.forUser(null).favorites.value.single().topicId)
    }
    @Test fun processRestorationReloadsPreviouslyLoadedTopicPages() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = TopicsViewModel(FakeClient(), saved)
        first.openForum("7", null); runCurrent()
        first.load(); runCurrent()
        assertEquals(listOf("1", "2"), first.state.value.items.map { it.id })
        val restored = TopicsViewModel(FakeClient(), saved)
        restored.openForum("7", null); runCurrent()
        assertEquals(listOf("1", "2"), restored.state.value.items.map { it.id })
        assertEquals(2, restored.state.value.page)
    }
    @Test fun authorFilterDoesNotOverwriteNormalReadingPosition() = runTest(dispatcher) {
        val client = FakeClient()
        val (vm, daos, _) = readerEnv(client = client)
        vm.open("1"); runCurrent()
        vm.savePosition(1, 4, 20)
        vm.load(page = 1, authorOnly = true); runCurrent()
        assertEquals("7", client.postRequests.last().second)
        vm.savePosition(1, 0, 0)
        assertEquals(ReadingPosition("1", 1, 4, 20), daos.forUser(null).position("1"))
    }
    @Test fun reopeningSameReaderKeepsLatestPositionAndRejectsOtherTopicCallbacks() = runTest(dispatcher) {
        val (vm, daos, _) = readerEnv()
        vm.open("1"); runCurrent()
        vm.savePosition(1, 4, 20)
        vm.open("1"); runCurrent()
        assertEquals(ReadingPosition("1", 1, 4, 20), vm.state.value.position)
        vm.open("2"); runCurrent()
        vm.savePosition(1, 8, 60, topicId = "1")
        assertNull(daos.forUser(null).position("2"))
        assertEquals(ReadingPosition("1", 1, 4, 20), daos.forUser(null).position("1"))
    }
    @Test fun favoriteToggleReadsPersistentMembershipNotAnUncollectedFlow() = runTest(dispatcher) {
        val daos = FakeDaoProvider().apply { forUser(null).saveFavorite(LocalFavorite("1", "Original")) }
        val (vm, _, _) = readerEnv(daos = daos)
        vm.open("1"); runCurrent()
        vm.toggleFavorite(); runCurrent()
        assertTrue(daos.forUser(null).favorites.value.isEmpty())
    }
    @Test fun refreshFailureRetainsTopicsAndPage() = runTest(dispatcher) {
        val client = FakeClient()
        val vm = TopicsViewModel(client, SavedStateHandle())
        vm.openForum("7", null); runCurrent()
        client.failTopics = true
        vm.load(true); runCurrent()
        assertEquals("1", vm.state.value.items.single().id)
        assertEquals(1, vm.state.value.page)
        assertNotNull(vm.state.value.error)
    }
    @Test fun guestAndAccountLocalDataStayIsolatedAcrossLoginSwitchAndLogout() = runTest(dispatcher) {
        val sessions = MemorySessionStore()
        val browser = FakeBrowserCookies()
        val daos = FakeDaoProvider()
        val manager = AccountManager(sessions, browser).also { it.initialize() }
        val vm = ReaderViewModel(FakeClient(), daos, sessions, manager)

        vm.open("1"); runCurrent()
        vm.savePosition(1, 2, 10)
        vm.toggleFavorite(); runCurrent()
        assertEquals(1, daos.forUser(null).favorites.value.size)

        val attempt7 = manager.beginLogin()
        browser.cookies[LOGIN_URL] = "ngaPassportUid=77; ngaPassportCid=token77"
        assertTrue(manager.captureAndSave(attempt7, LOGIN_URL))
        assertTrue(browser.cookies.isEmpty())
        assertEquals("77", manager.state.value.userId)
        assertTrue(daos.forUser("77").favorites.value.isEmpty())
        assertNull(daos.forUser("77").position("1"))

        val vm77 = ReaderViewModel(FakeClient(), daos, sessions, manager)
        vm77.open("1"); runCurrent()
        vm77.load(page = 2); runCurrent()
        vm77.savePosition(2, 5, 30)
        vm77.toggleFavorite(); runCurrent()
        assertEquals(ReadingPosition("1", 2, 5, 30), daos.forUser("77").position("1"))
        assertEquals(ReadingPosition("1", 1, 2, 10), daos.forUser(null).position("1"))

        val attempt88 = manager.beginLogin()
        browser.cookies[LOGIN_URL] = "ngaPassportUid=88; ngaPassportCid=token88"
        assertTrue(manager.captureAndSave(attempt88, LOGIN_URL))
        assertEquals("88", manager.state.value.userId)
        assertNull(daos.forUser("88").position("1"))

        manager.switchAccount("77")
        assertEquals("77", manager.state.value.userId)
        manager.logout()
        assertEquals("88", manager.state.value.accounts.single())
        assertNull(manager.state.value.userId)
        assertEquals(1, daos.forUser(null).favorites.value.size)
    }
    @Test fun staleReaderCallbacksCannotWriteIntoNewAccount() = runTest(dispatcher) {
        val sessions = MemorySessionStore()
        val browser = FakeBrowserCookies()
        val daos = FakeDaoProvider()
        val manager = AccountManager(sessions, browser).also { it.initialize() }
        val oldReader = ReaderViewModel(FakeClient(), daos, sessions, manager)
        oldReader.open("1"); runCurrent()
        val attempt = manager.beginLogin()
        browser.cookies[LOGIN_URL] = "ngaPassportUid=77; ngaPassportCid=token77"
        assertTrue(manager.captureAndSave(attempt, LOGIN_URL))
        assertTrue(runCatching { oldReader.savePosition(1, 8, 20) }.exceptionOrNull() is CancellationException)
        oldReader.toggleFavorite(); runCurrent()
        assertNull(daos.forUser("77").position("1"))
        assertTrue(daos.forUser("77").favorites.value.isEmpty())
    }
    @Test fun untrustedOrStaleLoginCaptureIsRejected() = runTest(dispatcher) {
        val sessions = MemorySessionStore()
        val browser = FakeBrowserCookies()
        val manager = AccountManager(sessions, browser).also { it.initialize() }
        val attempt = manager.beginLogin()
        browser.cookies["https://evil.example/login"] = "ngaPassportUid=77; ngaPassportCid=token77"
        assertFalse(manager.captureAndSave(attempt, "https://evil.example/login"))
        assertNull(manager.state.value.userId)

        browser.cookies[LOGIN_URL] = "ngaPassportUid=77; ngaPassportCid=token77"
        sessions.saveSession(NgaSession("99", "other"))
        assertFalse(manager.captureAndSave(attempt, LOGIN_URL))
        assertEquals("99", sessions.snapshot().session!!.userId)
    }
}

class FakeClient : NgaClient {
    var failPosts = false
    var failTopics = false
    var searchRequests = 0
    val searchScopes = mutableListOf<Pair<String?, String?>>()
    val postRequests = mutableListOf<Pair<Int, String?>>()
    override suspend fun getForums() = listOf(
        Forum("7", "Forum", "Category", "Group"),
        Forum("7", "Collection", "Category", "Group", collectionId = "99"),
    )
    override suspend fun getTopics(forumId: String, page: Int, collectionId: String?, sort: TopicSort): TopicPage {
        if (failTopics) throw NgaException.Network()
        return TopicPage(listOf(Topic("$page", "Title $page", "Author", "7")), page, 100, page < 3)
    }
    override suspend fun getPosts(topicId: String, page: Int, authorId: String?): PostPage {
        postRequests += page to authorId
        if (failPosts) throw NgaException.Network()
        return PostPage(List(10) { Post("$page:$it", (page - 1) * 30 + it, "Author", "7", "", "2026-01-01", listOf(ContentBlock.Text("Post $it"))) }, "Topic", "7", page, 100, page < 4, 30, 4)
    }
    override suspend fun searchTopics(keyword: String, forumId: String?, page: Int, collectionId: String?): TopicPage {
        searchRequests++
        searchScopes += forumId to collectionId
        return getTopics(forumId ?: "0", page, collectionId, TopicSort.LAST_ACTIVE)
    }
    override suspend fun findUser(username: String) = NgaUser("7", username, "", 1)
    override suspend fun getUser(userId: String) = NgaUser(userId, "User", "", 1)
    override suspend fun getUserTopics(userId: String, page: Int) = TopicPage(emptyList(), page, 0, false)
    override suspend fun getUserReplies(userId: String, page: Int) = UserReplyPage(emptyList(), page, false)
    override suspend fun getNotifications(since: Long) = emptyList<CommunityItem>()
    override suspend fun getPrivateMessages(page: Int) = emptyList<CommunityItem>()
    override suspend fun getMessageConversations(page: Int): MessageConversationPage = error("Unused")
    override suspend fun getMessagePosts(conversationId: String, page: Int): MessagePostPage = error("Unused")
    override suspend fun sendMessage(request: MessageRequest): WriteResult = error("Unused")
    override suspend fun ratePost(topicId: String, postId: String, rating: PostRating): WriteResult = error("Unused")
    override suspend fun preparePublish(target: PublishTarget): PublishPreparation = error("Unused")
    override suspend fun publish(request: PublishRequest): WriteResult = WriteResult.Unconfirmed()
}

class FakeDao : ReadingDao {
    val history = MutableStateFlow<List<ReadingHistory>>(emptyList())
    override fun history(): Flow<List<ReadingHistory>> = history
    override suspend fun saveHistory(item: ReadingHistory) {
        history.update { old -> (old.filterNot { it.topicId == item.topicId } + item).sortedWith(compareByDescending<ReadingHistory> { it.viewedAt }.thenBy { it.topicId }).take(100) }
    }
    val favorites = MutableStateFlow<List<LocalFavorite>>(emptyList())
    val forumFavorites = MutableStateFlow<List<LocalForumFavorite>>(emptyList())
    private val positions = mutableMapOf<String, ReadingPosition>()
    override fun favorites(): Flow<List<LocalFavorite>> = favorites
    override suspend fun favorite(id: String): LocalFavorite? = favorites.value.firstOrNull { it.topicId == id }
    override suspend fun saveFavorite(item: LocalFavorite) { favorites.update { old -> old.filterNot { it.topicId == item.topicId } + item } }
    override suspend fun removeFavorite(id: String) { favorites.update { it.filterNot { item -> item.topicId == id } } }
    override fun forumFavorites(): Flow<List<LocalForumFavorite>> = forumFavorites
    override suspend fun forumFavorite(key: String): LocalForumFavorite? = forumFavorites.value.firstOrNull { it.forumKey == key }
    override suspend fun saveForumFavorite(item: LocalForumFavorite) { forumFavorites.update { old -> old.filterNot { it.forumKey == item.forumKey } + item } }
    override suspend fun removeForumFavorite(key: String) { forumFavorites.update { it.filterNot { item -> item.forumKey == key } } }
    override suspend fun position(id: String): ReadingPosition? = positions[id]
    override suspend fun savePosition(item: ReadingPosition) { positions[item.topicId] = item }
}

class FakeDaoProvider : ReadingDaoProvider {
    private val map = mutableMapOf<String?, FakeDao>()
    override fun forUser(userId: String?): FakeDao = map.getOrPut(userId) { FakeDao() }
}

class FakeBrowserCookies : BrowserCookies {
    val cookies = mutableMapOf<String, String>()
    override suspend fun read(url: String): String? {
        require(isTrustedLoginUrl(url))
        return cookies[url]
    }
    override suspend fun clear() { cookies.clear() }
}
