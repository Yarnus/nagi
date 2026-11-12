package dev.nagi.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.nagi.app.viewmodel.NotificationsState
import coil.compose.SubcomposeAsyncImage
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.viewmodel.AccountIdentity

val LocalAccountIdentity = staticCompositionLocalOf { AccountIdentity() }
val LocalNotifications = staticCompositionLocalOf { NotificationsState() }

@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val colors = NagiThemeTokens.colors
    Surface(
        modifier = modifier.clearAndSetSemantics { },
        shape = CircleShape, color = colors.destructive,
        shadowElevation = 0.dp, tonalElevation = 0.dp,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            style = NagiThemeTokens.typography.tagBadge,
            color = if (colors.isDark) Color(0xFF18120A) else Color.White,
        )
    }
}

@Composable
fun AccountAvatar(identity: AccountIdentity, modifier: Modifier = Modifier) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.insetContainer),
        contentAlignment = Alignment.Center,
    ) {
        @Composable
        fun fallback() {
            val initial = identity.user?.name?.firstOrNull()?.toString()
            if (initial != null) {
                Text(
                    text = initial,
                    style = typography.capsuleLabel,
                    color = colors.goldText,
                )
            } else {
                Icon(
                    imageVector = NagiIcons.Person,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        val avatar = identity.user?.avatar.orEmpty()
        key(identity.revision, identity.userId) {
            if (avatar.isBlank()) {
                fallback()
            } else {
                SubcomposeAsyncImage(
                    model = avatar,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = { fallback() },
                    error = { fallback() },
                )
            }
        }
    }
}

@Composable
fun AccountMenuButton(onSelect: (String) -> Unit) {
    val identity = LocalAccountIdentity.current
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val notifications = LocalNotifications.current
    val unread = notifications.takeIf { it.revision == identity.revision && it.userId == identity.userId }?.unreadCount ?: 0
    var expanded by rememberSaveable(identity.revision) { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = if (unread > 0) "头像菜单，$unread 条未读通知" else "头像菜单"
                    stateDescription = identity.user?.name ?: identity.userId?.let { "UID $it" } ?: "访客模式"
                },
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = colors.frostedChrome,
                border = BorderStroke(1.dp, colors.cardBorder),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AccountAvatar(identity, Modifier.size(28.dp))
                }
            }
        }
        UnreadBadge(unread, Modifier.align(Alignment.TopEnd))
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(280.dp),
            shape = NagiThemeTokens.shapes.dropdown,
            containerColor = colors.frostedDropdown,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, colors.cardBorder),
        ) {
            @Composable
            fun entry(label: String, icon: ImageVector, route: String, supporting: String? = null) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = label,
                                style = typography.body,
                                color = colors.textPrimary,
                            )
                            if (supporting != null) {
                                Text(
                                    text = supporting,
                                    style = typography.metadata,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    },
                    leadingIcon = {
                        if (route == "profile" || label == "未登录") {
                            AccountAvatar(identity, Modifier.size(24.dp))
                        } else {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(route)
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }

            @Composable
            fun divider() {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs),
                    thickness = 1.dp,
                    color = colors.cardBorder,
                )
            }

            entry(
                label = identity.user?.name ?: identity.userId?.let { "UID $it" } ?: "未登录",
                icon = NagiIcons.Person,
                route = if (identity.userId == null) "login" else "profile",
                supporting = identity.userId?.let { "UID $it · 我的档案" } ?: "访客模式",
            )
            entry(
                label = if (identity.userId == null) "登录 NGA" else "管理账号",
                icon = NagiIcons.Person,
                route = if (identity.userId == null) "login" else "accounts",
            )
            if (identity.userId == null) {
                entry("管理账号", NagiIcons.Person, "accounts")
            }
            divider()
            entry("通知", NagiIcons.Notification, "notifications", if (unread > 0) "$unread 条未读" else null)
            entry("短消息", NagiIcons.Message, "messages")
            divider()
            entry("收藏话题", NagiIcons.Bookmark, "favorites", "本地收藏 · 仅保存在此设备")
            entry("浏览历史", NagiIcons.History, "history")
            divider()
            entry("设置", NagiIcons.Tuning, "settings")
            entry("关于与反馈", NagiIcons.Info, "about")
        }
    }
}
