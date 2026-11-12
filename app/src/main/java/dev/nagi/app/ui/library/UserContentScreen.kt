package dev.nagi.app.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.foundation.clickable
import dev.nagi.app.viewmodel.UserProfileViewModel
import dev.nagi.app.ui.components.SegmentedCapsuleControl
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import dev.nagi.app.ui.components.EdgeAlignedMetaRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nagi.app.ui.components.CardGroupPosition
import dev.nagi.app.ui.components.ChromeIconButton
import dev.nagi.app.ui.components.FlatCard
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.NagiHeader
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.Notice
import dev.nagi.app.ui.components.ReplyCountPill
import dev.nagi.app.ui.components.SectionCardItem
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.ui.topic.formatTime
import dev.nagi.app.viewmodel.UserContentViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserContentScreen(
    back: () -> Unit,
    read: (String) -> Unit,
    vm: UserContentViewModel = hiltViewModel(),
    profileVm: UserProfileViewModel = hiltViewModel(),
    sendMessage: (String) -> Unit = {},
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val state = vm.state.collectAsStateWithLifecycle().value
    val replies = vm.selected.collectAsStateWithLifecycle().value
    val profile = profileVm.state.collectAsStateWithLifecycle().value
    val scrollStates = rememberSaveableStateHolder()
    Column(Modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 760.dp)) {
        NagiHeader("用户详情", "UID ${vm.userId}", back = back) {
            ChromeIconButton(NagiIcons.Refresh, "刷新", onClick = { profileVm.refresh(); vm.refresh() })
        }
        key(replies) {
        scrollStates.SaveableStateProvider("${vm.userId}:$replies") {
        val listState = rememberLazyListState()
        LaunchedEffect(listState, state, replies) {
            snapshotFlow {
                val info = listState.layoutInfo
                info.totalItemsCount > 0 &&
                    (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 4
            }.collect { nearEnd -> if (nearEnd) vm.next() }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = Space.md),
        ) {
            item(key = "profile") { UserProfileCard(profile, profileVm::refresh, sendMessage) }
            stickyHeader(key = "tabs") {
                androidx.compose.material3.Surface(color = colors.groupedCanvas) {
                    SegmentedCapsuleControl(listOf("话题", "回复"), if (replies) 1 else 0,
                        { vm.selectReplies(it == 1) }, Modifier.padding(horizontal = Space.lg, vertical = Space.sm))
                }
            }
            if (replies) {
                itemsIndexed(
                    items = state.replies,
                    key = { _, reply -> "${reply.topic.id}:${reply.postId}" },
                ) { index, reply ->
                    SectionCardItem(CardGroupPosition.at(index, state.replies.size)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { read(reply.topic.id) }
                                .padding(Space.lg),
                            verticalArrangement = Arrangement.spacedBy(Space.xs),
                        ) {
                            Text(
                                text = reply.topic.title,
                                style = typography.topicTitle,
                                color = colors.textPrimary,
                            )
                            val preview = remember(reply.content) { contentPreview(reply.content) }
                            if (preview.isNotBlank()) {
                                Text(
                                    text = preview,
                                    style = typography.body,
                                    color = colors.textSecondary,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            EdgeAlignedMetaRow(
                                leading = { Text("查看主题 · 回复 ${reply.postId}", style = typography.metadata, color = colors.goldText) },
                                trailing = {
                                    if (reply.createdAt > 0) Text(formatTime(reply.createdAt), style = typography.metadata, color = colors.textSecondary)
                                },
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(state.topics, key = { _, topic -> topic.id }) { _, topic ->
                    FlatCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.lg, vertical = 4.dp),
                        shape = NagiThemeTokens.shapes.topicCard,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { read(topic.id) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(Space.md),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(Space.xs),
                            ) {
                                Text(
                                    text = topic.title,
                                    style = typography.topicTitle,
                                    color = colors.textPrimary,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val ts = topic.createdAt.takeIf { it > 0 } ?: topic.lastActiveAt
                                Text(
                                    text = if (ts > 0) "${topic.replies} 条回复 · ${formatTime(ts)}" else "${topic.replies} 条回复",
                                    style = typography.metadata,
                                    color = colors.textSecondary,
                                )
                            }
                            ReplyCountPill(count = topic.replies)
                        }
                    }
                }
            }
            if (state.loaded && !state.loading && state.error == null && state.topics.isEmpty() && state.replies.isEmpty()) {
                item { Notice("此页没有内容") }
            }
            if (state.loading) item(key = "loading") { LoadingLine() }
            state.error?.let { error -> item(key = "error") { Notice(error, vm::retry) } }
            if (state.loaded && state.hasMore == null) {
                item(key = "unknown-bounds") { Text("总页数未知", style = typography.metadata, color = colors.textSecondary, modifier = Modifier.padding(Space.lg)) }
            }
        }
        }
        }
    }
}
