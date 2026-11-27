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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nagi.app.ui.components.AccountMenuButton
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.CardGroupPosition
import dev.nagi.app.ui.components.ChromeIconButton
import dev.nagi.app.ui.components.InlineStatusBanner
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.NagiConfirmDialog
import dev.nagi.app.ui.components.NagiHeader
import dev.nagi.app.ui.components.NagiIconAction
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.NagiPullToRefreshBox
import dev.nagi.app.ui.components.NeutralTagBadge
import dev.nagi.app.ui.components.Notice
import dev.nagi.app.ui.components.SectionCardItem
import dev.nagi.app.ui.components.SegmentedCapsuleControl
import dev.nagi.app.ui.components.safeBottomPadding
import dev.nagi.app.ui.forum.ForumDirectoryIcon
import dev.nagi.app.ui.forum.resolveCloudForumMetadata
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.viewmodel.LibraryViewModel
import dev.nagi.sdk.Forum

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    vm: LibraryViewModel,
    back: () -> Unit,
    onSelect: (String) -> Unit,
    onForum: (Forum) -> Unit = {},
    onRead: (String) -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val directory by vm.forums.collectAsStateWithLifecycle()
    val cloudFavorites by vm.cloudFavoriteForums.collectAsStateWithLifecycle()
    val cloudBusyKeys by vm.cloudForumBusyKeys.collectAsStateWithLifecycle()
    val cloudUncertainKeys by vm.cloudForumUncertainKeys.collectAsStateWithLifecycle()
    val cloudNotice by vm.cloudForumNotice.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var confirmRemoveCloudForum by remember { mutableStateOf<Forum?>(null) }
    val scrollStates = rememberSaveableStateHolder()

    val resolvedCloudForums = remember(cloudFavorites.items, directory.items) {
        cloudFavorites.items.resolveCloudForumMetadata(directory.items)
    }

    confirmRemoveCloudForum?.let { target ->
        NagiConfirmDialog(
            onDismissRequest = { confirmRemoveCloudForum = null },
            title = "取消 NGA 收藏版块？",
            message = "将从你的 NGA 账号收藏版块中移除「${target.name}」。",
            confirmLabel = "移除",
            confirmStyle = CapsuleStyle.Destructive,
            onConfirm = {
                confirmRemoveCloudForum = null
                vm.setCloudForumFavorite(target, false)
            },
            dismissLabel = "取消",
        )
    }

    Column(Modifier.fillMaxSize()) {
        NagiHeader(
            title = "收藏",
            subtitle = if (selectedTab == 0) "仅保存在此设备，不同步 NGA" else "NGA 账号收藏的版块与合集",
            back = back,
        ) {
            if (selectedTab == 1 && vm.isSignedIn) {
                ChromeIconButton(NagiIcons.Refresh, "刷新 NGA 收藏版块", onClick = vm::loadCloudFavoriteForums)
            }
            ChromeIconButton(NagiIcons.Search, "搜索", onClick = { onSelect("search") })
            AccountMenuButton(onSelect)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 760.dp),
        ) {
            SegmentedCapsuleControl(
                options = listOf("本地收藏", "NGA 收藏版块"),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
            )
            key(selectedTab) {
                scrollStates.SaveableStateProvider("favorites:$selectedTab") {
                    val listState = rememberLazyListState()
                    if (selectedTab == 0) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = Space.xs, bottom = Space.md + safeBottomPadding()),
                        ) {
                            if (favorites.isNotEmpty()) {
                                item(key = "topic-fav-header") {
                                    Text(
                                        text = "本地收藏话题",
                                        style = typography.sectionHeader,
                                        color = colors.textSecondary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = Space.lg, vertical = Space.sm),
                                    )
                                }
                                itemsIndexed(favorites, key = { _, fav -> "topic:${fav.topicId}" }) { index, favorite ->
                                    SectionCardItem(CardGroupPosition.at(index, favorites.size)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onRead(favorite.topicId) }
                                                .padding(start = Space.lg, end = Space.sm, top = Space.sm, bottom = Space.sm),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Space.xs),
                                        ) {
                                            Text(
                                                text = favorite.title,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(vertical = Space.xs),
                                                style = typography.topicTitle,
                                                color = colors.textPrimary,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            NagiIconAction(
                                                icon = NagiIcons.Trash,
                                                description = "移除",
                                                onClick = { vm.removeFavorite(favorite.topicId) },
                                                tint = colors.textSecondary,
                                            )
                                        }
                                    }
                                }
                            } else {
                                item(key = "empty") {
                                    Notice("在阅读页选择“本地收藏”，即可在这里继续阅读")
                                }
                            }
                        }
                    } else if (!vm.isSignedIn) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = Space.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Notice("登录 NGA 后可查看并管理账号收藏的版块与合集")
                            CapsuleButton(
                                text = "登录 NGA",
                                onClick = { onSelect("login") },
                                icon = NagiIcons.Person,
                                style = CapsuleStyle.SelectedGold,
                            )
                        }
                    } else {
                        NagiPullToRefreshBox(
                            isRefreshing = cloudFavorites.loading && resolvedCloudForums.isNotEmpty(),
                            onRefresh = vm::loadCloudFavoriteForums,
                            topPadding = 0.dp,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = Space.xs, bottom = Space.md + safeBottomPadding()),
                            ) {
                                if (cloudFavorites.loading && resolvedCloudForums.isEmpty()) {
                                    item(key = "cloud-loading") { LoadingLine() }
                                }
                                if (cloudFavorites.error != null) {
                                    item(key = "cloud-error") {
                                        InlineStatusBanner(
                                            message = cloudFavorites.error!!,
                                            retry = vm::loadCloudFavoriteForums,
                                        )
                                    }
                                }
                                if (cloudNotice != null && cloudNotice != cloudFavorites.error) {
                                    item(key = "cloud-notice") {
                                        InlineStatusBanner(
                                            message = cloudNotice!!,
                                            retry = vm::loadCloudFavoriteForums,
                                        )
                                    }
                                }
                                if (resolvedCloudForums.isNotEmpty()) {
                                    item(key = "cloud-forum-fav-header") {
                                        Text(
                                            text = "NGA 收藏版块",
                                            style = typography.sectionHeader,
                                            color = colors.textSecondary,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = Space.lg, vertical = Space.sm),
                                        )
                                    }
                                    itemsIndexed(
                                        items = resolvedCloudForums,
                                        key = { _, forum -> "cloud-forum:${forum.key}" },
                                    ) { index, forum ->
                                        val isBusy = forum.key in cloudBusyKeys
                                        val isUncertain = forum.key in cloudUncertainKeys
                                        SectionCardItem(
                                            position = CardGroupPosition.at(index, resolvedCloudForums.size),
                                            dividerIndent = 58.dp,
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onForum(forum) }
                                                    .padding(start = Space.lg, end = Space.sm, top = Space.sm, bottom = Space.sm),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                                            ) {
                                                ForumDirectoryIcon(forum.iconUrl, isFavorite = true)
                                                Column(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(vertical = Space.xs),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                                ) {
                                                    Text(
                                                        text = forum.name,
                                                        style = typography.topicTitle,
                                                        color = colors.textPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    if (forum.description.isNotBlank()) {
                                                        Text(
                                                            text = forum.description,
                                                            style = typography.metadata,
                                                            color = colors.textSecondary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                    if (forum.collectionId != null) {
                                                        NeutralTagBadge(text = "主题合集")
                                                    }
                                                }
                                                NagiIconAction(
                                                    icon = NagiIcons.Trash,
                                                    description = if (isBusy) "移除中" else "移除",
                                                    enabled = !isBusy && !isUncertain,
                                                    onClick = { confirmRemoveCloudForum = forum },
                                                    tint = colors.textSecondary,
                                                )
                                            }
                                        }
                                    }
                                } else if (!cloudFavorites.loading && cloudFavorites.error == null) {
                                    item(key = "cloud-empty") {
                                        Notice("暂无 NGA 收藏版块，在版块目录长按或版块菜单中可加入 NGA 收藏")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

