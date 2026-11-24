package dev.nagi.app.ui.library

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nagi.app.data.DEFAULT_FONT_SIZE
import dev.nagi.app.data.DEFAULT_LINE_HEIGHT
import dev.nagi.app.data.MAX_FONT_SIZE
import dev.nagi.app.data.MAX_LINE_HEIGHT
import dev.nagi.app.data.MIN_FONT_SIZE
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.data.SUPPORTED_NGA_DOMAINS
import dev.nagi.app.data.UpdateCheckStatus
import dev.nagi.app.data.calculateAppCacheBytes
import dev.nagi.app.data.clearAppCache
import dev.nagi.app.data.displayName
import dev.nagi.app.data.formatCacheSize
import dev.nagi.app.data.hostLabel
import dev.nagi.app.ui.components.AccountMenuButton
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.CardGroupPosition
import dev.nagi.app.ui.components.ChromeIconButton
import dev.nagi.app.ui.components.FlatCard
import dev.nagi.app.ui.components.GoldSwitch
import dev.nagi.app.ui.components.GoldTagBadge
import dev.nagi.app.ui.components.InlineStatusBanner
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.LocalAppUpdate
import dev.nagi.app.ui.components.NagiConfirmDialog
import dev.nagi.app.ui.components.NagiDialogActions
import dev.nagi.app.ui.components.NagiFrostedSheet
import dev.nagi.app.ui.components.NagiHeader
import dev.nagi.app.ui.components.NagiIconAction
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.NagiPullToRefreshBox
import dev.nagi.app.ui.components.NagiSheetHeader
import dev.nagi.app.ui.components.NeutralTagBadge
import dev.nagi.app.ui.components.Notice
import dev.nagi.app.ui.components.SectionCardItem
import dev.nagi.app.ui.components.SegmentedCapsuleControl
import dev.nagi.app.ui.components.TypographyStepperRowContent
import dev.nagi.app.ui.components.lineHeightValueLabel
import dev.nagi.app.ui.components.safeBottomPadding
import dev.nagi.app.ui.components.stepDownFontSize
import dev.nagi.app.ui.components.stepDownLineHeight
import dev.nagi.app.ui.components.stepUpFontSize
import dev.nagi.app.ui.components.stepUpLineHeight
import dev.nagi.app.ui.forum.ForumDirectoryIcon
import dev.nagi.app.ui.forum.resolveCloudForumMetadata
import dev.nagi.app.ui.post.PostParagraph
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.viewmodel.LibraryViewModel
import dev.nagi.sdk.ContentBlock
import dev.nagi.sdk.Forum
import dev.nagi.sdk.NgaDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: LibraryViewModel,
    preferences: ReadingPreferences,
    back: () -> Unit,
    onSelect: (String) -> Unit,
    accountLabel: String? = null,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val appUpdate = LocalAppUpdate.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeOptions = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")
    val selectedThemeIndex = themeOptions.indexOfFirst { it.first == preferences.theme }.coerceAtLeast(0)
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
    }
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    var clearingCache by remember { mutableStateOf(false) }
    var confirmingClearCache by rememberSaveable { mutableStateOf(false) }
    var pickingDomain by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(context) {
        cacheBytes = calculateAppCacheBytes(context)
    }

    if (confirmingClearCache) {
        NagiConfirmDialog(
            onDismissRequest = { if (!clearingCache) confirmingClearCache = false },
            title = "清理图片与临时缓存？",
            message = "将清除已缓存的帖子图片与分享临时文件，不会影响登录状态、收藏与浏览历史。",
            confirmLabel = if (clearingCache) "清理中…" else "清理",
            confirmStyle = CapsuleStyle.Destructive,
            confirmEnabled = !clearingCache,
            onConfirm = {
                if (clearingCache) return@NagiConfirmDialog
                clearingCache = true
                scope.launch {
                    try {
                        val remainingBytes = clearAppCache(context)
                        withContext(Dispatchers.Main.immediate) {
                            cacheBytes = remainingBytes
                            confirmingClearCache = false
                            Toast.makeText(context, "已清理缓存", Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        withContext(NonCancellable + Dispatchers.Main.immediate) {
                            clearingCache = false
                        }
                    }
                }
            },
            dismissLabel = "取消",
            dismissEnabled = !clearingCache,
            onDismiss = { confirmingClearCache = false },
        )
    }

    if (pickingDomain) {
        NgaDomainPickerSheet(
            selected = preferences.ngaDomain,
            onSelect = { domain ->
                vm.setNgaDomain(domain)
                pickingDomain = false
            },
            onDismiss = { pickingDomain = false },
        )
    }

    Column(Modifier.fillMaxSize()) {
        NagiHeader("设置", back = back) {
            ChromeIconButton(NagiIcons.Search, "搜索", onClick = { onSelect("search") })
            AccountMenuButton(onSelect)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = Space.lg, bottom = Space.lg + safeBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                SettingSectionTitle("外观")
                Box(Modifier.padding(horizontal = Space.lg)) {
                    SegmentedCapsuleControl(
                        options = themeOptions.map { it.second },
                        selectedIndex = selectedThemeIndex,
                        onSelect = { idx -> vm.theme(themeOptions[idx].first) },
                    )
                }
                SettingSectionTitle("阅读排版")
                val sampleBlock = remember {
                    ContentBlock.Text("排版预览：艾泽拉斯议事厅的正文将以当前字号与行高显示，长段落阅读时行距保持舒展。")
                }
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
                        PostParagraph(
                            block = sampleBlock,
                            preferences = preferences,
                        )
                        Text(
                            text = "当前 ${preferences.fontSize} sp · 实际行高 ${preferences.effectiveLineHeight} sp",
                            style = typography.metadata,
                            color = colors.textSecondary,
                        )
                    }
                }
                Column {
                    SettingStepperRow(
                        position = CardGroupPosition.First,
                        title = "正文字号",
                        valueLabel = "${preferences.fontSize} sp",
                        decreaseDescription = "减小字号",
                        onDecrease = { stepDownFontSize(preferences, vm::font) },
                        decreaseEnabled = preferences.fontSize > MIN_FONT_SIZE,
                        increaseDescription = "增大字号",
                        onIncrease = { stepUpFontSize(preferences, vm::font) },
                        increaseEnabled = preferences.fontSize < MAX_FONT_SIZE,
                    )
                    SettingStepperRow(
                        position = CardGroupPosition.Middle,
                        title = "正文行高",
                        valueLabel = lineHeightValueLabel(preferences),
                        decreaseDescription = "紧凑",
                        onDecrease = { stepDownLineHeight(preferences, vm::lineHeight) },
                        decreaseEnabled = preferences.effectiveLineHeight > preferences.minLineHeight,
                        increaseDescription = "宽松",
                        onIncrease = { stepUpLineHeight(preferences, vm::lineHeight) },
                        increaseEnabled = preferences.effectiveLineHeight < MAX_LINE_HEIGHT,
                    )
                    val isDefault = preferences.isDefaultTypography
                    SectionCardItem(CardGroupPosition.Last) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isDefault, role = Role.Button) { vm.resetTypography() }
                                .semantics {
                                    stateDescription = if (isDefault) "已是默认值" else "已自定义"
                                }
                                .heightIn(min = 52.dp)
                                .padding(horizontal = Space.lg, vertical = Space.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = Space.sm),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "恢复默认排版",
                                    style = typography.topicTitle,
                                    color = if (isDefault) colors.textSecondary else colors.textPrimary,
                                )
                                Text(
                                    text = "默认字号 $DEFAULT_FONT_SIZE sp · 行高 $DEFAULT_LINE_HEIGHT sp",
                                    style = typography.metadata,
                                    color = colors.textSecondary,
                                )
                            }
                            Text(
                                text = if (isDefault) "已默认" else "重置",
                                style = typography.capsuleLabel,
                                color = if (isDefault) colors.textMuted else colors.goldText,
                            )
                        }
                    }
                }
                SettingSectionTitle("浏览与网络")
                Column {
                    SettingSwitchRow(
                        position = CardGroupPosition.First,
                        title = "剪贴板链接识别",
                        subtitle = "切回前台时自动识别剪贴板中的 NGA 链接",
                        checked = preferences.clipboardLinkDetection,
                        onCheckedChange = vm::setClipboardLinkDetection,
                    )
                    SectionCardItem(CardGroupPosition.Middle) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { pickingDomain = true }
                                .heightIn(min = 52.dp)
                                .padding(horizontal = Space.lg, vertical = Space.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = Space.sm),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text("NGA 访问线路", style = typography.topicTitle, color = colors.textPrimary)
                                Text(
                                    text = "${preferences.ngaDomain.displayName()} · ${preferences.ngaDomain.hostLabel()}",
                                    style = typography.metadata,
                                    color = colors.textSecondary,
                                )
                            }
                            Icon(NagiIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.textSecondary)
                        }
                    }
                    SettingSwitchRow(
                        position = CardGroupPosition.Last,
                        title = "自动检查更新",
                        subtitle = "每日静默检查 GitHub 最新正式版本",
                        checked = preferences.autoCheckUpdates,
                        onCheckedChange = vm::setAutoCheckUpdates,
                    )
                }
                SettingSectionTitle("账号与存储")
                Column {
                    SectionCardItem(CardGroupPosition.First) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect("accounts") }
                                .heightIn(min = 52.dp)
                                .padding(horizontal = Space.lg, vertical = Space.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = Space.sm),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text("账号管理", style = typography.topicTitle, color = colors.textPrimary)
                                Text(
                                    text = accountLabel ?: "管理已登录账号或切换访客模式",
                                    style = typography.metadata,
                                    color = colors.textSecondary,
                                )
                            }
                            Icon(NagiIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.textSecondary)
                        }
                    }
                    val bytes = cacheBytes
                    val canClear = !clearingCache && (bytes ?: 0L) > 0L
                    SectionCardItem(CardGroupPosition.Last) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = canClear, role = Role.Button) { confirmingClearCache = true }
                                .heightIn(min = 52.dp)
                                .padding(horizontal = Space.lg, vertical = Space.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = Space.sm),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "清理图片与临时缓存",
                                    style = typography.topicTitle,
                                    color = if (canClear || bytes == null) colors.textPrimary else colors.textSecondary,
                                )
                                Text(
                                    text = when {
                                        clearingCache -> "正在清理缓存…"
                                        bytes == null -> "正在计算缓存大小…"
                                        else -> formatCacheSize(bytes)
                                    },
                                    style = typography.metadata,
                                    color = colors.textSecondary,
                                )
                            }
                            if (canClear) {
                                Text(
                                    text = "清理",
                                    style = typography.capsuleLabel,
                                    color = colors.goldText,
                                )
                            }
                        }
                    }
                }
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
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = Space.sm),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                                verticalArrangement = Arrangement.spacedBy(Space.xs),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("关于与反馈", style = typography.topicTitle, color = colors.textPrimary)
                                if (appUpdate.hasUpdate) {
                                    GoldTagBadge("新版本")
                                }
                            }
                            if (version.isNotBlank()) {
                                Text(
                                    text = "当前版本 $version",
                                    style = typography.metadata,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                        Icon(NagiIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSectionTitle(title: String) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = Space.lg),
        style = typography.sectionHeader,
        color = colors.textSecondary,
    )
}

