package dev.nagi.app.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.CardGroupPosition
import dev.nagi.app.ui.components.ChromeIconButton
import dev.nagi.app.ui.components.FlatCard
import dev.nagi.app.ui.components.InlineStatusBanner
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.Notice
import dev.nagi.app.ui.components.ProgressiveBlurScaffold
import dev.nagi.app.ui.components.SectionCardItem
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.ui.topic.formatTime
import dev.nagi.app.viewmodel.ComposePhase
import dev.nagi.app.viewmodel.MessagesState
import dev.nagi.app.viewmodel.MessagesViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

private fun messageDate(seconds: Long): String = formatTime(seconds)

@Composable
fun NewMessageScreen(
    recipient: String,
    preferences: ReadingPreferences,
    back: () -> Unit,
    vm: MessagesViewModel = hiltViewModel(),
) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val composing by vm.composing.collectAsStateWithLifecycle()
    LaunchedEffect(vm, recipient) { vm.composeTo(recipient) }
    LaunchedEffect(draft.phase) {
        if (draft.phase == ComposePhase.Confirmed) {
            vm.closeComposer()
            back()
        }
    }
    if (composing) {
        ComposerScreen(
            state = draft,
            preferences = preferences,
            edit = vm::edit,
            send = vm::send,
            retry = {},
            initialRecipients = recipient,
            close = { vm.closeComposer(); back() },
            details = vm::details,
            editValue = vm::editValue,
            togglePreview = vm::preview,
        )
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)),
        ) {
            LoadingLine()
        }
    }
}

