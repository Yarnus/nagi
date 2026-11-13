package dev.nagi.app.ui.post

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.FlatCard
import dev.nagi.app.ui.components.GoldTagBadge
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.SegmentedCapsuleControl
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.viewmodel.ComposePhase
import dev.nagi.app.viewmodel.ComposerState
import dev.nagi.sdk.PublishAction

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComposerScreen(
    state: ComposerState,
    preferences: ReadingPreferences,
    edit: (String, Int) -> Unit,
    send: () -> Unit,
    retry: () -> Unit,
    close: () -> Unit,
    details: (String, String) -> Unit = { _, _ -> },
    editValue: ((TextFieldValue) -> Unit)? = null,
    togglePreview: (() -> Unit)? = null,
    initialRecipients: String = "",
    uploadImage: ((suspend () -> dev.nagi.sdk.ImageUpload) -> Unit)? = null,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val label = when {
        state.privateMessage -> if (state.newMessage) "新建私信" else "回复私信"
        state.target?.action == PublishAction.MODIFY -> if (state.target.postId == "0") "编辑帖子" else "编辑回复"
        else -> "发布帖子"
    }
    val shortForm = state.privateMessage && !state.newMessage
    var localPreview by remember { mutableStateOf(false) }
    val preview = if (togglePreview != null) state.preview else localPreview
    var showFormatChips by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var field by remember {
        mutableStateOf(TextFieldValue(state.content, TextRange(state.selectionStart, state.selection)))
    }
    LaunchedEffect(state.content, state.selection, state.selectionStart, state.composition) {
        if (
            field.text != state.content ||
            (editValue != null &&
                (field.selection != TextRange(state.selectionStart, state.selection) || field.composition != state.composition))
        ) {
            field = TextFieldValue(
                text = state.content,
                selection = TextRange(
                    state.selectionStart.coerceIn(0, state.content.length),
                    state.selection.coerceIn(0, state.content.length),
                ),
                composition = state.composition,
            )
        }
    }
    val update: (TextFieldValue) -> Unit = { value ->
        field = value
        if (editValue != null) editValue(value) else edit(value.text, value.selection.end)
    }
    val setPreviewMode: (Boolean) -> Unit = { target ->
        if (preview != target) {
            if (togglePreview != null) togglePreview() else localPreview = target
        }
    }
    val dismiss = {
        if (state.phase != ComposePhase.Submitting && !state.images.uploading) {
            if (
                state.phase != ComposePhase.Confirmed &&
                (state.content.isNotEmpty() || state.title.isNotEmpty() ||
                    state.recipients != initialRecipients || state.phase == ComposePhase.Unconfirmed)
            ) {
                confirmClose = true
            } else {
                close()
            }
        }
    }
    val clipboard = LocalClipboardManager.current
    val editable = state.phase in setOf(ComposePhase.Editing, ComposePhase.Rejected)

    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .padding(top = Space.xs, bottom = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                IconButton(
                    onClick = dismiss,
                    enabled = state.phase != ComposePhase.Submitting && !state.images.uploading,
                    modifier = Modifier.size(48.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = if (state.privateMessage) colors.frostedChrome else colors.destructiveContainer,
                        border = BorderStroke(1.dp, if (state.privateMessage) colors.cardBorder else colors.destructive.copy(alpha = 0.25f)),
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.privateMessage) NagiIcons.Back else NagiIcons.Trash,
                                contentDescription = "关闭$label",
                                modifier = Modifier.size(18.dp),
                                tint = (if (state.privateMessage) colors.textPrimary else colors.destructive).copy(
                                    alpha = if (state.phase != ComposePhase.Submitting) 1f else 0.4f,
                                ),
                            )
                        }
                    }
                }
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = typography.appBarTitle,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                CapsuleButton(
                    text = if (state.target?.action == PublishAction.MODIFY) {
                        if (state.phase == ComposePhase.Submitting) "保存中…" else "保存修改"
                    } else if (state.phase == ComposePhase.Submitting) "发送中…" else "发送",
                    onClick = send,
                    enabled = state.canSend,
                    style = CapsuleStyle.SolidGold,
                )
            }

            if (!shortForm) {
                if (state.context.isNotBlank() && !(state.privateMessage && state.newMessage)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        GoldTagBadge(text = state.context)
                    }
                }
                FlatCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = NagiThemeTokens.shapes.topicCard,
                ) {
                    Column {
                        if (state.newMessage) {
                            EditorDetail(
                                value = state.recipients,
                                placeholder = "完整用户名（空格分隔）",
                                label = "收件人",
                                phase = state.phase,
                            ) { details(state.title, it) }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = Space.lg),
                                thickness = 1.dp,
                                color = colors.cardBorder,
                            )
                        }
                        EditorDetail(
                            value = state.title,
                            placeholder = if (state.privateMessage) "填写私信标题" else "标题",
                            label = if (state.privateMessage) "标题" else null,
                            phase = state.phase,
                        ) { details(it, state.recipients) }
                    }
                }
            }

            when (state.phase) {
                ComposePhase.Preparing -> {
                    if (state.error == null) {
                        LoadingLine()
                    } else {
                        ComposerNoticeCard(
                            message = state.error,
                            actionLabel = "重试",
                            onAction = retry,
                            tone = ComposerNoticeTone.Error,
                        )
                    }
                }
                ComposePhase.Submitting -> LoadingLine()
                ComposePhase.Unconfirmed -> {
                    ComposerNoticeCard(
                        message = state.error ?: "暂未收到提交确认，可${if (state.privateMessage) "查看会话" else "查看帖子"}核对，请勿重复发送。",
                        actionLabel = "复制内容",
                        onAction = { clipboard.setText(AnnotatedString(state.content)) },
                        tone = ComposerNoticeTone.Neutral,
                    )
                }
                ComposePhase.Confirmed -> {
                    ComposerNoticeCard(
                        message = if (state.target?.action == PublishAction.MODIFY) "修改已确认" else "发布成功",
                        actionLabel = "完成",
                        onAction = close,
                        tone = ComposerNoticeTone.Success,
                    )
                }
                else -> state.error?.let { err ->
                    Text(
                        text = err,
                        style = typography.metadata,
                        color = colors.destructive,
                    )
                }
            }

            FlatCard(
                modifier = Modifier
                    .weight(1f, fill = !shortForm)
                    .fillMaxWidth(),
                shape = NagiThemeTokens.shapes.topicCard,
            ) {
                ComposerInput(
                    value = field,
                    edit = update,
                    placeholder = if (state.privateMessage) "写下私信…" else "写下帖子正文…",
                    readOnly = !editable,
                    preview = preview,
                    previewContent = state.content,
                    preferences = preferences,
                    modifier = if (shortForm) Modifier.heightIn(max = 260.dp) else Modifier.fillMaxSize(),
                )
            }

            state.images.error?.let { Text(it, style = typography.metadata, color = colors.destructive) }
            if (!preview) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    if (uploadImage != null && state.target?.action == PublishAction.NEW_TOPIC) ImageUploadButton(
                        enabled = editable && !state.images.blocked, busy = state.images.uploading,
                        scopeKey = state.target.toString(), upload = uploadImage,
                    )
                    SmilePickerButton(value = field, edit = update, enabled = editable)
                    if (showFormatChips) {
                        CapsuleButton(
                            text = "加粗",
                            onClick = { update(boldSelection(field)) },
                            enabled = editable,
                            style = CapsuleStyle.Neutral,
                        )
                        CapsuleButton(
                            text = "引用",
                            onClick = { update(wrapBbcodeSelection(field, "[quote]", "[/quote]")) },
                            enabled = editable,
                            style = CapsuleStyle.Neutral,
                        )
                        CapsuleButton(
                            text = "折叠",
                            onClick = { update(wrapBbcodeSelection(field, "[collapse]", "[/collapse]")) },
                            enabled = editable,
                            style = CapsuleStyle.Neutral,
                        )
                        CapsuleButton(
                            text = "代码",
                            onClick = { update(wrapBbcodeSelection(field, "[code]", "[/code]")) },
                            enabled = editable,
                            style = CapsuleStyle.Neutral,
                        )
                    }
                }
            }

            val selectedSegment = when {
                preview -> 2
                showFormatChips -> 1
                else -> 0
            }
            SegmentedCapsuleControl(
                options = listOf(
                    if (preview) "继续编辑" else "纯文本",
                    "排版",
                    "预览",
                ),
                selectedIndex = selectedSegment,
                onSelect = { idx ->
                    when (idx) {
                        0 -> {
                            showFormatChips = false
                            setPreviewMode(false)
                        }
                        1 -> {
                            showFormatChips = true
                            setPreviewMode(false)
                        }
                        2 -> {
                            showFormatChips = false
                            setPreviewMode(true)
                        }
                    }
                },
            )
        }
    }

    if (shortForm) {
        EditorPopup(dismiss, body)
    } else {
        Dialog(
            onDismissRequest = dismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 760.dp)
                    .fillMaxWidth(),
                color = colors.groupedCanvas,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier
                        .safeDrawingPadding()
                        .imePadding()
                        .fillMaxSize(),
                ) {
                    body()
                }
            }
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            shape = NagiThemeTokens.shapes.topicCard,
            containerColor = colors.cardSurface,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = if (state.privateMessage) "放弃未发送的私信？" else "暂时离开编辑器？",
                    style = typography.sectionTitle,
                    color = colors.textPrimary,
                )
            },
            text = {
                Text(
                    text = if (state.privateMessage) "已输入的内容不会保存。" else "内容会在当前页面保留。",
                    style = typography.body,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                CapsuleButton(
                    text = if (state.privateMessage) "放弃并返回" else "保留并返回",
                    onClick = {
                        confirmClose = false
                        close()
                    },
                    style = if (state.privateMessage) CapsuleStyle.Destructive else CapsuleStyle.SelectedGold,
                )
            },
            dismissButton = {
                CapsuleButton(
                    text = "继续编辑",
                    onClick = { confirmClose = false },
                    style = CapsuleStyle.Neutral,
                )
            },
        )
    }
}

