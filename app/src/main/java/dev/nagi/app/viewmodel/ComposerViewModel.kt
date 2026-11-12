package dev.nagi.app.viewmodel

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

enum class ComposePhase { Preparing, Editing, Submitting, Confirmed, Rejected, Unconfirmed }
data class ComposerState(
    val target: PublishTarget? = null, val context: String = "", val content: String = "",
    val phase: ComposePhase = ComposePhase.Editing, val error: String? = null,
    val selection: Int = 0, val title: String = "", val privateMessage: Boolean = false,
    val recipients: String = "", val newMessage: Boolean = false,
    val selectionStart: Int = selection, val composition: androidx.compose.ui.text.TextRange? = null,
    val preview: Boolean = false,
) {
    val canSend get() = content.isNotBlank() &&
        (target?.action != PublishAction.NEW_TOPIC || title.isNotBlank()) &&
        (!newMessage || (title.isNotBlank() && recipients.isNotBlank())) &&
        phase in setOf(ComposePhase.Editing, ComposePhase.Rejected)
}

@HiltViewModel
class ComposerViewModel @Inject constructor(
    private val client: NgaClient, private val accounts: AccountManager, private val saved: SavedStateHandle,
) : ViewModel() {
    private val owner = accounts.state.value
    private val restoredTarget = saved.get<String>("composerAction")?.takeIf {
        saved.get<String>("composerOwner") == owner.userId && saved.get<Long>("composerRevision") == owner.revision
    }?.let { action ->
        PublishTarget(PublishAction.valueOf(action), forumId = saved["composerForum"] ?: "",
            collectionId = saved["composerCollection"], topicId = saved["composerTopic"], postId = saved["composerPost"])
    }
    private val _state = MutableStateFlow(ComposerState(
        target = restoredTarget, context = if (restoredTarget != null) saved["composerContext"] ?: "" else "",
        content = if (restoredTarget != null) saved["composerText"] ?: "" else "",
        selection = saved["composerSelection"] ?: 0,
        title = if (restoredTarget != null) saved["composerTitle"] ?: "" else "",
        phase = when {
            saved.get<Boolean>("composerPending") == true -> ComposePhase.Unconfirmed
            restoredTarget?.action == PublishAction.MODIFY && saved.get<Boolean>("composerPrepared") != true -> ComposePhase.Preparing
            else -> ComposePhase.Editing
        },
    ))
    val state = _state.asStateFlow()
    private var preparation: Job? = null
    private fun current() = accounts.state.value.let { it.ready && !it.busy && it.revision == owner.revision && it.userId == owner.userId }

    fun open(target: PublishTarget, context: String) {
        if (!current() || _state.value.phase == ComposePhase.Submitting) return
        if (_state.value.target == target && _state.value.phase != ComposePhase.Confirmed) {
            if (_state.value.phase == ComposePhase.Preparing && preparation?.isActive != true) prepare()
            return
        }
        saved["composerOwner"] = owner.userId
        saved["composerRevision"] = owner.revision
        saved["composerForum"] = target.forumId
        saved["composerCollection"] = target.collectionId
        saved["composerTitle"] = ""
        saved["composerTopic"] = target.topicId
        saved["composerPost"] = target.postId
        saved["composerAction"] = target.action.name
        saved["composerContext"] = context
        saved["composerText"] = ""
        saved["composerPending"] = false
        saved["composerPrepared"] = false
        _state.value = ComposerState(target, context, phase = ComposePhase.Preparing)
        if (target.action == PublishAction.NEW_TOPIC) {
            preparation?.cancel()
            _state.value = _state.value.copy(phase = ComposePhase.Editing)
        } else prepare()
    }
    fun close() {
        if (_state.value.phase in setOf(ComposePhase.Submitting, ComposePhase.Unconfirmed)) return
        preparation?.cancel()
        if (_state.value.phase != ComposePhase.Confirmed) return
        listOf("composerOwner", "composerRevision", "composerAction", "composerForum", "composerCollection", "composerTopic",
            "composerPost", "composerContext", "composerText", "composerTitle", "composerSelection", "composerPending", "composerPrepared")
            .forEach { saved.remove<Any>(it) }
        _state.value = ComposerState()
    }
    fun prepare() {
        val target = _state.value.target ?: return
        if (!current() || _state.value.phase != ComposePhase.Preparing) return
        preparation?.cancel()
        _state.value = _state.value.copy(phase = ComposePhase.Preparing, error = null)
        preparation = viewModelScope.launch {
            try {
                if (owner.userId == null) throw NgaException.Authentication()
                val result = client.preparePublish(target)
                if (!current() || _state.value.target != target) return@launch
                edit(result.content, result.content.length)
                saved["composerTitle"] = result.title
                saved["composerPrepared"] = true
                _state.value = _state.value.copy(phase = ComposePhase.Editing, title = result.title, selectionStart = result.content.length)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (current() && _state.value.target == target) _state.value = _state.value.copy(phase = ComposePhase.Preparing,
                    error = if (owner.userId == null) "请先在账号设置中登录，再返回编辑器" else "无法准备正文：${userMessage(error)}")
            }
        }
    }
    fun edit(text: String, selection: Int) {
        if (_state.value.phase in setOf(ComposePhase.Submitting, ComposePhase.Confirmed)) return
        saved["composerText"] = text
        saved["composerSelection"] = selection.coerceIn(0, text.length)
        _state.value = _state.value.copy(content = text, selection = selection.coerceIn(0, text.length))
    }
    fun editValue(value: androidx.compose.ui.text.input.TextFieldValue) {
        if (_state.value.phase !in setOf(ComposePhase.Editing, ComposePhase.Rejected)) return
        edit(value.text, value.selection.end)
        _state.value = _state.value.copy(selectionStart = value.selection.start, composition = value.composition)
    }
    fun preview() { _state.value = _state.value.copy(preview = !_state.value.preview) }
    fun details(title: String, recipients: String) {
        if (_state.value.phase !in setOf(ComposePhase.Editing, ComposePhase.Rejected)) return
        saved["composerTitle"] = title
        _state.value = _state.value.copy(title = title)
    }
    fun send() {
        val draft = _state.value
        if (!draft.canSend || !current()) return
        val target = draft.target ?: return
        _state.value = draft.copy(phase = ComposePhase.Submitting, error = null)
        saved["composerPending"] = true
        viewModelScope.launch {
            try {
                if (!current()) return@launch
                val result = accounts.withCurrentRevision(owner.revision) {
                    client.publish(PublishRequest(target.action, target.forumId, draft.content,
                        title = draft.title, topicId = target.topicId, postId = target.postId, collectionId = target.collectionId))
                }
                if (!current()) return@launch
                when (result) {
                    is WriteResult.Confirmed -> {
                        saved["composerText"] = ""
                        saved["composerPending"] = false
                        _state.value = draft.copy(content = "", phase = ComposePhase.Confirmed)
                    }
                    is WriteResult.Rejected -> {
                        saved["composerPending"] = false
                        _state.value = draft.copy(phase = ComposePhase.Rejected, error = writeFeedback("${if (target.action == PublishAction.MODIFY) "修改" else "发送"}被拒绝：${userMessage(result.reason)}", result))
                    }
                    is WriteResult.Unconfirmed -> _state.value = draft.copy(phase = ComposePhase.Unconfirmed,
                        error = writeFeedback(if (target.action == PublishAction.MODIFY) "暂未收到修改确认，可查看帖子核对，请勿重复提交。"
                            else "暂未收到提交确认，可查看帖子核对，请勿重复发送。", result))
                }
            } catch (error: CancellationException) {
                _state.value = draft.copy(phase = ComposePhase.Unconfirmed, error = null)
                throw error
            } catch (_: Exception) { _state.value = draft.copy(phase = ComposePhase.Unconfirmed, error = null) }
        }
    }
}