@Composable
private fun SettingSwitchRow(
    position: CardGroupPosition,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    SectionCardItem(position) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(start = Space.lg, end = Space.md, top = Space.sm, bottom = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = Space.xs),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = typography.topicTitle, color = colors.textPrimary)
                Text(subtitle, style = typography.metadata, color = colors.textSecondary)
            }
            GoldSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                contentDescription = title,
            )
        }
    }
}

@Composable
private fun NgaDomainPickerSheet(
    selected: NgaDomain,
    onSelect: (NgaDomain) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val dividerColor = colors.cardBorder.copy(alpha = 0.55f)
    NagiFrostedSheet(
        onDismissRequest = onDismiss,
        maxWidth = 480.dp,
    ) {
        NagiSheetHeader(
            title = "选择 NGA 访问线路",
            subtitle = "切换后后续请求将优先使用所选域名",
            onClose = onDismiss,
        )
        HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
        SUPPORTED_NGA_DOMAINS.forEachIndexed { index, domain ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Space.lg),
                    thickness = 0.5.dp,
                    color = dividerColor,
                )
            }
            val isSelected = domain == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.RadioButton) { onSelect(domain) }
                    .semantics {
                        stateDescription = if (isSelected) "当前已选" else "未选中"
                    }
                    .heightIn(min = 52.dp)
                    .padding(horizontal = Space.lg, vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = Space.sm),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = domain.displayName(),
                        style = typography.topicTitle,
                        color = if (isSelected) colors.goldText else colors.textPrimary,
                    )
                    Text(
                        text = domain.hostLabel(),
                        style = typography.metadata,
                        color = colors.textSecondary,
                    )
                }
                if (isSelected) {
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
}

@Composable
private fun SettingStepperRow(
    position: CardGroupPosition,
    title: String,
    valueLabel: String,
    decreaseDescription: String,
    onDecrease: () -> Unit,
    decreaseEnabled: Boolean,
    increaseDescription: String,
    onIncrease: () -> Unit,
    increaseEnabled: Boolean,
) {
    SectionCardItem(position) {
        TypographyStepperRowContent(
            title = title,
            valueLabel = valueLabel,
            decreaseDescription = decreaseDescription,
            onDecrease = onDecrease,
            decreaseEnabled = decreaseEnabled,
            increaseDescription = increaseDescription,
            onIncrease = onIncrease,
            increaseEnabled = increaseEnabled,
        )
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
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
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
                            icon = NagiIcons.Refresh,
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
                            text = "发现新版本 ${appUpdate.latestTag.orEmpty()}，请前往发布页下载",
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
            NagiDialogActions(
                confirmLabel = "关闭",
                onConfirm = { licenses = false },
                confirmStyle = CapsuleStyle.Neutral,
            )
        }
    }
}
