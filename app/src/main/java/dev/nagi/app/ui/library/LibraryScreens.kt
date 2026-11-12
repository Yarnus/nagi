package dev.nagi.app.ui.library

import android.content.Intent
import android.os.Build
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.AccountMenuButton
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.CardGroupPosition
import dev.nagi.app.ui.components.ChromeIconButton
import dev.nagi.app.ui.components.FlatCard
import dev.nagi.app.ui.components.GoldTagBadge
import dev.nagi.app.ui.components.NagiHeader
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.Notice
import dev.nagi.app.ui.components.SectionCardItem
import dev.nagi.app.ui.components.SegmentedCapsuleControl
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.viewmodel.LibraryViewModel
import dev.nagi.sdk.Forum

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

    Column(Modifier.fillMaxSize()) {
        NagiHeader("本地收藏", "仅保存在此设备，不同步 NGA", back = back) {
            ChromeIconButton(NagiIcons.Search, "搜索", onClick = { onSelect("search") })
            AccountMenuButton(onSelect)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = Space.md),
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
    val themeOptions = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")
    val selectedThemeIndex = themeOptions.indexOfFirst { it.first == preferences.theme }.coerceAtLeast(0)

    Column(Modifier.fillMaxSize()) {
        NagiHeader("设置", back = back) {
            ChromeIconButton(NagiIcons.Search, "搜索", onClick = { onSelect("search") })
            AccountMenuButton(onSelect)
        }
        LazyColumn(
            contentPadding = PaddingValues(vertical = Space.lg),
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
                        Text("关于与反馈", style = typography.topicTitle, color = colors.textPrimary)
                        Icon(NagiIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(back: () -> Unit) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    var licenses by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    Column(Modifier.fillMaxSize()) {
        NagiHeader("关于与反馈", back = back)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Space.lg),
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
                    Text("Nagi $version", style = typography.sectionTitle, color = colors.textPrimary)
                    Text(
                        text = "反馈时请注明版本、复现步骤及设备信息，勿包含账号凭据或私信内容。",
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
                            .clickable {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Nagi $version\nAndroid ${Build.VERSION.RELEASE}\n\n复现步骤：\n预期结果：\n实际结果：",
                                    )
                                }
                                context.startActivity(Intent.createChooser(share, "分享反馈信息"))
                            }
                            .heightIn(min = 52.dp)
                            .padding(horizontal = Space.lg, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("分享反馈信息", style = typography.topicTitle, color = colors.textPrimary)
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
    if (licenses) {
        val text = remember(context) {
            listOf("LICENSE", "THIRD_PARTY_NOTICES.txt").joinToString("\n\n") { asset ->
                context.assets.open(asset).bufferedReader().use { it.readText() }
            }
        }
        Dialog(onDismissRequest = { licenses = false }) {
            FlatCard(
                modifier = Modifier.fillMaxWidth(),
                shape = NagiThemeTokens.shapes.cardGroup,
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 600.dp)
                        .padding(Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Text("开源许可", style = typography.sectionTitle, color = colors.textPrimary)
                    Text(
                        text = text,
                        modifier = Modifier
                            .weight(1f)
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
    }
}
