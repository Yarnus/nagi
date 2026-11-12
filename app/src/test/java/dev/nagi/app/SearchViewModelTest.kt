package dev.nagi.app

import androidx.lifecycle.SavedStateHandle
import dev.nagi.app.viewmodel.SearchMode
import dev.nagi.app.viewmodel.SearchViewModel
import dev.nagi.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun globalPagesRestoreAndScopeChangesResetResults() = runTest(dispatcher) {
        val client = FakeClient()
        val saved = SavedStateHandle()
        val vm = SearchViewModel(client, saved)
        vm.editQuery("测试")
        vm.submit(SearchMode.TOPICS); runCurrent()
        vm.loadMore(); runCurrent()
        assertEquals(listOf("1", "2"), vm.state.value.topics.map { it.id })
        assertTrue(client.searchScopes.all { it.first == null })
        val restored = SearchViewModel(client, saved); runCurrent()
        assertEquals(2, restored.state.value.page)
        assertEquals(listOf("1", "2"), restored.state.value.topics.map { it.id })
        restored.scope(Forum("7", "Forum", "Category", "Group", "42")); runCurrent()
        assertEquals(1, restored.state.value.page)
        assertEquals("7" to "42", client.searchScopes.last())
    }

    @Test fun entryScopeAppliesToFirstSearchPaginationAndRestoration() = runTest(dispatcher) {
        for (collection in listOf<String?>(null, "42")) {
            val client = FakeClient()
            val saved = SavedStateHandle(mapOf("forumId" to "7", "collectionId" to collection, "forumName" to "测试版面"))
            val vm = SearchViewModel(client, saved)
            assertEquals("测试版面", vm.state.value.forumName)
            assertEquals(0, client.searchRequests)
            vm.editQuery("测试"); vm.submit(); runCurrent()
            vm.loadMore(); runCurrent()
            assertEquals(listOf("7" to collection, "7" to collection), client.searchScopes)
            val restored = SearchViewModel(client, saved); runCurrent()
            assertEquals(2, restored.state.value.page)
            assertEquals("7", restored.state.value.forumId)
            assertEquals(collection, restored.state.value.collectionId)
            assertTrue(client.searchScopes.all { it == "7" to collection })
            restored.scope(null); runCurrent()
            assertEquals(null to null, client.searchScopes.last())
        }
    }

    @Test fun blankQueriesDoNotRequestAndSameTextDoesNotClearResults() = runTest(dispatcher) {
        val client = FakeClient()
        val vm = SearchViewModel(client, SavedStateHandle())
        vm.submit(); runCurrent()
        assertEquals(0, client.searchRequests)
        vm.editQuery("query"); vm.submit(); runCurrent()
        vm.editQuery("query"); runCurrent()
        assertEquals(SearchMode.TOPICS, vm.state.value.mode)
        assertEquals(1, vm.state.value.topics.size)
    }

    @Test fun emptyNavigationScopeDoesNotBecomeAnInvalidCollection() = runTest(dispatcher) {
        val client = FakeClient()
        val vm = SearchViewModel(client, SavedStateHandle(mapOf("forumId" to "7", "collectionId" to "", "forumName" to "Forum")))
        vm.editQuery("query"); vm.submit(); runCurrent()
        assertEquals("7" to null, client.searchScopes.single())
    }

    @Test fun editingQueryCancelsAndDiscardsOldResults() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val client = object : NgaClient by FakeClient() {
            override suspend fun searchTopics(keyword: String, forumId: String?, page: Int, collectionId: String?): TopicPage {
                withContext(NonCancellable) { gate.await() }
                return TopicPage(listOf(Topic("old", "Old", "", "")), 1, 1, false)
            }
        }
        val vm = SearchViewModel(client, SavedStateHandle())
        vm.editQuery("old"); vm.submit(); runCurrent()
        vm.editQuery("new"); gate.complete(Unit); runCurrent()
        assertEquals("new", vm.state.value.query)
        assertNull(vm.state.value.mode)
        assertTrue(vm.state.value.topics.isEmpty())
        assertFalse(vm.state.value.loading)
    }

    @Test fun paginationFailureKeepsResultsAndRetriesFailedPage() = runTest(dispatcher) {
        var fail = true
        val client = object : NgaClient by FakeClient() {
            override suspend fun searchTopics(keyword: String, forumId: String?, page: Int, collectionId: String?): TopicPage {
                if (page == 2 && fail) throw NgaException.Network()
                return TopicPage(listOf(Topic("$page", "Page $page", "", "")), page, 3, page < 3)
            }
        }
        val vm = SearchViewModel(client, SavedStateHandle())
        vm.editQuery("query"); vm.submit(); runCurrent()
        vm.loadMore(); runCurrent()
        assertEquals(listOf("1"), vm.state.value.topics.map { it.id })
        assertNotNull(vm.state.value.error)
        fail = false; vm.retry(); runCurrent()
        assertEquals(listOf("1", "2"), vm.state.value.topics.map { it.id })
    }

    @Test fun sdkFailuresStopLoadingAndAllowExplicitSearchRetry() = runTest(dispatcher) {
        for (error in listOf(NgaException.Network(java.net.SocketTimeoutException()), NgaException.Authentication(),
            NgaException.Restricted(), NgaException.Http(503))) {
            var failure: NgaException? = error
            val delegate = FakeClient()
            val client = object : NgaClient by delegate {
                override suspend fun searchTopics(keyword: String, forumId: String?, page: Int, collectionId: String?): TopicPage {
                    failure?.let { throw it }
                    return delegate.searchTopics(keyword, forumId, page, collectionId)
                }
            }
            val vm = SearchViewModel(client, SavedStateHandle())
            vm.editQuery("query"); vm.submit(); runCurrent()
            assertFalse(vm.state.value.loading)
            assertNotNull(vm.state.value.error)
            assertTrue(vm.state.value.topics.isEmpty())
            failure = null
            vm.retry(); runCurrent()
            assertNull(vm.state.value.error)
            assertEquals(1, vm.state.value.page)
            assertTrue(vm.state.value.topics.isNotEmpty())
        }
    }

    @Test fun missingUserResultsAreEmptyRatherThanNetworkErrors() = runTest(dispatcher) {
        val client = object : NgaClient by FakeClient() {
            override suspend fun getUser(userId: String): NgaUser = throw NgaException.NotFound()
            override suspend fun findUser(username: String): NgaUser? = null
        }
        val vm = SearchViewModel(client, SavedStateHandle())
        for (query in listOf("58", "missing-user")) {
            vm.editQuery(query); vm.submit(SearchMode.USERS); runCurrent()
            assertFalse(vm.state.value.loading)
            assertNull(vm.state.value.error)
            assertTrue(vm.state.value.users.isEmpty())
            assertFalse(vm.state.value.hasMore)
        }
    }

    @Test fun exactUsernameAndUidLookupsHaveNoInventedPagination() = runTest(dispatcher) {
        val vm = SearchViewModel(FakeClient(), SavedStateHandle())
        vm.editQuery("完整用户名"); vm.submit(SearchMode.USERS); runCurrent()
        assertEquals("完整用户名", vm.state.value.users.single().name)
        assertFalse(vm.state.value.hasMore)
        vm.editQuery("58"); vm.submit(SearchMode.USERS); runCurrent()
        assertEquals("58", vm.state.value.users.single().id)
    }
}
