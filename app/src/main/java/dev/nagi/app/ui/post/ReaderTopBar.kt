package dev.nagi.app.ui.post

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nagi.app.ui.components.ChromeIconButton
import dev.nagi.app.ui.components.FrostedDropdownDivider
import dev.nagi.app.ui.components.FrostedDropdownItem
import dev.nagi.app.ui.components.FrostedDropdownMenu
import dev.nagi.app.ui.components.FrostedSurface
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.nagiPressFeedback
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space

@Composable
internal fun ReaderTopBar(
    title: String,
    showTitle: Boolean,
    favorite: Boolean,
    authorOnly: Boolean,
    filterLabel: String? = null,
    enabled: Boolean,
    canFilter: Boolean,
    back: () -> Unit,
    page: Int = 0,
    totalPages: Int = 0,
    onPage: () -> Unit = {},
    onFavorite: () -> Unit,
    onFilter: () -> Unit,
    onRefresh: () -> Unit,
    onTypography: (() -> Unit)? = null,
    onShareCard: () -> Unit = {},
    onShare: () -> Unit = {},
    onCopyLink: () -> Unit = {},
    onOpenBrowser: () -> Unit = {},
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    var menu by remember { mutableStateOf(false) }
    val activeFilterLabel = filterLabel ?: "只看楼主"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChromeIconButton(NagiIcons.Back, "返回", back)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Space.sm),
            contentAlignment = Alignment.CenterStart,
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showTitle && title.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = title,
                    style = typography.appBarTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.textPrimary,
                )
            }
        }
        if (page > 0) {
            val pageText = if (totalPages > 0) "$page / $totalPages" else "$page"
            val description = buildString {
                append("第 $page 页")
                if (totalPages > 0) append("，共 $totalPages 页")
                if (authorOnly) append("，$activeFilterLabel")
            }
            val pageInteractionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .padding(end = Space.xs)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        interactionSource = pageInteractionSource,
                        indication = null,
                        onClickLabel = "跳转到指定页或楼层",
                        onClick = onPage,
                    )
                    .semantics {
                        contentDescription = description
                        stateDescription = if (authorOnly) activeFilterLabel else "全部回复"
                    },
                contentAlignment = Alignment.Center,
            ) {
                FrostedSurface(
                    modifier = Modifier.nagiPressFeedback(
                        interactionSource = pageInteractionSource,
                        enabled = enabled,
                        pressedScale = 0.95f,
                        shape = CircleShape,
                    ),
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
            FrostedDropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
            ) {
                FrostedDropdownItem(
                    text = if (authorOnly) "查看全部" else "只看楼主",
                    leadingIcon = NagiIcons.Person,
                    selected = authorOnly,
                    enabled = enabled && canFilter,
                    modifier = Modifier.semantics { selected = authorOnly },
                    onClick = {
                        menu = false
                        onFilter()
                    },
                )
                FrostedDropdownDivider()
                FrostedDropdownItem(
                    text = if (favorite) "取消本地收藏" else "本地收藏",
                    supporting = "仅保存在此设备",
                    leadingIcon = NagiIcons.Bookmark,
                    selected = favorite,
                    enabled = enabled,
                    onClick = {
                        menu = false
                        onFavorite()
                    },
                )
                FrostedDropdownItem(
                    text = "刷新",
                    leadingIcon = NagiIcons.Refresh,
                    enabled = enabled,
                    onClick = {
                        menu = false
                        onRefresh()
                    },
                )
                if (onTypography != null) {
                    FrostedDropdownItem(
                        text = "阅读排版",
                        leadingIcon = NagiIcons.Tuning,
                        onClick = {
                            menu = false
                            onTypography()
                        },
                    )
                }
                FrostedDropdownDivider()
                FrostedDropdownItem(
                    text = "分享主楼卡片",
                    leadingIcon = NagiIcons.ShareCard,
                    enabled = enabled,
                    onClick = { menu = false; onShareCard() },
                )
                FrostedDropdownItem(
                    text = "分享主题链接",
                    leadingIcon = NagiIcons.Share,
                    onClick = { menu = false; onShare() },
                )
                FrostedDropdownItem(
                    text = "复制主题链接",
                    leadingIcon = NagiIcons.Link,
                    onClick = { menu = false; onCopyLink() },
                )
                FrostedDropdownItem(
                    text = "在浏览器中打开",
                    leadingIcon = NagiIcons.Globe,
                    onClick = { menu = false; onOpenBrowser() },
                )
            }
        }
    }
}

