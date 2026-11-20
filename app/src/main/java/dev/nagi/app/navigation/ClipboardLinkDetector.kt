package dev.nagi.app.navigation

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.FrostedSurface
import dev.nagi.app.ui.components.NagiDialogActions
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.sdk.ExtractedExternalLink
import dev.nagi.sdk.NgaExternalLinks
import dev.nagi.sdk.NgaExternalTarget

internal data class ClipboardInspectionResult(
    val link: ExtractedExternalLink? = null,
    val timestamp: Long = 0L,
    val readAttempted: Boolean = false,
    val hasCandidateClip: Boolean = false,
)

internal class ClipboardLinkState {
    private var lastHandledUrl: String? = null
    private var lastHandledTimestamp: Long = 0L
    private var lastCheckedTimestamp: Long = 0L

    fun markHandled(url: String, timestamp: Long = 0L) {
        lastHandledUrl = url
        lastHandledTimestamp = timestamp
    }

    fun inspectText(text: String?, timestamp: Long = 0L): ExtractedExternalLink? {
        val raw = text?.takeIf { it.isNotBlank() }?.take(2048) ?: return null
        if (timestamp > 0L) lastCheckedTimestamp = timestamp
        val extracted = NgaExternalLinks.extractFromText(raw) ?: return null
        val alreadyHandled = extracted.url == lastHandledUrl &&
            (lastHandledTimestamp == 0L || timestamp == 0L || timestamp == lastHandledTimestamp)
        if (alreadyHandled) return null
        return extracted
    }

    fun shouldSkipByTimestamp(timestamp: Long): Boolean =
        timestamp > 0L && timestamp == lastCheckedTimestamp

    fun inspectClipboard(context: Context): ExtractedExternalLink? =
        inspectClipboardResult(context).link

    fun inspectClipboardResult(context: Context): ClipboardInspectionResult {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
            ?: return ClipboardInspectionResult(readAttempted = true)
        if (!clipboard.hasPrimaryClip()) {
            return ClipboardInspectionResult(readAttempted = true)
        }
        val description = clipboard.primaryClipDescription
            ?: return ClipboardInspectionResult(hasCandidateClip = true)
        val supportsText = description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
            description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) ||
            description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST) ||
            description.hasMimeType("text/*")
        if (!supportsText) {
            return ClipboardInspectionResult(readAttempted = true)
        }
        val timestamp = description.timestamp
        if (shouldSkipByTimestamp(timestamp)) {
            return ClipboardInspectionResult(readAttempted = true)
        }
        val clip = clipboard.primaryClip
            ?: return ClipboardInspectionResult(hasCandidateClip = true)
        if (clip.itemCount <= 0) {
            return ClipboardInspectionResult(readAttempted = true)
        }
        val item = clip.getItemAt(0)
        val text = runCatching {
            item?.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
                ?: item?.uri?.toString()?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: return ClipboardInspectionResult(hasCandidateClip = true)

        val link = inspectText(text, timestamp)
        return ClipboardInspectionResult(
            link = link,
            timestamp = timestamp,
            readAttempted = true,
            hasCandidateClip = true,
        )
    }
}

internal object ClipboardLinkTracker {
    private val state = ClipboardLinkState()

    fun markHandled(url: String, timestamp: Long = 0L) = state.markHandled(url, timestamp)

    fun inspectClipboard(context: Context): ExtractedExternalLink? = state.inspectClipboard(context)

    fun inspectClipboardResult(context: Context): ClipboardInspectionResult = state.inspectClipboardResult(context)
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

/**
 * Centered frosted modal dialog prompting to open a detected NGA link from the clipboard.
 */
@Composable
internal fun ClipboardLinkPromptDialog(
    link: ExtractedExternalLink,
    onOpen: () -> Unit,
    onIgnore: () -> Unit,
    modifier: Modifier = Modifier,
    resolvedTitle: String? = null,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val summary = remember(link.target) { clipboardLinkSummary(link.target) }
    val displayTitle = resolvedTitle?.trim()?.takeIf { it.isNotEmpty() } ?: summary
    FrostedSurface(
        modifier = modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .semantics { dialog() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        shape = NagiThemeTokens.shapes.flatCard,
        backgroundColor = colors.frostedDropdown,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(
                text = "剪贴板链接",
                style = typography.sectionTitle,
                color = colors.textPrimary,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NagiThemeTokens.shapes.innerCard)
                    .background(colors.insetContainer.copy(alpha = if (colors.isDark) 0.85f else 0.90f))
                    .padding(horizontal = Space.md, vertical = Space.sm),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                    Text(
                        text = displayTitle,
                        style = typography.topicTitle,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = link.url,
                        style = typography.metadata,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            NagiDialogActions(
                confirmLabel = "打开",
                onConfirm = onOpen,
                confirmStyle = CapsuleStyle.SelectedGold,
                dismissLabel = "忽略",
                onDismiss = onIgnore,
                dismissStyle = CapsuleStyle.Neutral,
            )
        }
    }
}
