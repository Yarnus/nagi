package dev.nagi.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
internal fun UserTagEditor(userId: String) {
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
    CapsuleButton(if (tag.isEmpty()) "添加标记" else "编辑标记", {
        draft = tag; attempted = false; editing = true
    }, enabled = !state.saving, style = CapsuleStyle.Neutral)
    if (editing) {
        val valid = validUserTag(draft.trim())
        Dialog(onDismissRequest = { if (!state.saving) editing = false }) {
            FlatCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(if (tag.isEmpty()) "添加标记" else "编辑标记", style = NagiThemeTokens.typography.sectionTitle,
                        color = NagiThemeTokens.colors.textPrimary)
                    OutlinedTextField(draft, { draft = it }, label = { Text("用户标记") }, singleLine = true,
                        enabled = !state.saving, isError = !valid, modifier = Modifier.fillMaxWidth())
                    if (!valid) Text("最多 24 个字符，不能包含换行或控制字符", color = NagiThemeTokens.colors.destructive)
                    if (attempted) state.error?.let { Text(it, color = NagiThemeTokens.colors.destructive) }
                    if (tag.isNotEmpty()) CapsuleButton("删除标记", {
                        attempted = true
                        access.save(userId, "") { editing = false }
                    }, enabled = !state.saving, style = CapsuleStyle.Neutral)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.End)) {
                        CapsuleButton("取消", { editing = false }, enabled = !state.saving, style = CapsuleStyle.Neutral)
                        CapsuleButton(if (state.saving) "保存中…" else "保存", {
                            attempted = true
                            access.save(userId, draft) { editing = false }
                        }, enabled = valid && draft.trim() != tag && !state.saving, style = CapsuleStyle.Neutral)
                    }
                }
            }
        }
    }
}
