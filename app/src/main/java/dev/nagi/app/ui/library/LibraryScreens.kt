package dev.nagi.app.ui.library

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.data.UpdateCheckStatus
import dev.nagi.app.ui.components.AccountMenuButton
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.CardGroupPosition
import dev.nagi.app.ui.components.ChromeIconButton
import dev.nagi.app.ui.components.FlatCard
import dev.nagi.app.ui.components.GoldTagBadge
import dev.nagi.app.ui.components.InlineStatusBanner
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.LocalAppUpdate
import dev.nagi.app.ui.components.NagiConfirmDialog
import dev.nagi.app.ui.components.NagiHeader
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.NagiPullToRefreshBox
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
    val forumFavorites by vm.forumFavorites.collectAsStateWithLifecycle()
    val directory by vm.forums.collectAsStateWithLifecycle()
    val cloudFavorites by vm.cloudFavoriteForums.collectAsStateWithLifecycle()
    val cloudBusyKeys by vm.cloudForumBusyKeys.collectAsStateWithLifecycle()
    val cloudUncertainKeys by vm.cloudForumUncertainKeys.collectAsStateWithLifecycle()
    val cloudNotice by vm.cloudForumNotice.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(0) }
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
                            if (forumFavorites.isNotEmpty()) {
                                item(key = "forum-fav-header") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = Space.lg, vertical = Space.sm),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                                    ) {
                                        Text("收藏版块", style = typography.sectionHeader, color = colors.textSecondary)
                                        GoldTagBadge(text = "本地")
                                    }
                                }
                                itemsIndexed(forumFavorites, key = { _, fav -> "forum:${fav.key}" }) { index, fav ->
                                    SectionCardItem(CardGroupPosition.at(index, forumFavorites.size)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onForum(
                                                        Forum(
                                                            id = fav.forumId,
                                                            name = fav.name,
                                                            description = fav.description,
                                                            category = fav.category,
                                                            group = fav.groupName,
                                                            collectionId = fav.collectionId,
                                                        ),
                                                    )
                                                }
                                                .padding(horizontal = Space.lg, vertical = Space.md),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    text = fav.name,
                                                    style = typography.topicTitle,
                                                    color = colors.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (fav.description.isNotBlank()) {
                                                    Text(
                                                        text = fav.description,
                                                        style = typography.metadata,
                                                        color = colors.textSecondary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                            CapsuleButton(
                                                text = "移除",
                                                onClick = { vm.removeForumFavorite(fav.key) },
                                                style = CapsuleStyle.Neutral,
                                            )
                                        }
                                    }
                                }
                            }

                            if (favorites.isNotEmpty()) {
                                item(key = "topic-fav-header") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = Space.lg, vertical = Space.sm),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                                    ) {
                                        Text("收藏话题", style = typography.sectionHeader, color = colors.textSecondary)
                                        GoldTagBadge(text = "本地")
                                    }
                                }
                                itemsIndexed(favorites, key = { _, fav -> "topic:${fav.topicId}" }) { index, favorite ->
                                    SectionCardItem(CardGroupPosition.at(index, favorites.size)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onRead(favorite.topicId) }
                                                .padding(horizontal = Space.lg, vertical = Space.md),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                                        ) {
                                            Text(
                                                text = favorite.title,
                                                modifier = Modifier.weight(1f),
                                                style = typography.topicTitle,
                                                color = colors.textPrimary,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            CapsuleButton(
                                                text = "移除",
                                                onClick = { vm.removeFavorite(favorite.topicId) },
                                                style = CapsuleStyle.Neutral,
                                            )
                                        }
                                    }
                                }
                            }

                            if (favorites.isEmpty() && forumFavorites.isEmpty()) {
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
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = Space.lg, vertical = Space.sm),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Space.xs),
                                        ) {
                                            Text("NGA 收藏版块", style = typography.sectionHeader, color = colors.textSecondary)
                                            GoldTagBadge(text = "NGA")
                                        }
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
                                                    .padding(horizontal = Space.lg, vertical = Space.md),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                                            ) {
                                                ForumDirectoryIcon(forum.iconUrl, isFavorite = true)
                                                Column(
                                                    modifier = Modifier.weight(1f),
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
                                                        GoldTagBadge(text = "主题合集")
                                                    }
                                                }
                                                CapsuleButton(
                                                    text = if (isBusy) "移除中" else "移除",
                                                    enabled = !isBusy && !isUncertain,
                                                    onClick = { confirmRemoveCloudForum = forum },
                                                    style = CapsuleStyle.Neutral,
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

@Composable
fun SettingsScreen(
    vm: LibraryViewModel,
    preferences: ReadingPreferences,
    back: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val appUpdate = LocalAppUpdate.current
    val themeOptions = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")
    val selectedThemeIndex = themeOptions.indexOfFirst { it.first == preferences.theme }.coerceAtLeast(0)

    Column(Modifier.fillMaxSize()) {
        NagiHeader("设置", back = back) {
            ChromeIconButton(NagiIcons.Search, "搜索", onClick = { onSelect("search") })
            AccountMenuButton(onSelect)
        }
        LazyColumn(
            contentPadding = PaddingValues(top = Space.lg, bottom = Space.lg + safeBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item(key = "appearance-header") {
                Text(
                    text = "外观",
                    modifier = Modifier.padding(horizontal = Space.lg),
                    style = typography.sectionHeader,
                    color = colors.textSecondary,
                )
            }
            item(key = "theme-selector") {
                Box(Modifier.padding(horizontal = Space.lg)) {
                    SegmentedCapsuleControl(
                        options = themeOptions.map { it.second },
                        selectedIndex = selectedThemeIndex,
                        onSelect = { idx -> vm.theme(themeOptions[idx].first) },
                    )
                }
            }
            item(key = "reading-header") {
                Text(
                    text = "阅读排版",
                    modifier = Modifier.padding(horizontal = Space.lg),
                    style = typography.sectionHeader,
                    color = colors.textSecondary,
                )
            }
            item(key = "font-size") {
                SectionCardItem(CardGroupPosition.First) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.lg, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("正文字号", style = typography.topicTitle, color = colors.textPrimary)
                            Text("${preferences.fontSize} sp", style = typography.metadata, color = colors.textSecondary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            CapsuleButton(
                                text = "减小字号",
                                onClick = { vm.font(preferences.fontSize - 1) },
                                style = CapsuleStyle.Neutral,
                            )
                            CapsuleButton(
                                text = "增大字号",
                                onClick = { vm.font(preferences.fontSize + 1) },
                                style = CapsuleStyle.Neutral,
                            )
                        }
                    }
                }
                SectionCardItem(CardGroupPosition.Last) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.lg, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("正文行高", style = typography.topicTitle, color = colors.textPrimary)
                            Text("${preferences.lineHeight} sp", style = typography.metadata, color = colors.textSecondary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            CapsuleButton(
                                text = "紧凑",
                                onClick = { vm.lineHeight(preferences.lineHeight - 2) },
                                style = CapsuleStyle.Neutral,
                            )
                            CapsuleButton(
                                text = "宽松",
                                onClick = { vm.lineHeight(preferences.lineHeight + 2) },
                                style = CapsuleStyle.Neutral,
                            )
                        }
                    }
                }
            }
            item(key = "about-entry") {
                SectionCardItem(CardGroupPosition.Single) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect("about") }
                            .heightIn(min = 52.dp)
                            .padding(horizontal = Space.lg, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        FlowRow(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = Space.sm),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            verticalArrangement = Arrangement.spacedBy(Space.xs),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("关于与反馈", style = typography.topicTitle, color = colors.textPrimary)
                            if (appUpdate.hasUpdate) {
                                GoldTagBadge("新版本")
                            }
                        }
                        Icon(NagiIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.textSecondary)
                    }
                }
            }
        }
    }
}

