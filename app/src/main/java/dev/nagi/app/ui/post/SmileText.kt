package dev.nagi.app.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.LightSemanticColors
import dev.nagi.sdk.ContentBlock
import dev.nagi.sdk.NgaSmile

internal fun paragraphText(block: ContentBlock.Text, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var offset = 0
    block.smiles.forEachIndexed { index, inline ->
        append(block.value.substring(offset, inline.start))
        appendInlineContent("smile-$index", block.value.substring(inline.start, inline.end))
        offset = inline.end
    }
    append(block.value.substring(offset))
    block.spans.forEach { span ->
        val decoration = when {
            span.underline && span.strikethrough -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
            span.underline -> TextDecoration.Underline
            span.strikethrough -> TextDecoration.LineThrough
            else -> null
        }
        addStyle(SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            textDecoration = decoration,
        ), span.start, span.end)
        span.url?.let { url ->
            addLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(
                color = linkColor, textDecoration = TextDecoration.Underline,
            ))), span.start, span.end)
        }
    }
}

@Composable
internal fun PostParagraph(block: ContentBlock.Text, preferences: ReadingPreferences) {
    val colors = NagiThemeTokens.colors
    val text = remember(block, colors.goldText) { paragraphText(block, colors.goldText) }
    val inlineContent = remember(block.smiles) {
        block.smiles.mapIndexed { index, inline ->
            "smile-$index" to InlineTextContent(
                Placeholder(1.6.em, 1.6.em, PlaceholderVerticalAlign.TextCenter),
            ) { SmileImage(inline.smile, Modifier.fillMaxSize()) }
        }.toMap()
    }
    Text(
        text = text,
        inlineContent = inlineContent,
        color = colors.textPrimary,
        style = NagiThemeTokens.typography.postBody.copy(
            fontSize = preferences.fontSize.sp,
            lineHeight = preferences.lineHeight.coerceAtLeast(preferences.fontSize + 6).sp,
        ),
    )
}

@Composable
internal fun SmileImage(smile: NgaSmile, modifier: Modifier = Modifier) {
    var loadState by remember(smile.url) { mutableStateOf("加载中") }
    val colors = NagiThemeTokens.colors
    val lightBacking = colors.isDark && (smile.groupId == "ac" || smile.groupId == "a2")
    Box(
        modifier = modifier.background(
            if (lightBacking) LightSemanticColors.insetContainer else Color.Transparent,
            RoundedCornerShape(4.dp),
        ).semantics {
            contentDescription = "表情：${smile.name}"
            stateDescription = loadState
        },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = smile.url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            onLoading = { loadState = "加载中" },
            onSuccess = { loadState = "已加载" },
            onError = { loadState = "加载失败" },
        )
        if (loadState != "已加载") {
            Text(
                text = smile.name,
                modifier = Modifier.clearAndSetSemantics {},
                color = if (lightBacking) LightSemanticColors.textSecondary else colors.textSecondary,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
