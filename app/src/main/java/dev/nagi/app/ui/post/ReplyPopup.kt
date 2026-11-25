package dev.nagi.app.ui.post

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.ChromeIconButton
import dev.nagi.app.ui.components.FlatCard
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.NagiIconAction
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.viewmodel.ComposePhase
import dev.nagi.app.viewmodel.ReplySession
import dev.nagi.sdk.ContentParser
import dev.nagi.sdk.PublishAction

@Composable
internal fun ReplyPopup(
    session: ReplySession,
    preferences: ReadingPreferences,
    edit: (TextFieldValue) -> Unit,
    send: () -> Unit,
    retry: () -> Unit,
    preview: () -> Unit,
    close: () -> Unit,
    expandContext: () -> Unit = {},
    uploadImage: ((suspend () -> dev.nagi.sdk.ImageUpload) -> Unit)? = null,
    onAnonymousChange: ((Boolean) -> Unit)? = null,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    var discard by remember { mutableStateOf(false) }
    var dockPanel by remember { mutableStateOf(EditorDockPanel.NONE) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val editable = session.phase in setOf(ComposePhase.Editing, ComposePhase.Rejected)
    LaunchedEffect(editable) {
        if (!editable) dockPanel = EditorDockPanel.NONE
    }
    LaunchedEffect(session.preview) {
        if (session.preview && dockPanel == EditorDockPanel.COLOR) {
            dockPanel = EditorDockPanel.NONE
        }
    }
    val dismiss = {
        if (session.phase != ComposePhase.Submitting && !session.images.uploading) {
            if (!session.comment && session.input.text.isNotEmpty()) {
                discard = true
            } else {
                close()
            }
        }
    }
    val returnToKeyboard = {
        dockPanel = EditorDockPanel.NONE
        if (!session.preview) {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }
    val context = LocalContext.current

    EditorPopup(dismiss) {
        BackHandler(enabled = dockPanel != EditorDockPanel.NONE) {
            dockPanel = EditorDockPanel.NONE
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .padding(top = Space.md, bottom = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Text(
                    text = session.context,
                    modifier = Modifier.weight(1f),
                    style = typography.sectionTitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (session.phase != ComposePhase.Preparing) {
                    CapsuleButton(
                        text = if (session.preview) "编辑" else "预览",
                        icon = if (session.preview) NagiIcons.Compose else NagiIcons.Eye,
                        onClick = preview,
                        style = if (session.preview) CapsuleStyle.SelectedGold else CapsuleStyle.Neutral,
                        visualHeight = 36.dp,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        contentDescription = if (session.preview) "编辑" else "预览",
                    )
                }
                ChromeIconButton(
                    icon = NagiIcons.Close,
                    description = "关闭${if (session.comment) "贴条评论" else "回复"}",
                    onClick = dismiss,
                    enabled = session.phase != ComposePhase.Submitting && !session.images.uploading,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                if (session.prefill.isNotBlank()) {
                    FlatCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        containerColor = colors.insetContainer,
                        borderColor = colors.cardBorder,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Space.md),
                            verticalArrangement = Arrangement.spacedBy(Space.xs),
                        ) {
                            Column(
                                modifier = Modifier
                                    .heightIn(max = if (session.expandedContext) 220.dp else 76.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                SelectionContainer {
                                    ContentBlocks(
                                        blocks = remember(session.prefill) { ContentParser.parse(session.prefill) },
                                        preferences = preferences,
                                        onImage = {},
                                    )
                                }
                            }
                            if (session.target.action == PublishAction.QUOTE) {
                                CapsuleButton(
                                    text = if (session.expandedContext) "收起引用" else "查看完整引用",
                                    onClick = expandContext,
                                    icon = if (session.expandedContext) NagiIcons.ChevronUp else NagiIcons.ChevronDown,
                                    style = CapsuleStyle.Neutral,
                                )
                            }
                        }
                    }
                }

                when (session.phase) {
                    ComposePhase.Preparing -> {
                        if (session.error == null) {
                            LoadingLine()
                        } else {
                            FlatCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = NagiThemeTokens.shapes.topicCard,
                                containerColor = colors.destructiveContainer,
                                borderColor = colors.destructive.copy(alpha = 0.25f),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Space.md, vertical = Space.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                                ) {
                                    Text(
                                        text = session.error,
                                        modifier = Modifier.weight(1f),
                                        style = typography.metadata,
                                        color = colors.destructive,
                                    )
                                    CapsuleButton(
                                        text = "重新准备",
                                        onClick = retry,
                                        icon = NagiIcons.Refresh,
                                        style = CapsuleStyle.Destructive,
                                    )
                                }
                            }
                        }
                    }
                    ComposePhase.Submitting -> LoadingLine()
                    ComposePhase.Unconfirmed -> {
                        FlatCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = NagiThemeTokens.shapes.topicCard,
                            containerColor = colors.insetContainer,
                            borderColor = colors.cardBorder,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Space.md, vertical = Space.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            ) {
                                Text(
                                    text = session.error ?: "暂未收到提交确认，可查看帖子核对，请勿重复发送。",
                                    modifier = Modifier.weight(1f),
                                    style = typography.metadata,
                                    color = colors.textSecondary,
                                )
                                CapsuleButton(
                                    text = "复制内容",
                                    onClick = {
                                        context.getSystemService(ClipboardManager::class.java)
                                            ?.setPrimaryClip(ClipData.newPlainText("回复内容", session.content))
                                    },
                                    icon = NagiIcons.Copy,
                                    style = CapsuleStyle.Neutral,
                                )
                            }
                        }
                    }
                    else -> session.error?.let { err ->
                        Text(
                            text = err,
                            style = typography.metadata,
                            color = colors.destructive,
                        )
                    }
                }

                if (session.phase != ComposePhase.Preparing) {
                    FlatCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = NagiThemeTokens.shapes.topicCard,
                    ) {
                        ComposerInput(
                            value = session.input,
                            edit = edit,
                            placeholder = if (session.comment) "写下贴条评论…" else "写下你的回复…",
                            readOnly = !editable,
                            preview = session.preview,
                            previewContent = session.content,
                            preferences = preferences,
                            modifier = Modifier.heightIn(max = if (dockPanel != EditorDockPanel.NONE) 180.dp else 260.dp),
                            minLines = if (session.comment) 3 else 4,
                            focusRequester = focusRequester,
                            onInputTap = { dockPanel = EditorDockPanel.NONE },
                            onTogglePreview = preview,
                            anonymous = session.anonymous,
                            onAnonymousChange = onAnonymousChange,
                        )
                    }
                    session.images.error?.let { Text(it, style = typography.metadata, color = colors.destructive) }
                }
            }

            if (session.phase != ComposePhase.Preparing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (uploadImage != null && !session.comment) ImageUploadButton(
                            enabled = !session.preview && editable && !session.images.blocked,
                            busy = session.images.uploading,
                            scopeKey = session.target.toString(),
                            upload = uploadImage,
                            iconOnly = true,
                            iconSize = 22.dp,
                            visualSize = 40.dp,
                        )
                        SmilePickerButton(
                            open = dockPanel == EditorDockPanel.SMILE,
                            enabled = editable,
                            iconOnly = true,
                            iconSize = 22.dp,
                            visualSize = 40.dp,
                            onClick = {
                                if (dockPanel == EditorDockPanel.SMILE) {
                                    returnToKeyboard()
                                } else {
                                    keyboard?.hide()
                                    dockPanel = EditorDockPanel.SMILE
                                }
                            },
                        )
                        NagiIconAction(
                            icon = NagiIcons.Bold,
                            description = "加粗",
                            onClick = { edit(boldSelection(session.input)) },
                            enabled = !session.preview && editable,
                            iconSize = 22.dp,
                            visualSize = 40.dp,
                            tint = colors.textSecondary,
                        )
                        NagiIconAction(
                            icon = NagiIcons.Strikethrough,
                            description = "删除线",
                            onClick = { edit(strikeSelection(session.input)) },
                            enabled = !session.preview && editable,
                            iconSize = 22.dp,
                            visualSize = 40.dp,
                            tint = colors.textSecondary,
                        )
                        NagiIconAction(
                            icon = NagiIcons.Palette,
                            description = "字体颜色",
                            onClick = {
                                if (dockPanel == EditorDockPanel.COLOR) {
                                    returnToKeyboard()
                                } else {
                                    keyboard?.hide()
                                    dockPanel = EditorDockPanel.COLOR
                                }
                            },
                            enabled = !session.preview && editable,
                            selected = dockPanel == EditorDockPanel.COLOR,
                            iconSize = 22.dp,
                            visualSize = 40.dp,
                            tint = if (dockPanel == EditorDockPanel.COLOR) colors.goldAccent else colors.textSecondary,
                        )
                    }
                    CapsuleButton(
                        text = when {
                            session.phase == ComposePhase.Submitting -> "发送中…"
                            session.anonymous -> "匿名发送"
                            else -> "发送"
                        },
                        onClick = send,
                        icon = NagiIcons.Send,
                        enabled = session.canSend,
                        style = CapsuleStyle.WarmGold,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    )
                }

                if (dockPanel != EditorDockPanel.NONE && editable) {
                    InlineEditorDock(
                        panel = dockPanel,
                        onReturnToKeyboard = returnToKeyboard,
                        onSelectSmile = { smile -> if (editable) edit(insertSmile(session.input, smile)) },
                        onSelectColor = { colorName -> if (!session.preview && editable) edit(colorSelection(session.input, colorName)) },
                    )
                }
            }
        }
    }

    if (discard) {
        dev.nagi.app.ui.components.NagiConfirmDialog(
            onDismissRequest = { discard = false },
            title = "放弃这次回复？",
            message = "关闭后将清除本次输入。",
            confirmLabel = "放弃回复",
            onConfirm = {
                discard = false
                close()
            },
            confirmStyle = CapsuleStyle.Destructive,
            dismissLabel = "继续编辑",
            onDismiss = { discard = false },
        )
    }
}