@Composable
fun MessagesScreen(
    id: String?,
    preferences: ReadingPreferences,
    back: () -> Unit,
    onConversation: (String) -> Unit,
    vm: MessagesViewModel = hiltViewModel(),
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val state by vm.state.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val composing by vm.composing.collectAsStateWithLifecycle()
    var sent by remember { mutableStateOf(false) }
    LaunchedEffect(draft.phase) {
        if (draft.phase == ComposePhase.Confirmed) {
            vm.closeComposer()
            sent = true
        }
    }
    if (sent) {
        dev.nagi.app.ui.components.NagiConfirmDialog(
            onDismissRequest = { sent = false },
            title = "私信已发送",
            message = "可刷新会话查看。",
            confirmLabel = "刷新",
            onConfirm = {
                sent = false
                vm.load(refresh = true)
            },
            confirmStyle = CapsuleStyle.SelectedGold,
            dismissLabel = "继续阅读",
            onDismiss = { sent = false },
        )
    }
    LaunchedEffect(id) { vm.open(id) }
    if (composing) {
        ComposerScreen(
            state = draft,
            preferences = preferences,
            edit = vm::edit,
            send = vm::send,
            retry = {},
            close = vm::closeComposer,
            details = vm::details,
            editValue = vm::editValue,
            togglePreview = vm::preview,
        )
    }
    MessagesContent(
        state = state,
        preferences = preferences,
        back = back,
        onConversation = onConversation,
        refresh = { vm.load(refresh = true) },
        loadMore = { vm.load() },
        compose = { vm.compose(new = id == null) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MessagesContent(
    state: MessagesState,
    preferences: ReadingPreferences,
    back: () -> Unit,
    onConversation: (String) -> Unit,
    refresh: () -> Unit,
    loadMore: () -> Unit,
    compose: () -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val inbox = state.conversationId == null
    val activeCount = if (inbox) state.conversations.size else state.posts.size
    val latestState by rememberUpdatedState(state)
    val latestLoadMore by rememberUpdatedState(loadMore)
    val listState = key(state.conversationId) { rememberLazyListState() }
    var loadingMore by remember(state.conversationId) { mutableStateOf(false) }
    var viewerImages by rememberSaveable(state.conversationId) { mutableStateOf<List<String>>(emptyList()) }
    var viewerIndex by rememberSaveable(state.conversationId) { mutableIntStateOf(0) }
    LaunchedEffect(state.loading, state.error) {
        if (!state.loading && state.error == null) {
            loadingMore = false
        }
    }
    LaunchedEffect(listState, state.conversationId) {
        var armed = true
        snapshotFlow {
            val current = latestState
            val currentInbox = current.conversationId == null
            val currentCount = if (currentInbox) current.conversations.size else current.posts.size
            val targetType = if (currentInbox) "conversation" else "message-post"
            val info = listState.layoutInfo
            val lastItem = info.visibleItemsInfo.lastOrNull { it.contentType == targetType }?.index ?: -1
            val scrolled = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
            val nearEnd = (scrolled || !listState.canScrollForward) && currentCount > 0 && lastItem >= currentCount - 3
            Triple(
                nearEnd,
                current.hasMore && !current.loading && current.error == null,
                current.loading && !loadingMore,
            )
        }.distinctUntilChanged().collect { (nearEnd, eligible, refreshing) ->
            if (!nearEnd || refreshing) {
                armed = true
            } else if (armed && eligible) {
                armed = false
                loadingMore = true
                latestLoadMore()
            }
        }
    }
    CompositionLocalProvider(LocalContentColor provides colors.textPrimary) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gutter = if (maxWidth < 600.dp) Space.lg else maxOf(Space.xl, (maxWidth - 760.dp) / 2)
            ProgressiveBlurScaffold(
                topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(horizontal = Space.sm, vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        ChromeIconButton(icon = NagiIcons.Back, description = "返回", onClick = back)
                        Text(
                            text = if (inbox) "短消息" else "私信会话",
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = Space.sm),
                            style = typography.appBarTitle,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ChromeIconButton(
                            icon = NagiIcons.Refresh,
                            description = "刷新短消息",
                            onClick = {
                                loadingMore = false
                                refresh()
                            },
                            enabled = !state.loading,
                        )
                        ChromeIconButton(
                            icon = NagiIcons.Compose,
                            description = if (inbox) "新建私信" else "回复私信",
                            onClick = compose,
                            enabled = inbox || state.posts.isNotEmpty(),
                        )
                    }
                },
            ) { padding ->
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = padding) {
                    if (state.loading && !loadingMore) {
                        item(key = "loading") {
                            Box(Modifier.padding(horizontal = gutter, vertical = Space.xs)) {
                                LoadingLine()
                            }
                        }
                    }
                    if (!loadingMore || activeCount == 0) {
                        state.error?.let { error ->
                            item(key = "error") {
                                FlatCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = gutter, vertical = Space.xs),
                                    shape = NagiThemeTokens.shapes.topicCard,
                                ) {
                                    Notice(error) {
                                        loadingMore = false
                                        refresh()
                                    }
                                }
                            }
                        }
                    }
                    if (inbox) {
                        itemsIndexed(
                            items = state.conversations,
                            key = { _, item -> item.id },
                            contentType = { _, _ -> "conversation" },
                        ) { _, item ->
                            FlatCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = gutter, vertical = 4.dp),
                                shape = NagiThemeTokens.shapes.topicCard,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onConversation(item.id) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                                    ) {
                                        Text(
                                            text = item.title,
                                            modifier = Modifier.weight(1f),
                                            style = typography.topicTitle,
                                            color = colors.textPrimary,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(colors.goldContainer)
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = item.postCount.toString(),
                                                modifier = Modifier.semantics {
                                                    contentDescription = "${item.postCount} 条消息"
                                                },
                                                style = typography.badge,
                                                color = colors.goldText,
                                            )
                                        }
                                    }
                                    val participants = item.participants.joinToString("、") { it.name }.ifBlank { item.senderName }
                                    val date = item.lastActiveAt.takeIf { it > 0 }?.let(::messageDate)
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                                    ) {
                                        if (participants.isNotBlank()) {
                                            Text(
                                                text = participants,
                                                style = typography.metadata,
                                                color = colors.textSecondary,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (date != null) {
                                            Text(
                                                text = date,
                                                style = typography.metadata,
                                                color = colors.textSecondary,
                                                softWrap = false,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = state.posts,
                            key = { _, post -> post.id },
                            contentType = { _, _ -> "message-post" },
                        ) { index, post ->
                            SectionCardItem(
                                position = CardGroupPosition.at(index, state.posts.size),
                                margin = gutter,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Space.lg),
                                    verticalArrangement = Arrangement.spacedBy(Space.md),
                                ) {
                                    Text(
                                        text = state.participants.firstOrNull { it.userId == post.authorId }?.name ?: post.authorId,
                                        style = typography.topicTitle,
                                        color = colors.goldText,
                                    )
                                    if (post.title.isNotBlank()) {
                                        Text(
                                            text = post.title,
                                            style = typography.sectionTitle,
                                            color = colors.textPrimary,
                                        )
                                    }
                                    SelectionContainer {
                                        ContentBlocks(post.content, preferences) { clickedUrl ->
                                            val (images, index) = resolveContentImageClick(post.content, clickedUrl)
                                            viewerImages = images
                                            viewerIndex = index
                                        }
                                    }
                                    if (post.createdAt > 0) {
                                        Text(
                                            text = messageDate(post.createdAt),
                                            style = typography.metadata,
                                            color = colors.textSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (!state.loading && state.error == null && state.conversations.isEmpty() && state.posts.isEmpty()) {
                        item(key = "empty") {
                            FlatCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = gutter, vertical = Space.xs),
                                shape = NagiThemeTokens.shapes.topicCard,
                            ) {
                                Notice("暂无短消息")
                            }
                        }
                    }
                    if (state.hasMore) {
                        item(key = "pagination-footer", contentType = "footer") {
                            if (state.error != null && loadingMore) {
                                InlineStatusBanner(
                                    message = state.error,
                                    retry = {
                                        loadingMore = true
                                        loadMore()
                                    },
                                    horizontalPadding = gutter,
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = gutter, vertical = Space.sm),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(
                                        modifier = Modifier.heightIn(min = 48.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                                    ) {
                                        if (state.loading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = colors.goldAccent,
                                            )
                                        }
                                        Text(
                                            text = if (state.loading) "正在加载更多…" else "上滑继续浏览",
                                            style = typography.metadata,
                                            color = colors.textSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (viewerImages.isNotEmpty()) {
            ImageViewer(
                urls = viewerImages,
                initialIndex = viewerIndex,
                close = { viewerImages = emptyList() },
            )
        }
    }
}
