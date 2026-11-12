package dev.nagi.app.ui.post

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.ChromeIconButton
import dev.nagi.app.ui.components.FlatCard
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.viewmodel.ComposePhase
import dev.nagi.app.viewmodel.ReplySession
import dev.nagi.sdk.ContentParser
import dev.nagi.sdk.PublishAction

@OptIn(ExperimentalLayoutApi::class)
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
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    var discard by remember { mutableStateOf(false) }
    val dismiss = {
        if (session.phase != ComposePhase.Submitting) {
            if (!session.comment && session.input.text.isNotEmpty()) {
                discard = true
            } else {
                close()
            }
        }
    }
    val clipboard = LocalClipboardManager.current
    val editable = session.phase in setOf(ComposePhase.Editing, ComposePhase.Rejected)

    EditorPopup(dismiss) {
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
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Text(
                    text = session.context,
                    modifier = Modifier.weight(1f),
                    style = typography.sectionTitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ChromeIconButton(
                    icon = NagiIcons.Close,
                    description = "关闭${if (session.comment) "贴条评论" else "回复"}",
                    onClick = dismiss,
                    enabled = session.phase != ComposePhase.Submitting,
                )
            }

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
                                .weight(1f, fill = false)
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
                                onClick = { clipboard.setText(AnnotatedString(session.content)) },
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
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
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
                        modifier = Modifier.heightIn(max = 260.dp),
                        minLines = if (session.comment) 3 else 5,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        SmilePickerButton(value = session.input, edit = edit, enabled = !session.preview && editable)
                        if (!session.comment) {
                            CapsuleButton(
                                text = "加粗",
                                onClick = { edit(boldSelection(session.input)) },
                                enabled = !session.preview && editable,
                                style = CapsuleStyle.Neutral,
                            )
                            CapsuleButton(
                                text = if (session.preview) "编辑" else "预览",
                                onClick = preview,
                                style = if (session.preview) CapsuleStyle.SelectedGold else CapsuleStyle.Neutral,
                            )
                        }
                    }
                    CapsuleButton(
                        text = if (session.phase == ComposePhase.Submitting) "发送中…" else "发送",
                        onClick = send,
                        enabled = session.canSend,
                        style = CapsuleStyle.SolidGold,
                    )
                }
            }
        }
    }

    if (discard) {
        AlertDialog(
            onDismissRequest = { discard = false },
            shape = NagiThemeTokens.shapes.topicCard,
            containerColor = colors.cardSurface,
            tonalElevation = 0.dp,
            title = { Text("放弃这次回复？", style = typography.sectionTitle, color = colors.textPrimary) },
            text = { Text("关闭后将清除本次输入。", style = typography.body, color = colors.textSecondary) },
            confirmButton = {
                CapsuleButton(
                    text = "放弃回复",
                    onClick = {
                        discard = false
                        close()
                    },
                    style = CapsuleStyle.Destructive,
                )
            },
            dismissButton = {
                CapsuleButton(
                    text = "继续编辑",
                    onClick = { discard = false },
                    style = CapsuleStyle.Neutral,
                )
            },
        )
    }
}
