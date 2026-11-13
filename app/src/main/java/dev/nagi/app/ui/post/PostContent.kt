package dev.nagi.app.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.FlatCard
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.ui.topic.formatTimeText
import dev.nagi.sdk.ContentAlignment
import dev.nagi.sdk.ContentBlock
import dev.nagi.sdk.MediaKind

@Composable
fun ContentBlocks(
    blocks: List<ContentBlock>,
    preferences: ReadingPreferences,
    onImage: (String) -> Unit,
) {
    ContentBlocks(blocks, preferences, onImage, quoteDepth = 0)
}

@Composable
private fun ContentBlocks(
    blocks: List<ContentBlock>,
    preferences: ReadingPreferences,
    onImage: (String) -> Unit,
    quoteDepth: Int,
    textAlign: TextAlign = TextAlign.Start,
    isHeading: Boolean = false,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val blockShape = RoundedCornerShape(12.dp)
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = when (textAlign) {
            TextAlign.Center -> Alignment.CenterHorizontally
            TextAlign.End -> Alignment.End
            else -> Alignment.Start
        },
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        blocks.forEach { block ->
            when (block) {
                is ContentBlock.Text -> PostParagraph(block, preferences, textAlign, isHeading)

                is ContentBlock.Heading -> Column(Modifier.fillMaxWidth().semantics { heading() }) {
                    ContentBlocks(block.children, preferences, onImage, quoteDepth, textAlign, isHeading = true)
                    HorizontalDivider(Modifier.padding(top = Space.xs), color = colors.cardBorder)
                }

                is ContentBlock.Divider -> Column(Modifier.fillMaxWidth()) {
                    ContentBlocks(block.children, preferences, onImage, quoteDepth, textAlign, isHeading = true)
                    HorizontalDivider(Modifier.padding(top = Space.xs), color = colors.cardBorder)
                }

                is ContentBlock.Aligned -> ContentBlocks(block.children, preferences, onImage, quoteDepth,
                    when (block.alignment) {
                        ContentAlignment.START -> TextAlign.Start
                        ContentAlignment.CENTER -> TextAlign.Center
                        ContentAlignment.END -> TextAlign.End
                    }, isHeading)

                is ContentBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    block.items.forEachIndexed { index, item ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(if (block.ordered) "${index + 1}. " else "• ", color = colors.textPrimary,
                                style = typography.postBody.copy(fontSize = preferences.fontSize.sp))
                            Box(Modifier.weight(1f)) {
                                ContentBlocks(item, preferences, onImage, quoteDepth, textAlign, isHeading)
                            }
                        }
                    }
                }

                is ContentBlock.Media -> OutlinedButton(
                    onClick = { uriHandler.openUri(block.url) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    val label = when (block.kind) {
                        MediaKind.AUDIO -> "打开音频"
                        MediaKind.VIDEO -> "打开视频"
                        MediaKind.ATTACHMENT -> "打开附件"
                    }
                    Text("$label：${block.url.substringBefore('?').substringAfterLast('/').ifBlank { block.url }}")
                }

                is ContentBlock.Dice -> Text(
                    text = if (block.total == null) "骰子：${block.expression}（结果不可用）"
                        else "骰子：${block.expression} = ${block.total}" +
                            if (block.rolls.isEmpty()) "" else "（投掷 ${block.rolls.joinToString(" + ")}）",
                    color = colors.textPrimary,
                    style = typography.postBody.copy(fontSize = preferences.fontSize.sp),
                )

                is ContentBlock.Image -> AsyncImage(
                    model = block.url,
                    contentDescription = "帖子图片，点击查看",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 420.dp)
                        .clip(blockShape)
                        .background(colors.insetContainer)
                        .border(1.dp, colors.cardBorder, blockShape)
                        .clickable { onImage(block.url) },
                )

                is ContentBlock.Code -> FlatCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = blockShape,
                    containerColor = colors.insetContainer,
                    borderColor = colors.cardBorder,
                ) {
                    Text(
                        text = block.value,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(Space.md),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        color = colors.textPrimary,
                    )
                }

                is ContentBlock.Table -> FlatCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = blockShape,
                    containerColor = colors.insetContainer,
                    borderColor = colors.cardBorder,
                ) {
                    Column(Modifier.horizontalScroll(rememberScrollState())) {
                        block.rows.forEach { cells ->
                            Row {
                                cells.forEach { cell ->
                                    Box(Modifier.width(160.dp).border(0.5.dp, colors.cardBorder).padding(Space.sm)) {
                                        ContentBlocks(cell, preferences, onImage, quoteDepth, textAlign, isHeading)
                                    }
                                }
                            }
                        }
                    }
                }

                is ContentBlock.Quote -> {
                    val content: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                            block.author?.let { author ->
                                Text(
                                    text = "回复 $author",
                                    style = typography.capsuleLabel,
                                    color = colors.goldText,
                                )
                            }
                            block.date?.let { date ->
                                Text(
                                    text = formatTimeText(date),
                                    style = typography.metadata,
                                    color = colors.textSecondary,
                                )
                            }
                            ContentBlocks(block.children, preferences, onImage, quoteDepth + 1, textAlign, isHeading)
                        }
                    }
                    if (quoteDepth < 2) {
                        FlatCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = blockShape,
                            containerColor = colors.insetContainer,
                            borderColor = colors.cardBorder,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Space.md),
                            ) {
                                content()
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Space.sm),
                        ) {
                            content()
                        }
                    }
                }

                is ContentBlock.Collapse -> {
                    var open by rememberSaveable { mutableStateOf(false) }
                    FlatCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = blockShape,
                        containerColor = colors.insetContainer,
                        borderColor = colors.cardBorder,
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable(role = Role.Button, onClick = { open = !open })
                                    .semantics { stateDescription = if (open) "已展开" else "已折叠" }
                                    .padding(horizontal = Space.md, vertical = Space.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = if (open) "收起：${block.title}" else "展开：${block.title}",
                                    modifier = Modifier.weight(1f),
                                    style = typography.capsuleLabel,
                                    color = colors.goldText,
                                )
                                Icon(
                                    imageVector = NagiIcons.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .graphicsLayer { rotationZ = if (open) 90f else 0f },
                                    tint = colors.goldText,
                                )
                            }
                            if (open) {
                                HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
                                Box(Modifier.padding(Space.md)) {
                                    ContentBlocks(block.children, preferences, onImage, quoteDepth, textAlign, isHeading)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
