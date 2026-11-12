package dev.nagi.app

import dev.nagi.app.data.AccountManager
import dev.nagi.app.viewmodel.*
import dev.nagi.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()
    private suspend fun accounts() = AccountManager(MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }, FakeBrowserCookies()).also { it.initialize() }

    @Test fun paginationAndConversationRequestsRemainSeparate() = runTest(dispatcher) {
        val client = object : NgaClient by FakeClient() {
            override suspend fun getMessageConversations(page: Int) = MessageConversationPage(listOf(
                MessageConversation("$page", "Title", "8", "User", 1, 2, 3, emptyList())), page, page < 2)
            override suspend fun getMessagePosts(conversationId: String, page: Int) = MessagePostPage(listOf(
                MessagePost("$conversationId:$page", "8", "", emptyList(), 1)), emptyList(), page, false)
        }
        val vm = MessagesViewModel(client, accounts())
        vm.open(null); runCurrent(); vm.load(); runCurrent()
        assertEquals(listOf("1", "2"), vm.state.value.conversations.map { it.id })
        assertFalse(vm.state.value.hasMore)
        vm.open("1"); runCurrent()
        assertTrue(vm.state.value.conversations.isEmpty())
        assertEquals("1:1", vm.state.value.posts.single().id)
    }
    @Test fun sendingUsesMidAndUnknownResultCannotRepeat() = runTest(dispatcher) {
        var writes = 0
        var sent: MessageRequest? = null
        val client = object : NgaClient by FakeClient() {
            override suspend fun getMessagePosts(conversationId: String, page: Int) = MessagePostPage(emptyList(), emptyList(), page, false)
            override suspend fun sendMessage(request: MessageRequest): WriteResult { sent = request; writes++; delay(100); return WriteResult.Unconfirmed() }
        }
        val vm = MessagesViewModel(client, accounts())
        vm.open("9"); runCurrent(); vm.compose(false); vm.edit("回复", 2)
        vm.send(); vm.send(); advanceUntilIdle()
        assertEquals(1, writes)
        assertEquals("9", sent!!.conversationId)
        assertFalse(vm.draft.value.canSend)
        assertEquals("回复", vm.draft.value.content)
        assertEquals(ComposePhase.Unconfirmed, vm.draft.value.phase)
    }
    @Test fun privateEditorClearsOnCloseAndRetainsSelectionDuringPreview() = runTest(dispatcher) {
        val account = accounts()
        val vm = MessagesViewModel(FakeClient(), account)
        vm.compose(true)
        val value = androidx.compose.ui.text.input.TextFieldValue("私信正文", androidx.compose.ui.text.TextRange(1, 3), androidx.compose.ui.text.TextRange(0, 2))
        vm.editValue(value); vm.details("标题", "Alice"); vm.preview(); vm.preview()
        assertEquals(1, vm.draft.value.selectionStart)
        assertEquals(3, vm.draft.value.selection)
        assertEquals(value.composition, vm.draft.value.composition)
        vm.closeComposer(); assertFalse(vm.composing.value)
        vm.compose(true)
        assertEquals("", vm.draft.value.content); assertEquals("", vm.draft.value.title)
        assertEquals("", vm.draft.value.recipients)
        account.logout(); runCurrent()
        assertFalse(vm.composing.value)
    }
    @Test fun uncertaintySurvivesRouteRecreationWithoutBlockingOtherNewMessages() = runTest(dispatcher) {
        val account = accounts()
        val guard = dev.nagi.app.data.MessageWriteGuard()
        var sends = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun sendMessage(request: MessageRequest): WriteResult { sends++; return WriteResult.Unconfirmed() }
        }
        val vm = MessagesViewModel(client, account, guard)
        vm.compose(true); vm.edit("私密内容", 4); vm.details("标题", "Alice")
        vm.send(); runCurrent(); vm.closeComposer()
        val restored = MessagesViewModel(client, account, guard)
        assertEquals("", restored.draft.value.content)
        restored.compose(true); restored.edit("私密内容", 4); restored.details("标题", "Alice")
        restored.send(); runCurrent()
        assertEquals(ComposePhase.Unconfirmed, restored.draft.value.phase)
        assertEquals(1, sends)
        restored.closeComposer(); restored.compose(true)
        restored.edit("另一封", 3); restored.details("标题", "Bob"); restored.send(); runCurrent()
        assertEquals(2, sends)
        restored.closeComposer(); restored.open("9"); runCurrent()
        restored.compose(false); restored.edit("回复", 2); restored.send(); runCurrent()
        val conversation = MessagesViewModel(client, account, guard)
        conversation.open("9"); runCurrent(); conversation.compose(false)
        assertEquals(ComposePhase.Unconfirmed, conversation.draft.value.phase)
    }
    @Test fun profileRecipientIsPrefilledWithoutOverwritingAnOpenDraft() = runTest(dispatcher) {
        val account = accounts()
        val vm = MessagesViewModel(FakeClient(), account)
        vm.composeTo("目标用户")
        assertEquals("目标用户", vm.draft.value.recipients)
        vm.edit("私信草稿", 4)
        vm.composeTo("另一用户")
        assertEquals("目标用户", vm.draft.value.recipients)
        assertEquals("私信草稿", vm.draft.value.content)
        account.logout(); runCurrent()
        vm.composeTo("目标用户")
        assertFalse(vm.composing.value)
    }

    @Test fun newMessageRequiresRecipientAndTitle() = runTest(dispatcher) {
        var sent: MessageRequest? = null
        val client = object : NgaClient by FakeClient() {
            override suspend fun sendMessage(request: MessageRequest): WriteResult { sent = request; return WriteResult.Rejected(NgaException.Business()) }
        }
        val vm = MessagesViewModel(client, accounts())
        vm.compose(true); vm.edit("内容", 2)
        assertFalse(vm.draft.value.canSend)
        vm.details("标题", "Alice Bob"); assertTrue(vm.draft.value.canSend)
        vm.send(); runCurrent()
        assertEquals(listOf("Alice", "Bob"), sent!!.recipients)
        assertEquals(ComposePhase.Rejected, vm.draft.value.phase)
        assertEquals("内容", vm.draft.value.content)
    }
}
