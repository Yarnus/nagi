package dev.nagi.app.viewmodel

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import dev.nagi.sdk.*

// Attachment checks are submission credentials; never include them in logs.
data class DraftImages(
    val attachments: List<UploadedAttachment> = emptyList(),
    val uploading: Boolean = false, val blocked: Boolean = false, val error: String? = null,
)

internal fun insertImage(value: TextFieldValue, attachment: UploadedAttachment): TextFieldValue {
    val start = value.selection.min
    val text = attachment.bbcode
    return TextFieldValue(value.text.replaceRange(start, value.selection.max, text), TextRange(start + text.length))
}

internal fun ImageUploadResult.feedback(): String? = when (val outcome = outcome) {
    is WriteResult.Confirmed -> if (attachment == null) "上传已接受，但未取得附件信息，请勿重复上传。" else null
    is WriteResult.Rejected -> "图片上传被拒绝：${userMessage(outcome.reason)}"
    is WriteResult.Unconfirmed -> "图片上传结果不明，请勿重复上传。正文已保留。"
}

internal fun SavedStateHandle.saveImages(images: DraftImages) {
    this["composerImageNames"] = ArrayList(images.attachments.map { it.name })
    this["composerImageChecks"] = ArrayList(images.attachments.map { it.check })
    this["composerImageUrls"] = ArrayList(images.attachments.map { it.url })
}

internal fun SavedStateHandle.restoreImages(): DraftImages {
    val names = get<ArrayList<String>>("composerImageNames").orEmpty()
    val checks = get<ArrayList<String>>("composerImageChecks").orEmpty()
    val urls = get<ArrayList<String>>("composerImageUrls").orEmpty()
    return DraftImages(names.indices.map { UploadedAttachment(names[it], checks[it], urls[it]) },
        blocked = get<Boolean>("composerUploadPending") == true,
        error = if (get<Boolean>("composerUploadPending") == true) "上次图片上传结果不明，请勿重复上传。" else null)
}
