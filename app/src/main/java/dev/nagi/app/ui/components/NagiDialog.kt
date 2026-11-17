package dev.nagi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space

/**
 * Shared Nagi modal container using a zero-elevation frosted card.
 */
@Composable
fun NagiDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 440.dp,
    properties: DialogProperties = DialogProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = NagiThemeTokens.colors
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        FrostedSurface(
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = maxWidth),
            shape = NagiThemeTokens.shapes.flatCard,
            backgroundColor = colors.frostedDropdown,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.md),
                content = content,
            )
        }
    }
}

/**
 * Shared wide capsule action bar for Nagi dialogs, stacking vertically on narrow or large-font layouts.
 */
@Composable
fun NagiDialogActions(
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmStyle: CapsuleStyle = CapsuleStyle.SelectedGold,
    confirmEnabled: Boolean = true,
    dismissLabel: String? = null,
    onDismiss: () -> Unit = {},
    dismissStyle: CapsuleStyle = CapsuleStyle.Neutral,
    dismissEnabled: Boolean = true,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val stackVertically = dismissLabel != null && (maxWidth < 260.dp || fontScale >= 1.5f)
        if (stackVertically) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                CapsuleButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = confirmEnabled,
                    style = confirmStyle,
                    visualHeight = 40.dp,
                    fillWidth = true,
                )
                CapsuleButton(
                    text = dismissLabel!!,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = dismissEnabled,
                    style = dismissStyle,
                    visualHeight = 40.dp,
                    fillWidth = true,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                if (dismissLabel != null) {
                    CapsuleButton(
                        text = dismissLabel,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = dismissEnabled,
                        style = dismissStyle,
                        visualHeight = 40.dp,
                        fillWidth = true,
                    )
                }
                CapsuleButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    enabled = confirmEnabled,
                    style = confirmStyle,
                    visualHeight = 40.dp,
                    fillWidth = true,
                )
            }
        }
    }
}

/**
 * Standard Nagi confirmation and notice dialog with wide capsule actions.
 */
@Composable
fun NagiConfirmDialog(
    onDismissRequest: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    message: String? = null,
    confirmStyle: CapsuleStyle = CapsuleStyle.SelectedGold,
    confirmEnabled: Boolean = true,
    dismissLabel: String? = null,
    onDismiss: () -> Unit = onDismissRequest,
    dismissStyle: CapsuleStyle = CapsuleStyle.Neutral,
    dismissEnabled: Boolean = true,
    maxWidth: Dp = 440.dp,
    properties: DialogProperties = DialogProperties(),
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    NagiDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        maxWidth = maxWidth,
        properties = properties,
    ) {
        if (title != null) {
            Text(
                text = title,
                style = typography.sectionTitle,
                color = colors.textPrimary,
            )
        }
        if (message != null) {
            Text(
                text = message,
                style = typography.body,
                color = colors.textSecondary,
            )
        }
        if (content != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
                content = content,
            )
        }
        NagiDialogActions(
            confirmLabel = confirmLabel,
            onConfirm = onConfirm,
            confirmStyle = confirmStyle,
            confirmEnabled = confirmEnabled,
            dismissLabel = dismissLabel,
            onDismiss = onDismiss,
            dismissStyle = dismissStyle,
            dismissEnabled = dismissEnabled,
        )
    }
}
