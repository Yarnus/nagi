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
    val preview: Boolean = false, val images: DraftImages = DraftImages(),
) {
    val canSend get() = !images.uploading && content.isNotBlank() &&
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
        images = if (restoredTarget != null) saved.restoreImages() else DraftImages(),
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
        if (!current() || _state.value.phase == ComposePhase.Submitting || _state.value.images.uploading) return
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
        saved["composerUploadPending"] = false
        saved.saveImages(DraftImages())
        _state.value = ComposerState(target, context, phase = ComposePhase.Preparing)
        if (target.action == PublishAction.NEW_TOPIC) {
            preparation?.cancel()
            _state.value = _state.value.copy(phase = ComposePhase.Editing)
        } else prepare()
    }
    fun close() {
        if (_state.value.phase in setOf(ComposePhase.Submitting, ComposePhase.Unconfirmed) || _state.value.images.uploading) return
        preparation?.cancel()
        if (_state.value.phase != ComposePhase.Confirmed) return
        listOf("composerOwner", "composerRevision", "composerAction", "composerForum", "composerCollection", "composerTopic",
            "composerPost", "composerContext", "composerText", "composerTitle", "composerSelection", "composerPending", "composerPrepared",
            "composerImageNames", "composerImageChecks", "composerImageUrls", "composerUploadPending")
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
    fun uploadImage(read: suspend () -> ImageUpload) {
        val draft = _state.value
        val target = draft.target ?: return
        if (!current() || draft.phase !in setOf(ComposePhase.Editing, ComposePhase.Rejected) ||
            draft.images.uploading || draft.images.blocked || target.action != PublishAction.NEW_TOPIC) return
        _state.value = draft.copy(images = draft.images.copy(uploading = true, error = null))
        viewModelScope.launch {
            var started = false
            try {
                val image = read()
                if (!current()) return@launch
                saved["composerUploadPending"] = true
                started = true
                val result = client.uploadImage(target, image)
                if (!current() || _state.value.target != target) return@launch
                val attachment = result.attachment
                val blocked = result.outcome !is WriteResult.Rejected && attachment == null
                saved["composerUploadPending"] = blocked
                val images = _state.value.images.copy(uploading = false, blocked = blocked, error = result.feedback(),
                    attachments = draft.images.attachments + listOfNotNull(attachment))
                if (attachment != null) {
                    val value = _state.value.let { androidx.compose.ui.text.input.TextFieldValue(it.content,
                        androidx.compose.ui.text.TextRange(it.selectionStart, it.selection)) }
                    editValue(insertImage(value, attachment))
                }
                saved.saveImages(images)
                _state.value = _state.value.copy(images = images)
            } catch (error: CancellationException) { throw error }
            catch (error: NgaException) {
                if (current()) {
                    saved["composerUploadPending"] = false
                    _state.value = _state.value.copy(images = draft.images.copy(error = "无法准备图片上传：${userMessage(error)}"))
                }
            }
            catch (_: Exception) {
                if (current()) _state.value = _state.value.copy(images = draft.images.copy(blocked = started,
                    error = if (started) "图片上传结果不明，请勿重复上传。" else "无法读取图片，请选择 10MB 以内的 JPEG、PNG、GIF 或 WebP 图片。"))
            }
        }
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
                        title = draft.title, topicId = target.topicId, postId = target.postId, collectionId = target.collectionId,
                        attachments = draft.images.attachments.filter { draft.content.contains(it.bbcode) }))
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
