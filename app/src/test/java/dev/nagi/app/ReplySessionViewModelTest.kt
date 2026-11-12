package dev.nagi.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import dev.nagi.app.data.AccountManager
import dev.nagi.app.viewmodel.*
import dev.nagi.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReplySessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()
    private val target = PublishTarget(PublishAction.REPLY, topicId = "123", postId = "0")
    private suspend fun accounts() = AccountManager(
        MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }, FakeBrowserCookies()).also { it.initialize() }

    @Test fun assemblyPreservesRawContextAndSeparators() {
        listOf("", "[pid=0]原文[/pid]", "[quote][b]嵌套[/b][/quote]", "实体 &amp; 表情😀\n", "两行\n原文").forEach { raw ->
            val body = "我的回复😀"
            val joined = assembleReply(raw, body)
            assertTrue(joined.endsWith(body)); assertTrue(joined.startsWith(raw))
            assertEquals(joined, assembleReply(raw, body))
        }
        assertEquals("a\nb", assembleReply("a\n", "b"))
        assertEquals("a\nb", assembleReply("a", "\nb"))
        assertEquals("a\n\nb", assembleReply("a", "b"))
    }
    @Test fun sessionRetainsFullSelectionButNeverRestoresBody() = runTest(dispatcher) {
        val accounts = accounts()
        val saved = SavedStateHandle()
        val client = object : NgaClient by FakeClient() {
            override suspend fun preparePublish(target: PublishTarget) = PublishPreparation("", "[pid=0]原文[/pid]")
        }
        val vm = ReplySessionViewModel(client, accounts, saved)
        vm.open(target, "回复 #0"); runCurrent()
        val input = TextFieldValue("选择中文", TextRange(1, 3), TextRange(0, 2))
        vm.edit(input); vm.preview(); vm.preview()
        assertEquals(input, vm.session.value!!.input)
        assertNull(ReplySessionViewModel(client, accounts, saved).session.value)
        vm.close(); vm.open(target, "回复 #0"); runCurrent()
        assertEquals("", vm.session.value!!.input.text)
        assertEquals("[pid=0]原文[/pid]", vm.session.value!!.prefill)
    }
    @Test fun canceledPreparationCannotReopenSession() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val client = object : NgaClient by FakeClient() {
            override suspend fun preparePublish(target: PublishTarget): PublishPreparation {
                withContext(NonCancellable) { gate.await() }
                return PublishPreparation("", "迟到的原文")
            }
        }
        val vm = ReplySessionViewModel(client, accounts(), SavedStateHandle())
        vm.open(target, "回复"); runCurrent(); vm.close()
        gate.complete(Unit); runCurrent()
        assertNull(vm.session.value)
    }
    @Test fun unknownSendCannotReplayEvenAfterCloseOrRestoration() = runTest(dispatcher) {
        var writes = 0
        var sent: PublishRequest? = null
        val gate = CompletableDeferred<WriteResult>()
        val client = object : NgaClient by FakeClient() {
            override suspend fun preparePublish(target: PublishTarget) = PublishPreparation("", "服务器原文")
            override suspend fun publish(request: PublishRequest): WriteResult { writes++; sent = request; return gate.await() }
        }
        val accounts = accounts(); val saved = SavedStateHandle()
        val vm = ReplySessionViewModel(client, accounts, saved)
        vm.open(target, "回复"); runCurrent(); vm.edit(TextFieldValue("输入"))
        val previewContent = vm.session.value!!.content
        vm.send(); vm.send(); vm.close(); runCurrent()
        assertEquals(ComposePhase.Submitting, vm.session.value!!.phase)
        assertEquals(previewContent, sent!!.content); assertEquals(1, writes)
        gate.complete(WriteResult.Unconfirmed()); runCurrent()
        vm.close(); vm.open(target, "回复"); vm.send(); runCurrent()
        assertEquals(ComposePhase.Unconfirmed, vm.session.value!!.phase)
        val restored = ReplySessionViewModel(client, accounts, saved)
        assertNull(restored.session.value)
        restored.open(target, "回复")
        assertFalse(restored.session.value!!.canSend)
        assertEquals("", restored.session.value!!.input.text)
        assertEquals(1, writes)
    }
    @Test fun rejectionAllowsCorrectionAndConfirmationClearsSessionContent() = runTest(dispatcher) {
        var reject = true
        val client = object : NgaClient by FakeClient() {
            override suspend fun preparePublish(target: PublishTarget) = PublishPreparation("", "原文")
            override suspend fun publish(request: PublishRequest): WriteResult = if (reject)
                WriteResult.Rejected(NgaException.Restricted()) else WriteResult.Confirmed("成功")
        }
        val vm = ReplySessionViewModel(client, accounts(), SavedStateHandle())
        vm.open(target, "回复"); runCurrent(); vm.edit(TextFieldValue("输入")); vm.send(); runCurrent()
        assertTrue(vm.session.value!!.canSend)
        assertEquals("输入", vm.session.value!!.input.text)
        reject = false; vm.send(); runCurrent()
        assertEquals(ComposePhase.Confirmed, vm.session.value!!.phase)
        assertEquals("", vm.session.value!!.content)
    }
    @Test fun rejectedReplyShowsAuthenticationReasonAndKeepsDraftWithoutReplay() = runTest(dispatcher) {
        var writes = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun preparePublish(target: PublishTarget) = PublishPreparation("", "")
            override suspend fun publish(request: PublishRequest): WriteResult {
                writes++
                return WriteResult.Rejected(NgaException.Authentication())
            }
        }
        val vm = ReplySessionViewModel(client, accounts(), SavedStateHandle())
        vm.open(target, "回复"); runCurrent()
        vm.edit(TextFieldValue("我的回复")); vm.send(); runCurrent()
        assertEquals("发送被拒绝：请先登录 NGA，或当前登录已过期", vm.session.value!!.error)
        assertEquals("我的回复", vm.session.value!!.input.text)
        assertEquals(ComposePhase.Rejected, vm.session.value!!.phase)
        assertEquals(1, writes)
    }

    @Test fun rejectedCommentShowsContentReasonAndPreservesInput() = runTest(dispatcher) {
        val client = object : NgaClient by FakeClient() {
            override suspend fun preparePublish(target: PublishTarget) = PublishPreparation("", "")
            override suspend fun publish(request: PublishRequest) =
                WriteResult.Rejected(NgaException.Business(NgaRejectionReason.CONTENT_LENGTH))
        }
        val vm = ReplySessionViewModel(client, accounts(), SavedStateHandle())
        vm.open(target.copy(action = PublishAction.COMMENT), "贴条评论"); runCurrent()
        vm.edit(TextFieldValue("输入")); vm.send(); runCurrent()
        assertEquals("发送被拒绝：正文长度不符合 NGA 要求，请调整后再试", vm.session.value!!.error)
        assertEquals("输入", vm.session.value!!.input.text)
        assertTrue(vm.session.value!!.canSend)
    }

    @Test fun diagnosticUnconfirmedReplyKeepsDraftAndCannotBeResent() = runTest(dispatcher) {
        var writes = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun preparePublish(target: PublishTarget) = PublishPreparation("", "")
            override suspend fun publish(request: PublishRequest): WriteResult {
                writes++
                return WriteResult.Unconfirmed(WriteDiagnostic(httpStatus = 200, errorCode = 39))
            }
        }
        val vm = ReplySessionViewModel(client, accounts(), SavedStateHandle())
        vm.open(target, "回复"); runCurrent()
        vm.edit(TextFieldValue("Private draft")); vm.send(); runCurrent()
        val state = vm.session.value!!
        assertEquals(ComposePhase.Unconfirmed, state.phase)
        assertFalse(state.canSend)
        assertEquals("Private draft", state.input.text)
        assertTrue(state.error!!.contains("HTTP=200"))
        assertTrue(state.error!!.contains("code=39"))
        assertFalse(state.error!!.contains("Private draft"))
        vm.send(); runCurrent()
        assertEquals(1, writes)
    }

    @Test fun quotePrefillIsSeparateAndPreviewSendAssemblyIsExact() = runTest(dispatcher) {
        val accounts = accounts()
        val raw = "[quote][pid=0,123,1]Reply[/pid]\n[b]作者 &amp; 😀[/b]\n[quote]嵌套[/quote][/quote]\n"
        val requests = mutableListOf<PublishRequest>()
        val targets = mutableListOf<PublishTarget>()
        val client = object : NgaClient by FakeClient() {
            override suspend fun preparePublish(target: PublishTarget): PublishPreparation {
                targets += target
                return PublishPreparation("", raw)
            }
            override suspend fun publish(request: PublishRequest): WriteResult {
                requests += request
                return WriteResult.Rejected(NgaException.Restricted())
            }
        }
        val vm = ReplySessionViewModel(client, accounts, SavedStateHandle())
        for (pid in listOf("0", "12")) {
            vm.open(target.copy(action = PublishAction.QUOTE, postId = pid), "引用回复")
            runCurrent()
            assertEquals("", vm.session.value!!.input.text)
            assertEquals(raw, vm.session.value!!.prefill)
            vm.edit(TextFieldValue("我的回复😀", TextRange(1, 4)))
            vm.preview(); vm.preview(); vm.expandContext(); vm.prepare(); runCurrent()
            val expected = raw + "我的回复😀"
            assertEquals(expected, vm.session.value!!.content)
            vm.send(); runCurrent(); vm.send(); runCurrent()
            assertEquals(expected, requests.last().content)
            assertEquals(TextRange(1, 4), vm.session.value!!.input.selection)
            vm.close()
        }
        assertEquals(listOf("0", "12"), targets.map { it.postId })
        assertEquals(4, requests.size)
        assertTrue(requests.all { it.action == PublishAction.QUOTE && it.content == raw + "我的回复😀" })
    }

    @Test fun topicReplyHasNoFloorTargetAndNoConstructedReference() = runTest(dispatcher) {
        var prepared: PublishTarget? = null
        var sent: PublishRequest? = null
        val client = object : NgaClient by FakeClient() {
            override suspend fun preparePublish(target: PublishTarget): PublishPreparation { prepared = target; return PublishPreparation("", "") }
            override suspend fun publish(request: PublishRequest): WriteResult { sent = request; return WriteResult.Confirmed("回复成功") }
        }
        val vm = ReplySessionViewModel(client, accounts(), SavedStateHandle())
        vm.open(PublishTarget(PublishAction.REPLY, topicId = "123"), "回复帖子"); runCurrent()
        assertNull(prepared!!.postId)
        vm.edit(TextFieldValue("普通主题回复")); vm.send(); runCurrent()
        assertNull(sent!!.postId)
        assertEquals("普通主题回复", sent!!.content)
    }

    @Test fun accountChangeInvalidatesSession() = runTest(dispatcher) {
        val accounts = accounts()
        val client = object : NgaClient by FakeClient() {
            override suspend fun preparePublish(target: PublishTarget) = PublishPreparation("", "")
        }
        val vm = ReplySessionViewModel(client, accounts, SavedStateHandle())
        vm.open(target, "回复"); runCurrent(); vm.edit(TextFieldValue("旧账号输入"))
        accounts.logout(); runCurrent()
        assertNull(vm.session.value)
    }
}
