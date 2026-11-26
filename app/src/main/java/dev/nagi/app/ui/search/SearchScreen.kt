package dev.nagi.app.ui.search

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nagi.app.data.formatReplySummary
import dev.nagi.app.data.resolveTopicReadStatus
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.CardGroupPosition
import dev.nagi.app.ui.components.ChromeIconButton
import dev.nagi.app.ui.components.ForumScopePickerSheet
import dev.nagi.app.ui.components.InlineStatusBanner
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.NagiSearchTextField
import dev.nagi.app.ui.components.Notice
import dev.nagi.app.ui.components.ProgressiveBlurScaffold
import dev.nagi.app.ui.components.SectionCardItem
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.ui.topic.FlatTopicCard
import dev.nagi.app.viewmodel.LibraryViewModel
import dev.nagi.app.viewmodel.SearchMode
import dev.nagi.app.viewmodel.SearchViewModel
import dev.nagi.sdk.Forum
import dev.nagi.sdk.Topic
import dev.nagi.sdk.TopicSort
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    library: LibraryViewModel,
    onCancel: () -> Unit,
    onForum: (Forum) -> Unit,
    onRead: (String) -> Unit,
    onUserContent: (String, String) -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val state by vm.state.collectAsStateWithLifecycle()
    val scoped = state.forumId != null || state.collectionId != null
    val directory by library.forums.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var choosingScope by rememberSaveable { mutableStateOf(false) }
    var topicDetails by remember { mutableStateOf<Topic?>(null) }
    val cancel = {
        focusManager.clearFocus()
        keyboard?.hide()
        onCancel()
    }
    val submit: (SearchMode) -> Unit = { mode ->
        vm.submit(mode)
        focusManager.clearFocus()
        keyboard?.hide()
    }
    BackHandler { cancel() }
    LaunchedEffect(Unit) {
        if (state.mode == null) {
            focus.requestFocus()
            keyboard?.show()
        }
    }
    val matchingForums = remember(directory.items, state.submitted) {
        directory.items.filter { it.name.contains(state.submitted, ignoreCase = true) }
    }
    val listState = rememberLazyListState()
    var loadingMore by remember(state.mode, state.submitted, state.forumId, state.collectionId) { mutableStateOf(false) }
    LaunchedEffect(state.loading, state.error) {
        if (!state.loading && state.error == null) {
            loadingMore = false
        }
    }
    LaunchedEffect(listState, state.mode, state.submitted, state.forumId, state.collectionId) {
        var armed = true
        snapshotFlow {
            val lastTopic = listState.layoutInfo.visibleItemsInfo.lastOrNull { it.contentType == "topic" }?.index ?: -1
            val scrolled = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
            val nearEnd = (scrolled || !listState.canScrollForward) && state.topics.isNotEmpty() && lastTopic >= state.topics.size - 3
            Triple(
                nearEnd,
                state.mode == SearchMode.TOPICS && state.page > 0 && state.hasMore && !state.loading && state.error == null,
                state.loading && !loadingMore,
            )
        }.distinctUntilChanged().collect { (nearEnd, eligible, refreshing) ->
            if (!nearEnd || refreshing) {
                armed = true
            } else if (armed && eligible) {
                armed = false
                loadingMore = true
                vm.loadMore()
            }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        ProgressiveBlurScaffold(
            modifier = Modifier.widthIn(max = 760.dp),
            topBar = {},
            topVisible = { 0f },
            bottomEdgeBlur = false,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                NagiSearchTextField(
                    value = state.query,
                    onValueChange = vm::editQuery,
                    placeholder = if (scoped) "搜索当前版面" else "搜索",
                    clearDescription = "清空搜索",
                    onClear = {
                        vm.editQuery("")
                        focus.requestFocus()
                        keyboard?.show()
                    },
                    onSubmit = {
                        if (state.query.isNotBlank()) submit(state.mode ?: SearchMode.TOPICS)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .focusRequester(focus),
                    containerColor = colors.frostedChrome,
                    focusedContainerColor = colors.frostedChrome,
                    borderColor = colors.cardBorder,
                    focusedBorderColor = colors.goldAccent,
                )
                ChromeIconButton(
                    icon = NagiIcons.Close,
                    description = "取消搜索",
                    onClick = cancel,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = padding,
        ) {
            item(key = "heading") {
                Column(
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Text(
                        text = if (state.query.isNotBlank()) {
                            "搜索“${state.query}”"
                        } else if (scoped) {
                            "搜索当前版面的话题"
                        } else {
                            "搜索版块、话题或用户"
                        },
                        style = typography.sectionHeader,
                        color = colors.textSecondary,
                    )
                    if (scoped || state.mode == SearchMode.TOPICS) {
                        CapsuleButton(
                            text = "搜索范围：${state.forumName ?: "所有版块"}（切换）",
                            onClick = { choosingScope = true },
                            icon = NagiIcons.ForumStack,
                            trailingIcon = NagiIcons.ChevronDown,
                            style = CapsuleStyle.SelectedGold,
                        )
                    }
                }
            }
            if (state.mode == null && scoped) {
                item(key = "scoped-topics-option") {
                    SearchOption(
                        label = "搜索当前版面",
                        icon = NagiIcons.TopicDocument,
                        position = CardGroupPosition.Single,
                        enabled = state.query.isNotBlank(),
                    ) { submit(SearchMode.TOPICS) }
                }
            } else if (state.mode == null) {
                item(key = "forums-option") {
                    SearchOption(
                        label = "所有版块",
                        icon = NagiIcons.ForumStack,
                        position = CardGroupPosition.First,
                        enabled = state.query.isNotBlank(),
                    ) { submit(SearchMode.FORUMS) }
                }
                item(key = "topics-option") {
                    SearchOption(
                        label = "所有话题",
                        icon = NagiIcons.TopicDocument,
                        position = CardGroupPosition.Middle,
                        enabled = state.query.isNotBlank(),
                    ) { submit(SearchMode.TOPICS) }
                }
                item(key = "users-option") {
                    SearchOption(
                        label = "所有用户",
                        icon = NagiIcons.Users,
                        position = CardGroupPosition.Last,
                        enabled = state.query.isNotBlank(),
                        supporting = "完整用户名或 UID；纯数字按 UID 查询",
                    ) { submit(SearchMode.USERS) }
                }
            } else {
                if (state.loading && !loadingMore) item(key = "loading") { LoadingLine() }
                if (!loadingMore || !state.hasMore) {
                    state.error?.let { error ->
                        item(key = "error") {
                            Notice(error) {
                                loadingMore = false
                                vm.retry()
                            }
                        }
                    }
                }
                when (requireNotNull(state.mode)) {
                    SearchMode.FORUMS -> {
                        if (directory.loading) item(key = "directory-loading") { LoadingLine() }
                        directory.error?.let { error -> item(key = "directory-error") { Notice(error, library::loadForums) } }
                        itemsIndexed(matchingForums, key = { _, forum -> forum.key }) { index, forum ->
                            SearchOption(
                                label = forum.name,
                                icon = NagiIcons.ForumStack,
                                position = CardGroupPosition.at(index, matchingForums.size),
                                enabled = true,
                                supporting = forum.description.ifBlank { null },
                            ) { onForum(forum) }
                        }
                        if (!directory.loading && directory.error == null && matchingForums.isEmpty()) {
                            item(key = "empty") { Notice("没有匹配的版块") }
                        }
                    }
                    SearchMode.TOPICS -> {
                        itemsIndexed(
                            items = state.topics,
                            key = { _, topic -> topic.id },
                            contentType = { _, _ -> "topic" },
                        ) { _, topic ->
                            FlatTopicCard(
                                topic = topic,
                                sort = TopicSort.NEWEST,
                                selected = false,
                                selectableMode = false,
                                margin = Space.lg,
                                onDetails = { topicDetails = topic },
                                onClick = { onRead(topic.id) },
                            )
                        }
                        if (state.hasMore) {
                            item(key = "pagination-footer", contentType = "footer") {
                                if (state.error != null && loadingMore) {
                                    InlineStatusBanner(
                                        message = state.error!!,
                                        retry = {
                                            loadingMore = true
                                            vm.loadMore()
                                        },
                                        horizontalPadding = Space.lg,
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = Space.lg, vertical = Space.sm),
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
                                                text = if (state.loading) "正在加载更多主题…" else "上滑继续浏览",
                                                style = typography.metadata,
                                                color = colors.textSecondary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (!state.loading && state.error == null && state.topics.isEmpty()) {
                            item(key = "empty") { Notice("没有匹配的主题") }
                        }
                    }
                    SearchMode.USERS -> {
                        itemsIndexed(state.users, key = { _, user -> user.id }) { index, user ->
                            SearchOption(
                                label = user.name,
                                icon = NagiIcons.Users,
                                position = CardGroupPosition.at(index, state.users.size),
                                enabled = true,
                                supporting = "UID ${user.id}",
                            ) { onUserContent(user.id, "topics") }
                        }
                        if (!state.loading && state.error == null && state.users.isEmpty()) {
                            item(key = "empty") { Notice("没有找到该用户，请输入完整用户名或 UID") }
                        }
                    }
                }
            }
        }
    }
    }

    if (choosingScope) {
        val selectedKey = state.forumId?.let { id ->
            directory.items.firstOrNull { it.id == id && it.collectionId == state.collectionId }?.key
        }
        ForumScopePickerSheet(
            forums = directory.items,
            selectedForumKey = selectedKey,
            includeAllOption = true,
            allSelected = state.forumId == null && state.collectionId == null,
            onSelectAll = {
                choosingScope = false
                vm.scope(null)
            },
            onSelectForum = { forum ->
                choosingScope = false
                vm.scope(forum)
            },
            onDismissRequest = { choosingScope = false },
            loading = directory.loading,
            error = directory.error,
            onRetry = library::loadForums,
        )
    }

    topicDetails?.let { topic ->
        val readStatus = resolveTopicReadStatus(
            topic,
            dev.nagi.app.ui.components.LocalTopicReadStates.current[topic.id],
        )
        val replyLine = readStatus.formatReplySummary(topic.replies)
        dev.nagi.app.ui.components.NagiConfirmDialog(
            onDismissRequest = { topicDetails = null },
            title = topic.title,
            message = "${dev.nagi.app.ui.components.formatAuthorDisplayName(topic.author)}\n$replyLine",
            confirmLabel = "关闭",
            onConfirm = { topicDetails = null },
            confirmStyle = CapsuleStyle.Neutral,
        )
    }
}

@Composable
private fun SearchOption(
    label: String,
    icon: ImageVector,
    position: CardGroupPosition,
    enabled: Boolean,
    supporting: String? = null,
    onClick: () -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    SectionCardItem(position = position, dividerIndent = 54.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .heightIn(min = 54.dp)
                .padding(horizontal = Space.lg, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (enabled) colors.goldAccent else colors.textSecondary.copy(alpha = 0.45f),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = label,
                    style = typography.topicTitle,
                    color = if (enabled) colors.textPrimary else colors.textSecondary,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = typography.metadata,
                        color = colors.textSecondary,
                    )
                }
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
