package dev.nagi.app.viewmodel

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
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

internal fun assembleReply(prefill: String, input: String): String = when {
    prefill.isEmpty() -> input
    input.isEmpty() -> prefill
    prefill.endsWith('\n') || input.startsWith('\n') -> prefill + input
    else -> "$prefill\n\n$input"
}

data class ReplySession(
    val target: PublishTarget, val context: String,
    val prefill: String = "", val input: TextFieldValue = TextFieldValue(),
    val phase: ComposePhase = ComposePhase.Preparing, val error: String? = null,
    val preview: Boolean = false, val expandedContext: Boolean = false,
) {
    val comment get() = target.action == PublishAction.COMMENT
    val content get() = assembleReply(prefill, input.text)
    val canSend get() = input.text.isNotBlank() && phase in setOf(ComposePhase.Editing, ComposePhase.Rejected)
}

@HiltViewModel
class ReplySessionViewModel @Inject constructor(
    private val client: NgaClient, private val accounts: AccountManager, private val saved: SavedStateHandle,
) : ViewModel() {
    private val owner = accounts.state.value
    private val _session = MutableStateFlow<ReplySession?>(null)
    val session = _session.asStateFlow()
    private var preparation: Job? = null
    private var generation = 0L
    private fun current() = accounts.state.value.let {
        it.ready && !it.busy && it.userId == owner.userId && it.revision == owner.revision
    }
    init {
        viewModelScope.launch { accounts.state.collect { if (!current()) clear() } }
    }
    private fun pendingKey(target: PublishTarget) = "${owner.userId}:${owner.revision}:$target"
    private fun pending() = saved.get<ArrayList<String>>("replyPendingTargets") ?: arrayListOf()
    private fun clear() { generation++; preparation?.cancel(); _session.value = null }
    fun close() { if (_session.value?.phase != ComposePhase.Submitting) clear() }
    fun open(target: PublishTarget, context: String) {
        if (!current() || _session.value != null) return
        require(target.action in setOf(PublishAction.REPLY, PublishAction.COMMENT, PublishAction.QUOTE))
        val interrupted = pendingKey(target) in pending()
        _session.value = ReplySession(target, context, phase = if (interrupted) ComposePhase.Unconfirmed else ComposePhase.Preparing)
        if (!interrupted) prepare()
    }
    fun prepare() {
        val session = _session.value ?: return
        if (!current() || session.phase != ComposePhase.Preparing) return
        preparation?.cancel()
        val token = ++generation
        _session.value = session.copy(error = null)
        preparation = viewModelScope.launch {
            try {
                if (owner.userId == null) throw NgaException.Authentication()
                val result = client.preparePublish(session.target)
                if (current() && generation == token) _session.value = _session.value?.copy(
                    prefill = result.content, phase = ComposePhase.Editing)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (current() && generation == token) _session.value = _session.value?.copy(
                    error = if (owner.userId == null) "请先登录后再回复" else "无法准备回复：${userMessage(error)}")
            }
        }
    }
    fun edit(value: TextFieldValue) {
        val session = _session.value ?: return
        if (session.phase in setOf(ComposePhase.Editing, ComposePhase.Rejected)) _session.value = session.copy(input = value)
    }
    fun expandContext() { _session.value = _session.value?.let { it.copy(expandedContext = !it.expandedContext) } }
    fun preview() { _session.value = _session.value?.let { it.copy(preview = !it.preview) } }
    fun send() {
        val session = _session.value ?: return
        if (!current() || !session.canSend) return
        val token = generation
        _session.value = session.copy(phase = ComposePhase.Submitting, error = null)
        saved["replyPendingTargets"] = ArrayList((pending() + pendingKey(session.target)).distinct())
        viewModelScope.launch {
            val result = try {
                val target = session.target
                accounts.withCurrentRevision(owner.revision) {
                    client.publish(PublishRequest(target.action, target.forumId, session.content,
                        topicId = target.topicId, postId = target.postId, collectionId = target.collectionId))
                }
            } catch (error: CancellationException) {
                if (current() && token == generation) _session.value = session.copy(phase = ComposePhase.Unconfirmed, error = null)
                throw error
            } catch (_: Exception) { WriteResult.Unconfirmed() }
            if (!current() || token != generation) return@launch
            when (result) {
                is WriteResult.Confirmed -> {
                    saved["replyPendingTargets"] = ArrayList(pending().filterNot { it == pendingKey(session.target) })
                    _session.value = session.copy(input = TextFieldValue(), prefill = "", phase = ComposePhase.Confirmed)
                }
                is WriteResult.Rejected -> {
                    saved["replyPendingTargets"] = ArrayList(pending().filterNot { it == pendingKey(session.target) })
                    _session.value = session.copy(phase = ComposePhase.Rejected, error = writeFeedback("发送被拒绝：${userMessage(result.reason)}", result))
                }
                is WriteResult.Unconfirmed -> _session.value = session.copy(phase = ComposePhase.Unconfirmed,
                    error = writeFeedback("暂未收到提交确认，可查看帖子核对，请勿重复发送。", result))
            }
        }
    }
}
