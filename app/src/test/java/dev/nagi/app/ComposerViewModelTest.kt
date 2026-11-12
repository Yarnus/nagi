package dev.nagi.app

import androidx.lifecycle.SavedStateHandle
import dev.nagi.app.data.AccountManager
import dev.nagi.app.viewmodel.*
import dev.nagi.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComposerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()
    private val target = PublishTarget(PublishAction.QUOTE, topicId = "123", postId = "0")

    @Test fun prefillAndSingleSubmissionKeepUnknownDraft() = runTest(dispatcher) {
        val sessions = MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }
        val accounts = AccountManager(sessions, FakeBrowserCookies()).also { it.initialize() }
        var writes = 0
        val gate = CompletableDeferred<WriteResult>()
        val client = object : NgaClient by FakeClient() {
            override suspend fun preparePublish(target: PublishTarget) = PublishPreparation("", "[quote]正文[/quote]")
            override suspend fun publish(request: PublishRequest): WriteResult { writes++; return gate.await() }
        }
        val saved = SavedStateHandle()
        val vm = ComposerViewModel(client, accounts, saved)
        vm.open(target, "引用 #0"); runCurrent()
        assertEquals("[quote]正文[/quote]", vm.state.value.content)
        vm.edit("我的回复", 2)
        vm.send(); vm.send(); runCurrent()
        assertEquals(1, writes)
        gate.complete(WriteResult.Unconfirmed()); runCurrent()
        assertEquals(ComposePhase.Unconfirmed, vm.state.value.phase)
        assertFalse(vm.state.value.canSend)
        assertEquals("我的回复", vm.state.value.content)
        val restored = ComposerViewModel(client, accounts, saved)
        assertEquals(ComposePhase.Unconfirmed, restored.state.value.phase)
        assertEquals(2, restored.state.value.selection)
    }

    @Test fun confirmedClearsOnlySubmittedDraftAndAccountSwitchBlocksSend() = runTest(dispatcher) {
        val sessions = MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }
        val accounts = AccountManager(sessions, FakeBrowserCookies()).also { it.initialize() }
        var writes = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun preparePublish(target: PublishTarget) = PublishPreparation("", "引用")
            override suspend fun publish(request: PublishRequest): WriteResult { writes++; return WriteResult.Confirmed("回复成功") }
        }
        val saved = SavedStateHandle()
        val vm = ComposerViewModel(client, accounts, saved)
        vm.open(target, "引用"); runCurrent(); vm.send(); runCurrent()
        assertEquals(ComposePhase.Confirmed, vm.state.value.phase)
        assertEquals("", vm.state.value.content)
        vm.open(target, "引用"); runCurrent()
        sessions.saveSession(NgaSession("8", "other")); accounts.initialize()
        vm.send(); runCurrent()
        assertEquals(1, writes)
        assertNull(ComposerViewModel(client, accounts, saved).state.value.target)
    }

    @Test fun rejectionRetainsTextAndAllowsExplicitCorrectedSubmission() = runTest(dispatcher) {
        val sessions = MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }
        val accounts = AccountManager(sessions, FakeBrowserCookies()).also { it.initialize() }
        val client = object : NgaClient by FakeClient() {
            override suspend fun preparePublish(target: PublishTarget) = PublishPreparation("", "引用")
            override suspend fun publish(request: PublishRequest): WriteResult = WriteResult.Rejected(NgaException.Restricted())
        }
        val vm = ComposerViewModel(client, accounts, SavedStateHandle())
        vm.open(target, "引用"); runCurrent()
        vm.edit("保留正文", 4); vm.send(); runCurrent()
        assertEquals(ComposePhase.Rejected, vm.state.value.phase)
        assertEquals("保留正文", vm.state.value.content)
        assertTrue(vm.state.value.canSend)
    }

    @Test fun newTopicUsesTitleAndCollectionWithoutPreparation() = runTest(dispatcher) {
        val sessions = MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }
        val accounts = AccountManager(sessions, FakeBrowserCookies()).also { it.initialize() }
        var sent: PublishRequest? = null
        val client = object : NgaClient by FakeClient() {
            override suspend fun publish(request: PublishRequest): WriteResult { sent = request; return WriteResult.Confirmed("发帖成功") }
        }
        val vm = ComposerViewModel(client, accounts, SavedStateHandle())
        vm.open(PublishTarget(PublishAction.NEW_TOPIC, collectionId = "99"), "合集")
        vm.edit("正文", 2); assertFalse(vm.state.value.canSend)
        vm.details("标题", ""); vm.send(); runCurrent()
        assertEquals("99", sent!!.collectionId)
        assertEquals("标题", sent!!.title)
        assertEquals(ComposePhase.Confirmed, vm.state.value.phase)
    }

    @Test fun editLoadsOriginalMarkupAndTitleBeforeExplicitSubmission() = runTest(dispatcher) {
        val accounts = AccountManager(MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }, FakeBrowserCookies())
            .also { it.initialize() }
        val modify = PublishTarget(PublishAction.MODIFY, topicId = "123", postId = "12")
        val original = "[b]原文[/b]\n[quote]引用[/quote] &amp; [img]image.png[/img]"
        var sent: PublishRequest? = null
        val client = object : NgaClient by FakeClient() {
            override suspend fun preparePublish(target: PublishTarget): PublishPreparation {
                assertEquals(modify, target)
                return PublishPreparation("原标题", original)
            }
            override suspend fun publish(request: PublishRequest): WriteResult {
                sent = request
                return WriteResult.Unconfirmed()
            }
        }
        val vm = ComposerViewModel(client, accounts, SavedStateHandle())
        vm.open(modify, "编辑 #12"); runCurrent()
        assertEquals(original, vm.state.value.content)
        assertEquals("原标题", vm.state.value.title)
        assertEquals(original.length, vm.state.value.selectionStart)
        assertNull(sent)
        vm.edit(original + "\n修改", original.length + 3)
        vm.send(); runCurrent()
        assertEquals(PublishAction.MODIFY, sent!!.action)
        assertEquals("12", sent!!.postId)
        assertEquals(original + "\n修改", sent!!.content)
        assertEquals("原标题", sent!!.title)
        vm.close(); vm.open(modify, "编辑 #12"); vm.prepare(); runCurrent()
        assertEquals(ComposePhase.Unconfirmed, vm.state.value.phase)
        assertFalse(vm.state.value.canSend)
    }

    @Test fun closingEditKeepsDraftWhenReturningToTheSamePost() = runTest(dispatcher) {
        val accounts = AccountManager(MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }, FakeBrowserCookies())
            .also { it.initialize() }
        val client = object : NgaClient by FakeClient() {
            override suspend fun preparePublish(target: PublishTarget) = PublishPreparation("", "原始正文")
        }
        val vm = ComposerViewModel(client, accounts, SavedStateHandle())
        val modify = PublishTarget(PublishAction.MODIFY, topicId = "123", postId = "12")
        vm.open(modify, "编辑"); runCurrent(); vm.edit("保留的修改", 5); vm.close()
        assertEquals(modify, vm.state.value.target)
        vm.open(modify, "编辑"); runCurrent()
        assertEquals("保留的修改", vm.state.value.content)
    }

    @Test fun editPreparationRestartsAfterRestorationBeforeOriginalBodyArrives() = runTest(dispatcher) {
        val accounts = AccountManager(MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }, FakeBrowserCookies())
            .also { it.initialize() }
        val client = object : NgaClient by FakeClient() {
            override suspend fun preparePublish(target: PublishTarget) = PublishPreparation("", "服务器原文")
        }
        val saved = SavedStateHandle()
        val modify = PublishTarget(PublishAction.MODIFY, topicId = "123", postId = "12")
        val initial = ComposerViewModel(client, accounts, saved)
        initial.open(modify, "编辑"); initial.close()
        val restored = ComposerViewModel(client, accounts, saved)
        assertEquals(ComposePhase.Preparing, restored.state.value.phase)
        restored.open(modify, "编辑"); runCurrent()
        assertEquals(ComposePhase.Editing, restored.state.value.phase)
        assertEquals("服务器原文", restored.state.value.content)
    }

    @Test fun confirmedEditCanCloseAndReopenWithFreshServerBody() = runTest(dispatcher) {
        val accounts = AccountManager(MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }, FakeBrowserCookies())
            .also { it.initialize() }
        var prepares = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun preparePublish(target: PublishTarget) = PublishPreparation("", "正文${++prepares}")
            override suspend fun publish(request: PublishRequest) = WriteResult.Confirmed("修改已确认")
        }
        val vm = ComposerViewModel(client, accounts, SavedStateHandle())
        val modify = PublishTarget(PublishAction.MODIFY, topicId = "123", postId = "12")
        vm.open(modify, "编辑"); runCurrent(); vm.send(); runCurrent()
        assertEquals(ComposePhase.Confirmed, vm.state.value.phase)
        vm.close(); assertNull(vm.state.value.target)
        vm.open(modify, "编辑"); runCurrent()
        assertEquals("正文2", vm.state.value.content)
        assertEquals(ComposePhase.Editing, vm.state.value.phase)
    }

    @Test fun editPreparationFailureDisplaysSafeServerReasonWithoutEnablingSubmit() = runTest(dispatcher) {
        val accounts = AccountManager(MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }, FakeBrowserCookies())
            .also { it.initialize() }
        val client = object : NgaClient by FakeClient() {
            override suspend fun preparePublish(target: PublishTarget): PublishPreparation =
                throw NgaException.Restricted(NgaRejectionReason.PERMISSION)
        }
        val vm = ComposerViewModel(client, accounts, SavedStateHandle())
        vm.open(PublishTarget(PublishAction.MODIFY, topicId = "123", postId = "12"), "编辑"); runCurrent()
        assertEquals(ComposePhase.Preparing, vm.state.value.phase)
        assertTrue(vm.state.value.error!!.contains("权限"))
        assertFalse(vm.state.value.canSend)
    }

    @Test fun uncertainEditAfterRejectionDoesNotReuseFailureMessage() = runTest(dispatcher) {
        val accounts = AccountManager(MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }, FakeBrowserCookies())
            .also { it.initialize() }
        var writes = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun preparePublish(target: PublishTarget) = PublishPreparation("", "原文")
            override suspend fun publish(request: PublishRequest): WriteResult {
                if (++writes == 1) return WriteResult.Rejected(NgaException.Restricted())
                throw IllegalStateException("PRIVATE_TRANSPORT_DETAIL")
            }
        }
        val vm = ComposerViewModel(client, accounts, SavedStateHandle())
        vm.open(PublishTarget(PublishAction.MODIFY, topicId = "123", postId = "12"), "编辑"); runCurrent()
        vm.send(); runCurrent(); assertEquals(ComposePhase.Rejected, vm.state.value.phase)
        vm.send(); runCurrent()
        assertEquals(ComposePhase.Unconfirmed, vm.state.value.phase)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.canSend)
        assertEquals("原文", vm.state.value.content)
        vm.send(); runCurrent(); assertEquals(2, writes)
    }

    @Test fun guestPrefillFailureDoesNotEnableSending() = runTest(dispatcher) {
        val accounts = AccountManager(MemorySessionStore(), FakeBrowserCookies()).also { it.initialize() }
        val vm = ComposerViewModel(FakeClient(), accounts, SavedStateHandle())
        vm.open(target, "引用"); runCurrent()
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.canSend)
    }
}
