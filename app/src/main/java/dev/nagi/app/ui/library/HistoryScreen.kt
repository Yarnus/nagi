package dev.nagi.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
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
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.Notice
import dev.nagi.app.ui.components.SectionCardItem
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
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            shape = NagiThemeTokens.shapes.topicCard,
            containerColor = colors.cardSurface,
            tonalElevation = 0.dp,
            title = { Text("清空浏览历史？", style = typography.sectionTitle, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(
                        text = "清空后将移除当前身份的全部浏览历史记录。",
                        style = typography.body,
                        color = colors.textSecondary,
                    )
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
            },
            confirmButton = {
                CapsuleButton(
                    text = "清空",
                    onClick = {
                        confirmingClear = false
                        query = ""
                        vm.clearHistory(clearUnfavoritedPositions = clearPositions)
                    },
                    style = CapsuleStyle.Destructive,
                )
            },
            dismissButton = {
                CapsuleButton(
                    text = "取消",
                    onClick = { confirmingClear = false },
                    style = CapsuleStyle.Neutral,
                )
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        NagiHeader("浏览历史", "当前身份最近阅读的 100 个主题", back = back) {
            if (history.isNotEmpty()) {
                ChromeIconButton(NagiIcons.Trash, "清空浏览历史", onClick = { confirmingClear = true })
            }
        }
        if (history.isNotEmpty() || query.isNotEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg, vertical = Space.sm),
                singleLine = true,
                shape = NagiThemeTokens.shapes.topicCard,
                textStyle = typography.body.copy(color = colors.textPrimary),
                placeholder = { Text("筛选历史标题或主题 ID", style = typography.body, color = colors.textSecondary) },
                leadingIcon = { Icon(NagiIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textSecondary) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(48.dp)) {
                            Icon(NagiIcons.ClearCircle, contentDescription = "清除筛选", modifier = Modifier.size(18.dp), tint = colors.textSecondary)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.cardSurface,
                    unfocusedContainerColor = colors.cardSurface,
                    focusedBorderColor = colors.goldAccent,
                    unfocusedBorderColor = colors.cardBorder,
                ),
            )
        }
        LazyColumn(contentPadding = PaddingValues(vertical = Space.lg)) {
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
                            .padding(horizontal = Space.lg, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
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
                        CapsuleButton(
                            text = "移除",
                            onClick = { vm.removeHistory(item.topicId) },
                            style = CapsuleStyle.Neutral,
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
