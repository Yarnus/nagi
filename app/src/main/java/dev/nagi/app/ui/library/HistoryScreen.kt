package dev.nagi.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nagi.app.ui.components.CardGroupPosition
import dev.nagi.app.ui.components.NagiHeader
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.Notice
import dev.nagi.app.ui.components.SectionCardItem
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.viewmodel.LibraryViewModel

@Composable
fun HistoryScreen(vm: LibraryViewModel, back: () -> Unit, read: (String) -> Unit) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val history by vm.history.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        NagiHeader("浏览历史", "当前身份最近阅读的 100 个主题", back = back)
        LazyColumn(contentPadding = PaddingValues(vertical = Space.lg)) {
            itemsIndexed(history, key = { _, item -> item.topicId }) { index, item ->
                SectionCardItem(CardGroupPosition.at(index, history.size)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { read(item.topicId) }
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
                                text = "继续阅读",
                                style = typography.metadata,
                                color = colors.goldText,
                            )
                        }
                        Icon(
                            imageVector = NagiIcons.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colors.textSecondary,
                        )
                    }
                }
            }
            if (history.isEmpty()) {
                item {
                    Notice("阅读主题后会在这里留下记录；访客与各账号的历史独立保存")
                }
            }
        }
    }
}
