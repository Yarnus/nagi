package dev.nagi.app.ui.topic

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.CardGroupPosition
import dev.nagi.app.ui.components.ChromeIconButton
import dev.nagi.app.ui.components.EdgeAlignedMetaRow
import dev.nagi.app.ui.components.NeutralTagBadge
import dev.nagi.app.ui.components.FlatCard
import dev.nagi.app.ui.components.FrostedDropdownItem
import dev.nagi.app.ui.components.FrostedDropdownMenu
import dev.nagi.app.ui.components.GoldSwitch
import dev.nagi.app.ui.components.GoldTagBadge
import dev.nagi.app.ui.components.InlineStatusBanner
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.Notice
import dev.nagi.app.ui.components.ProgressiveBlurScaffold
import dev.nagi.app.ui.components.ReplyCountPill
import dev.nagi.app.ui.components.SectionCardItem
import dev.nagi.app.ui.components.TopicListFloatingDock
import dev.nagi.app.ui.post.ComposerScreen
import dev.nagi.app.ui.post.ReaderScreen
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.viewmodel.ComposerViewModel
import dev.nagi.app.viewmodel.TopicsViewModel
import dev.nagi.sdk.Forum
import dev.nagi.sdk.PublishAction
import dev.nagi.sdk.PublishTarget
import dev.nagi.sdk.Topic
import dev.nagi.sdk.TopicSort
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TopicsScreen(
    forumId: String?,
    collection: String?,
    title: String,
    expanded: Boolean,
    preferences: ReadingPreferences,
    back: (() -> Unit)?,
    onRead: (String) -> Unit,
    onSearch: () -> Unit,
    searchForums: List<Forum> = emptyList(),
    vm: TopicsViewModel = hiltViewModel(),
    bottomBar: (@Composable () -> Unit)? = null,
    composer: ComposerViewModel = hiltViewModel(),
    directoryForums: List<Forum> = emptyList(),
    favoriteForumKeys: Set<String> = emptySet(),
    onToggleForumFavorite: ((Forum) -> Unit)? = null,
    onSwitchForum: ((Forum) -> Unit)? = null,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val context = LocalContext.current
    val draft by composer.state.collectAsStateWithLifecycle()
    var composing by rememberSaveable { mutableStateOf(false) }
    var replacingDraft by remember { mutableStateOf(false) }
    var activeForumId by rememberSaveable(forumId) { mutableStateOf(forumId) }
    var activeCollection by rememberSaveable(forumId, collection) { mutableStateOf(collection) }
    var activeTitle by rememberSaveable(forumId, collection, title) { mutableStateOf(title) }
    val publishTarget = activeForumId?.let {
        PublishTarget(PublishAction.NEW_TOPIC, forumId = it, collectionId = activeCollection)
    }

    if (replacingDraft) {
        AlertDialog(
            onDismissRequest = { replacingDraft = false },
            shape = NagiThemeTokens.shapes.topicCard,
            containerColor = colors.cardSurface,
            tonalElevation = 0.dp,
            title = { Text("替换现有草稿？", style = typography.sectionTitle, color = colors.textPrimary) },
            text = {
                Text(
                    text = "请先复制需要保留的原草稿内容，替换后无法恢复。",
                    style = typography.body,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                CapsuleButton(
                    text = "替换",
                    onClick = {
                        publishTarget?.let { composer.open(it, "发布到 $activeTitle") }
                        replacingDraft = false
                        composing = true
                    },
                    style = CapsuleStyle.Destructive,
                )
            },
            dismissButton = {
                CapsuleButton(
                    text = "查看原草稿",
                    onClick = {
                        replacingDraft = false
                        composing = true
                    },
                    style = CapsuleStyle.Neutral,
                )
            },
        )
    }

    if (composing) {
        ComposerScreen(
            state = draft,
            preferences = preferences,
            edit = composer::edit,
            send = composer::send,
            retry = composer::prepare,
            close = { composing = false },
            details = composer::details,
            editValue = composer::editValue,
            togglePreview = composer::preview,
        )
    }

    val state by vm.state.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val subforums by vm.subforums.collectAsStateWithLifecycle()
    val filterBusy by vm.filterBusy.collectAsStateWithLifecycle()
    val filterNotice by vm.filterNotice.collectAsStateWithLifecycle()
    var showingSubforums by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var loadingMore by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val filterLocked by vm.filterLocked.collectAsStateWithLifecycle()
    val filterEpoch by vm.filterEpoch.collectAsStateWithLifecycle()
    LaunchedEffect(filterEpoch) { if (filterEpoch > 0) listState.scrollToItem(0) }
    var scopeKey by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingForum by rememberSaveable { mutableStateOf(false) }
    var showingRelatedForums by rememberSaveable { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }
    var forumFilter by rememberSaveable { mutableStateOf("") }
    val scope = remember(searchForums, scopeKey) { searchForums.firstOrNull { it.key == scopeKey } }
    val keyboard = LocalSoftwareKeyboardController.current
    var details by remember { mutableStateOf<Topic?>(null) }
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val chromeAlpha by animateFloatAsState(
        targetValue = if (collapsed || activeForumId == null) 1f else 0f,
        animationSpec = tween(180),
        label = "topic chrome",
    )

    val currentForumKey = remember(activeForumId, activeCollection) {
        if (activeForumId != null) "${activeForumId}:${activeCollection.orEmpty()}" else null
    }
    val currentForum = remember(directoryForums, activeForumId, activeCollection, activeTitle) {
        if (activeForumId == null) {
            null
        } else {
            directoryForums.firstOrNull { it.id == activeForumId && it.collectionId.orEmpty() == activeCollection.orEmpty() }
                ?: Forum(id = activeForumId!!, name = activeTitle, category = "", group = "", collectionId = activeCollection)
        }
    }
    val isCurrentForumFavorite = currentForumKey != null && currentForumKey in favoriteForumKeys

    val openComposer = {
        if (draft.content.isNotBlank() && draft.target != publishTarget) {
            replacingDraft = true
        } else {
            publishTarget?.let { composer.open(it, "发布到 $activeTitle") }
            composing = true
        }
    }

    val submit = {
        if (scope != null && query.isNotBlank()) {
            loadingMore = false
            submitted = query.trim()
            vm.search(submitted, scope.id, scope.collectionId)
            keyboard?.hide()
        }
    }

    LaunchedEffect(state.loading, state.error) {
        if (!state.loading && state.error == null) {
            loadingMore = false
        }
    }

    var previousForumKey by rememberSaveable { mutableStateOf(currentForumKey) }
    LaunchedEffect(activeForumId, activeCollection, scope?.key) {
        val currentId = activeForumId
        if (currentId != null) {
            val switched = previousForumKey != null && previousForumKey != currentForumKey
            previousForumKey = currentForumKey
            if (switched) {
                listState.scrollToItem(0)
            }
            vm.openForum(currentId, activeCollection)
        } else if (submitted.isNotBlank() && scope != null) {
            vm.search(submitted, scope.id, scope.collectionId)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = expanded && maxWidth >= 700.dp
        val listPaneWidth = (maxWidth * 0.42f).coerceIn(320.dp, 360.dp)
        val wideSinglePane = !twoPane && maxWidth >= 480.dp
        val gutter = if (wideSinglePane) Space.xl else Space.lg

        BackHandler(!twoPane && selected != null) { selected = null }

        Row(Modifier.fillMaxSize()) {
            if (twoPane || selected == null) {
                Box(
                    modifier = if (twoPane) {
                        Modifier.width(listPaneWidth).fillMaxHeight()
                    } else {
                        Modifier.fillMaxSize()
                    },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    LaunchedEffect(listState, activeForumId, activeCollection, submitted, scope?.key, sort) {
                        var armed = true
                        snapshotFlow {
                            val lastTopic = listState.layoutInfo.visibleItemsInfo.lastOrNull { it.contentType == "topic" }?.index ?: -1
                            val scrolled = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
                            val nearEnd = (scrolled || !listState.canScrollForward) && state.items.isNotEmpty() && lastTopic >= state.items.size - 3
                            Triple(
                                nearEnd,
                                state.page > 0 && state.hasMore && !state.loading && state.error == null,
                                state.loading && !loadingMore,
                            )
                        }.distinctUntilChanged().collect { (nearEnd, eligible, refreshing) ->
                            if (!nearEnd || refreshing) {
                                armed = true
                            } else if (armed && eligible) {
                                armed = false
                                loadingMore = true
                                vm.load(false)
                            }
                        }
                    }

                    ProgressiveBlurScaffold(
                        modifier = Modifier.widthIn(max = 760.dp),
                        topVisible = { chromeAlpha },
                        bottomEdgeBlur = activeForumId == null,
                        bottomBar = if (activeForumId == null) {
                            bottomBar
                        } else {
                            {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = gutter, vertical = Space.sm),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TopicListFloatingDock(
                                        onSubforumsClick = { showingSubforums = true },
                                        subforumFilterActive = subforums.any { it.filterable && !it.selected },
                                        subforumsContentDescription = if (subforums.any { it.filterable && !it.selected })
                                            "子板块筛选，部分子板块已屏蔽" else "子板块筛选",
                                        onSearchClick = onSearch,
                                        onRefreshClick = {
                                            loadingMore = false
                                            vm.load(true)
                                        },
                                        onComposeClick = openComposer,
                                        searchLabel = "搜索当前版面",
                                        searchContentDescription = "搜索当前版面",
                                        composeContentDescription = "发帖",
                                    )
                                }
                            }
                        },
                        topBar = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .padding(horizontal = Space.sm, vertical = Space.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            ) {
                                if (back != null) {
                                    ChromeIconButton(NagiIcons.Back, "返回", back)
                                }
                                Text(
                                    text = activeTitle,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = Space.sm)
                                        .graphicsLayer { alpha = chromeAlpha }
                                        .clearAndSetSemantics {
                                            if (collapsed || activeForumId == null) text = AnnotatedString(activeTitle)
                                        },
                                    style = typography.appBarTitle,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (activeForumId != null) {
                                    Box {
                                        ChromeIconButton(
                                            icon = NagiIcons.More,
                                            description = "更多选项",
                                            onClick = { moreExpanded = true },
                                        )
                                        FrostedDropdownMenu(
                                            expanded = moreExpanded,
                                            onDismissRequest = { moreExpanded = false },
                                        ) {
                                            FrostedDropdownItem(
                                                text = "同组版块与主题合集",
                                                leadingIcon = NagiIcons.Subforums,
                                                onClick = { moreExpanded = false; showingRelatedForums = true },
                                            )
                                            if (currentForum != null && onToggleForumFavorite != null) {
                                                FrostedDropdownItem(
                                                    text = if (isCurrentForumFavorite) "取消本地收藏版块" else "收藏当前版块",
                                                    supporting = "本地收藏 · 仅保存在此设备",
                                                    leadingIcon = NagiIcons.Bookmark,
                                                    selected = isCurrentForumFavorite,
                                                    onClick = {
                                                        moreExpanded = false
                                                        onToggleForumFavorite(currentForum)
                                                    },
                                                )
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs),
                                                    thickness = 1.dp,
                                                    color = colors.cardBorder,
                                                )
                                            }
                                            FrostedDropdownItem(
                                                text = "按最新发布排序",
                                                leadingIcon = NagiIcons.Sort,
                                                selected = sort == TopicSort.NEWEST,
                                                onClick = {
                                                    moreExpanded = false
                                                    loadingMore = false
                                                    vm.changeSort(TopicSort.NEWEST)
                                                },
                                            )
                                            FrostedDropdownItem(
                                                text = "按最近活跃排序",
                                                leadingIcon = NagiIcons.Sort,
                                                selected = sort == TopicSort.LAST_ACTIVE,
                                                onClick = {
                                                    moreExpanded = false
                                                    loadingMore = false
                                                    vm.changeSort(TopicSort.LAST_ACTIVE)
                                                },
                                            )
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs),
                                                thickness = 1.dp,
                                                color = colors.cardBorder,
                                            )
                                            val forumUrl = if (!activeCollection.isNullOrBlank()) {
                                                "https://bbs.nga.cn/thread.php?stid=$activeCollection"
                                            } else {
                                                "https://bbs.nga.cn/thread.php?fid=$activeForumId"
                                            }
                                            FrostedDropdownItem(
                                                text = "分享版块链接",
                                                leadingIcon = NagiIcons.Share,
                                                onClick = {
                                                    moreExpanded = false
                                                    runCatching {
                                                        val send = Intent(Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(Intent.EXTRA_TEXT, "$activeTitle $forumUrl")
                                                        }
                                                        context.startActivity(Intent.createChooser(send, activeTitle))
                                                    }
                                                },
                                            )
                                            FrostedDropdownItem(
                                                text = "在浏览器中打开",
                                                leadingIcon = NagiIcons.Globe,
                                                onClick = {
                                                    moreExpanded = false
                                                    runCatching {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(forumUrl)))
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    ) { chromePadding ->
                        PullToRefreshBox(
                            isRefreshing = state.loading && state.items.isNotEmpty() && !loadingMore,
                            onRefresh = {
                                loadingMore = false
                                vm.load(true)
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = chromePadding,
                            ) {
                                item(key = "list-heading", contentType = "heading") {
                                    Column(
                                        modifier = Modifier.padding(horizontal = gutter, vertical = Space.sm),
                                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                                    ) {
                                        if (activeForumId != null) {
                                            Text(
                                                text = activeTitle,
                                                style = typography.largeTitle,
                                                color = colors.textPrimary,
                                            )
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                                                verticalArrangement = Arrangement.spacedBy(Space.xs),
                                                itemVerticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                TopicSortMenu(sort) { next ->
                                                    loadingMore = false
                                                    vm.changeSort(next)
                                                }
                                                if (!activeCollection.isNullOrBlank()) {
                                                    GoldTagBadge(text = "主题合集")
                                                }
                                                if (isCurrentForumFavorite) {
                                                    GoldTagBadge(text = "本地已收藏")
                                                }
                                            }
                                        } else {
                                            SearchControls(
                                                scope = scope,
                                                query = query,
                                                onQueryChange = { query = it },
                                                onChooseScope = { choosingForum = true },
                                                onSubmit = submit,
                                            )
                                            Text(
                                                text = "搜索结果",
                                                style = typography.metadata,
                                                color = colors.textSecondary,
                                                modifier = Modifier.padding(top = Space.xs),
                                            )
                                        }
                                    }
                                }

                                if (state.loading && state.items.isEmpty()) {
                                    item(key = "initial-loading", contentType = "status") {
                                        FlatCard(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = gutter, vertical = Space.xs),
                                            shape = NagiThemeTokens.shapes.topicCard,
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(Space.xl),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(Space.md),
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp,
                                                    color = colors.goldAccent,
                                                )
                                                Text(
                                                    text = if (forumId != null) "正在加载主题…" else "正在搜索主题…",
                                                    style = typography.body,
                                                    color = colors.textSecondary,
                                                )
                                            }
                                        }
                                    }
                                }

                                if (state.error != null && !loadingMore) {
                                    item(key = "refresh-error", contentType = "banner") {
                                        InlineStatusBanner(
                                            message = state.error!!,
                                            retry = {
                                                loadingMore = false
                                                vm.load(true)
                                            },
                                            horizontalPadding = gutter,
                                        )
                                    }
                                }

                                itemsIndexed(
                                    items = state.items,
                                    key = { _, topic -> topic.id },
                                    contentType = { _, _ -> "topic" },
                                ) { _, topic ->
                                    FlatTopicCard(
                                        topic = topic,
                                        sort = sort,
                                        selected = twoPane && selected == topic.id,
                                        selectableMode = twoPane,
                                        margin = gutter,
                                        onDetails = { details = topic },
                                        onClick = {
                                            if (twoPane) selected = topic.id else onRead(topic.id)
                                        },
                                    )
                                }

                                if (state.hasMore) {
                                    item(key = "pagination-footer", contentType = "footer") {
                                        if (state.error != null && loadingMore) {
                                            InlineStatusBanner(
                                                message = state.error!!,
                                                retry = {
                                                    loadingMore = true
                                                    vm.load(false)
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
                                                        text = if (state.loading) "正在加载更多主题…" else "上滑继续浏览",
                                                        style = typography.metadata,
                                                        color = colors.textSecondary,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (!state.loading && state.error == null && state.items.isEmpty()) {
                                    item(key = "empty", contentType = "empty") {
                                        val emptyMessage = when {
                                            activeForumId != null -> "暂无主题"
                                            scope == null -> "先选择版块，再搜索该版主题"
                                            submitted.isBlank() -> "输入关键词查找主题"
                                            else -> "没有匹配的主题"
                                        }
                                        FlatCard(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = gutter, vertical = Space.xs),
                                            shape = NagiThemeTokens.shapes.topicCard,
                                        ) {
                                            Notice(emptyMessage)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (twoPane || selected != null) {
                if (twoPane) {
                    VerticalDivider(Modifier.fillMaxHeight(), thickness = 1.dp, color = colors.cardBorder)
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val id = selected
                    if (id == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Notice("选择一个主题，在这里继续阅读")
                        }
                    } else {
                        key(id) {
                            ReaderScreen(
                                id = id,
                                preferences = preferences,
                                back = { selected = null },
                                vm = hiltViewModel(key = "reader"),
                            )
                        }
                    }
                }
            }
        }
    }

    details?.let { topic ->
        ModalBottomSheet(
            onDismissRequest = { details = null },
            shape = NagiThemeTokens.shapes.bottomSheet,
            containerColor = colors.cardSurface,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.xl)
                    .padding(bottom = Space.xxl),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Text(topic.title, style = typography.sectionTitle, color = colors.textPrimary)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Icon(NagiIcons.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textSecondary)
                    Text(topic.author.ifBlank { "匿名" }, style = typography.body, color = colors.textSecondary)
                }
                if (topic.createdAt > 0) {
                    Text("发布 ${formatTime(topic.createdAt)}", style = typography.metadata, color = colors.textSecondary)
                }
                if (topic.lastActiveAt > 0) {
                    Text("活跃 ${formatTime(topic.lastActiveAt)}", style = typography.metadata, color = colors.textSecondary)
                }
                Text("${topic.replies} 条回复", style = typography.metadata, color = colors.goldText)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    CapsuleButton(
                        text = "关闭",
                        onClick = { details = null },
                        style = CapsuleStyle.Neutral,
                    )
                }
            }
        }
    }

    if (showingSubforums) {
        ModalBottomSheet(
            onDismissRequest = { showingSubforums = false },
            containerColor = colors.groupedCanvas,
            tonalElevation = 0.dp,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.lg),
            ) {
                item {
                    Text("子板块筛选", style = NagiThemeTokens.typography.sectionTitle, color = colors.textPrimary)
                    Text("开关设置当前 NGA 账号的帖子显示范围，与本地收藏无关；点按名称进入子板块。",
                        style = NagiThemeTokens.typography.metadata, color = colors.textSecondary)
                    filterNotice?.let { Notice(it) }
                    if (filterBusy) Notice("正在保存子板块设置…")
                    if (state.loading) Notice("正在读取子板块设置…")
                    state.error?.let { Notice(it) }
                    if (!state.loading && subforums.isEmpty()) Notice("当前版块暂无子板块筛选")
                }
                itemsIndexed(subforums, key = { _, sub -> sub.forum.key }) { _, sub ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable(enabled = !filterBusy) {
                            showingSubforums = false
                            activeForumId = sub.forum.id
                            activeCollection = sub.forum.collectionId
                            activeTitle = sub.forum.name
                            onSwitchForum?.invoke(sub.forum)
                        }.heightIn(min = 48.dp).padding(vertical = 8.dp)) {
                            Text(sub.forum.name, style = NagiThemeTokens.typography.topicTitle, color = colors.textPrimary)
                            if (sub.forum.description.isNotBlank()) Text(sub.forum.description,
                                style = NagiThemeTokens.typography.metadata, color = colors.textSecondary)
                        }
                        GoldSwitch(
                            checked = sub.selected,
                            enabled = sub.filterable && !filterBusy && !filterLocked && !state.loading && activeCollection == null,
                            onCheckedChange = { vm.setSubforumVisible(sub, it) },
                            contentDescription = "显示 ${sub.forum.name} 的帖子",
                        )
                    }
                }
                item {
                    CapsuleButton(text = "刷新核对", onClick = { vm.load(true) })
                }
            }
        }
    }

    if (showingRelatedForums) {
        RelatedForumsSheet(
            currentForum = currentForum,
            directoryForums = directoryForums,
            favoriteForumKeys = favoriteForumKeys,
            onToggleFavorite = onToggleForumFavorite,
            onSelectForum = { targetForum ->
                showingRelatedForums = false
                if (targetForum.key != currentForumKey) {
                    activeForumId = targetForum.id.ifBlank { "0" }
                    activeCollection = targetForum.collectionId?.ifBlank { null }
                    activeTitle = targetForum.name
                    selected = null
                    loadingMore = false
                    onSwitchForum?.invoke(targetForum)
                }
            },
            onDismiss = { showingRelatedForums = false },
        )
    }

    if (choosingForum) {
        val filteredForums = remember(searchForums, forumFilter) {
            searchForums.filter { it.name.contains(forumFilter, ignoreCase = true) }
        }
        ModalBottomSheet(
            onDismissRequest = { choosingForum = false },
            containerColor = colors.groupedCanvas,
            shape = NagiThemeTokens.shapes.bottomSheet,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .padding(horizontal = Space.lg),
            ) {
                Text("选择搜索版块", style = typography.sectionTitle, color = colors.textPrimary)
                OutlinedTextField(
                    value = forumFilter,
                    onValueChange = { forumFilter = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.md),
                    singleLine = true,
                    shape = CircleShape,
                    placeholder = { Text("筛选版块", style = typography.body, color = colors.textSecondary) },
                    leadingIcon = {
                        Icon(
                            imageVector = NagiIcons.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colors.textSecondary,
                        )
                    },
                    trailingIcon = if (forumFilter.isNotEmpty()) {
                        {
                            IconButton(onClick = { forumFilter = "" }) {
                                Icon(
                                    imageVector = NagiIcons.ClearCircle,
                                    contentDescription = "清空筛选",
                                    modifier = Modifier.size(18.dp),
                                    tint = colors.textSecondary,
                                )
                            }
                        }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = colors.cardSurface,
                        focusedContainerColor = colors.cardSurface,
                        unfocusedBorderColor = colors.cardBorder,
                        focusedBorderColor = colors.goldAccent,
                    ),
                )
                LazyColumn(contentPadding = PaddingValues(bottom = Space.xl)) {
                    itemsIndexed(filteredForums, key = { _, forum -> forum.key }) { index, forum ->
                        val active = forum.key == scopeKey
                        SectionCardItem(
                            position = CardGroupPosition.at(index, filteredForums.size),
                            margin = 0.dp,
                            dividerIndent = Space.lg,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .clickable {
                                        scopeKey = forum.key
                                        choosingForum = false
                                        forumFilter = ""
                                        submitted = ""
                                        selected = null
                                        loadingMore = false
                                        vm.clearSearch()
                                    }
                                    .padding(horizontal = Space.lg, vertical = Space.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = forum.name,
                                        style = typography.topicTitle,
                                        color = if (active) colors.goldText else colors.textPrimary,
                                    )
                                    val groupLine = listOf(forum.category, forum.group)
                                        .filter { it.isNotBlank() }
                                        .distinct()
                                        .joinToString(" / ")
                                    if (groupLine.isNotBlank()) {
                                        Text(
                                            text = groupLine,
                                            style = typography.metadata,
                                            color = colors.textSecondary,
                                        )
                                    }
                                }
                                if (active) {
                                    Icon(
                                        imageVector = NagiIcons.Check,
                                        contentDescription = "当前已选",
                                        modifier = Modifier.size(18.dp),
                                        tint = colors.goldText,
                                    )
                                }
                            }
                        }
                    }
                    if (searchForums.isEmpty()) {
                        item { Notice("版块目录尚未加载，请返回版块页重试") }
                    } else if (filteredForums.isEmpty()) {
                        item { Notice("没有匹配的版块") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelatedForumsSheet(
    currentForum: Forum?,
    directoryForums: List<Forum>,
    favoriteForumKeys: Set<String>,
    onToggleFavorite: ((Forum) -> Unit)?,
    onSelectForum: (Forum) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val related = remember(currentForum, directoryForums) {
        if (currentForum == null || directoryForums.isEmpty()) {
            emptyList()
        } else {
            val matched = directoryForums.firstOrNull { it.key == currentForum.key }
                ?: directoryForums.firstOrNull { it.id == currentForum.id }
            when {
                matched != null && (matched.category.isNotBlank() || matched.group.isNotBlank()) ->
                    directoryForums.filter { it.category == matched.category && it.group == matched.group }
                else -> directoryForums.filter { it.id == currentForum.id }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = NagiThemeTokens.shapes.bottomSheet,
        containerColor = colors.groupedCanvas,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .padding(bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "同组版块与主题合集",
                        style = typography.sectionTitle,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "点按切换版块；右侧开关可将版块加入本地收藏（仅保存在此设备）",
                        style = typography.metadata,
                        color = colors.textSecondary,
                    )
                }
                ChromeIconButton(
                    icon = NagiIcons.Close,
                    description = "关闭面板",
                    onClick = onDismiss,
                )
            }

            if (related.isEmpty()) {
                val single = currentForum
                if (single != null) {
                    SectionCardItem(position = CardGroupPosition.Single) {
                        RelatedForumRow(
                            forum = single,
                            isCurrent = true,
                            isFavorite = single.key in favoriteForumKeys,
                            onClick = onDismiss,
                            onToggleFavorite = onToggleFavorite?.let { toggle -> { toggle(single) } },
                        )
                    }
                } else {
                    Notice("暂无同组版块或合集")
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = Space.xs)) {
                    itemsIndexed(related, key = { _, forum -> forum.key }) { index, forum ->
                        SectionCardItem(
                            position = CardGroupPosition.at(index, related.size),
                            dividerIndent = Space.lg,
                        ) {
                            RelatedForumRow(
                                forum = forum,
                                isCurrent = forum.key == currentForum?.key,
                                isFavorite = forum.key in favoriteForumKeys,
                                onClick = { onSelectForum(forum) },
                                onToggleFavorite = onToggleFavorite?.let { toggle -> { toggle(forum) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedForumRow(
    forum: Forum,
    isCurrent: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 54.dp)
            .padding(horizontal = Space.lg, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Text(
                    text = forum.name,
                    style = typography.topicTitle,
                    color = if (isCurrent) colors.goldText else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (forum.collectionId != null) {
                    GoldTagBadge(text = "合集")
                }
                if (isCurrent) {
                    GoldTagBadge(text = "当前")
                }
            }
            if (forum.description.isNotBlank()) {
                Text(
                    text = forum.description,
                    style = typography.metadata,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onToggleFavorite != null) {
            GoldSwitch(
                checked = isFavorite,
                onCheckedChange = { onToggleFavorite() },
                contentDescription = "本地收藏 ${forum.name}",
            )
        } else {
            Icon(
                imageVector = NagiIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun TopicSortMenu(current: TopicSort, onSelect: (TopicSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(TopicSort.NEWEST to "最新发布", TopicSort.LAST_ACTIVE to "最近活跃")
    val activeLabel = options.first { it.first == current }.second
    Box {
        CapsuleButton(
            text = activeLabel,
            onClick = { expanded = true },
            icon = NagiIcons.Sort,
            trailingIcon = NagiIcons.ChevronDown,
            style = CapsuleStyle.Neutral,
            contentDescription = "排序方式",
            stateDescription = "当前：$activeLabel",
        )
        FrostedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (option, label) ->
                val active = current == option
                FrostedDropdownItem(
                    text = label,
                    selected = active,
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    modifier = Modifier.semantics { selected = active },
                )
            }
        }
    }
}

@Composable
private fun SearchControls(
    scope: Forum?,
    query: String,
    onQueryChange: (String) -> Unit,
    onChooseScope: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        CapsuleButton(
            text = scope?.let { "搜索范围：${it.name}（切换）" } ?: "选择搜索版块",
            onClick = onChooseScope,
            icon = NagiIcons.Forums,
            style = if (scope != null) CapsuleStyle.SelectedGold else CapsuleStyle.Neutral,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = CircleShape,
                placeholder = { Text("搜索主题", style = typography.body, color = colors.textSecondary) },
                leadingIcon = {
                    Icon(
                        imageVector = NagiIcons.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colors.textSecondary,
                    )
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = NagiIcons.ClearCircle,
                                contentDescription = "清空搜索词",
                                modifier = Modifier.size(18.dp),
                                tint = colors.textSecondary,
                            )
                        }
                    }
                } else null,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = colors.cardSurface,
                    focusedContainerColor = colors.cardSurface,
                    unfocusedBorderColor = colors.cardBorder,
                    focusedBorderColor = colors.goldAccent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            )
            CapsuleButton(
                text = "搜索",
                onClick = onSubmit,
                enabled = query.isNotBlank() && scope != null,
                style = CapsuleStyle.SolidGold,
            )
        }
    }
}

@Composable
internal fun FlatTopicCard(
    topic: Topic,
    sort: TopicSort,
    selected: Boolean,
    selectableMode: Boolean,
    margin: Dp = Space.lg,
    onDetails: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = NagiThemeTokens.colors
    FlatCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = margin, vertical = 4.dp),
        shape = NagiThemeTokens.shapes.topicCard,
        containerColor = if (selected) colors.selectedRowFill else colors.cardSurface,
        borderColor = if (selected) colors.goldAccent else colors.cardBorder,
    ) {
        TopicRow(
            topic = topic,
            sort = sort,
            selected = selected,
            selectableMode = selectableMode,
            gutter = 14.dp,
            onDetails = onDetails,
            onClick = onClick,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TopicRow(
    topic: Topic,
    sort: TopicSort,
    selected: Boolean,
    selectableMode: Boolean,
    gutter: Dp,
    onDetails: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val interactionModifier = Modifier
        .testTag("topic-${topic.id}")
        .combinedClickable(
            onClick = onClick,
            onLongClick = onDetails,
            onLongClickLabel = "查看完整标题和时间",
        )
        .semantics {
            if (selectableMode) this.selected = selected
            customActions = listOf(CustomAccessibilityAction("查看主题信息") { onDetails(); true })
        }

    val parsed = remember(topic.title) { parseTopicSubject(topic.title) }
    val hasTopBadges = topic.recommended || parsed.tag != null
    val indicatorColor = colors.goldAccent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                if (selected) {
                    val barW = 3.dp.toPx()
                    val insetY = 10.dp.toPx()
                    val barH = (size.height - insetY * 2f).coerceAtLeast(barW)
                    drawRoundRect(
                        color = indicatorColor,
                        topLeft = Offset(0f, (size.height - barH) / 2f),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barW, barW),
                    )
                }
            }
            .then(interactionModifier),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = gutter, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            if (hasTopBadges) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (topic.recommended) {
                            GoldTagBadge(text = "精选")
                        }
                        if (parsed.tag != null) {
                            NeutralTagBadge(text = parsed.tag)
                        }
                    }
                    ReplyCountPill(count = topic.replies)
                }
                Text(
                    text = parsed.cleanTitle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics { text = AnnotatedString(topic.title) },
                    style = typography.topicTitle,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = colors.textPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    Text(
                        text = parsed.cleanTitle,
                        modifier = Modifier
                            .weight(1f)
                            .clearAndSetSemantics { text = AnnotatedString(topic.title) },
                        style = typography.topicTitle,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = colors.textPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ReplyCountPill(count = topic.replies)
                }
            }

            EdgeAlignedMetaRow(
                leading = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = NagiIcons.Person,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = colors.textSecondary,
                        )
                        Text(
                            text = topic.author.ifBlank { "匿名" },
                            style = typography.metadata,
                            color = colors.textSecondary,
                        )
                    }
                },
                trailing = {
                    val primaryTime = if (sort == TopicSort.NEWEST) topic.createdAt else topic.lastActiveAt
                    val timestamp = primaryTime.takeIf { it > 0 } ?: topic.createdAt.takeIf { it > 0 } ?: topic.lastActiveAt
                    if (timestamp > 0) {
                        Text(
                            text = formatTime(timestamp),
                            style = typography.metadata,
                            color = colors.textSecondary,
                            maxLines = 1,
                        )
                    } else {
                        Spacer(Modifier.size(0.dp))
                    }
                },
            )
        }
    }
}

private data class ParsedTopicSubject(val tag: String?, val cleanTitle: String)

private fun parseTopicSubject(rawTitle: String): ParsedTopicSubject {
    val trimmed = rawTitle.trim()
    if (trimmed.startsWith("[")) {
        val end = trimmed.indexOf(']')
        if (end in 2..16) {
            val tag = trimmed.substring(1, end).trim()
            val remainder = trimmed.substring(end + 1).trimStart()
            if (tag.isNotEmpty() && remainder.isNotEmpty()) {
                return ParsedTopicSubject(tag = tag, cleanTitle = remainder)
            }
        }
    }
    return ParsedTopicSubject(tag = null, cleanTitle = rawTitle)
}

private val SameYearDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val FullYearDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val RawPostSecondsFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

fun formatTime(
    seconds: Long,
    nowSeconds: Long = Instant.now().epochSecond,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    if (seconds <= 0L) return "—"
    val target = Instant.ofEpochSecond(seconds).atZone(zone)
    val now = Instant.ofEpochSecond(nowSeconds).atZone(zone)
    if (target.toLocalDate() == now.toLocalDate()) {
        val diffSeconds = (nowSeconds - seconds).coerceAtLeast(0L)
        return when {
            diffSeconds < 60L -> "刚刚"
            diffSeconds < 3600L -> "${diffSeconds / 60L} 分钟之前"
            else -> "${diffSeconds / 3600L} 小时之前"
        }
    }
    return if (target.year == now.year) {
        SameYearDateFormatter.format(target)
    } else {
        FullYearDateFormatter.format(target)
    }
}

fun formatTimeText(
    rawDate: String,
    nowSeconds: Long = Instant.now().epochSecond,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val trimmed = rawDate.trim()
    if (trimmed.isEmpty()) return rawDate
    trimmed.toLongOrNull()?.takeIf { it > 0L }?.let { epoch ->
        return formatTime(epoch, nowSeconds, zone)
    }
    val parsed = runCatching { LocalDateTime.parse(trimmed, RawPostSecondsFormatter) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(trimmed, FullYearDateFormatter) }.getOrNull()
    return if (parsed != null) {
        formatTime(parsed.atZone(zone).toEpochSecond(), nowSeconds, zone)
    } else {
        rawDate
    }
}
