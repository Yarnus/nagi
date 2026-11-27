package dev.nagi.app.ui.post

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import dev.nagi.sdk.PostReference
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.CardGroupPosition
import dev.nagi.app.ui.components.ChromeIconButton
import dev.nagi.app.ui.components.InlineStatusBanner
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.Notice
import dev.nagi.app.ui.components.ProgressiveBlurScaffold
import dev.nagi.app.ui.components.SectionCardItem
import dev.nagi.app.ui.components.formatAuthorDisplayName
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.viewmodel.ComposePhase
import dev.nagi.app.viewmodel.PostRatingState
import dev.nagi.app.ui.components.LocalAccountIdentity
import dev.nagi.app.viewmodel.ComposerViewModel
import dev.nagi.app.viewmodel.PostRatingViewModel
import dev.nagi.app.viewmodel.PostReferenceState
import dev.nagi.app.viewmodel.ReaderViewModel
import dev.nagi.app.viewmodel.ReplySessionViewModel
import dev.nagi.sdk.Post
import dev.nagi.sdk.PostRating
import dev.nagi.sdk.PublishAction
import dev.nagi.sdk.PublishTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
private fun ProvidePostReferences(
    referencesFlow: StateFlow<Map<String, PostReferenceState>>,
    request: (PostReference, Boolean) -> Unit,
    locate: (PostReference) -> Unit,
    content: @Composable () -> Unit,
) {
    val referenceStates by referencesFlow.collectAsStateWithLifecycle()
    val postReferences = remember(referenceStates, request, locate) {
        PostReferences(referenceStates, request, locate)
    }
    CompositionLocalProvider(LocalPostReferences provides postReferences, content = content)
}

private val LocalPostRatings = compositionLocalOf<Map<String, PostRatingState>> { emptyMap() }

@Composable
private fun ProvidePostRatings(
    ratingsFlow: StateFlow<Map<String, PostRatingState>>,
    content: @Composable () -> Unit,
) {
    val ratingStates by ratingsFlow.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalPostRatings provides ratingStates, content = content)
}

@Composable
private fun ReaderReplyOverlay(
    replies: ReplySessionViewModel,
    preferences: ReadingPreferences,
    onConfirmed: () -> Unit,
) {
    val replySession by replies.session.collectAsStateWithLifecycle()
    val currentOnConfirmed by rememberUpdatedState(onConfirmed)
    LaunchedEffect(replySession?.phase, replies) {
        if (replySession?.phase == ComposePhase.Confirmed) {
            replies.close()
            currentOnConfirmed()
        }
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
            uploadImage = replies::uploadImage,
            onAnonymousChange = replies::setAnonymous,
        )
    }
}

@Composable
private fun ReaderShareCardOverlay(
    topicId: String,
    topicTitle: String,
    mainPost: Post?,
    vm: ReaderViewModel,
    preferences: ReadingPreferences,
) {
    val shareCard by vm.shareCard.collectAsStateWithLifecycle()
    if (shareCard.open) {
        PostShareCardDialog(
            topicId = topicId,
            topicTitle = topicTitle,
            post = mainPost,
            shareState = shareCard,
            preferences = preferences,
            onRetry = vm::retryShareCard,
            close = vm::closeShareCard,
        )
    }
}

