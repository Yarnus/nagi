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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.CapsuleButton
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

internal val LocalPostReferences = staticCompositionLocalOf<PostReferences?> { null }

internal fun referencePreview(blocks: List<ContentBlock>): String = blocks.joinToString("\n") { block ->
    when (block) {
        is ContentBlock.Text -> block.value
        is ContentBlock.Image -> "[图片]"
        is ContentBlock.Media -> "[附件]"
        is ContentBlock.Code -> block.value
        is ContentBlock.Quote -> "" // Keep the directly referenced reply, not its ancestors.
        is ContentBlock.Table -> "[表格]"
        is ContentBlock.Heading -> referencePreview(block.children)
        is ContentBlock.Divider -> referencePreview(block.children)
        is ContentBlock.Aligned -> referencePreview(block.children)
        is ContentBlock.ListBlock -> block.items.joinToString("\n") { referencePreview(it) }
        is ContentBlock.Collapse -> "[${block.title}]"
        is ContentBlock.Dice -> "[骰子]"
    }
}.trim().ifBlank { if (blocks.any { it is ContentBlock.Quote }) "[引用内容]" else "" }

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
    val author = result?.post?.author ?: quote.author
    val floor = result?.post?.floor
    val preview = referencePreview(if (hasSnapshot) quote.children else result?.post?.content.orEmpty())
    val heading = buildString {
        append(if (author == null) "引用原文" else "回复 $author")
        if (floor != null) append(" · #$floor")
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "查看完整原文") { expanded = true },
        shape = RoundedCornerShape(12.dp),
        color = colors.insetContainer,
    ) {
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(heading, Modifier.weight(1f), style = typography.bodyText, color = colors.textPrimary)
                Icon(NagiIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.textSecondary)
            }
            Text(
                text = preview.ifBlank {
                    result?.error ?: if (result?.loading == true) "正在加载原文…" else "点击查看原文"
                },
                style = typography.bodyText.copy(fontSize = preferences.fontSize.sp),
                color = colors.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false }, containerColor = colors.cardSurface) {
            Column(Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = Space.lg)) {
                Text(heading, style = typography.sectionTitle, color = colors.textPrimary)
                if (target != null && references != null) {
                    CapsuleButton("定位到原楼层", onClick = { expanded = false; references.locate(target) })
                }
                when {
                    result?.post != null -> Text("完整原帖", style = typography.metadata, color = colors.textSecondary)
                    hasSnapshot -> Text("引用片段", style = typography.metadata, color = colors.textSecondary)
                }
                if (result?.loading == true) LinearProgressIndicator(Modifier.fillMaxWidth())
                result?.error?.let { error ->
                    Text(error, style = typography.bodyText, color = colors.textSecondary)
                    if (target != null && references != null) {
                        CapsuleButton("重试加载原文", onClick = { references.request(target, true) })
                    }
                }
                val content = result?.post?.content ?: quote.children
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = PaddingValues(vertical = Space.md),
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
}