private enum class ExternalLinkTarget(val copyButtonText: String, val clipLabel: String) {
    Feedback("复制反馈链接", "GitHub 反馈"),
    Release("复制发布页链接", "GitHub Releases"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(
    onCheckUpdate: () -> Unit = {},
    onResetUpdateStatus: () -> Unit = {},
    back: () -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val appUpdate = LocalAppUpdate.current
    DisposableEffect(Unit) {
        onDispose { onResetUpdateStatus() }
    }
    var licenses by rememberSaveable { mutableStateOf(false) }
    var unavailableUri by rememberSaveable { mutableStateOf<String?>(null) }
    var unavailableTarget by rememberSaveable { mutableStateOf<ExternalLinkTarget?>(null) }
    val context = LocalContext.current
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    val feedback = remember(version) {
        feedbackUri(version, Build.VERSION.RELEASE, "${Build.MANUFACTURER} ${Build.MODEL}")
    }
    val latestRelease = remember { latestReleaseUri() }
    fun openExternal(uri: Uri, target: ExternalLinkTarget) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            unavailableUri = null
            unavailableTarget = null
        } catch (_: ActivityNotFoundException) {
            unavailableUri = uri.toString()
            unavailableTarget = target
        }
    }
    Column(Modifier.fillMaxSize()) {
        NagiHeader("关于与反馈", back = back)
        if (appUpdate.checking) {
            LoadingLine()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(top = Space.lg, bottom = Space.lg + safeBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            FlatCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg),
                shape = NagiThemeTokens.shapes.cardGroup,
            ) {
                Column(
                    modifier = Modifier.padding(Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            verticalArrangement = Arrangement.spacedBy(Space.xs),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Nagi $version", style = typography.sectionTitle, color = colors.textPrimary)
                            if (appUpdate.hasUpdate) {
                                GoldTagBadge("新版本")
                            }
                        }
                        CapsuleButton(
                            text = if (appUpdate.checking) "检查中…" else "检查更新",
                            onClick = onCheckUpdate,
                            enabled = !appUpdate.checking,
                            style = CapsuleStyle.Neutral,
                        )
                    }
                    when (appUpdate.manualStatus) {
                        UpdateCheckStatus.IDLE -> Unit
                        UpdateCheckStatus.CHECKING -> Text(
                            text = "正在检查 GitHub 最新版本…",
                            style = typography.metadata,
                            color = colors.textSecondary,
                        )
                        UpdateCheckStatus.UP_TO_DATE -> Text(
                            text = "当前已是最新版本",
                            style = typography.metadata,
                            color = colors.textSecondary,
                        )
                        UpdateCheckStatus.UPDATE_AVAILABLE -> Text(
                            text = "发现新版本 ${appUpdate.latestTag.orEmpty()}，可点击下方「查看最新版本」前往下载",
                            style = typography.metadata,
                            color = colors.goldText,
                        )
                        UpdateCheckStatus.FAILED -> Text(
                            text = "检查更新失败，请检查网络后重试",
                            style = typography.metadata,
                            color = colors.destructive,
                        )
                    }
                    Text(
                        text = "在 GitHub 提交问题或功能建议，需要登录 GitHub。将附带 App 版本、Android 版本和设备型号，请勿包含账号凭据或私信内容。",
                        style = typography.body,
                        color = colors.textSecondary,
                    )
                }
            }
            Column {
                SectionCardItem(CardGroupPosition.First) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openExternal(feedback, ExternalLinkTarget.Feedback) }
                            .heightIn(min = 52.dp)
                            .padding(horizontal = Space.lg, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("前往 GitHub 反馈", style = typography.topicTitle, color = colors.textPrimary)
                        Icon(NagiIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.textSecondary)
                    }
                }
                SectionCardItem(CardGroupPosition.Middle) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openExternal(latestRelease, ExternalLinkTarget.Release) }
                            .heightIn(min = 52.dp)
                            .padding(horizontal = Space.lg, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        FlowRow(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = Space.sm),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            verticalArrangement = Arrangement.spacedBy(Space.xs),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("查看最新版本", style = typography.topicTitle, color = colors.textPrimary)
                            if (appUpdate.hasUpdate) {
                                GoldTagBadge(appUpdate.latestTag ?: "新版本")
                            }
                        }
                        Icon(NagiIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.textSecondary)
                    }
                }
                SectionCardItem(CardGroupPosition.Last) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { licenses = true }
                            .heightIn(min = 52.dp)
                            .padding(horizontal = Space.lg, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("开源许可", style = typography.topicTitle, color = colors.textPrimary)
                        Icon(NagiIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.textSecondary)
                    }
                }
            }
        }
    }
    val blockedUri = unavailableUri
    val blockedTarget = unavailableTarget
    if (blockedUri != null && blockedTarget != null) {
        val clearUnavailable = {
            unavailableUri = null
            unavailableTarget = null
        }
        dev.nagi.app.ui.components.NagiConfirmDialog(
            onDismissRequest = clearUnavailable,
            message = "无法打开 GitHub，请安装浏览器或复制链接后打开。",
            confirmLabel = blockedTarget.copyButtonText,
            onConfirm = {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText(blockedTarget.clipLabel, blockedUri))
                clearUnavailable()
            },
            confirmStyle = CapsuleStyle.SelectedGold,
            dismissLabel = "关闭",
            onDismiss = clearUnavailable,
        )
    }
    if (licenses) {
        val text = remember(context) {
            listOf("LICENSE", "THIRD_PARTY_NOTICES.txt").joinToString("\n\n") { asset ->
                context.assets.open(asset).bufferedReader().use { it.readText() }
            }
        }
        dev.nagi.app.ui.components.NagiDialog(
            onDismissRequest = { licenses = false },
            modifier = Modifier.heightIn(max = 600.dp),
        ) {
            Text("开源许可", style = typography.sectionTitle, color = colors.textPrimary)
            Text(
                text = text,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                style = typography.body,
                color = colors.textSecondary,
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                CapsuleButton(
                    text = "关闭",
                    onClick = { licenses = false },
                    style = CapsuleStyle.Neutral,
                )
            }
        }
    }
}
