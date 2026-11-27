package dev.nagi.app.ui.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.EdgeAlignedMetaRow
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.NagiDialog
import dev.nagi.app.ui.components.NagiIconAction
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.viewmodel.PostReferenceState
import dev.nagi.app.viewmodel.key
import dev.nagi.sdk.ContentBlock
import dev.nagi.sdk.PostReference

internal data class PostReferences(
    val states: Map<String, PostReferenceState>,
    val request: (PostReference, Boolean) -> Unit,
    val locate: (PostReference) -> Unit,
)

internal val LocalPostReferences = compositionLocalOf<PostReferences?> { null }

internal fun referencePreview(blocks: List<ContentBlock>): String = blocks.mapNotNull { block ->
    when (block) {
        is ContentBlock.Text -> block.value
        is ContentBlock.Image -> "[图片]"
        is ContentBlock.Media -> "[附件]"
        is ContentBlock.Code -> block.value
        is ContentBlock.Quote -> null // Keep the directly referenced reply, not its ancestors.
        is ContentBlock.Table -> "[表格]"
        is ContentBlock.Heading -> referencePreview(block.children)
        is ContentBlock.Divider -> referencePreview(block.children)
        is ContentBlock.Aligned -> referencePreview(block.children)
        is ContentBlock.Styled -> referencePreview(block.children)
        is ContentBlock.ListBlock -> block.items.joinToString("\n") { referencePreview(it) }
        is ContentBlock.Collapse -> "[${block.title}]"
        is ContentBlock.Dice -> "[骰子]"
    }?.trim()?.takeIf(String::isNotEmpty)
}.joinToString("\n")
    .replace(Regex("\n{2,}"), "\n")
    .ifBlank { if (blocks.any { it is ContentBlock.Quote }) "[引用内容]" else "" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReplyReferenceCard(quote: ContentBlock.Quote, preferences: ReadingPreferences, onImage: (String) -> Unit) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val references = LocalPostReferences.current
    val target = quote.reference
    val result = target?.let { references?.states?.get(it.key) }
    var expanded by rememberSaveable(quote) { mutableStateOf(false) }
    val hasSnapshot = quote.children.isNotEmpty()
    LaunchedEffect(target, hasSnapshot) {
        if (target != null && !hasSnapshot) references?.request?.invoke(target, false)
    }
    LaunchedEffect(expanded, target) {
        if (expanded && target != null) references?.request?.invoke(target, false)
    }
    val author = (result?.post?.author ?: quote.author)?.let {
        dev.nagi.app.ui.components.formatAuthorDisplayName(it)
    }
    val floor = result?.post?.floor
    val preview = referencePreview(if (hasSnapshot) quote.children else result?.post?.content.orEmpty())
    val heading = buildString {
        append(if (author == null) "引用原文" else "回复 $author")
        if (floor != null) append(" · #$floor")
    }
    val cardShape = RoundedCornerShape(12.dp)
    val previewSize = (preferences.fontSize - 1).coerceAtLeast(13)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(role = Role.Button, onClickLabel = "查看完整原文") { expanded = true },
        shape = cardShape,
        color = colors.insetContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Text(
                    text = heading,
                    modifier = Modifier.weight(1f),
                    style = typography.capsuleLabel,
                    color = colors.textPrimary,
                )
                Icon(
                    imageVector = NagiIcons.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colors.textSecondary,
                )
            }
            Text(
                text = preview.ifBlank {
                    result?.error ?: if (result?.loading == true) "正在加载原文…" else "点击查看原文"
                },
                style = typography.bodyText.copy(
                    fontSize = previewSize.sp,
                    lineHeight = (previewSize + 6).sp,
                ),
                color = colors.textSecondary,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (expanded) {
        val subtitleText = when {
            result?.post != null -> "完整原帖"
            hasSnapshot -> "引用片段"
            else -> null
        }
        NagiDialog(
            onDismissRequest = { expanded = false },
            maxWidth = 560.dp,
            modifier = Modifier.fillMaxHeight(0.70f),
        ) {
            EdgeAlignedMetaRow(
                verticalAlignment = Alignment.CenterVertically,
                leading = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = heading,
                            style = typography.sectionTitle.copy(fontSize = 16.sp, lineHeight = 22.sp),
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitleText != null) {
                            Text(
                                text = subtitleText,
                                style = typography.metadata,
                                color = colors.textSecondary,
                            )
                        }
                    }
                },
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        if (target != null && references != null) {
                            CapsuleButton(
                                text = "定位到原楼层",
                                onClick = { expanded = false; references.locate(target) },
                                icon = NagiIcons.Locate,
                                style = CapsuleStyle.SelectedGold,
                            )
                        }
                        NagiIconAction(
                            icon = NagiIcons.Close,
                            description = "关闭引用原文",
                            onClick = { expanded = false },
                        )
                    }
                },
            )
            if (result?.loading == true) LoadingLine()
            result?.error?.let { error ->
                Text(error, style = typography.bodyText, color = colors.textSecondary)
                if (references != null) {
                    CapsuleButton(
                        text = "重试加载原文",
                        onClick = { references.request(target, true) },
                        icon = NagiIcons.Refresh,
                        style = CapsuleStyle.Neutral,
                    )
                }
            }
            val content = result?.post?.content ?: quote.children
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                items(content.withIndex().toList(), key = { it.index }) { (_, block) ->
                    SelectionContainer { ContentBlocks(listOf(block), preferences, onImage) }
                }
                if (content.isEmpty() && result?.loading != true && result?.error == null) {
                    item { Text("暂无可显示的原文", color = colors.textSecondary) }
                }
            }
        }
    }
}
