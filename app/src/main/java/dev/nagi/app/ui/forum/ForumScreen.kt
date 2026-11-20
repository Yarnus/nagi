package dev.nagi.app.ui.forum

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nagi.app.ui.components.AccountMenuButton
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.CardGroupPosition
import dev.nagi.app.ui.components.ChromeIconButton
import dev.nagi.app.ui.components.FrostedDropdownDivider
import dev.nagi.app.ui.components.FrostedDropdownItem
import dev.nagi.app.ui.components.FrostedDropdownMenu
import dev.nagi.app.ui.components.FrostedSurface
import dev.nagi.app.ui.components.GoldTagBadge
import dev.nagi.app.ui.components.InlineStatusBanner
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.LocalOpenLogin
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.NagiPullToRefreshBox
import dev.nagi.app.ui.components.NeutralTagBadge
import dev.nagi.app.ui.components.Notice
import dev.nagi.app.ui.components.ProgressiveBlurScaffold
import dev.nagi.app.ui.components.SearchCapsuleBar
import dev.nagi.app.ui.components.SectionCardItem
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.viewmodel.LibraryViewModel
import dev.nagi.app.viewmodel.ListState
import dev.nagi.sdk.Forum

@Composable
fun ForumScreen(
    vm: LibraryViewModel,
    onSelect: (String) -> Unit,
    onForum: (Forum) -> Unit,
) {
    val state by vm.forums.collectAsStateWithLifecycle()
    val cloudFavorites by vm.cloudFavoriteForums.collectAsStateWithLifecycle()
    val cloudBusyKeys by vm.cloudForumBusyKeys.collectAsStateWithLifecycle()
    val cloudUncertainKeys by vm.cloudForumUncertainKeys.collectAsStateWithLifecycle()
    val cloudNotice by vm.cloudForumNotice.collectAsStateWithLifecycle()
    val hasCustomCloudOrder by vm.hasCustomCloudForumOrder.collectAsStateWithLifecycle()
    ForumDirectory(
        state = state,
        refresh = vm::loadForums,
        onSelect = onSelect,
        onForum = onForum,
        isSignedIn = vm.isSignedIn,
        cloudFavoriteForums = cloudFavorites,
        cloudBusyKeys = cloudBusyKeys,
        cloudUncertainKeys = cloudUncertainKeys,
        cloudNotice = cloudNotice,
        hasCustomCloudOrder = hasCustomCloudOrder,
        onRefreshCloudFavorites = vm::loadCloudFavoriteForums,
        onToggleCloudFavorite = vm::toggleCloudForumFavorite,
        onSaveCloudOrder = vm::saveCloudFavoriteForumOrder,
        onResetCloudOrder = vm::resetCloudFavoriteForumOrder,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun ForumDirectory(
    state: ListState<Forum>,
    refresh: () -> Unit,
    onSelect: (String) -> Unit,
    onForum: (Forum) -> Unit,
    isSignedIn: Boolean = false,
    cloudFavoriteForums: ListState<Forum> = ListState(),
    cloudBusyKeys: Set<String> = emptySet(),
    cloudUncertainKeys: Set<String> = emptySet(),
    cloudNotice: String? = null,
    hasCustomCloudOrder: Boolean = false,
    onRefreshCloudFavorites: (() -> Unit)? = null,
    onToggleCloudFavorite: ((Forum) -> Unit)? = null,
    onSaveCloudOrder: ((List<String>) -> Unit)? = null,
    onResetCloudOrder: (() -> Unit)? = null,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val openLogin = LocalOpenLogin.current
    val haptic = LocalHapticFeedback.current
    val cloudKeys = remember(cloudFavoriteForums.items) { cloudFavoriteForums.items.map { it.key }.toSet() }
    var onlyCloudFavorites by rememberSaveable { mutableStateOf(false) }
    val activeCloudFilter = onlyCloudFavorites && isSignedIn
    var menuExpanded by remember { mutableStateOf(false) }
    var isReorderingCloud by rememberSaveable { mutableStateOf(false) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var draggingOffsetY by remember { mutableFloatStateOf(0f) }

    val cloudResolvedForums = remember(cloudFavoriteForums.items, state.items) {
        cloudFavoriteForums.items.resolveCloudForumMetadata(state.items)
    }
    var reorderingList by remember { mutableStateOf(cloudResolvedForums) }
    LaunchedEffect(cloudResolvedForums, isSignedIn, activeCloudFilter) {
        if (!isSignedIn || activeCloudFilter || cloudResolvedForums.size <= 1) {
            isReorderingCloud = false
            draggingKey = null
            draggingOffsetY = 0f
        }
        if (!isReorderingCloud) {
            reorderingList = cloudResolvedForums
        }
    }

    val visibleItems = remember(state.items, activeCloudFilter, cloudResolvedForums) {
        if (activeCloudFilter) cloudResolvedForums else state.items
    }
    val grouped = remember(visibleItems) { visibleItems.groupBy { it.category to it.group } }
    val allGroupKeys = remember(grouped) { grouped.keys.map { "${it.first}\u0000${it.second}" } }
    var collapsed by rememberSaveable { mutableStateOf(listOf<String>()) }
    val allCollapsed = allGroupKeys.isNotEmpty() && allGroupKeys.all { it in collapsed }
    val listState = rememberLazyListState()
    val showTopChrome by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val chromeAlpha by animateFloatAsState(
        targetValue = if (showTopChrome) 1f else 0f,
        animationSpec = tween(180),
        label = "forum chrome",
    )
    val refreshAll = {
        refresh()
        if (isSignedIn) onRefreshCloudFavorites?.invoke()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        ProgressiveBlurScaffold(
            modifier = Modifier.widthIn(max = 760.dp),
            topVisible = { chromeAlpha },
            bottomEdgeBlur = false,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.lg, vertical = Space.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AccountMenuButton(onSelect)
                    Box {
                        ChromeIconButton(
                            icon = NagiIcons.Hamburger,
                            description = "版块选项",
                            onClick = { menuExpanded = true },
                        )
                        FrostedDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            if (onToggleCloudFavorite != null) {
                                FrostedDropdownItem(
                                    text = if (activeCloudFilter) "显示全部版块" else "只看 NGA 收藏",
                                    supporting = if (isSignedIn) "NGA 账号收藏版块" else "登录 NGA 后可用",
                                    leadingIcon = NagiIcons.Star,
                                    selected = activeCloudFilter,
                                    onClick = {
                                        menuExpanded = false
                                        if (!isSignedIn) {
                                            openLogin?.invoke()
                                        } else {
                                            onlyCloudFavorites = !activeCloudFilter
                                        }
                                    },
                                )
                            }
                            if (allGroupKeys.isNotEmpty()) {
                                FrostedDropdownItem(
                                    text = if (allCollapsed) "展开全部分类" else "收起全部分类",
                                    leadingIcon = NagiIcons.Forums,
                                    onClick = {
                                        menuExpanded = false
                                        collapsed = if (allCollapsed) emptyList() else allGroupKeys
                                    },
                                )
                            }
                            FrostedDropdownItem(
                                text = "刷新版块目录",
                                leadingIcon = NagiIcons.Refresh,
                                onClick = {
                                    menuExpanded = false
                                    refreshAll()
                                },
                            )
                            FrostedDropdownDivider()
                            FrostedDropdownItem(
                                text = "管理收藏",
                                supporting = "本地收藏话题与 NGA 收藏版块",
                                leadingIcon = NagiIcons.Bookmark,
                                onClick = {
                                    menuExpanded = false
                                    onSelect("favorites")
                                },
                            )
                        }
                    }
                }
            },
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.lg, vertical = Space.sm),
                ) {
                    if (isReorderingCloud) {
                        FrostedSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Space.dockHeight),
                            shape = CircleShape,
                            borderColor = colors.goldBorder,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = Space.lg, end = Space.xs, top = Space.xs, bottom = Space.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            ) {
                                Text(
                                    text = "长按拖动排序",
                                    modifier = Modifier.weight(1f),
                                    style = typography.sectionHeader,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (hasCustomCloudOrder && onResetCloudOrder != null) {
                                    CapsuleButton(
                                        text = "恢复默认",
                                        style = CapsuleStyle.Neutral,
                                        visualHeight = 38.dp,
                                        onClick = {
                                            isReorderingCloud = false
                                            draggingKey = null
                                            draggingOffsetY = 0f
                                            onResetCloudOrder()
                                        },
                                    )
                                }
                                CapsuleButton(
                                    text = "完成",
                                    icon = NagiIcons.Check,
                                    style = CapsuleStyle.SelectedGold,
                                    visualHeight = 38.dp,
                                    onClick = {
                                        isReorderingCloud = false
                                        draggingKey = null
                                        draggingOffsetY = 0f
                                        onSaveCloudOrder?.invoke(reorderingList.map { it.key })
                                    },
                                )
                            }
                        }
                    } else {
                        SearchCapsuleBar(
                            placeholder = "搜索版块、话题或用户",
                            onClick = { onSelect("search") },
                            actionDescription = "搜索",
                        )
                    }
                }
            },
        ) { padding ->
            NagiPullToRefreshBox(
                isRefreshing = !isReorderingCloud && ((state.loading && state.items.isNotEmpty()) || (activeCloudFilter && cloudFavoriteForums.loading)),
                onRefresh = refreshAll,
                topPadding = padding.calculateTopPadding(),
                enabled = !isReorderingCloud,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = padding,
                ) {
                    item(key = "brand-header", contentType = "brand") {
                        val brandText = remember(colors.textPrimary, colors.goldAccent) {
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = colors.textPrimary)) { append("Nagi") }
                                withStyle(SpanStyle(color = colors.goldAccent)) { append(".") }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Space.lg, vertical = Space.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            Text(
                                text = brandText,
                                style = typography.largeTitle,
                            )
                            if (activeCloudFilter) {
                                GoldTagBadge(text = "只看 NGA 收藏")
                            }
                        }
                    }

                    if ((state.loading && state.items.isEmpty()) || (activeCloudFilter && cloudFavoriteForums.loading && visibleItems.isEmpty())) {
                        item(key = "loading") { LoadingLine() }
                    }
                    if (state.error != null && !activeCloudFilter) {
                        item(key = "error") { Notice(state.error, refresh) }
                    }
                    if (activeCloudFilter && cloudFavoriteForums.error != null) {
                        item(key = "cloud-error") {
                            InlineStatusBanner(cloudFavoriteForums.error, retry = onRefreshCloudFavorites)
                        }
                    }
                    if (cloudNotice != null) {
                        item(key = "cloud-notice") {
                            InlineStatusBanner(cloudNotice, retry = onRefreshCloudFavorites)
                        }
                    }

                    if (!activeCloudFilter && isSignedIn && cloudResolvedForums.isNotEmpty()) {
                        val cloudFavKey = "__cloud_favorites__"
                        val cloudFavExpanded = isReorderingCloud || cloudFavKey !in collapsed
                        val displayedCloudForums = if (isReorderingCloud) reorderingList else cloudResolvedForums
                        item(key = "header:$cloudFavKey", contentType = "group-heading") {
                            DirectoryGroupHeader(
                                title = "NGA 收藏版块",
                                badge = if (isReorderingCloud) "长按拖动" else "NGA",
                                expanded = cloudFavExpanded,
                                onToggle = {
                                    if (!isReorderingCloud) {
                                        collapsed = if (cloudFavExpanded) collapsed + cloudFavKey else collapsed - cloudFavKey
                                    }
                                },
                            )
                        }
                        if (cloudFavExpanded) {
                            itemsIndexed(
                                items = displayedCloudForums,
                                key = { _, forum -> "cloud-fav:${forum.key}" },
                                contentType = { _, _ -> "forum" },
                            ) { index, forum ->
                                val isDragging = isReorderingCloud && draggingKey == forum.key
                                val dragModifier = if (isReorderingCloud) {
                                    Modifier
                                        .zIndex(if (isDragging) 1f else 0f)
                                        .then(if (isDragging) Modifier else Modifier.animateItem())
                                        .graphicsLayer {
                                            translationY = if (isDragging) draggingOffsetY else 0f
                                            scaleX = if (isDragging) 1.02f else 1f
                                            scaleY = if (isDragging) 1.02f else 1f
                                        }
                                        .pointerInput(forum.key) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    draggingKey = forum.key
                                                    draggingOffsetY = 0f
                                                },
                                                onDragEnd = {
                                                    draggingKey = null
                                                    draggingOffsetY = 0f
                                                    onSaveCloudOrder?.invoke(reorderingList.map { it.key })
                                                },
                                                onDragCancel = {
                                                    draggingKey = null
                                                    draggingOffsetY = 0f
                                                    onSaveCloudOrder?.invoke(reorderingList.map { it.key })
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    draggingOffsetY += dragAmount.y
                                                    val currentKey = "cloud-fav:${forum.key}"
                                                    val currentInfo = listState.layoutInfo.visibleItemsInfo
                                                        .firstOrNull { it.key == currentKey }
                                                        ?: return@detectDragGesturesAfterLongPress
                                                    val currentCenter = currentInfo.offset + currentInfo.size / 2f + draggingOffsetY
                                                    val currentIndex = reorderingList.indexOfFirst { it.key == forum.key }
                                                    if (currentIndex < 0) return@detectDragGesturesAfterLongPress
                                                    val targetInfo = listState.layoutInfo.visibleItemsInfo
                                                        .firstOrNull { item ->
                                                            val itemKey = item.key as? String
                                                            itemKey != null &&
                                                                itemKey.startsWith("cloud-fav:") &&
                                                                itemKey != currentKey &&
                                                                currentCenter in item.offset.toFloat()..(item.offset + item.size).toFloat()
                                                        }
                                                    if (targetInfo != null) {
                                                        val targetForumKey = (targetInfo.key as String).removePrefix("cloud-fav:")
                                                        val targetIndex = reorderingList.indexOfFirst { it.key == targetForumKey }
                                                        if (targetIndex >= 0 && targetIndex != currentIndex) {
                                                            reorderingList = reorderingList.toMutableList().apply {
                                                                add(targetIndex, removeAt(currentIndex))
                                                            }
                                                            draggingOffsetY += (currentInfo.offset - targetInfo.offset)
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                } else {
                                    Modifier
                                }
                                Box(modifier = dragModifier) {
                                    SectionCardItem(
                                        position = CardGroupPosition.at(index, displayedCloudForums.size),
                                        dividerIndent = 58.dp,
                                    ) {
                                        ForumDirectoryRow(
                                            forum = forum,
                                            isCloudFavorite = true,
                                            isSignedIn = isSignedIn,
                                            isCloudBusy = forum.key in cloudBusyKeys,
                                            isCloudUncertain = forum.key in cloudUncertainKeys,
                                            onClick = { onForum(forum) },
                                            onToggleCloudFavorite = onToggleCloudFavorite?.let { toggle -> { toggle(forum) } },
                                            onOpenLogin = openLogin,
                                            isReordering = isReorderingCloud,
                                            isDragging = isDragging,
                                            canStartReorder = onSaveCloudOrder != null && cloudResolvedForums.size > 1,
                                            hasCustomCloudOrder = hasCustomCloudOrder,
                                            onStartReorder = {
                                                reorderingList = cloudResolvedForums
                                                isReorderingCloud = true
                                            },
                                            onResetCloudOrder = onResetCloudOrder,
                                            onMoveUp = if (isReorderingCloud && index > 0) {
                                                {
                                                    reorderingList = reorderingList.toMutableList().apply {
                                                        add(index - 1, removeAt(index))
                                                    }
                                                    onSaveCloudOrder?.invoke(reorderingList.map { it.key })
                                                }
                                            } else null,
                                            onMoveDown = if (isReorderingCloud && index < displayedCloudForums.lastIndex) {
                                                {
                                                    reorderingList = reorderingList.toMutableList().apply {
                                                        add(index + 1, removeAt(index))
                                                    }
                                                    onSaveCloudOrder?.invoke(reorderingList.map { it.key })
                                                }
                                            } else null,
                                            onFinishReorder = if (isReorderingCloud) {
                                                {
                                                    isReorderingCloud = false
                                                    draggingKey = null
                                                    draggingOffsetY = 0f
                                                    onSaveCloudOrder?.invoke(reorderingList.map { it.key })
                                                }
                                            } else null,
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "spacing:$cloudFavKey", contentType = "spacing") {
                            Spacer(Modifier.height(Space.md))
                        }
                    }

                    grouped.forEach { (group, forums) ->
                        val key = "${group.first}\u0000${group.second}"
                        val title = listOf(group.first, group.second)
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString(" · ")
                            .ifBlank { if (activeCloudFilter) "NGA 收藏版块" else "其他版块" }
                        val expanded = key !in collapsed
                        item(key = "header:$key", contentType = "group-heading") {
                            DirectoryGroupHeader(
                                title = title,
                                badge = null,
                                expanded = expanded,
                                onToggle = {
                                    collapsed = if (expanded) collapsed + key else collapsed - key
                                },
                            )
                        }
                        if (expanded) {
                            itemsIndexed(
                                items = forums,
                                key = { _, forum -> "$key:${forum.key}" },
                                contentType = { _, _ -> "forum" },
                            ) { index, forum ->
                                SectionCardItem(
                                    position = CardGroupPosition.at(index, forums.size),
                                    dividerIndent = 58.dp,
                                ) {
                                    ForumDirectoryRow(
                                        forum = forum,
                                        isCloudFavorite = forum.key in cloudKeys,
                                        isSignedIn = isSignedIn,
                                        isCloudBusy = forum.key in cloudBusyKeys,
                                        isCloudUncertain = forum.key in cloudUncertainKeys,
                                        onClick = { onForum(forum) },
                                        onToggleCloudFavorite = onToggleCloudFavorite?.let { toggle -> { toggle(forum) } },
                                        onOpenLogin = openLogin,
                                    )
                                }
                            }
                        }
                        item(key = "spacing:$key", contentType = "spacing") {
                            Spacer(Modifier.height(Space.md))
                        }
                    }

                    if (visibleItems.isEmpty()) {
                        if (activeCloudFilter) {
                            if (!cloudFavoriteForums.loading && cloudFavoriteForums.error == null) {
                                item(key = "empty") {
                                    Notice("暂无 NGA 收藏版块，长按任意版块可加入 NGA 收藏") {
                                        onlyCloudFavorites = false
                                    }
                                }
                            }
                        } else if (!state.loading && state.error == null) {
                            item(key = "empty") { Notice("暂无版块", refresh) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryGroupHeader(
    title: String,
    badge: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = if (expanded) "收起$title" else "展开$title",
                onClick = onToggle,
            )
            .semantics {
                heading()
                stateDescription = if (expanded) "已展开" else "已收起"
            }
            .heightIn(min = 48.dp)
            .padding(horizontal = Space.sm, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Text(
            text = title,
            style = typography.sectionHeader,
            color = colors.textSecondary,
        )
        if (badge != null) {
            GoldTagBadge(text = badge)
        }
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = NagiIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = if (expanded) 90f else 0f },
            tint = colors.textSecondary,
        )
    }
}

/**
 * Aligns matched cloud favorite forums with directory name, grouping, icon, and fallback description.
 */
internal fun List<Forum>.resolveCloudForumMetadata(directory: List<Forum>): List<Forum> {
    if (isEmpty() || directory.isEmpty()) return this
    val byKey = directory.associateBy { it.key }
    return map { cloud ->
        val matched = byKey[cloud.key] ?: return@map cloud
        cloud.copy(
            name = matched.name.ifBlank { cloud.name },
            category = matched.category,
            group = matched.group,
            description = cloud.description.ifBlank { matched.description },
            iconUrl = matched.iconUrl,
        )
    }
}

@Composable
internal fun ForumDirectoryIcon(iconUrl: String?, isFavorite: Boolean) {
    val colors = NagiThemeTokens.colors
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFavorite) colors.goldContainer else colors.insetContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (!iconUrl.isNullOrBlank()) {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ForumDirectoryRow(
    forum: Forum,
    isCloudFavorite: Boolean,
    isSignedIn: Boolean,
    isCloudBusy: Boolean,
    isCloudUncertain: Boolean,
    onClick: () -> Unit,
    onToggleCloudFavorite: (() -> Unit)?,
    onOpenLogin: (() -> Unit)?,
    isReordering: Boolean = false,
    isDragging: Boolean = false,
    canStartReorder: Boolean = false,
    hasCustomCloudOrder: Boolean = false,
    onStartReorder: (() -> Unit)? = null,
    onResetCloudOrder: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onFinishReorder: (() -> Unit)? = null,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    var actionMenuExpanded by remember { mutableStateOf(false) }
    val hasActions = !isReordering && onToggleCloudFavorite != null
    val a11yActions = buildList {
        if (isReordering) {
            onMoveUp?.let { move -> add(CustomAccessibilityAction("上移") { move(); true }) }
            onMoveDown?.let { move -> add(CustomAccessibilityAction("下移") { move(); true }) }
            onFinishReorder?.let { finish -> add(CustomAccessibilityAction("完成排序") { finish(); true }) }
        } else if (onToggleCloudFavorite != null && isSignedIn && !isCloudBusy && !isCloudUncertain) {
            add(
                CustomAccessibilityAction(if (isCloudFavorite) "取消 NGA 收藏版块" else "收藏到 NGA 版块") {
                    onToggleCloudFavorite()
                    true
                },
            )
            if (canStartReorder && onStartReorder != null) {
                add(CustomAccessibilityAction("调整版块顺序") { onStartReorder(); true })
            }
            if (hasCustomCloudOrder && onResetCloudOrder != null) {
                add(CustomAccessibilityAction("恢复默认顺序") { onResetCloudOrder(); true })
            }
        }
    }
    val clickModifier = when {
        isReordering -> Modifier.semantics(mergeDescendants = true) { customActions = a11yActions }
        hasActions -> Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = { actionMenuExpanded = true },
                onLongClickLabel = "版块收藏选项",
            )
            .semantics { customActions = a11yActions }
        else -> Modifier.clickable(onClick = onClick)
    }

    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isDragging) colors.goldContainer else Color.Transparent)
                .then(clickModifier)
                .heightIn(min = 56.dp)
                .padding(horizontal = Space.lg, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ForumDirectoryIcon(forum.iconUrl, isCloudFavorite)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    Text(
                        text = forum.name,
                        modifier = Modifier.padding(end = Space.sm),
                        style = typography.topicTitle,
                        color = if (isDragging) colors.goldText else colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (forum.description.isNotBlank()) {
                        Text(
                            text = forum.description,
                            modifier = Modifier.align(Alignment.CenterVertically),
                            style = typography.metadata,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (forum.collectionId != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        NeutralTagBadge(text = "主题合集")
                    }
                }
            }
            Icon(
                imageVector = if (isReordering) NagiIcons.DragHandle else NagiIcons.ChevronRight,
                contentDescription = if (isReordering) "长按拖动调整顺序" else null,
                modifier = Modifier.size(18.dp),
                tint = if (isDragging) colors.goldText else colors.textSecondary,
            )
        }
        if (!isReordering && onToggleCloudFavorite != null) {
            FrostedDropdownMenu(
                expanded = actionMenuExpanded,
                onDismissRequest = { actionMenuExpanded = false },
            ) {
                FrostedDropdownItem(
                    text = if (isCloudFavorite) "取消 NGA 收藏版块" else "收藏到 NGA 版块",
                    supporting = when {
                        !isSignedIn -> "登录 NGA 后可用"
                        isCloudBusy -> "正在提交…"
                        isCloudUncertain -> "结果未确认，请先刷新核对"
                        else -> "同步至 NGA 账号"
                    },
                    leadingIcon = NagiIcons.Star,
                    selected = isCloudFavorite,
                    enabled = !isSignedIn || (!isCloudBusy && !isCloudUncertain),
                    onClick = {
                        actionMenuExpanded = false
                        if (!isSignedIn) onOpenLogin?.invoke() else onToggleCloudFavorite()
                    },
                )
                if (canStartReorder && onStartReorder != null) {
                    FrostedDropdownDivider()
                    FrostedDropdownItem(
                        text = "调整版块顺序",
                        supporting = "仅保存在当前账号本地",
                        leadingIcon = NagiIcons.Sort,
                        onClick = {
                            actionMenuExpanded = false
                            onStartReorder()
                        },
                    )
                }
                if (hasCustomCloudOrder && onResetCloudOrder != null) {
                    if (!canStartReorder) FrostedDropdownDivider()
                    FrostedDropdownItem(
                        text = "恢复默认顺序",
                        supporting = "使用 NGA 返回顺序",
                        leadingIcon = NagiIcons.Refresh,
                        onClick = {
                            actionMenuExpanded = false
                            onResetCloudOrder()
                        },
                    )
                }
            }
        }
    }
}
