package dev.nagi.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.nagi.app.data.AccountManager
import dev.nagi.sdk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MessagesState(
    val conversations: List<MessageConversation> = emptyList(), val posts: List<MessagePost> = emptyList(),
    val participants: List<MessageParticipant> = emptyList(), val conversationId: String? = null,
    val page: Int = 0, val hasMore: Boolean = false, val loading: Boolean = false, val error: String? = null,
)

@HiltViewModel
class MessagesViewModel @Inject constructor(private val client: NgaClient, private val accounts: AccountManager,
    private val writes: dev.nagi.app.data.MessageWriteGuard = dev.nagi.app.data.MessageWriteGuard()) : ViewModel() {
    private val owner = accounts.state.value
    private val _state = MutableStateFlow(MessagesState())
    val state = _state.asStateFlow()
    private val _draft = MutableStateFlow(ComposerState(privateMessage = true))
    val draft = _draft.asStateFlow()
    private val _composing = MutableStateFlow(false)
    val composing = _composing.asStateFlow()
    private var read: Job? = null
    private var generation = 0
    private fun current() = accounts.state.value.let { it.ready && !it.busy && it.userId == owner.userId && it.revision == owner.revision }
    fun open(id: String?) {
        read?.cancel(); generation++
        _state.value = MessagesState(conversationId = id)
        load()
    }
    fun load(refresh: Boolean = false) {
        if (!current() || _state.value.loading) return
        val before = _state.value
        val page = if (refresh) 1 else before.page + 1
        val version = generation
        _state.value = before.copy(loading = true, error = null)
        read = viewModelScope.launch {
            try {
                if (owner.userId == null) throw NgaException.Authentication()
                val next = if (before.conversationId == null) {
                    val data = client.getMessageConversations(page)
                    before.copy(conversations = ((if (refresh) emptyList() else before.conversations) + data.items).distinctBy { it.id },
                        page = page, hasMore = data.hasMore, loading = false)
                } else {
                    val data = client.getMessagePosts(before.conversationId, page)
                    before.copy(posts = ((if (refresh) emptyList() else before.posts) + data.items).distinctBy { it.id },
                        participants = data.participants, page = page, hasMore = data.hasMore, loading = false)
                }
                if (current() && version == generation) _state.value = next
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (current() && version == generation) _state.value = before.copy(error = when {
                    owner.userId == null || error is NgaException.Authentication -> "请先在账号设置中登录"
                    error is NgaException.Restricted -> "短消息访问受限，请稍后重试"
                    error is NgaException.Protocol -> "无法识别短消息响应，接口尚待真实验证"
                    error is NgaException.Network -> "短消息网络请求失败，请重试"
                    else -> "无法加载短消息，请重试"
                }, loading = false)
            }
        }
    }
    init {
        viewModelScope.launch { accounts.state.collect { if (!current()) {
            _draft.value = ComposerState(privateMessage = true); _composing.value = false
        } } }
    }
    fun closeComposer() {
        if (_draft.value.phase != ComposePhase.Submitting) {
            _draft.value = ComposerState(privateMessage = true); _composing.value = false
        }
    }
    fun preview() { _draft.value = _draft.value.copy(preview = !_draft.value.preview) }
    fun editValue(value: androidx.compose.ui.text.input.TextFieldValue) {
        if (_draft.value.phase !in setOf(ComposePhase.Editing, ComposePhase.Rejected)) return
        _draft.value = _draft.value.copy(content = value.text, selection = value.selection.end,
            selectionStart = value.selection.start, composition = value.composition)
    }
    private fun pendingKey(id: String?) = writes.key(owner.userId, "conversation:$id")
    private var destination: String? = null
    fun compose(new: Boolean) {
        if (!current()) return
        _composing.value = true
        val id = if (new) null else _state.value.conversationId ?: return
        if (_draft.value.phase == ComposePhase.Submitting) return
        if (destination == id && _draft.value.newMessage == new &&
            (_draft.value.phase == ComposePhase.Unconfirmed || _draft.value.content.isNotBlank())) return
        destination = id
        _draft.value = ComposerState(privateMessage = true, newMessage = new,
            phase = if (!new && writes.contains(pendingKey(id))) ComposePhase.Unconfirmed else ComposePhase.Editing,
            context = if (new) "发送给完整用户名" else "回复当前会话")
    }
    fun composeTo(username: String) {
        if (!current() || owner.userId == null || username.isBlank() || _composing.value) return
        compose(new = true)
        if (_draft.value.phase == ComposePhase.Editing) {
            _draft.value = _draft.value.copy(recipients = username, context = "发送给 $username")
        }
    }
    fun edit(text: String, cursor: Int) {
        if (_draft.value.phase !in setOf(ComposePhase.Editing, ComposePhase.Rejected)) return
        _draft.value = _draft.value.copy(content = text, selection = cursor)
    }
    fun details(title: String, recipients: String) {
        if (_draft.value.phase !in setOf(ComposePhase.Editing, ComposePhase.Rejected)) return
        _draft.value = _draft.value.copy(title = title, recipients = recipients)
    }
    fun send() {
        val draft = _draft.value
        if (!current() || !draft.canSend) return
        val request = try { MessageRequest(if (draft.newMessage) MessageAction.NEW else MessageAction.REPLY,
            draft.content, draft.title, if (draft.newMessage) draft.recipients.trim().split(Regex("\\s+")).distinct() else emptyList(), destination)
        } catch (_: IllegalArgumentException) { _draft.value = draft.copy(error = "请检查收件人、标题与正文"); return }
        _draft.value = draft.copy(phase = ComposePhase.Submitting, error = null)
        val pendingTarget = if (draft.newMessage) writes.key(owner.userId,
            "new:${request.recipients.size}:" + (request.recipients.sorted() + listOf(request.title, request.content))
                .joinToString("") { "${it.length}:$it" })
            else pendingKey(destination)
        viewModelScope.launch {
            try {
                val result = accounts.withCurrentRevision(owner.revision) {
                    if (writes.contains(pendingTarget)) WriteResult.Unconfirmed()
                    else if (owner.userId == null) WriteResult.Rejected(NgaException.Authentication())
                    else {
                        writes.add(pendingTarget)
                        client.sendMessage(request)
                    }
                }
                if (!current()) return@launch
                if (result !is WriteResult.Unconfirmed) writes.remove(pendingTarget)
                _draft.value = when (result) {
                    is WriteResult.Confirmed -> draft.copy(content = "", phase = ComposePhase.Confirmed)
                    is WriteResult.Rejected -> draft.copy(phase = ComposePhase.Rejected, error = "发送被拒绝，请检查登录状态与权限")
                    is WriteResult.Unconfirmed -> draft.copy(phase = ComposePhase.Unconfirmed)
                }
            } catch (error: CancellationException) {
                if (current()) _draft.value = draft.copy(phase = ComposePhase.Unconfirmed)
                throw error
            } catch (_: Exception) {
                if (current()) _draft.value = draft.copy(phase = ComposePhase.Unconfirmed)
            }
        }
    }
}