private enum class ComposerNoticeTone { Neutral, Error, Success }

@Composable
private fun ComposerNoticeCard(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    tone: ComposerNoticeTone,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    FlatCard(
        modifier = Modifier.fillMaxWidth(),
        shape = NagiThemeTokens.shapes.topicCard,
        containerColor = when (tone) {
            ComposerNoticeTone.Neutral -> colors.insetContainer
            ComposerNoticeTone.Error -> colors.destructiveContainer
            ComposerNoticeTone.Success -> colors.goldContainer
        },
        borderColor = when (tone) {
            ComposerNoticeTone.Neutral -> colors.cardBorder
            ComposerNoticeTone.Error -> colors.destructive.copy(alpha = 0.25f)
            ComposerNoticeTone.Success -> colors.goldAccent.copy(alpha = 0.3f)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.md, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = typography.metadata,
                color = when (tone) {
                    ComposerNoticeTone.Neutral -> colors.textSecondary
                    ComposerNoticeTone.Error -> colors.destructive
                    ComposerNoticeTone.Success -> colors.goldText
                },
            )
            CapsuleButton(
                text = actionLabel,
                onClick = onAction,
                style = when (tone) {
                    ComposerNoticeTone.Neutral -> CapsuleStyle.Neutral
                    ComposerNoticeTone.Error -> CapsuleStyle.Destructive
                    ComposerNoticeTone.Success -> CapsuleStyle.SelectedGold
                },
            )
        }
    }
}

@Composable
private fun EditorDetail(
    value: String,
    placeholder: String,
    phase: ComposePhase,
    label: String? = null,
    edit: (String) -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    TextField(
        value = value,
        onValueChange = edit,
        modifier = Modifier.fillMaxWidth(),
        label = label?.let { title -> { Text(title, style = typography.metadata, color = colors.textSecondary) } },
        placeholder = {
            Text(
                text = placeholder,
                style = typography.topicTitle,
                color = colors.textSecondary,
            )
        },
        textStyle = typography.topicTitle.copy(color = colors.textPrimary),
        maxLines = 2,
        enabled = phase in setOf(ComposePhase.Editing, ComposePhase.Rejected),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.cardSurface,
            unfocusedContainerColor = colors.cardSurface,
            disabledContainerColor = colors.cardSurface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = colors.goldAccent,
        ),
    )
}
