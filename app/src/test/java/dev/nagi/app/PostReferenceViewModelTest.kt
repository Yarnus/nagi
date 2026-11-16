package dev.nagi.app

import dev.nagi.app.data.AccountManager
import dev.nagi.app.viewmodel.ReaderViewModel
import dev.nagi.app.viewmodel.key
import dev.nagi.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PostReferenceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private val target = PostReference("123", "42", 3)
    private val original = Post("42", 43, "Original author", "8", "", "", listOf(ContentBlock.Text("Original body")))
    private fun page() = PostPage(listOf(original), "Topic", "7", 3, 100, true, 20, 5)
    private suspend fun env(client: NgaClient): Pair<ReaderViewModel, AccountManager> {
        val sessions = MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }
        val accounts = AccountManager(sessions, FakeBrowserCookies()).also { it.initialize() }
        return ReaderViewModel(client, FakeDaoProvider(), sessions, accounts) to accounts
    }

    @Test fun requestsAreDeduplicatedAndPageHintsDoNotSplitCache() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var reads = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun getPostPage(reference: PostReference): PostPage { reads++; gate.await(); return page() }
        }
        val (vm) = env(client)
        vm.open("123"); runCurrent()
        vm.resolveReference(target); vm.resolveReference(target.copy(page = 1)); runCurrent()
        assertEquals(1, reads)
        assertTrue(vm.references.value[target.key]!!.loading)
        gate.complete(Unit); runCurrent()
        assertEquals(original, vm.references.value[target.key]!!.post)
        vm.resolveReference(target, true); runCurrent()
        assertEquals(1, reads)
    }

    @Test fun loadedOriginalIsReusedButNeverAcrossTopics() = runTest(dispatcher) {
        var reads = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun getPosts(topicId: String, page: Int, authorId: String?) = page()
            override suspend fun getPostPage(reference: PostReference): PostPage { reads++; return page() }
        }
        val (vm) = env(client)
        vm.open("123"); runCurrent()
        vm.resolveReference(target); runCurrent()
        assertEquals(0, reads)
        vm.resolveReference(target.copy(topicId = "456")); runCurrent()
        assertEquals(1, reads)
    }

    @Test fun failureRequiresExplicitRetryAndKeepsReaderContent() = runTest(dispatcher) {
        var reads = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun getPostPage(reference: PostReference): PostPage {
                reads++
                if (reads == 1) throw NgaException.Network()
                return page()
            }
        }
        val (vm) = env(client)
        vm.open("123"); runCurrent()
        val items = vm.state.value.items
        vm.resolveReference(target); runCurrent()
        assertNotNull(vm.references.value[target.key]!!.error)
        vm.resolveReference(target); runCurrent()
        assertEquals(1, reads)
        assertEquals(items, vm.state.value.items)
        vm.resolveReference(target, true); runCurrent()
        assertEquals(original, vm.references.value[target.key]!!.post)
    }

    @Test fun accountChangeDiscardsEvenNonCooperativeLateResults() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val client = object : NgaClient by FakeClient() {
            override suspend fun getPostPage(reference: PostReference): PostPage = withContext(NonCancellable) {
                gate.await(); page()
            }
        }
        val (vm, accounts) = env(client)
        vm.open("123"); runCurrent()
        vm.resolveReference(target); runCurrent()
        accounts.logout(); runCurrent()
        gate.complete(Unit); runCurrent()
        assertTrue(vm.references.value.isEmpty())
    }

    @Test fun locatingOriginalUsesTargetedReadAndDisablesAuthorFilter() = runTest(dispatcher) {
        var targetReads = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun getPostPage(reference: PostReference): PostPage { targetReads++; return page() }
        }
        val (vm) = env(client)
        vm.open("123"); runCurrent()
        vm.load(page = 1, authorOnly = true); runCurrent()
        vm.locateReference(target); runCurrent()
        assertEquals(1, targetReads)
        assertEquals(3, vm.state.value.startPage)
        assertEquals(43, vm.state.value.targetFloor)
        assertFalse(vm.state.value.authorOnly)
        vm.locateReference(target); runCurrent()
        assertEquals(1, targetReads)
    }

    @Test fun locatingLoadedFloorDoesNotStallAdjacentPagination() = runTest(dispatcher) {
        for (prepend in listOf(false, true)) {
            for (byReference in listOf(false, true)) {
                val gate = CompletableDeferred<Unit>()
                val adjacentPage = if (prepend) 2 else 4
                val adjacentPost = original.copy(id = "adjacent", floor = (adjacentPage - 1) * 20)
                var adjacentReads = 0
                val client = object : NgaClient by FakeClient() {
                    override suspend fun getPosts(topicId: String, page: Int, authorId: String?): PostPage {
                        if (page != adjacentPage) return page()
                        adjacentReads++
                        if (adjacentReads == 1) withContext(NonCancellable) { gate.await() }
                        return page().copy(page = page, items = listOf(adjacentPost))
                    }
                }
                val (vm) = env(client)
                vm.open("123"); runCurrent()
                if (prepend) vm.loadPrevious() else vm.loadNext()
                runCurrent()
                assertTrue(vm.state.value.loadingMore || vm.state.value.loadingPrevious)
                if (byReference) vm.locateReference(target) else vm.jumpToFloor(original.floor)
                val located = vm.state.value
                gate.complete(Unit); runCurrent()
                assertFalse("Loading after locating: prepend=$prepend, reference=$byReference", located.loadingMore || located.loadingPrevious)
                assertFalse(vm.state.value.loadingMore || vm.state.value.loadingPrevious)
                assertEquals(original.floor, vm.state.value.targetFloor)
                assertEquals(listOf(original), vm.state.value.items)
                if (prepend) vm.loadPrevious() else vm.loadNext()
                runCurrent()
                assertEquals(2, adjacentReads)
                assertTrue(vm.state.value.items.contains(adjacentPost))
                assertFalse(vm.state.value.loadingMore || vm.state.value.loadingPrevious)
            }
        }
    }

    @Test fun locatingDiscardsLateAdjacentFailures() = runTest(dispatcher) {
        for (prepend in listOf(false, true)) {
            val gate = CompletableDeferred<Unit>()
            val adjacentPage = if (prepend) 2 else 4
            val client = object : NgaClient by FakeClient() {
                override suspend fun getPosts(topicId: String, page: Int, authorId: String?): PostPage {
                    if (page != adjacentPage) return page()
                    return withContext(NonCancellable) { gate.await(); throw NgaException.Network() }
                }
            }
            val (vm) = env(client)
            vm.open("123"); runCurrent()
            if (prepend) vm.loadPrevious() else vm.loadNext()
            runCurrent()
            vm.locateReference(target)
            gate.complete(Unit); runCurrent()
            assertNull(vm.state.value.moreError)
            assertNull(vm.state.value.previousError)
            assertFalse(vm.state.value.loadingMore || vm.state.value.loadingPrevious)
            assertEquals(listOf(original), vm.state.value.items)
        }
    }

    @Test fun openingDifferentTopicCancelsAndClearsReferences() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val client = object : NgaClient by FakeClient() {
            override suspend fun getPostPage(reference: PostReference): PostPage { gate.await(); return page() }
        }
        val (vm) = env(client)
        vm.open("123"); runCurrent()
        vm.resolveReference(target); runCurrent()
        vm.open("456"); gate.complete(Unit); runCurrent()
        assertTrue(vm.references.value.isEmpty())
    }
}
