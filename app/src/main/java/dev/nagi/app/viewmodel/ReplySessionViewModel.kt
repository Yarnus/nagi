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
    val preview: Boolean = false, val expandedContext: Boolean = false, val images: DraftImages = DraftImages(),
) {
    val comment get() = target.action == PublishAction.COMMENT
    val content get() = assembleReply(prefill, input.text)
    val canSend get() = !images.uploading && input.text.isNotBlank() && phase in setOf(ComposePhase.Editing, ComposePhase.Rejected)
}

@HiltViewModel
class ReplySessionViewModel @Inject constructor(
    private val client: NgaClient, private val accounts: AccountManager, private val saved: SavedStateHandle,
) : ViewModel() {
    private val owner = accounts.state.value
    private val _session = MutableStateFlow<ReplySession?>(null)
    val session = _session.asStateFlow()
    private var preparation: Job? = null
    private var upload: Job? = null
    private var generation = 0L
    private fun current() = accounts.state.value.let {
        it.ready && !it.busy && it.userId == owner.userId && it.revision == owner.revision
    }
    init {
        viewModelScope.launch { accounts.state.collect { if (!current()) clear() } }
    }
    private fun pendingKey(target: PublishTarget) = "${owner.userId}:${owner.revision}:$target"
    private fun pending() = saved.get<ArrayList<String>>("replyPendingTargets") ?: arrayListOf()
    private fun clear() { generation++; preparation?.cancel(); upload?.cancel(); _session.value = null }
    fun close() { if (_session.value?.phase != ComposePhase.Submitting && _session.value?.images?.uploading != true) clear() }
    fun open(target: PublishTarget, context: String) {
        if (!current() || _session.value != null) return
        require(target.action in setOf(PublishAction.REPLY, PublishAction.COMMENT, PublishAction.QUOTE))
        val interrupted = pendingKey(target) in pending()
        val uploadBlocked = pendingKey(target) in saved.get<ArrayList<String>>("replyPendingUploads").orEmpty()
        _session.value = ReplySession(target, context, phase = if (interrupted) ComposePhase.Unconfirmed else ComposePhase.Preparing,
            images = DraftImages(blocked = uploadBlocked, error = if (uploadBlocked) "上次图片上传结果不明，请勿重复上传。" else null))
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
    fun uploadImage(read: suspend () -> ImageUpload) {
        val session = _session.value ?: return
        if (!current() || session.comment || session.phase !in setOf(ComposePhase.Editing, ComposePhase.Rejected) ||
            session.images.uploading || session.images.blocked) return
        val token = generation
        _session.value = session.copy(images = session.images.copy(uploading = true, error = null))
        upload = viewModelScope.launch {
            var started = false
            try {
                val image = read()
                if (!current() || token != generation) return@launch
                saved["replyPendingUploads"] = ArrayList((saved.get<ArrayList<String>>("replyPendingUploads").orEmpty() + pendingKey(session.target)).distinct())
                started = true
                val result = client.uploadImage(session.target, image)
                if (!current() || token != generation) return@launch
                val attachment = result.attachment
                val blocked = result.outcome !is WriteResult.Rejected && attachment == null
                if (!blocked) saved["replyPendingUploads"] = ArrayList(saved.get<ArrayList<String>>("replyPendingUploads").orEmpty().filterNot { it == pendingKey(session.target) })
                _session.value = _session.value?.let { latest -> latest.copy(
                    input = if (attachment != null) insertImage(latest.input, attachment) else latest.input,
                    images = latest.images.copy(uploading = false, blocked = blocked, error = result.feedback(),
                        attachments = latest.images.attachments + listOfNotNull(attachment))) }
            } catch (error: CancellationException) { throw error }
            catch (error: NgaException) {
                if (current() && token == generation) {
                    saved["replyPendingUploads"] = ArrayList(saved.get<ArrayList<String>>("replyPendingUploads").orEmpty().filterNot { it == pendingKey(session.target) })
                    _session.value = _session.value?.copy(images = session.images.copy(error = "无法准备图片上传：${userMessage(error)}"))
                }
            }
            catch (_: Exception) {
                if (current() && token == generation) _session.value = _session.value?.copy(images = session.images.copy(blocked = started,
                    error = if (started) "图片上传结果不明，请勿重复上传。" else "无法读取图片，请选择 10MB 以内的 JPEG、PNG、GIF 或 WebP 图片。"))
            }
        }
    }

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
                        topicId = target.topicId, postId = target.postId, collectionId = target.collectionId,
                        attachments = session.images.attachments.filter { session.content.contains(it.bbcode) }))
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
