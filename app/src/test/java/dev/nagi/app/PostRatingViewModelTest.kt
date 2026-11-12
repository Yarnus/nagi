package dev.nagi.app

import androidx.lifecycle.SavedStateHandle
import dev.nagi.app.data.AccountManager
import dev.nagi.app.viewmodel.*
import dev.nagi.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PostRatingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()
    private suspend fun accounts() = AccountManager(MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }, FakeBrowserCookies()).also { it.initialize() }
    @Test fun unknownRatingBlocksDoubleTapAndRestoredReplay() = runTest(dispatcher) {
        var writes = 0
        val gate = CompletableDeferred<WriteResult>()
        val client = object : NgaClient by FakeClient() {
            override suspend fun ratePost(topicId: String, postId: String, rating: PostRating): WriteResult { writes++; return gate.await() }
        }
        val accounts = accounts(); val saved = SavedStateHandle()
        val vm = PostRatingViewModel(client, accounts, saved)
        vm.rate("123", "0", PostRating.UP); vm.rate("123", "0", PostRating.DOWN); runCurrent()
        assertEquals(1, writes)
        assertEquals(ComposePhase.Submitting, vm.states.value["123:0"]!!.phase)
        gate.complete(WriteResult.Unconfirmed()); runCurrent()
        vm.rate("123", "0", PostRating.UP); runCurrent()
        val restored = PostRatingViewModel(client, accounts, saved)
        restored.rate("123", "0", PostRating.DOWN); runCurrent()
        assertEquals(1, writes)
        assertEquals(ComposePhase.Unconfirmed, restored.states.value["123:0"]!!.phase)
        accounts.logout(); runCurrent()
        assertTrue(vm.states.value.isEmpty())
    }
    @Test fun confirmedRatingUsesReceiptDeltaAndRecognizesRemoval() = runTest(dispatcher) {
        val receipts = listOf(PostRatingReceipt(2, PostRating.UP), PostRatingReceipt(-1, null))
        var writes = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun ratePost(topicId: String, postId: String, rating: PostRating): WriteResult =
                WriteResult.Confirmed("服务端文案", receipts[writes++])
        }
        val vm = PostRatingViewModel(client, accounts(), SavedStateHandle())
        vm.rate("123", "1", PostRating.UP); runCurrent()
        assertEquals(2, vm.states.value["123:1"]!!.scoreDelta)
        assertEquals(PostRating.UP, vm.states.value["123:1"]!!.rating)
        vm.rate("123", "2", PostRating.UP); runCurrent()
        val removed = vm.states.value["123:2"]!!
        assertEquals(-1, removed.scoreDelta)
        assertNull(removed.rating)
        assertTrue(removed.enabled)
        assertEquals(2, writes)
    }

    @Test fun rejectedRatingShowsSafeReasonWithoutChangingScore() = runTest(dispatcher) {
        val client = object : NgaClient by FakeClient() {
            override suspend fun ratePost(topicId: String, postId: String, rating: PostRating) =
                WriteResult.Rejected(NgaException.Restricted(NgaRejectionReason.RATE_LIMIT))
        }
        val vm = PostRatingViewModel(client, accounts(), SavedStateHandle())
        val feedback = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.feedback.collect { feedback += it } }
        vm.rate("123", "0", PostRating.UP); runCurrent()
        val state = vm.states.value["123:0"]!!
        assertEquals(listOf("评价失败：操作过于频繁，请稍后再试"), feedback)
        assertEquals(0, state.scoreDelta)
        assertEquals(ComposePhase.Rejected, state.phase)
    }

    @Test fun confirmedVotesCanBeExplicitlyRemovedAndSwitchedInBothDirections() = runTest(dispatcher) {
        var serverVote: PostRating? = null
        val requests = mutableListOf<PostRating>()
        val client = object : NgaClient by FakeClient() {
            override suspend fun ratePost(topicId: String, postId: String, rating: PostRating): WriteResult {
                requests += rating
                val previous = serverVote
                serverVote = if (previous == rating) null else rating
                return WriteResult.Confirmed("评价完成", PostRatingReceipt(
                    (serverVote?.value ?: 0) - (previous?.value ?: 0), serverVote))
            }
        }
        val vm = PostRatingViewModel(client, accounts(), SavedStateHandle())
        val sequence = listOf(PostRating.UP, PostRating.UP, PostRating.DOWN, PostRating.DOWN,
            PostRating.UP, PostRating.DOWN, PostRating.UP, PostRating.UP)
        val expected = listOf(1, 0, -1, 0, 1, -1, 1, 0)
        sequence.forEachIndexed { index, direction ->
            vm.rate("123", "0", direction); runCurrent()
            val state = vm.states.value["123:0"]!!
            assertEquals(index + 1, requests.size)
            assertEquals(serverVote, state.rating)
            assertEquals(expected[index], state.scoreDelta)
            assertEquals(ComposePhase.Confirmed, state.phase)
            assertTrue(state.enabled)
        }
        assertEquals(sequence, requests)
    }

    @Test fun pendingAndUnknownSecondVoteKeepConfirmedScoreAndPreventReplay() = runTest(dispatcher) {
        var writes = 0
        val gate = CompletableDeferred<WriteResult>()
        val client = object : NgaClient by FakeClient() {
            override suspend fun ratePost(topicId: String, postId: String, rating: PostRating): WriteResult {
                writes++
                return if (writes == 1) WriteResult.Confirmed("已支持", PostRatingReceipt(1, PostRating.UP)) else gate.await()
            }
        }
        val vm = PostRatingViewModel(client, accounts(), SavedStateHandle())
        vm.rate("123", "0", PostRating.UP); runCurrent()
        vm.rate("123", "0", PostRating.UP); runCurrent()
        assertEquals(2, writes)
        assertEquals(ComposePhase.Submitting, vm.states.value["123:0"]!!.phase)
        assertEquals(1, vm.states.value["123:0"]!!.scoreDelta)
        assertEquals(PostRating.UP, vm.states.value["123:0"]!!.rating)
        vm.rate("123", "0", PostRating.DOWN); runCurrent()
        assertEquals(2, writes)
        gate.complete(WriteResult.Unconfirmed(WriteDiagnostic(httpStatus = 200, delta = -1, flags = 0))); runCurrent()
        assertEquals(ComposePhase.Unconfirmed, vm.states.value["123:0"]!!.phase)
        assertEquals(1, vm.states.value["123:0"]!!.scoreDelta)
        assertEquals(PostRating.UP, vm.states.value["123:0"]!!.rating)
        vm.rate("123", "0", PostRating.UP); runCurrent()
        assertEquals(2, writes)
    }

    @Test fun rejectedCancellationKeepsConfirmedVoteAndCanBeExplicitlyRetried() = runTest(dispatcher) {
        var writes = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun ratePost(topicId: String, postId: String, rating: PostRating): WriteResult = when (++writes) {
                1 -> WriteResult.Confirmed("已反对", PostRatingReceipt(-1, PostRating.DOWN))
                2 -> WriteResult.Rejected(NgaException.Restricted())
                else -> WriteResult.Confirmed("已取消反对", PostRatingReceipt(1, null))
            }
        }
        val vm = PostRatingViewModel(client, accounts(), SavedStateHandle())
        vm.rate("123", "0", PostRating.DOWN); runCurrent()
        vm.rate("123", "0", PostRating.DOWN); runCurrent()
        val rejected = vm.states.value["123:0"]!!
        assertEquals(ComposePhase.Rejected, rejected.phase)
        assertEquals(PostRating.DOWN, rejected.rating)
        assertEquals(-1, rejected.scoreDelta)
        assertTrue(rejected.enabled)
        vm.rate("123", "0", PostRating.DOWN); runCurrent()
        assertEquals(3, writes)
        assertEquals(0, vm.states.value["123:0"]!!.scoreDelta)
        assertNull(vm.states.value["123:0"]!!.rating)
    }

    @Test fun missingReceiptsStillToggleBothButtonsWithoutGuessingScores() = runTest(dispatcher) {
        val client = object : NgaClient by FakeClient() {
            override suspend fun ratePost(topicId: String, postId: String, rating: PostRating) = WriteResult.Confirmed("Accepted")
        }
        val vm = PostRatingViewModel(client, accounts(), SavedStateHandle())
        val sequence = listOf(PostRating.UP, PostRating.UP, PostRating.DOWN, PostRating.DOWN, PostRating.UP, PostRating.DOWN)
        val expected = listOf(PostRating.UP, null, PostRating.DOWN, null, PostRating.UP, PostRating.DOWN)
        sequence.forEachIndexed { index, rating ->
            vm.rate("123", "0", rating); runCurrent()
            val state = vm.states.value.getValue("123:0")
            assertEquals(expected[index], state.rating)
            assertEquals(0, state.scoreDelta)
            assertTrue(state.enabled)
            assertNull(state.pendingRating)
        }
    }

    @Test fun freshScoresReplaceLocalDeltasAndReleaseOnlyReloadedUncertainPosts() = runTest(dispatcher) {
        val client = object : NgaClient by FakeClient() {
            override suspend fun ratePost(topicId: String, postId: String, rating: PostRating): WriteResult =
                if (postId == "0") WriteResult.Confirmed("Accepted", PostRatingReceipt(1, PostRating.UP)) else WriteResult.Unconfirmed()
        }
        val saved = SavedStateHandle()
        val vm = PostRatingViewModel(client, accounts(), saved)
        vm.rate("123", "0", PostRating.UP); vm.rate("123", "1", PostRating.UP); runCurrent()
        vm.onPostsReloaded("123", 1, listOf("0"))
        assertEquals(0, vm.states.value.getValue("123:0").scoreDelta)
        assertEquals(PostRating.UP, vm.states.value.getValue("123:0").rating)
        assertFalse(vm.states.value.getValue("123:1").enabled)
        vm.onPostsReloaded("123", 2, listOf("1"))
        assertTrue(vm.states.value.getValue("123:1").enabled)
        assertTrue(saved.get<ArrayList<String>>("ratingPending")!!.isEmpty())
    }

    @Test fun repeatedReadDeliveryDoesNotRebaseNewVotesOrReleaseNewUncertainWrites() = runTest(dispatcher) {
        var uncertain = false
        val client = object : NgaClient by FakeClient() {
            override suspend fun ratePost(topicId: String, postId: String, rating: PostRating): WriteResult =
                if (uncertain) WriteResult.Unconfirmed() else WriteResult.Confirmed("Accepted", PostRatingReceipt(1, PostRating.UP))
        }
        val vm = PostRatingViewModel(client, accounts(), SavedStateHandle())
        vm.onPostsReloaded("123", 1, listOf("0", "1"))
        vm.rate("123", "0", PostRating.UP); runCurrent()
        uncertain = true
        vm.rate("123", "1", PostRating.UP); runCurrent()
        vm.onPostsReloaded("123", 1, listOf("0", "1"))
        assertEquals(1, vm.states.value.getValue("123:0").scoreDelta)
        assertFalse(vm.states.value.getValue("123:1").enabled)
        vm.onPostsReloaded("123", 2, listOf("0", "1"))
        assertEquals(0, vm.states.value.getValue("123:0").scoreDelta)
        assertTrue(vm.states.value.getValue("123:1").enabled)
    }

    @Test fun refreshDuringSubmissionDoesNotDoubleCountReceiptOrEnableReplay() = runTest(dispatcher) {
        val gate = CompletableDeferred<WriteResult>()
        val client = object : NgaClient by FakeClient() {
            override suspend fun ratePost(topicId: String, postId: String, rating: PostRating) = gate.await()
        }
        val vm = PostRatingViewModel(client, accounts(), SavedStateHandle())
        vm.rate("123", "0", PostRating.UP); runCurrent()
        assertEquals(PostRating.UP, vm.states.value.getValue("123:0").pendingRating)
        vm.onPostsReloaded("123", 1, listOf("0"))
        assertFalse(vm.states.value.getValue("123:0").enabled)
        gate.complete(WriteResult.Confirmed("Accepted", PostRatingReceipt(1, PostRating.UP))); runCurrent()
        assertEquals(0, vm.states.value.getValue("123:0").scoreDelta)
        assertEquals(PostRating.UP, vm.states.value.getValue("123:0").rating)
    }

    @Test fun rejectedCanBeExplicitlyRetried() = runTest(dispatcher) {
        var writes = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun ratePost(topicId: String, postId: String, rating: PostRating): WriteResult {
                writes++
                return if (writes == 1) WriteResult.Rejected(NgaException.Restricted()) else WriteResult.Confirmed("评分成功")
            }
        }
        val vm = PostRatingViewModel(client, accounts(), SavedStateHandle())
        vm.rate("123", "0", PostRating.UP); runCurrent()
        assertTrue(vm.states.value["123:0"]!!.enabled)
        vm.rate("123", "0", PostRating.DOWN); runCurrent()
        assertEquals(PostRating.DOWN, vm.states.value["123:0"]!!.rating)
        assertEquals(2, writes)
        assertTrue(vm.states.value["123:0"]!!.enabled)
    }
}
