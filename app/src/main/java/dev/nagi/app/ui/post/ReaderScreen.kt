package dev.nagi.app.ui.post

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.CardGroupPosition
import dev.nagi.app.ui.components.ChromeIconButton
import dev.nagi.app.ui.components.EdgeAlignedMetaRow
import dev.nagi.app.ui.components.FlatCard
import dev.nagi.app.ui.components.FrostedSurface
import dev.nagi.app.ui.components.GoldTagBadge
import dev.nagi.app.ui.components.InlineStatusBanner
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.Notice
import dev.nagi.app.ui.components.ProgressiveBlurScaffold
import dev.nagi.app.ui.components.SectionCardItem
import dev.nagi.app.ui.components.SegmentedCapsuleControl
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.ui.topic.formatTimeText
import dev.nagi.app.viewmodel.ComposePhase
import dev.nagi.app.viewmodel.PostRatingState
import dev.nagi.app.ui.components.LocalAccountIdentity
import dev.nagi.app.viewmodel.ComposerViewModel
import dev.nagi.app.viewmodel.PostRatingViewModel
import dev.nagi.app.viewmodel.ReaderViewModel
import dev.nagi.app.viewmodel.ReplySessionViewModel
import dev.nagi.sdk.Post
import dev.nagi.sdk.PostRating
import dev.nagi.sdk.PublishAction
import dev.nagi.sdk.PublishTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun ReaderScreen(
    id: String,
    preferences: ReadingPreferences,
    back: () -> Unit,
    vm: ReaderViewModel = hiltViewModel(),
    replies: ReplySessionViewModel = hiltViewModel(),
    ratings: PostRatingViewModel = hiltViewModel(),
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val viewerId = LocalAccountIdentity.current.userId
    var editingPostId by rememberSaveable(id, viewerId) { mutableStateOf<String?>(null) }
    val ratingStates by ratings.states.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(ratings, context) {
        ratings.feedback.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    val replySession by replies.session.collectAsStateWithLifecycle()
    var visiblePage by remember(id) { mutableStateOf(1) }
    LaunchedEffect(replySession?.phase) {
        if (replySession?.phase == ComposePhase.Confirmed) {
            replies.close()
            vm.load(page = visiblePage)
        }
    }
    val openComposer: (Post, PublishAction) -> Unit = { post, action ->
        val target = PublishTarget(action, topicId = id, postId = post.id)
        val label = when (action) {
            PublishAction.COMMENT -> "贴条评论"
            PublishAction.QUOTE -> "引用回复"
            else -> "回复"
        }
        replies.open(target, "$label ${post.author} · #${post.floor}")
    }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.id, state.loadedRevision) {
        if (state.id == id && state.loadedRevision > 0) ratings.onPostsReloaded(id, state.loadedRevision, state.loadedPostIds)
    }
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    LaunchedEffect(id) { vm.open(id) }
    var image by rememberSaveable(id) { mutableStateOf<String?>(null) }
    val data = state.data?.takeIf { state.id == id }
    val favorite = favorites.any { it.topicId == id }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (data != null) {
            key(id, state.epoch, state.authorOnly) {
                val items = state.items
                val position = state.position
                val floorIndex = state.targetFloor?.let { floor ->
                    items.indexOfFirst { it.floor == floor }.takeIf { it >= 0 }
                        ?: items.indexOfFirst { it.floor > floor }.takeIf { it >= 0 } ?: items.lastIndex
                }?.takeIf { it >= 0 }
                val startIndex = floorIndex ?: position?.let { state.itemIndex(it.page, it.index) } ?: 0
                val startOffset = if (floorIndex != null) 0 else position?.takeIf { state.itemIndex(it.page, it.index) != null }?.offset ?: 0
                val list = rememberLazyListState(state.headerCount + startIndex, startOffset)
                var highlighted by remember { mutableStateOf(floorIndex?.let { items[it].floor }) }
                LaunchedEffect(highlighted) {
                    if (highlighted != null) {
                        delay(1500)
                        highlighted = null
                    }
                }
                val collapsed by remember {
                    derivedStateOf {
                        list.firstVisibleItemIndex > state.headerCount ||
                            (list.firstVisibleItemIndex == state.headerCount && list.firstVisibleItemScrollOffset > 0)
                    }
                }
                val currentPage by remember { derivedStateOf { state.locate(list.firstVisibleItemIndex)?.first ?: data.page } }
                LaunchedEffect(currentPage) { visiblePage = currentPage }
                var jumping by remember { mutableStateOf(false) }
                LaunchedEffect(list, state.loading) {
                    if (!state.loading) {
                        snapshotFlow { Triple(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, list.isScrollInProgress) }
                            .filter { !it.third }
                            .distinctUntilChanged()
                            .collect { (index, offset) -> vm.saveVisible(index, offset, topicId = id) }
                    }
                }
                LaunchedEffect(list, state.epoch) {
                    var armed = true
                    snapshotFlow {
                        val info = list.layoutInfo
                        val scrolled = list.firstVisibleItemIndex > state.headerCount ||
                            (list.firstVisibleItemIndex == state.headerCount && list.firstVisibleItemScrollOffset > 0)
                        Triple(scrolled, info.visibleItemsInfo.lastOrNull()?.index ?: -1, info.totalItemsCount)
                    }.distinctUntilChanged().collect { (scrolled, last, total) ->
                        val nearEnd = total > 0 && last >= total - 1 - AUTO_LOAD_DISTANCE
                        if (!nearEnd) {
                            armed = true
                        } else if (armed && scrolled) {
                            armed = false
                            vm.loadNext()
                        }
                    }
                }
                ReaderScaffold(
                    modifier = Modifier.widthIn(max = 760.dp),
                    scrollInProgress = list.isScrollInProgress,
                    onReply = { replies.open(PublishTarget(PublishAction.REPLY, topicId = id), "回复帖子") },
                    topBar = {
                        Column {
                            ReaderTopBar(
                                title = data.title.ifBlank { "主题" },
                                showTitle = collapsed || items.isEmpty(),
                                favorite = favorite,
                                authorOnly = state.authorOnly,
                                enabled = !state.loading,
                                canFilter = data.authorId.isNotBlank(),
                                back = back,
                                page = currentPage,
                                totalPages = state.totalPages,
                                onPage = { jumping = true },
                                onFavorite = vm::toggleFavorite,
                                onFilter = { vm.load(page = 1, authorOnly = !state.authorOnly) },
                                onRefresh = { vm.load(page = currentPage) },
                            )
                            if (state.loading) LoadingLine()
                            state.error?.let { error ->
                                Box(Modifier.testTag("reader-error")) {
                                    InlineStatusBanner(error, retry = { vm.load() })
                                }
                            }
                        }
                    },
                ) { padding ->
                    val hasMain = items.firstOrNull()?.floor == 0
                    val mainCount = if (hasMain) 1 else 0
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = list,
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding(),
                        ),
                    ) {
                        if (state.headerCount > 0) {
                            item(key = "previous-page", contentType = "edge") {
                                val target = state.startPage - 1
                                val label = state.previousError?.let { "$it，点击重试第 $target 页" }
                                    ?: if (state.loadingPrevious) "正在加载第 $target 页…" else "加载第 $target 页"
                                ReaderEdgeButton(
                                    label = label,
                                    loading = state.loadingPrevious,
                                    onClick = vm::loadPrevious,
                                    modifier = if (state.previousError != null) Modifier.testTag("reader-error") else Modifier,
                                )
                            }
                        }
                        itemsIndexed(items, key = { _, post -> post.id }, contentType = { _, _ -> "post" }) { index, post ->
                            val main = hasMain && index == 0
                            val group = if (main) CardGroupPosition.Single else CardGroupPosition.at(index - mainCount, items.size - mainCount)
                            Column(Modifier.fillMaxWidth()) {
                                if (hasMain && index == 1) Spacer(Modifier.height(16.dp))
                                SectionCardItem(group) {
                                    ReaderPost(
                                        post = post,
                                        preferences = preferences,
                                        isAuthor = post.authorId.isNotBlank() && post.authorId == data.authorId,
                                        title = data.title.takeIf { index == 0 },
                                        onImage = { image = it },
                                        highlighted = highlighted == post.floor,
                                        onReply = { openComposer(post, PublishAction.REPLY) },
                                        onComment = { openComposer(post, PublishAction.COMMENT) },
                                        ratingState = ratingStates["$id:${post.id}"],
                                        onRating = { ratings.rate(id, post.id, it) },
                                        viewerId = viewerId,
                                        onEdit = { editingPostId = post.id },
                                    )
                                }
                            }
                        }
                        if (items.isEmpty()) item { Notice("本页没有可显示的帖子") }
                        item(key = "page-end", contentType = "edge") {
                            when {
                                state.moreError != null -> ReaderEdgeButton(
                                    label = "${state.moreError}，点击重试",
                                    loading = false,
                                    onClick = vm::retryMore,
                                    modifier = Modifier.testTag("reader-error"),
                                )
                                state.hasMore -> ReaderEdgeButton(
                                    label = if (state.loadingMore) "正在加载第 ${state.endPage + 1} 页…" else "加载第 ${state.endPage + 1} 页",
                                    loading = state.loadingMore,
                                    onClick = vm::loadNext,
                                )
                                items.isNotEmpty() -> Text(
                                    text = "已到最后一页",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Space.xl),
                                    textAlign = TextAlign.Center,
                                    style = typography.metadata,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
                if (jumping) {
                    PageJumpSheet(
                        currentPage = currentPage,
                        totalPages = state.totalPages,
                        maxFloor = (data.total - 1).coerceAtLeast(0),
                        canJumpFloor = state.canJumpFloor,
                        dismiss = { jumping = false },
                        onPage = {
                            jumping = false
                            vm.jumpToPage(it)
                        },
                        onFloor = {
                            jumping = false
                            vm.jumpToFloor(it)
                        },
                    )
                }
            }
        } else {
            ProgressiveBlurScaffold(
                modifier = Modifier.widthIn(max = 760.dp),
                topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.lg, vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChromeIconButton(NagiIcons.Back, "返回", back)
                        Text(
                            text = "阅读",
                            modifier = Modifier.padding(start = Space.md),
                            style = typography.appBarTitle,
                            color = colors.textPrimary,
                        )
                    }
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .then(if (state.error != null) Modifier.testTag("reader-error") else Modifier),
                ) {
                    if (state.loading) LoadingLine()
                    Notice(state.error ?: "正在加载帖子…", if (state.error != null) ({ vm.load() }) else null)
                }
            }
        }
    }
    editingPostId?.let { postId ->
        val editor: ComposerViewModel = hiltViewModel(key = "reader-edit:$id:$postId")
        val draft by editor.state.collectAsStateWithLifecycle()
        LaunchedEffect(id, postId) {
            editor.open(PublishTarget(PublishAction.MODIFY, topicId = id, postId = postId), "编辑原帖")
        }
        LaunchedEffect(draft.phase) {
            if (draft.phase == ComposePhase.Confirmed) {
                editingPostId = null
                editor.close()
                vm.load(page = visiblePage)
            }
        }
        ComposerScreen(
            state = draft, preferences = preferences, edit = editor::edit, send = editor::send,
            retry = editor::prepare, close = { editor.close(); editingPostId = null },
            details = editor::details, editValue = editor::editValue, togglePreview = editor::preview,
        )
    }
    replySession?.let { session ->
        ReplyPopup(
            session = session,
            preferences = preferences,
            edit = replies::edit,
            send = replies::send,
            retry = replies::prepare,
            preview = replies::preview,
            close = replies::close,
            expandContext = replies::expandContext,
        )
    }
    image?.let { url ->
        ImageViewer(url = url, close = { image = null })
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    showTitle: Boolean,
    favorite: Boolean,
    authorOnly: Boolean,
    enabled: Boolean,
    canFilter: Boolean,
    back: () -> Unit,
    page: Int = 0,
    totalPages: Int = 0,
    onPage: () -> Unit = {},
    onFavorite: () -> Unit,
    onFilter: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChromeIconButton(NagiIcons.Back, "返回", back)
        Text(
            text = if (showTitle) title else "阅读",
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Space.sm),
            style = typography.appBarTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = colors.textPrimary,
        )
        if (page > 0) {
            val pageText = if (totalPages > 0) "$page / $totalPages" else "$page"
            val description = buildString {
                append("第 $page 页")
                if (totalPages > 0) append("，共 $totalPages 页")
                if (authorOnly) append("，只看楼主")
            }
            Box(
                modifier = Modifier
                    .padding(end = Space.xs)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, role = Role.Button, onClickLabel = "跳转到指定页或楼层", onClick = onPage)
                    .semantics {
                        contentDescription = description
                        stateDescription = if (authorOnly) "只看楼主" else "全部回复"
                    },
                contentAlignment = Alignment.Center,
            ) {
                FrostedSurface(
                    backgroundColor = if (authorOnly) colors.goldContainer else colors.frostedChrome,
                    borderColor = if (authorOnly) colors.goldBorder else colors.cardBorder,
                ) {
                    Row(
                        modifier = Modifier.heightIn(min = 42.dp).padding(horizontal = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        if (authorOnly) {
                            Icon(
                                imageVector = NagiIcons.Person,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = colors.goldText,
                            )
                        }
                        Text(
                            text = pageText,
                            style = typography.capsuleLabel,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            color = (if (authorOnly) colors.goldText else colors.textPrimary)
                                .copy(alpha = if (enabled) 1f else 0.4f),
                        )
                        Icon(
                            imageVector = NagiIcons.ChevronDown,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colors.textSecondary.copy(alpha = if (enabled) 1f else 0.4f),
                        )
                    }
                }
            }
        }
        Box {
            ChromeIconButton(
                icon = NagiIcons.More,
                description = "主题操作",
                onClick = { menu = true },
                state = if (favorite) "已收藏" else "未收藏",
            )
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                shape = NagiThemeTokens.shapes.dropdown,
                containerColor = colors.frostedDropdown,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, colors.cardBorder),
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (authorOnly) "查看全部" else "只看楼主",
                            style = typography.body,
                            color = if (authorOnly) colors.goldText else colors.textPrimary,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (authorOnly) NagiIcons.Check else NagiIcons.Person,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (authorOnly) colors.goldText else colors.textSecondary,
                        )
                    },
                    enabled = enabled && canFilter,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { selected = authorOnly },
                    onClick = {
                        menu = false
                        onFilter()
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs),
                    thickness = 1.dp,
                    color = colors.cardBorder,
                )
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = if (favorite) "取消本地收藏" else "本地收藏",
                                style = typography.body,
                                color = if (favorite) colors.goldText else colors.textPrimary,
                            )
                            Text(
                                text = "仅保存在此设备",
                                style = typography.metadata,
                                color = colors.textSecondary,
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = NagiIcons.Bookmark,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (favorite) colors.goldText else colors.textSecondary,
                        )
                    },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = {
                        menu = false
                        onFavorite()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text("刷新", style = typography.body, color = colors.textPrimary)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = NagiIcons.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colors.textSecondary,
                        )
                    },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = {
                        menu = false
                        onRefresh()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderPost(
    post: Post,
    preferences: ReadingPreferences,
    isAuthor: Boolean,
    title: String?,
    onImage: (String) -> Unit,
    highlighted: Boolean = false,
    onReply: (() -> Unit)? = null,
    onComment: (() -> Unit)? = null,
    ratingState: PostRatingState? = null,
    onRating: ((PostRating) -> Unit)? = null,
    viewerId: String? = null,
    onEdit: (() -> Unit)? = null,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val openUser = dev.nagi.app.ui.components.LocalOpenUser.current
    val canOpenUser = openUser != null && dev.nagi.app.ui.components.isPublicUserId(post.authorId)
    val tint = if (highlighted) colors.goldContainer else Color.Transparent
    val metaSegments = remember(post.authorReputation, post.authorPosts) {
        buildList {
            post.authorReputation?.takeIf { it.isNotBlank() }?.let { add("威望: $it") }
            post.authorPosts?.let { add("发帖: $it") }
        }
    }
    val displayScore = post.score + (ratingState?.scoreDelta ?: 0)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint)
            .padding(horizontal = Space.lg)
            .padding(top = Space.md, bottom = Space.sm),
    ) {
        EdgeAlignedMetaRow(
            verticalAlignment = Alignment.Top,
            leading = {
                Row(
                    modifier = if (canOpenUser) Modifier
                        .clickable(onClickLabel = "查看 ${post.author} 的用户详情") { openUser?.invoke(post.authorId) }
                        .heightIn(min = 48.dp) else Modifier,
                    verticalAlignment = Alignment.Top,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colors.insetContainer,
                        border = BorderStroke(1.dp, colors.cardBorder),
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp,
                    ) {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = NagiIcons.Person,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = colors.textSecondary,
                            )
                            if (post.avatar.isNotBlank()) {
                                AsyncImage(
                                    model = post.avatar,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = Space.md),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.xs),
                        ) {
                            Text(
                                text = post.author,
                                modifier = Modifier.weight(1f, fill = false),
                                style = typography.bodyText.copy(fontWeight = FontWeight.Medium),
                                color = colors.textPrimary,
                            )
                            if (isAuthor) {
                                GoldTagBadge(text = "楼主")
                            }
                        }
                        if (metaSegments.isNotEmpty()) {
                            Text(
                                text = metaSegments.joinToString("  "),
                                style = typography.metadata,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            },
            trailing = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (post.date.isNotBlank()) {
                        Text(
                            text = formatTimeText(post.date),
                            style = typography.metadata,
                            color = colors.textSecondary,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ReaderClientBadge(post.clientPlatform)
                        Text(
                            text = "#${post.floor}",
                            modifier = Modifier.semantics { contentDescription = "第 ${post.floor} 楼" },
                            style = typography.metadata,
                            color = colors.textSecondary,
                        )
                    }
                }
            },
        )
        if (!title.isNullOrBlank()) {
            Spacer(Modifier.height(Space.md))
            SelectionContainer {
                Text(
                    text = title,
                    style = typography.sectionTitle.copy(fontSize = 18.5.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        SelectionContainer { ContentBlocks(post.content, preferences, onImage) }
        if (post.comments.isNotEmpty()) {
            var expandedComments by rememberSaveable(post.id) { mutableStateOf(false) }
            FlatCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.md),
                shape = RoundedCornerShape(12.dp),
                containerColor = colors.insetContainer,
                borderColor = colors.cardBorder,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    post.comments.take(3).forEach { comment ->
                        Text(
                            text = comment.author,
                            style = typography.capsuleLabel,
                            color = colors.goldText,
                            modifier = Modifier.padding(top = 2.dp)
                                .clickable(enabled = openUser != null && dev.nagi.app.ui.components.isPublicUserId(comment.authorId), onClickLabel = "查看用户详情") { openUser?.invoke(comment.authorId) }
                                .heightIn(min = 48.dp),
                        )
                        SelectionContainer { ContentBlocks(comment.content, preferences, onImage) }
                    }
                }
            }
            if (post.comments.size > 3) {
                Box(Modifier.padding(top = Space.xs)) {
                    CapsuleButton(
                        text = "查看贴条（${post.comments.size}）",
                        onClick = { expandedComments = !expandedComments },
                        style = CapsuleStyle.Neutral,
                    )
                }
            }
            if (expandedComments) {
                Dialog(onDismissRequest = { expandedComments = false }) {
                    FlatCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = NagiThemeTokens.shapes.cardGroup,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 600.dp)
                                .padding(Space.lg),
                            verticalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("全部贴条", style = typography.sectionTitle, color = colors.textPrimary)
                                CapsuleButton(
                                    text = "关闭贴条",
                                    onClick = { expandedComments = false },
                                    style = CapsuleStyle.Neutral,
                                )
                            }
                            LazyColumn {
                                itemsIndexed(post.comments, key = { index, comment -> "${comment.id}:$index" }) { idx, comment ->
                                     Column(Modifier.padding(vertical = 10.dp)) {
                                        Text(
                                            text = comment.author,
                                            style = typography.capsuleLabel,
                                            color = colors.goldText,
                                            modifier = Modifier.heightIn(min = 48.dp)
                                                .clickable(enabled = openUser != null && dev.nagi.app.ui.components.isPublicUserId(comment.authorId), onClickLabel = "查看用户详情") { expandedComments = false; openUser?.invoke(comment.authorId) },
                                        )
                                        SelectionContainer { ContentBlocks(comment.content, preferences, onImage) }
                                    }
                                    if (idx < post.comments.lastIndex) {
                                        HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (post.id.toLongOrNull()?.let { it >= 0 } == true && onReply != null) {
            var actions by remember { mutableStateOf(false) }
            val ratingEnabled = ratingState?.enabled != false
            val upConfirmed = ratingState?.rating == PostRating.UP
            val downConfirmed = ratingState?.rating == PostRating.DOWN
            val pendingRating = ratingState?.pendingRating.takeIf { ratingState?.phase == ComposePhase.Submitting }
            val voteDescription = when (ratingState?.phase) {
                ComposePhase.Submitting -> "处理中"
                ComposePhase.Unconfirmed -> "结果未确认，请刷新核对"
                else -> null
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.xs),
                horizontalArrangement = Arrangement.End,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onRating != null) {
                        Row(
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .clip(CircleShape)
                                .clickable(
                                    enabled = ratingEnabled,
                                    role = Role.Button,
                                    onClick = { onRating(PostRating.UP) },
                                )
                                .padding(horizontal = 8.dp)
                                .semantics {
                                    contentDescription = "点赞第 ${post.floor} 楼"
                                    selected = upConfirmed
                                    stateDescription = voteDescription ?: if (upConfirmed) "已点赞，再次点击取消；净赞 $displayScore" else "未点赞；净赞 $displayScore"
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (pendingRating == PostRating.UP) {
                                CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = colors.goldAccent)
                            } else {
                                Icon(
                                    imageVector = NagiIcons.ThumbUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                    tint = if (upConfirmed) colors.goldText else colors.textSecondary,
                                )
                            }
                            Text(
                                text = displayScore.toString(),
                                style = typography.metadata.copy(
                                    fontWeight = if (displayScore > 0 || upConfirmed) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = if (upConfirmed) colors.goldText else colors.textSecondary,
                            )
                        }
                        IconButton(
                            onClick = { onRating(PostRating.DOWN) },
                            enabled = ratingEnabled,
                            modifier = Modifier.size(48.dp).semantics {
                                contentDescription = "点踩第 ${post.floor} 楼"
                                selected = downConfirmed
                                stateDescription = voteDescription ?: if (downConfirmed) "已点踩，再次点击取消" else "未点踩"
                            },
                        ) {
                            if (pendingRating == PostRating.DOWN) {
                                CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = colors.goldAccent)
                            } else {
                                Icon(
                                    imageVector = NagiIcons.ThumbDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                    tint = if (downConfirmed) colors.goldText else colors.textSecondary,
                                )
                            }
                        }
                    }
                    IconButton(onClick = onReply, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = NagiIcons.Reply,
                            contentDescription = "回复第 ${post.floor} 楼",
                            modifier = Modifier.size(18.dp),
                            tint = colors.textSecondary,
                        )
                    }
                    Box {
                        IconButton(onClick = { actions = true }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = NagiIcons.More,
                                contentDescription = "第 ${post.floor} 楼更多操作",
                                modifier = Modifier.size(18.dp),
                                tint = colors.textSecondary,
                            )
                        }
                        DropdownMenu(
                            expanded = actions,
                            onDismissRequest = { actions = false },
                            modifier = Modifier.widthIn(min = 184.dp),
                            shape = NagiThemeTokens.shapes.dropdown,
                            containerColor = colors.frostedDropdown,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            border = BorderStroke(1.dp, colors.cardBorder),
                        ) {
                            if (onRating != null) {
                                listOf(PostRating.UP to "点赞", PostRating.DOWN to "踩").forEach { (rating, label) ->
                                    val selectedRating = ratingState?.rating == rating
                                    DropdownMenuItem(
                                        text = { Text(if (selectedRating) "取消$label" else label, style = typography.body, color = colors.textPrimary) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (rating == PostRating.UP) NagiIcons.ThumbUp else NagiIcons.ThumbDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = if (selectedRating) colors.goldText else colors.textSecondary,
                                            )
                                        },
                                        enabled = ratingEnabled,
                                        modifier = Modifier.heightIn(min = 48.dp).semantics { selected = selectedRating },
                                        onClick = {
                                            actions = false
                                            onRating(rating)
                                        },
                                    )
                                }
                            }
                            if (onEdit != null && !viewerId.isNullOrBlank() && viewerId == post.authorId) {
                                DropdownMenuItem(
                                    text = { Text(if (post.floor == 0) "编辑帖子" else "编辑回复", style = typography.body, color = colors.textPrimary) },
                                    leadingIcon = { Icon(NagiIcons.Compose, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textSecondary) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    onClick = { actions = false; onEdit() },
                                )
                            }
                            if (onComment != null) {
                                DropdownMenuItem(
                                    text = { Text("贴条评论", style = typography.body, color = colors.textPrimary) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = NagiIcons.Message,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = colors.textSecondary,
                                        )
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    onClick = {
                                        actions = false
                                        onComment()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val AUTO_LOAD_DISTANCE = 3

@Composable
private fun ReaderEdgeButton(
    label: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = !loading, onClick = onClick)
            .padding(Space.lg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = colors.goldAccent,
            )
        }
        Text(
            text = label,
            modifier = Modifier.padding(start = if (loading) Space.sm else 0.dp),
            style = typography.capsuleLabel,
            color = colors.textSecondary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageJumpSheet(
    currentPage: Int,
    totalPages: Int,
    maxFloor: Int,
    canJumpFloor: Boolean,
    dismiss: () -> Unit,
    onPage: (Int) -> Unit,
    onFloor: (Int) -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    var byFloor by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf(currentPage.toString()) }
    val floorMode = byFloor && canJumpFloor
    val limit = if (floorMode) maxFloor else totalPages
    val value = input.toIntOrNull()
    val valid = value != null && (if (floorMode) value in 0..maxFloor else value >= 1 && (totalPages == 0 || value <= totalPages))
    val submit = {
        if (valid) {
            if (floorMode) onFloor(value!!) else onPage(value!!)
        }
    }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        shape = NagiThemeTokens.shapes.bottomSheet,
        containerColor = colors.groupedCanvas,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .padding(bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            if (canJumpFloor) {
                SegmentedCapsuleControl(
                    options = listOf("跳页", "跳楼"),
                    selectedIndex = if (floorMode) 1 else 0,
                    onSelect = { idx ->
                        byFloor = idx == 1
                        input = if (byFloor) "0" else currentPage.toString()
                    },
                )
            } else {
                Text("跳页", style = typography.sectionTitle, color = colors.textPrimary)
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit).take(9) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = NagiThemeTokens.shapes.topicCard,
                textStyle = typography.body.copy(color = colors.textPrimary),
                label = {
                    Text(
                        text = if (floorMode) "楼层 (0–$maxFloor)" else if (limit > 0) "页码 (1–$limit)" else "页码",
                        style = typography.metadata,
                    )
                },
                isError = input.isNotEmpty() && !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.cardSurface,
                    unfocusedContainerColor = colors.cardSurface,
                    focusedBorderColor = colors.goldAccent,
                    unfocusedBorderColor = colors.cardBorder,
                ),
            )
            if (!floorMode && totalPages > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    CapsuleButton(
                        text = "首页",
                        onClick = { onPage(1) },
                        style = CapsuleStyle.Neutral,
                    )
                    CapsuleButton(
                        text = "末页",
                        onClick = { onPage(totalPages) },
                        style = CapsuleStyle.Neutral,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                CapsuleButton(
                    text = "跳转",
                    onClick = { submit() },
                    enabled = valid,
                    style = CapsuleStyle.SolidGold,
                )
            }
        }
    }
}
