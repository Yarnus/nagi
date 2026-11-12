package dev.nagi.app.ui.post

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.nagi.app.ui.components.frostedCapsuleBackdrop
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.ProgressiveBlurScaffold
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space

@Composable
internal fun ReaderScaffold(
    onReply: () -> Unit,
    scrollInProgress: Boolean,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    ProgressiveBlurScaffold(
        modifier = modifier,
        topBar = topBar,
        bottomEdgeBlur = false,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Space.lg, end = Space.lg, bottom = Space.lg)
                    .testTag("reader-reply-slot"),
                contentAlignment = Alignment.CenterEnd,
            ) {
                ReaderReplyCapsule(onReply, visible = !scrollInProgress)
            }
        },
        content = content,
    )
}

@Composable
private fun ReaderReplyCapsule(onReply: () -> Unit, visible: Boolean) {
    val colors = NagiThemeTokens.colors
    val opacity by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 180 else 120),
        label = "reader-reply-opacity",
    )
    val action = if (visible) {
        Modifier
            .clickable(role = Role.Button, onClick = onReply)
            .semantics { contentDescription = "回复帖子" }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer { alpha = opacity }
            .clip(CircleShape)
            .then(action),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 42.dp)
                .frostedCapsuleBackdrop(colors.frostedChrome, CircleShape)
                .border(1.dp, colors.cardBorder, CircleShape)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = NagiIcons.Reply,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.goldText,
            )
            Text(
                text = "回复",
                modifier = Modifier.clearAndSetSemantics { },
                style = NagiThemeTokens.typography.capsuleButton,
                color = colors.goldText,
            )
        }
    }
}
