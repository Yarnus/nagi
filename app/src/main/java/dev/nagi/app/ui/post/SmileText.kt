package dev.nagi.app.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
import dev.nagi.sdk.ContentColors
import dev.nagi.sdk.NgaUserLinks
import dev.nagi.app.ui.components.LocalOpenUser
import dev.nagi.sdk.NgaSmile

internal fun contentTextColor(name: String, background: Color): Color? {
    val rgb = ContentColors.rgb[name] ?: return null
    val color = Color(rgb or 0xFF000000.toInt())
    val target = if (background.luminance() > 0.5f) Color.Black else Color.White
    for (step in 0..100) {
        val candidate = lerp(color, target, step / 100f)
        val lighter = maxOf(candidate.luminance(), background.luminance())
        val darker = minOf(candidate.luminance(), background.luminance())
        if ((lighter + 0.05f) / (darker + 0.05f) >= 4.5f) return candidate
    }
    return target
}

internal fun paragraphText(
    block: ContentBlock.Text,
    linkColor: Color,
    background: Color = Color.White,
    onOpenUser: ((String) -> Unit)? = null,
): AnnotatedString = buildAnnotatedString {
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
            color = span.color?.let { contentTextColor(it, background) } ?: Color.Unspecified,
            fontSize = span.scale?.em ?: androidx.compose.ui.unit.TextUnit.Unspecified,
        ), span.start, span.end)
        span.url?.let { url ->
            val uid = NgaUserLinks.userId(url)
            addLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(
                color = linkColor, textDecoration = TextDecoration.Underline,
            )), linkInteractionListener = if (uid != null && onOpenUser != null) {
                { onOpenUser(uid) }
            } else null), span.start, span.end)
        }
    }
}

@Composable
internal fun PostParagraph(
    block: ContentBlock.Text,
    preferences: ReadingPreferences,
    textAlign: TextAlign = TextAlign.Start,
    heading: Boolean = false,
) {
    val colors = NagiThemeTokens.colors
    val openUser = LocalOpenUser.current
    val text = remember(block, colors.goldText, colors.insetContainer, openUser) {
        paragraphText(block, colors.goldText, colors.insetContainer, openUser)
    }
    val inlineContent = remember(block.smiles) {
        block.smiles.mapIndexed { index, inline ->
            "smile-$index" to InlineTextContent(
                Placeholder(1.6.em, 1.6.em, PlaceholderVerticalAlign.TextCenter),
            ) { SmileImage(inline.smile, Modifier.fillMaxSize()) }
        }.toMap()
    }
    val fontSize = preferences.fontSize + if (heading) 2 else 0
    val largestScale = (block.spans.maxOfOrNull { it.scale ?: 1f } ?: 1f).coerceAtLeast(1f)
    Text(
        text = text,
        modifier = if (textAlign == TextAlign.Start) Modifier else Modifier.fillMaxWidth(),
        inlineContent = inlineContent,
        color = colors.textPrimary,
        style = NagiThemeTokens.typography.postBody.copy(
            fontSize = fontSize.sp,
            lineHeight = (preferences.lineHeight.coerceAtLeast(fontSize + 6) * largestScale).sp,
            fontWeight = if (heading) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = textAlign,
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
