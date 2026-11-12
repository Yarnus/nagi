package dev.nagi.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import dev.nagi.app.ui.components.*
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.ui.topic.formatTime
import dev.nagi.app.viewmodel.NotificationsState
import dev.nagi.sdk.CommunityItem

@Composable
fun NotificationsScreen(
    state: NotificationsState, back: () -> Unit, refresh: () -> Unit,
    login: () -> Unit, open: (CommunityItem) -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    Column(Modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 760.dp)) {
        NagiHeader("通知", state.unreadCount?.let { "$it 条未读" } ?: "社区通知", back = back) {
            ChromeIconButton(NagiIcons.Refresh, "刷新通知", onClick = refresh)
        }
        if (state.userId == null) {
            Notice("请先登录 NGA", login)
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = Space.md)) {
                if (state.loading) item { LoadingLine() }
                state.error?.let { error -> item { Notice(error, refresh) } }
                if (state.loaded && !state.loading && state.items.isEmpty()) item { Notice("暂无通知") }
                items(state.items, key = { it.id }) { item ->
                    FlatCard(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.xs)) {
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = item.topicId.toLongOrNull()?.let { it > 0 } == true || item.privateMessage) { open(item) }
                                .padding(Space.lg),
                            verticalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            EdgeAlignedMetaRow(
                                leading = { Text(item.actor.ifBlank { "NGA" }, style = typography.bodyText, color = colors.textPrimary) },
                                trailing = { if (item.createdAt > 0) Text(formatTime(item.createdAt), style = typography.metadata, color = colors.textSecondary) },
                            )
                            if (item.unread) Text("未读", style = typography.metadata, color = colors.destructive)
                            if (item.title.isNotBlank()) Text(item.title, style = typography.topicTitle, color = colors.textPrimary)
                            if (item.summary.isNotBlank()) Text(item.summary, style = typography.body, color = colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
