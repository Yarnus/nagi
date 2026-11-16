package dev.nagi.app.navigation

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.sdk.ExtractedExternalLink
import dev.nagi.sdk.NgaExternalLinks
import dev.nagi.sdk.NgaExternalTarget

internal class ClipboardLinkState {
    private var lastHandledUrl: String? = null
    private var lastCheckedTimestamp: Long = 0L

    fun markHandled(url: String) {
        lastHandledUrl = url
    }

    fun inspectText(text: String?, timestamp: Long = 0L): ExtractedExternalLink? {
        if (timestamp > 0L) lastCheckedTimestamp = timestamp
        val raw = text?.takeIf { it.isNotBlank() }?.take(2048) ?: return null
        val extracted = NgaExternalLinks.extractFromText(raw) ?: return null
        if (extracted.url == lastHandledUrl) return null
        return extracted
    }

    fun shouldSkipByTimestamp(timestamp: Long): Boolean =
        timestamp > 0L && timestamp == lastCheckedTimestamp

    fun inspectClipboard(context: Context): ExtractedExternalLink? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        if (!clipboard.hasPrimaryClip()) return null
        val description = clipboard.primaryClipDescription ?: return null
        val supportsText = description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
            description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
        if (!supportsText) return null
        val timestamp = description.timestamp
        if (shouldSkipByTimestamp(timestamp)) return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        val text = runCatching { clip.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
        return inspectText(text, timestamp)
    }
}

internal object ClipboardLinkTracker {
    private val state = ClipboardLinkState()

    fun markHandled(url: String) = state.markHandled(url)

    fun inspectClipboard(context: Context): ExtractedExternalLink? = state.inspectClipboard(context)
}

internal fun clipboardLinkSummary(target: NgaExternalTarget): String = when (target) {
    is NgaExternalTarget.Topic -> buildString {
        append("帖子 #${target.topicId}")
        if (target.postId != null) append(" · 回复 #${target.postId}")
        else if (target.page > 1) append(" · 第 ${target.page} 页")
    }
    is NgaExternalTarget.Forum -> if (target.collectionId != null) {
        "合集 #${target.collectionId}"
    } else {
        "版块 #${target.forumId}"
    }
    is NgaExternalTarget.User -> "用户主页 UID ${target.userId}"
}

@Composable
internal fun ClipboardLinkPromptDialog(
    link: ExtractedExternalLink,
    onOpen: () -> Unit,
    onIgnore: () -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    AlertDialog(
        onDismissRequest = onIgnore,
        shape = NagiThemeTokens.shapes.topicCard,
        containerColor = colors.cardSurface,
        tonalElevation = 0.dp,
        title = {
            Text("检测到剪贴板中的 NGA 链接", style = typography.sectionTitle, color = colors.textPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    text = clipboardLinkSummary(link.target),
                    style = typography.body,
                    color = colors.textPrimary,
                )
                Text(
                    text = link.url,
                    style = typography.metadata,
                    color = colors.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            CapsuleButton(text = "打开", onClick = onOpen, style = CapsuleStyle.SolidGold)
        },
        dismissButton = {
            CapsuleButton(text = "忽略", onClick = onIgnore, style = CapsuleStyle.Neutral)
        },
    )
}
