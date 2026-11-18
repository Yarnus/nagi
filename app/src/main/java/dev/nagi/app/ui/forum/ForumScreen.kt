package dev.nagi.app.ui.forum

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
import dev.nagi.app.data.LocalForumFavorite
import dev.nagi.app.ui.components.AccountMenuButton
import dev.nagi.app.ui.components.CardGroupPosition
import dev.nagi.app.ui.components.ChromeIconButton
import dev.nagi.app.ui.components.FrostedDropdownItem
import dev.nagi.app.ui.components.FrostedDropdownMenu
import dev.nagi.app.ui.components.GoldTagBadge
import dev.nagi.app.ui.components.InlineStatusBanner
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.LocalOpenLogin
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.NagiPullToRefreshBox
import dev.nagi.app.ui.components.Notice
import dev.nagi.app.ui.components.ProgressiveBlurScaffold
import dev.nagi.app.ui.components.SearchCapsuleBar
import dev.nagi.app.ui.components.SectionCardItem
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.viewmodel.LibraryViewModel
import dev.nagi.app.viewmodel.ListState
import dev.nagi.sdk.Forum

private enum class DirectoryFilter { ALL, LOCAL, CLOUD }

@Composable
fun ForumScreen(
    vm: LibraryViewModel,
    onSelect: (String) -> Unit,
    onForum: (Forum) -> Unit,
) {
    val state by vm.forums.collectAsStateWithLifecycle()
    val favorites by vm.forumFavorites.collectAsStateWithLifecycle()
    val cloudFavorites by vm.cloudFavoriteForums.collectAsStateWithLifecycle()
    val cloudBusyKeys by vm.cloudForumBusyKeys.collectAsStateWithLifecycle()
    val cloudUncertainKeys by vm.cloudForumUncertainKeys.collectAsStateWithLifecycle()
    val cloudNotice by vm.cloudForumNotice.collectAsStateWithLifecycle()
    ForumDirectory(
        state = state,
        refresh = vm::loadForums,
        onSelect = onSelect,
        onForum = onForum,
        favoriteForums = favorites,
        onToggleFavorite = vm::toggleForumFavorite,
        isSignedIn = vm.isSignedIn,
        cloudFavoriteForums = cloudFavorites,
        cloudBusyKeys = cloudBusyKeys,
        cloudUncertainKeys = cloudUncertainKeys,
        cloudNotice = cloudNotice,
        onRefreshCloudFavorites = vm::loadCloudFavoriteForums,
        onToggleCloudFavorite = vm::toggleCloudForumFavorite,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun ForumDirectory(
    state: ListState<Forum>,
    refresh: () -> Unit,
    onSelect: (String) -> Unit,
    onForum: (Forum) -> Unit,
    favoriteForums: List<LocalForumFavorite> = emptyList(),
    onToggleFavorite: ((Forum) -> Unit)? = null,
    isSignedIn: Boolean = false,
    cloudFavoriteForums: ListState<Forum> = ListState(),
    cloudBusyKeys: Set<String> = emptySet(),
    cloudUncertainKeys: Set<String> = emptySet(),
    cloudNotice: String? = null,
    onRefreshCloudFavorites: (() -> Unit)? = null,
    onToggleCloudFavorite: ((Forum) -> Unit)? = null,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val openLogin = LocalOpenLogin.current
    val favoriteKeys = remember(favoriteForums) { favoriteForums.map { it.key }.toSet() }
    val cloudKeys = remember(cloudFavoriteForums.items) { cloudFavoriteForums.items.map { it.key }.toSet() }
    var filterMode by rememberSaveable { mutableStateOf(DirectoryFilter.ALL) }
    val activeFilter = if (filterMode == DirectoryFilter.CLOUD && !isSignedIn) DirectoryFilter.ALL else filterMode
    var menuExpanded by remember { mutableStateOf(false) }

    val favoriteResolvedForums = remember(favoriteForums, state.items) {
        val byKey = state.items.associateBy { it.key }
        favoriteForums.map { fav ->
            byKey[fav.key] ?: Forum(
                id = fav.forumId,
                name = fav.name,
                description = fav.description,
                category = fav.category,
                group = fav.groupName,
                collectionId = fav.collectionId,
            )
        }
    }
    val cloudResolvedForums = remember(cloudFavoriteForums.items, state.items) {
        cloudFavoriteForums.items.resolveCloudForumMetadata(state.items)
    }

    val visibleItems = remember(state.items, activeFilter, favoriteKeys, cloudKeys, cloudResolvedForums) {
        when (activeFilter) {
            DirectoryFilter.ALL -> state.items
            DirectoryFilter.LOCAL -> state.items.filter { it.key in favoriteKeys }
            DirectoryFilter.CLOUD -> cloudResolvedForums
        }
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
                            FrostedDropdownItem(
                                text = if (activeFilter == DirectoryFilter.LOCAL) "显示全部版块" else "只看本地收藏",
                                supporting = "仅保存在此设备",
                                leadingIcon = NagiIcons.Bookmark,
                                selected = activeFilter == DirectoryFilter.LOCAL,
                                onClick = {
                                    menuExpanded = false
                                    filterMode = if (activeFilter == DirectoryFilter.LOCAL) DirectoryFilter.ALL else DirectoryFilter.LOCAL
                                },
                            )
                            if (onToggleCloudFavorite != null) {
                                FrostedDropdownItem(
                                    text = if (activeFilter == DirectoryFilter.CLOUD) "显示全部版块" else "只看 NGA 收藏",
                                    supporting = if (isSignedIn) "NGA 账号收藏版块" else "登录 NGA 后可用",
                                    leadingIcon = NagiIcons.Star,
                                    selected = activeFilter == DirectoryFilter.CLOUD,
                                    onClick = {
                                        menuExpanded = false
                                        if (!isSignedIn) {
                                            openLogin?.invoke()
                                        } else {
                                            filterMode = if (activeFilter == DirectoryFilter.CLOUD) DirectoryFilter.ALL else DirectoryFilter.CLOUD
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
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs),
                                thickness = 1.dp,
                                color = colors.cardBorder,
                            )
                            FrostedDropdownItem(
                                text = "管理收藏",
                                supporting = "本地收藏与 NGA 收藏版块",
                                leadingIcon = NagiIcons.Star,
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
                    SearchCapsuleBar(
                        placeholder = "搜索版块、话题或用户",
                        onClick = { onSelect("search") },
                        actionDescription = "搜索",
                    )
                }
            },
        ) { padding ->
            NagiPullToRefreshBox(
                isRefreshing = (state.loading && state.items.isNotEmpty()) || (activeFilter == DirectoryFilter.CLOUD && cloudFavoriteForums.loading),
                onRefresh = refreshAll,
                topPadding = padding.calculateTopPadding(),
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
                            when (activeFilter) {
                                DirectoryFilter.LOCAL -> GoldTagBadge(text = "只看本地收藏")
                                DirectoryFilter.CLOUD -> GoldTagBadge(text = "只看 NGA 收藏")
                                DirectoryFilter.ALL -> Unit
                            }
                        }
                    }

                    if ((state.loading && state.items.isEmpty()) || (activeFilter == DirectoryFilter.CLOUD && cloudFavoriteForums.loading && visibleItems.isEmpty())) {
                        item(key = "loading") { LoadingLine() }
                    }
                    if (state.error != null && activeFilter != DirectoryFilter.CLOUD) {
                        item(key = "error") { Notice(state.error, refresh) }
                    }
                    if (activeFilter == DirectoryFilter.CLOUD && cloudFavoriteForums.error != null) {
                        item(key = "cloud-error") {
                            InlineStatusBanner(cloudFavoriteForums.error, retry = onRefreshCloudFavorites)
                        }
                    }
                    if (cloudNotice != null) {
                        item(key = "cloud-notice") {
                            InlineStatusBanner(cloudNotice, retry = onRefreshCloudFavorites)
                        }
                    }

                    if (activeFilter == DirectoryFilter.ALL && isSignedIn && cloudResolvedForums.isNotEmpty()) {
                        val cloudFavKey = "__cloud_favorites__"
                        val cloudFavExpanded = cloudFavKey !in collapsed
                        item(key = "header:$cloudFavKey", contentType = "group-heading") {
                            DirectoryGroupHeader(
                                title = "NGA 收藏版块",
                                badge = "NGA",
                                expanded = cloudFavExpanded,
                                onToggle = {
                                    collapsed = if (cloudFavExpanded) collapsed + cloudFavKey else collapsed - cloudFavKey
                                },
                            )
                        }
                        if (cloudFavExpanded) {
                            itemsIndexed(
                                items = cloudResolvedForums,
                                key = { _, forum -> "cloud-fav:${forum.key}" },
                                contentType = { _, _ -> "forum" },
                            ) { index, forum ->
                                SectionCardItem(
                                    position = CardGroupPosition.at(index, cloudResolvedForums.size),
                                    dividerIndent = 58.dp,
                                ) {
                                    ForumDirectoryRow(
                                        forum = forum,
                                        isLocalFavorite = forum.key in favoriteKeys,
                                        isCloudFavorite = true,
                                        isSignedIn = isSignedIn,
                                        isCloudBusy = forum.key in cloudBusyKeys,
                                        isCloudUncertain = forum.key in cloudUncertainKeys,
                                        onClick = { onForum(forum) },
                                        onToggleLocalFavorite = onToggleFavorite?.let { toggle -> { toggle(forum) } },
                                        onToggleCloudFavorite = onToggleCloudFavorite?.let { toggle -> { toggle(forum) } },
                                        onOpenLogin = openLogin,
                                    )
                                }
                            }
                        }
                        item(key = "spacing:$cloudFavKey", contentType = "spacing") {
                            Spacer(Modifier.height(Space.md))
                        }
                    }

                    if (activeFilter == DirectoryFilter.ALL && favoriteResolvedForums.isNotEmpty()) {
                        val favKey = "__local_favorites__"
                        val favExpanded = favKey !in collapsed
                        item(key = "header:$favKey", contentType = "group-heading") {
                            DirectoryGroupHeader(
                                title = "收藏版块",
                                badge = "本地收藏",
                                expanded = favExpanded,
                                onToggle = {
                                    collapsed = if (favExpanded) collapsed + favKey else collapsed - favKey
                                },
                            )
                        }
                        if (favExpanded) {
                            itemsIndexed(
                                items = favoriteResolvedForums,
                                key = { _, forum -> "fav:${forum.key}" },
                                contentType = { _, _ -> "forum" },
                            ) { index, forum ->
                                SectionCardItem(
                                    position = CardGroupPosition.at(index, favoriteResolvedForums.size),
                                    dividerIndent = 58.dp,
                                ) {
                                    ForumDirectoryRow(
                                        forum = forum,
                                        isLocalFavorite = true,
                                        isCloudFavorite = forum.key in cloudKeys,
                                        isSignedIn = isSignedIn,
                                        isCloudBusy = forum.key in cloudBusyKeys,
                                        isCloudUncertain = forum.key in cloudUncertainKeys,
                                        onClick = { onForum(forum) },
                                        onToggleLocalFavorite = onToggleFavorite?.let { toggle -> { toggle(forum) } },
                                        onToggleCloudFavorite = onToggleCloudFavorite?.let { toggle -> { toggle(forum) } },
                                        onOpenLogin = openLogin,
                                    )
                                }
                            }
                        }
                        item(key = "spacing:$favKey", contentType = "spacing") {
                            Spacer(Modifier.height(Space.md))
                        }
                    }

                    grouped.forEach { (group, forums) ->
                        val key = "${group.first}\u0000${group.second}"
                        val title = listOf(group.first, group.second)
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString(" · ")
                            .ifBlank { if (activeFilter == DirectoryFilter.CLOUD) "NGA 收藏版块" else "其他版块" }
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
                                        isLocalFavorite = forum.key in favoriteKeys,
                                        isCloudFavorite = forum.key in cloudKeys,
                                        isSignedIn = isSignedIn,
                                        isCloudBusy = forum.key in cloudBusyKeys,
                                        isCloudUncertain = forum.key in cloudUncertainKeys,
                                        onClick = { onForum(forum) },
                                        onToggleLocalFavorite = onToggleFavorite?.let { toggle -> { toggle(forum) } },
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
                        when (activeFilter) {
                            DirectoryFilter.LOCAL -> if (!state.loading && state.error == null && state.items.isNotEmpty()) {
                                item(key = "empty") {
                                    Notice("尚未添加本地收藏版块，长按任意版块可加入本地收藏") {
                                        filterMode = DirectoryFilter.ALL
                                    }
                                }
                            } else if (!state.loading && state.error == null) {
                                item(key = "empty") { Notice("暂无版块", refresh) }
                            }
                            DirectoryFilter.CLOUD -> if (!cloudFavoriteForums.loading && cloudFavoriteForums.error == null) {
                                item(key = "empty") {
                                    Notice("暂无 NGA 收藏版块，长按任意版块可加入 NGA 收藏") {
                                        filterMode = DirectoryFilter.ALL
                                    }
                                }
                            }
                            DirectoryFilter.ALL -> if (!state.loading && state.error == null) {
                                item(key = "empty") { Notice("暂无版块", refresh) }
                            }
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
 * Preserves the server-returned favorite forum name while filling directory icon and grouping metadata.
 */
internal fun List<Forum>.resolveCloudForumMetadata(directory: List<Forum>): List<Forum> {
    if (isEmpty() || directory.isEmpty()) return this
    val byKey = directory.associateBy { it.key }
    return map { cloud ->
        val matched = byKey[cloud.key] ?: return@map cloud
        cloud.copy(
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
    isLocalFavorite: Boolean,
    isCloudFavorite: Boolean,
    isSignedIn: Boolean,
    isCloudBusy: Boolean,
    isCloudUncertain: Boolean,
    onClick: () -> Unit,
    onToggleLocalFavorite: (() -> Unit)?,
    onToggleCloudFavorite: (() -> Unit)?,
    onOpenLogin: (() -> Unit)?,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    var actionMenuExpanded by remember { mutableStateOf(false) }
    val hasActions = onToggleLocalFavorite != null || onToggleCloudFavorite != null
    val a11yActions = buildList {
        if (onToggleLocalFavorite != null) {
            add(
                CustomAccessibilityAction(if (isLocalFavorite) "取消本地收藏版块" else "本地收藏版块") {
                    onToggleLocalFavorite()
                    true
                },
            )
        }
        if (onToggleCloudFavorite != null && isSignedIn && !isCloudBusy && !isCloudUncertain) {
            add(
                CustomAccessibilityAction(if (isCloudFavorite) "取消 NGA 收藏版块" else "收藏到 NGA 版块") {
                    onToggleCloudFavorite()
                    true
                },
            )
        }
    }
    val clickModifier = if (hasActions) {
        Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = { actionMenuExpanded = true },
                onLongClickLabel = "版块收藏选项",
            )
            .semantics { customActions = a11yActions }
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(clickModifier)
                .heightIn(min = 56.dp)
                .padding(horizontal = Space.lg, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ForumDirectoryIcon(forum.iconUrl, isLocalFavorite || isCloudFavorite)
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
                        color = colors.textPrimary,
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
                        GoldTagBadge(text = "主题合集")
                    }
                }
            }
            Icon(
                imageVector = NagiIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.textSecondary,
            )
        }
        if (hasActions) {
            FrostedDropdownMenu(
                expanded = actionMenuExpanded,
                onDismissRequest = { actionMenuExpanded = false },
            ) {
                if (onToggleLocalFavorite != null) {
                    FrostedDropdownItem(
                        text = if (isLocalFavorite) "取消本地收藏版块" else "本地收藏版块",
                        supporting = "仅保存在此设备",
                        leadingIcon = NagiIcons.Bookmark,
                        selected = isLocalFavorite,
                        onClick = {
                            actionMenuExpanded = false
                            onToggleLocalFavorite()
                        },
                    )
                }
                if (onToggleCloudFavorite != null) {
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
                }
            }
        }
    }
}
