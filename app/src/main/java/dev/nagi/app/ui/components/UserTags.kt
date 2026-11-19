package dev.nagi.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.nagi.app.data.validUserTag
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.viewmodel.UserTagsState

class UserTagAccess(
    val state: UserTagsState = UserTagsState(),
    val save: (String, String, () -> Unit) -> Unit = { _, _, _ -> },
)

val LocalUserTags = staticCompositionLocalOf { UserTagAccess() }

@Composable
internal fun UserTagBadge(userId: String) {
    if (!isPublicUserId(userId)) return
    val text = LocalUserTags.current.state.tags[userId] ?: return
    val colors = NagiThemeTokens.colors
    Surface(color = colors.insetContainer, shape = CircleShape,
        modifier = Modifier.semantics { contentDescription = "用户标记：$text" }) {
        Text(text, style = NagiThemeTokens.typography.tagBadge, color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp))
    }
}

@Composable
internal fun UserTagEditor(userId: String, iconOnly: Boolean = false) {
    if (!isPublicUserId(userId)) return
    val access = LocalUserTags.current
    val state = access.state
    if (!state.loaded) {
        state.error?.let { Text("无法读取用户标记", color = NagiThemeTokens.colors.destructive) }
        return
    }
    val tag = state.tags[userId].orEmpty()
    var editing by rememberSaveable(userId, state.revision) { mutableStateOf(false) }
    var draft by rememberSaveable(userId, state.revision) { mutableStateOf("") }
    var attempted by remember(userId, state.revision) { mutableStateOf(false) }
    val actionLabel = if (tag.isEmpty()) "添加标记" else "编辑标记"
    if (iconOnly) {
        val colors = NagiThemeTokens.colors
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(
                    enabled = !state.saving,
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = null,
                    onClickLabel = actionLabel,
                    onClick = { draft = tag; attempted = false; editing = true },
                )
                .semantics { contentDescription = actionLabel },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .nagiPressFeedback(
                        interactionSource = interactionSource,
                        enabled = !state.saving,
                        pressedScale = 0.92f,
                        shape = CircleShape,
                    ),
                shape = CircleShape,
                color = colors.insetContainer,
                border = BorderStroke(1.dp, colors.cardBorder),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = NagiIcons.Tag,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.textSecondary.copy(alpha = if (!state.saving) 1f else 0.38f),
                    )
                }
            }
        }
    } else {
        CapsuleButton(
            text = actionLabel,
            onClick = {
                draft = tag; attempted = false; editing = true
            },
            icon = NagiIcons.Tag,
            enabled = !state.saving,
            style = CapsuleStyle.Neutral,
        )
    }
    if (editing) {
        val valid = validUserTag(draft.trim())
        NagiDialog(onDismissRequest = { if (!state.saving) editing = false }) {
            Text(
                if (tag.isEmpty()) "添加标记" else "编辑标记",
                style = NagiThemeTokens.typography.sectionTitle,
                color = NagiThemeTokens.colors.textPrimary,
            )
            OutlinedTextField(
                draft, { draft = it }, label = { Text("用户标记") }, singleLine = true,
                enabled = !state.saving, isError = !valid, modifier = Modifier.fillMaxWidth(),
            )
            if (!valid) Text("最多 24 个字符，不能包含换行或控制字符", color = NagiThemeTokens.colors.destructive)
            if (attempted) state.error?.let { Text(it, color = NagiThemeTokens.colors.destructive) }
            if (tag.isNotEmpty()) {
                CapsuleButton(
                    text = "删除标记",
                    onClick = {
                        attempted = true
                        access.save(userId, "") { editing = false }
                    },
                    icon = NagiIcons.Trash,
                    enabled = !state.saving,
                    style = CapsuleStyle.Destructive,
                    fillWidth = true,
                )
            }
            NagiDialogActions(
                confirmLabel = if (state.saving) "保存中…" else "保存",
                onConfirm = {
                    attempted = true
                    access.save(userId, draft) { editing = false }
                },
                confirmEnabled = valid && draft.trim() != tag && !state.saving,
                confirmStyle = CapsuleStyle.SelectedGold,
                dismissLabel = "取消",
                onDismiss = { editing = false },
                dismissEnabled = !state.saving,
                dismissStyle = CapsuleStyle.Neutral,
            )
        }
    }
}