@Composable
fun ReaderScreen(
    id: String,
    preferences: ReadingPreferences,
    back: () -> Unit,
    vm: ReaderViewModel = hiltViewModel(),
    replies: ReplySessionViewModel = hiltViewModel(key = "reply:$id"),
    ratings: PostRatingViewModel = hiltViewModel(),
    initialReference: PostReference? = null,
    referenceConsumed: () -> Unit = {},
    initialPage: Int? = null,
    initialPageConsumed: () -> Unit = {},
    onOpenReference: ((PostReference) -> Unit)? = null,
    onFontSizeChange: ((Int) -> Unit)? = null,
    onLineHeightChange: ((Int) -> Unit)? = null,
    onResetTypography: (() -> Unit)? = null,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val viewerId = LocalAccountIdentity.current.userId
    var editingPostId by rememberSaveable(id, viewerId) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    LaunchedEffect(ratings, context) {
        ratings.feedback.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    var visiblePage by remember(id) { mutableIntStateOf(1) }
    val openComposer: (Post, PublishAction) -> Unit = remember(id, replies) {
        { post, action ->
            val target = PublishTarget(action, topicId = id, postId = post.id)
            val label = when (action) {
                PublishAction.COMMENT -> "贴条评论"
                PublishAction.QUOTE -> "引用回复"
                else -> "回复"
            }
            replies.open(target, "$label ${formatAuthorDisplayName(post.author)} · #${post.floor}")
        }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.id, state.loadedRevision) {
        if (state.id == id && state.loadedRevision > 0) ratings.onPostsReloaded(id, state.loadedRevision, state.loadedPostIds)
    }
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    LaunchedEffect(id) { vm.open(id) }
    LaunchedEffect(initialReference) {
        initialReference?.let { vm.locateReference(it); referenceConsumed() }
    }
    LaunchedEffect(initialPage) {
        initialPage?.let { vm.jumpToPage(it); initialPageConsumed() }
    }
    val uriHandler = LocalUriHandler.current
    var viewerImages by rememberSaveable(id) { mutableStateOf<List<String>>(emptyList()) }
    var viewerIndex by rememberSaveable(id) { mutableIntStateOf(0) }
    var adjustingTypography by rememberSaveable(id) { mutableStateOf(false) }
    val canAdjustTypography = onFontSizeChange != null && onLineHeightChange != null
    val data = state.data?.takeIf { state.id == id }
    val favorite = favorites.any { it.topicId == id }
    val locateReference = remember(id, vm, onOpenReference, uriHandler) {
        { target: PostReference ->
            if (target.topicId == id) vm.locateReference(target)
            else if (onOpenReference != null) onOpenReference(target)
            else uriHandler.openUri("https://bbs.nga.cn/read.php?tid=${target.topicId}&pid=${target.postId}&page=${target.page}")
        }
    }
    ProvidePostRatings(ratingsFlow = ratings.states) {
    ProvidePostReferences(
        referencesFlow = vm.references,
        request = vm::resolveReference,
        locate = locateReference,
    ) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (data != null) {
            key(id, state.epoch, state.filterAuthorId) {
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
                            val topicUrl = "https://bbs.nga.cn/read.php?tid=$id"
                            val topicTitle = data.title.ifBlank { "主题" }
                            val filterLabel = state.filterAuthorName?.let { "只看 $it" }
                            ReaderTopBar(
                                title = topicTitle,
                                showTitle = collapsed || items.isEmpty(),
                                favorite = favorite,
                                authorOnly = state.authorOnly,
                                filterLabel = filterLabel,
                                enabled = !state.loading,
                                canFilter = data.authorId.isNotBlank() || state.authorOnly,
                                back = back,
                                page = currentPage,
                                totalPages = state.totalPages,
                                onPage = { jumping = true },
                                onFavorite = vm::toggleFavorite,
                                onFilter = {
                                    if (state.authorOnly) vm.load(page = 1, clearAuthorFilter = true)
                                    else vm.load(page = 1, authorOnly = true)
                                },
                                onRefresh = { vm.load(page = currentPage) },
                                onTypography = if (canAdjustTypography) ({ adjustingTypography = true }) else null,
                                onShareCard = vm::openShareCard,
                                onShare = {
                                    runCatching {
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "$topicTitle $topicUrl")
                                        }
                                        context.startActivity(Intent.createChooser(send, topicTitle))
                                    }.onFailure {
                                        Toast.makeText(context, "无法打开分享菜单", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onCopyLink = {
                                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("NGA 主题链接", topicUrl))
                                    val timestamp = clipboard?.primaryClipDescription?.timestamp ?: 0L
                                    dev.nagi.app.navigation.ClipboardLinkTracker.markHandled(topicUrl, timestamp)
                                    Toast.makeText(context, "已复制主题链接", Toast.LENGTH_SHORT).show()
                                },
                                onOpenBrowser = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, topicUrl.toUri()))
                                    }.onFailure {
                                        Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                                    }
                                },
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
                                    val canCopyText = remember(post.id, post.content) { hasCopyablePlainText(post.content) }
                                    val canFilterAuthor = dev.nagi.app.ui.components.isPublicUserId(post.authorId)
                                    val filteringThisAuthor = state.filterAuthorId == post.authorId
                                    val validPostLink = post.id.toLongOrNull()?.let { it > 0L } == true
                                    val onImageClick = remember(post.id, post.content) {
                                        { clickedUrl: String ->
                                            val (images, matchedIndex) = resolveContentImageClick(post.content, clickedUrl)
                                            viewerImages = images
                                            viewerIndex = matchedIndex
                                        }
                                    }
                                    val onReplyClick = remember(post, openComposer) { { openComposer(post, PublishAction.REPLY) } }
                                    val onCommentClick = remember(post, openComposer) { { openComposer(post, PublishAction.COMMENT) } }
                                    val onRatingClick = remember(id, post.id, ratings) {
                                        { rating: PostRating -> ratings.rate(id, post.id, rating) }
                                    }
                                    val onEditClick = remember(post.id) { { editingPostId = post.id } }
                                    val onFilterAuthorClick = remember(post.authorId, post.author, filteringThisAuthor, vm) {
                                        if (canFilterAuthor) {
                                            {
                                                if (filteringThisAuthor) vm.load(page = 1, clearAuthorFilter = true)
                                                else vm.load(page = 1, filterAuthorId = post.authorId, filterAuthorName = formatAuthorDisplayName(post.author))
                                            }
                                        } else null
                                    }
                                    val onCopyTextClick = remember(post.id, post.content, canCopyText, context) {
                                        if (canCopyText) {
                                            {
                                                val copiedText = contentPlainText(post.content)
                                                if (copiedText.isNotEmpty()) {
                                                    context.getSystemService(ClipboardManager::class.java)
                                                        ?.setPrimaryClip(ClipData.newPlainText("帖子内容", copiedText))
                                                    Toast.makeText(context, "已复制楼层内容", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } else null
                                    }
                                    val onCopyLinkClick = remember(id, post.id, validPostLink, context) {
                                        if (validPostLink) {
                                            {
                                                val postUrl = "https://bbs.nga.cn/read.php?tid=$id&pid=${post.id}"
                                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                                clipboard?.setPrimaryClip(ClipData.newPlainText("NGA 楼层链接", postUrl))
                                                val timestamp = clipboard?.primaryClipDescription?.timestamp ?: 0L
                                                dev.nagi.app.navigation.ClipboardLinkTracker.markHandled(postUrl, timestamp)
                                                Toast.makeText(context, "已复制楼层链接", Toast.LENGTH_SHORT).show()
                                            }
                                        } else null
                                    }
                                    val onLocateHotReplyClick = remember(id, vm) {
                                        { reply: Post -> vm.locateReference(PostReference(id, reply.id)) }
                                    }
                                    ReaderPost(
                                        post = post,
                                        preferences = preferences,
                                        isAuthor = post.authorId.isNotBlank() && post.authorId == data.authorId,
                                        title = data.title.takeIf { index == 0 },
                                        onImage = onImageClick,
                                        highlighted = highlighted == post.floor,
                                        onReply = onReplyClick,
                                        onComment = onCommentClick,
                                        ratingState = LocalPostRatings.current["$id:${post.id}"],
                                        onRating = onRatingClick,
                                        viewerId = viewerId,
                                        onEdit = onEditClick,
                                        filteringThisAuthor = filteringThisAuthor,
                                        onFilterAuthor = onFilterAuthorClick,
                                        onCopyText = onCopyTextClick,
                                        onCopyLink = onCopyLinkClick,
                                        onShareCard = if (post.floor == 0) vm::openShareCard else null,
                                        topicAuthorId = data.authorId,
                                        showHotReplies = data.page == 1,
                                        onLocateHotReply = onLocateHotReplyClick,
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
                if (adjustingTypography && onFontSizeChange != null && onLineHeightChange != null) {
                    ReaderTypographySheet(
                        preferences = preferences,
                        onFontSizeChange = onFontSizeChange,
                        onLineHeightChange = onLineHeightChange,
                        onReset = onResetTypography,
                        dismiss = { adjustingTypography = false },
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
    ReaderReplyOverlay(
        replies = replies,
        preferences = preferences,
        onConfirmed = { vm.load(page = visiblePage) },
    )
    if (viewerImages.isNotEmpty()) {
        ImageViewer(
            urls = viewerImages,
            initialIndex = viewerIndex,
            close = { viewerImages = emptyList() },
        )
    }
    if (state.id == id) {
        ReaderShareCardOverlay(
            topicId = id,
            topicTitle = data?.title.orEmpty(),
            mainPost = state.mainPost ?: state.items.firstOrNull { it.floor == 0 },
            vm = vm,
            preferences = preferences,
        )
    }
}


private const val AUTO_LOAD_DISTANCE = 3

