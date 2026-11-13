package dev.nagi.app.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
    FlatCard(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm)) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                AccountAvatar(AccountIdentity(user = user), Modifier.size(64.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs),
                        itemVerticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(user.name, style = typography.sectionTitle, color = colors.textPrimary)
                        UserTagBadge(user.id)
                    }
                    Text("UID ${user.id}", style = typography.metadata, color = colors.textSecondary)
                }
            }
            user.ipLocation?.let { Text("IP 属地：$it", style = typography.body, color = colors.textSecondary) }
            user.registeredAt?.let { timestamp ->
                val date = remember(timestamp) { DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(timestamp)) }
                Text("注册时间：$date", style = typography.body, color = colors.textSecondary)
            }
            Text("发帖：${user.posts}", style = typography.metadata, color = colors.textSecondary)
            if (signature.isNotBlank()) {
                Text(signature, style = typography.body, color = colors.textSecondary,
                    maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow })
                if (expanded || overflow) CapsuleButton(if (expanded) "收起签名" else "展开签名", { expanded = !expanded }, style = CapsuleStyle.Neutral)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                UserTagEditor(user.id)
                if (viewer.userId != user.id) CapsuleButton(
                    text = "发送短消息",
                    onClick = { sendMessage(user.name) },
                    icon = NagiIcons.Message,
                    style = CapsuleStyle.FrostedGold,
                    visualHeight = 42.dp,
                )
            }
        }
    }
}
