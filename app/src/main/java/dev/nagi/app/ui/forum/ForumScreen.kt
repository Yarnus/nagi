package dev.nagi.app.ui.forum

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.NagiIcons
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
    val favorites by vm.forumFavorites.collectAsStateWithLifecycle()
    ForumDirectory(
        state = state,
        refresh = vm::loadForums,
        onSelect = onSelect,
        onForum = onForum,
        favoriteForums = favorites,
        onToggleFavorite = vm::toggleForumFavorite,
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
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val favoriteKeys = remember(favoriteForums) { favoriteForums.map { it.key }.toSet() }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val visibleItems = remember(state.items, onlyFavorites, favoriteKeys) {
        if (onlyFavorites) state.items.filter { it.key in favoriteKeys } else state.items
    }
    val grouped = remember(visibleItems) { visibleItems.groupBy { it.category to it.group } }
    val allGroupKeys = remember(grouped) { grouped.keys.map { "${it.first}\u0000${it.second}" } }
    var collapsed by rememberSaveable { mutableStateOf(listOf<String>()) }
    val allCollapsed = allGroupKeys.isNotEmpty() && allGroupKeys.all { it in collapsed }

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

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        ProgressiveBlurScaffold(
            modifier = Modifier.widthIn(max = 760.dp),
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
                                text = if (onlyFavorites) "显示全部版块" else "只看本地收藏",
                                supporting = "仅保存在此设备",
                                leadingIcon = NagiIcons.Bookmark,
                                selected = onlyFavorites,
                                onClick = {
                                    menuExpanded = false
                                    onlyFavorites = !onlyFavorites
                                },
                            )
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
                                    refresh()
                                },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs),
                                thickness = 1.dp,
                                color = colors.cardBorder,
                            )
                            FrostedDropdownItem(
                                text = "管理本地收藏",
                                supporting = "收藏话题与版块",
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
            PullToRefreshBox(
                isRefreshing = state.loading && state.items.isNotEmpty(),
                onRefresh = refresh,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
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
                            if (onlyFavorites) {
                                GoldTagBadge(text = "只看本地收藏")
                            }
                        }
                    }

                    if (state.loading && state.items.isEmpty()) {
                        item(key = "loading") { LoadingLine() }
                    }
                    if (state.error != null) {
                        item(key = "error") { Notice(state.error, refresh) }
                    }

                    if (!onlyFavorites && favoriteResolvedForums.isNotEmpty()) {
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
                                        isFavorite = true,
                                        onClick = { onForum(forum) },
                                        onToggleFavorite = onToggleFavorite?.let { toggle -> { toggle(forum) } },
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
                            .ifBlank { "其他版块" }
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
                                        isFavorite = forum.key in favoriteKeys,
                                        onClick = { onForum(forum) },
                                        onToggleFavorite = onToggleFavorite?.let { toggle -> { toggle(forum) } },
                                    )
                                }
                            }
                        }
                        item(key = "spacing:$key", contentType = "spacing") {
                            Spacer(Modifier.height(Space.md))
                        }
                    }

                    if (!state.loading && state.error == null && visibleItems.isEmpty()) {
                        item(key = "empty") {
                            if (onlyFavorites && state.items.isNotEmpty()) {
                                Notice("尚未添加本地收藏版块，长按任意版块可加入本地收藏") {
                                    onlyFavorites = false
                                }
                            } else {
                                Notice("暂无版块", refresh)
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
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val clickModifier = if (onToggleFavorite != null) {
        Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onToggleFavorite,
                onLongClickLabel = if (isFavorite) "取消本地收藏版块" else "本地收藏版块",
            )
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(if (isFavorite) "取消本地收藏版块" else "本地收藏版块") {
                        onToggleFavorite()
                        true
                    },
                )
            }
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickModifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = Space.lg, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ForumDirectoryIcon(forum.iconUrl, isFavorite)
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
}
