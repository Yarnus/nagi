package dev.nagi.app.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nagi.app.ui.components.*
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.viewmodel.AccountIdentity
import dev.nagi.app.viewmodel.UserProfileState
import dev.nagi.sdk.ContentBlock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun contentPreview(blocks: List<ContentBlock>): String = blocks.joinToString("\n") { block ->
    when (block) {
        is ContentBlock.Text -> block.value
        is ContentBlock.Code -> block.value
        is ContentBlock.Quote -> contentPreview(block.children)
        is ContentBlock.Collapse -> block.title + "\n" + contentPreview(block.children)
        is ContentBlock.Table -> block.rows.joinToString("\n") { row -> row.joinToString(" ") { contentPreview(it) } }
        is ContentBlock.Heading -> contentPreview(block.children)
        is ContentBlock.Divider -> contentPreview(block.children)
        is ContentBlock.Aligned -> contentPreview(block.children)
        is ContentBlock.ListBlock -> block.items.joinToString("\n") { contentPreview(it) }
        is ContentBlock.Media -> block.url
        is ContentBlock.Dice -> block.expression + (block.total?.let { " = $it" } ?: "")
        is ContentBlock.Image -> "[图片]"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UserProfileCard(state: UserProfileState, retry: () -> Unit, sendMessage: (String) -> Unit) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val viewer = LocalAccountIdentity.current
    if (state.loading) LoadingLine()
    state.error?.let { Notice(it, retry) }
    val user = state.user ?: return
    val signature = remember(user.signature) { contentPreview(user.signature) }
    var expanded by rememberSaveable(user.id) { mutableStateOf(false) }
    var overflow by remember(user.id, signature) { mutableStateOf(false) }
    val registeredDate = remember(user.registeredAt) {
        user.registeredAt?.let { timestamp ->
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochSecond(timestamp))
        }
    }
    val statStyle = typography.metadata.copy(
        fontFeatureSettings = "tnum",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    FlatCard(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.xs)) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                AccountAvatar(AccountIdentity(user = user), size = 60.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(user.name, style = typography.sectionTitle, color = colors.textPrimary)
                        UserTagBadge(user.id)
                    }
                    Text(
                        text = "UID ${user.id}",
                        style = statStyle,
                        color = colors.textSecondary,
                    )
                    FlowRow(
                        modifier = Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        verticalArrangement = Arrangement.spacedBy(Space.xxs),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileMetaStat(
                            icon = NagiIcons.Message,
                            value = user.posts.toString(),
                            description = "发帖：${user.posts}",
                            style = statStyle,
                        )
                        user.ipLocation?.let { loc ->
                            ProfileMetaStat(
                                icon = NagiIcons.Globe,
                                value = loc,
                                description = "IP 属地：$loc",
                                style = statStyle,
                            )
                        }
                        registeredDate?.let { date ->
                            ProfileMetaStat(
                                icon = NagiIcons.History,
                                value = date,
                                description = "注册时间：$date",
                                style = statStyle,
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserTagEditor(user.id, iconOnly = true)
                    if (viewer.userId != user.id) {
                        val msgInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .clickable(
                                    role = Role.Button,
                                    interactionSource = msgInteraction,
                                    indication = null,
                                    onClickLabel = "发送短消息",
                                    onClick = { sendMessage(user.name) },
                                )
                                .semantics { contentDescription = "发送短消息" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(36.dp)
                                    .nagiPressFeedback(
                                        interactionSource = msgInteraction,
                                        enabled = true,
                                        pressedScale = 0.92f,
                                        shape = CircleShape,
                                    ),
                                shape = CircleShape,
                                color = colors.goldContainer,
                                border = BorderStroke(1.dp, colors.goldBorder),
                                shadowElevation = 0.dp,
                                tonalElevation = 0.dp,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = NagiIcons.Message,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = colors.goldText,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (signature.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.insetContainer,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                ) {
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                                .background(colors.goldAccent),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = Space.md, vertical = Space.sm),
                            verticalArrangement = Arrangement.spacedBy(Space.xs),
                        ) {
                            Text(
                                text = "签名",
                                style = typography.metadata.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.goldText,
                            )
                            Text(
                                text = signature,
                                style = typography.body,
                                color = colors.textSecondary,
                                maxLines = if (expanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow },
                            )
                            if (expanded || overflow) {
                                CapsuleButton(
                                    text = if (expanded) "收起签名" else "展开签名",
                                    onClick = { expanded = !expanded },
                                    icon = if (expanded) NagiIcons.ChevronUp else NagiIcons.ChevronDown,
                                    style = CapsuleStyle.Neutral,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMetaStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    description: String,
    style: androidx.compose.ui.text.TextStyle,
) {
    val colors = NagiThemeTokens.colors
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = description
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = colors.textSecondary,
        )
        Text(
            text = value,
            style = style,
            color = colors.textSecondary,
        )
    }
}
