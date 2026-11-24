package dev.nagi.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.CardGroupPosition
import dev.nagi.app.ui.components.ChromeIconButton
import dev.nagi.app.ui.components.NagiHeader
import dev.nagi.app.ui.components.NagiIconAction
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.NagiSearchTextField
import dev.nagi.app.ui.components.Notice
import dev.nagi.app.ui.components.SectionCardItem
import dev.nagi.app.ui.components.safeBottomPadding
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.ui.topic.formatTime
import dev.nagi.app.viewmodel.LibraryViewModel

@Composable
fun HistoryScreen(vm: LibraryViewModel, back: () -> Unit, read: (String) -> Unit) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val history by vm.history.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var confirmingClear by rememberSaveable { mutableStateOf(false) }
    var clearPositions by rememberSaveable { mutableStateOf(true) }
    val trimmed = query.trim()
    val visibleHistory = remember(history, trimmed) {
        if (trimmed.isEmpty()) history
        else history.filter {
            it.title.contains(trimmed, ignoreCase = true) || it.topicId.contains(trimmed, ignoreCase = true)
        }
    }

    if (confirmingClear) {
        dev.nagi.app.ui.components.NagiConfirmDialog(
            onDismissRequest = { confirmingClear = false },
            title = "清空浏览历史？",
            message = "清空后将移除当前身份的全部浏览历史记录。",
            confirmLabel = "清空",
            onConfirm = {
                confirmingClear = false
                query = ""
                vm.clearHistory(clearUnfavoritedPositions = clearPositions)
            },
            confirmStyle = CapsuleStyle.Destructive,
            dismissLabel = "取消",
            onDismiss = { confirmingClear = false },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Checkbox) { clearPositions = !clearPositions },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Checkbox(
                    checked = clearPositions,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.goldSolidFill,
                        checkmarkColor = colors.goldSolidText,
                        uncheckedColor = colors.textSecondary,
                    ),
                )
                Text(
                    text = "同时清除未收藏帖子的阅读进度",
                    style = typography.body,
                    color = colors.textPrimary,
                )
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        NagiHeader("浏览历史", "当前身份最近阅读的 100 个主题", back = back) {
            if (history.isNotEmpty()) {
                ChromeIconButton(NagiIcons.Trash, "清空浏览历史", onClick = { confirmingClear = true })
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize(),
            ) {
                if (history.isNotEmpty() || query.isNotEmpty()) {
                    NagiSearchTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "筛选历史标题或主题 ID",
                        clearDescription = "清除筛选",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.lg, vertical = Space.sm),
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(top = Space.lg, bottom = Space.lg + safeBottomPadding()),
                ) {
            itemsIndexed(visibleHistory, key = { _, item -> item.topicId }) { index, item ->
                SectionCardItem(CardGroupPosition.at(index, visibleHistory.size)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { read(item.topicId) }
                            .semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction("移除记录") {
                                        vm.removeHistory(item.topicId)
                                        true
                                    },
                                )
                            }
                            .padding(start = Space.lg, end = Space.sm, top = Space.sm, bottom = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = Space.xs),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = item.title,
                                style = typography.topicTitle,
                                color = colors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = formatTime(item.viewedAt / 1000L),
                                style = typography.metadata,
                                color = colors.textSecondary,
                            )
                        }
                        NagiIconAction(
                            icon = NagiIcons.Trash,
                            description = "移除",
                            onClick = { vm.removeHistory(item.topicId) },
                            tint = colors.textSecondary,
                        )
                    }
                }
            }
            if (history.isEmpty()) {
                item(key = "empty") {
                    Notice("阅读主题后会在这里留下记录；访客与各账号的历史独立保存")
                }
            } else if (visibleHistory.isEmpty()) {
                item(key = "empty-filter") {
                    Notice("没有匹配的浏览历史")
                }
            }
                }
            }
        }
    }
}
