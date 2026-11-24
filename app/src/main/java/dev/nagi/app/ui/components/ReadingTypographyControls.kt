package dev.nagi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.nagi.app.data.LINE_HEIGHT_STEP
import dev.nagi.app.data.MAX_FONT_SIZE
import dev.nagi.app.data.MAX_LINE_HEIGHT
import dev.nagi.app.data.MIN_FONT_SIZE
import dev.nagi.app.data.MIN_LINE_HEIGHT
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space

@Composable
internal fun TypographyStepperRowContent(
    title: String,
    valueLabel: String,
    decreaseDescription: String,
    onDecrease: () -> Unit,
    decreaseEnabled: Boolean,
    increaseDescription: String,
    onIncrease: () -> Unit,
    increaseEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Space.lg, end = Space.sm, top = Space.sm, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = Space.xs)
                .semantics(mergeDescendants = true) { stateDescription = valueLabel },
        ) {
            Text(title, style = typography.topicTitle, color = colors.textPrimary)
            Text(valueLabel, style = typography.metadata, color = colors.textSecondary)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            ChromeIconButton(
                icon = NagiIcons.Minus,
                description = decreaseDescription,
                onClick = onDecrease,
                enabled = decreaseEnabled,
                state = if (decreaseEnabled) null else "已到最小值",
                visualSize = 38.dp,
                iconSize = 18.dp,
            )
            ChromeIconButton(
                icon = NagiIcons.Plus,
                description = increaseDescription,
                onClick = onIncrease,
                enabled = increaseEnabled,
                state = if (increaseEnabled) null else "已到最大值",
                visualSize = 38.dp,
                iconSize = 18.dp,
            )
        }
    }
}

internal fun lineHeightValueLabel(preferences: ReadingPreferences): String =
    if (preferences.minLineHeight > MIN_LINE_HEIGHT) {
        "${preferences.effectiveLineHeight} sp（最小 ${preferences.minLineHeight} sp）"
    } else {
        "${preferences.effectiveLineHeight} sp"
    }

internal fun stepDownFontSize(preferences: ReadingPreferences, onChange: (Int) -> Unit) {
    if (preferences.fontSize > MIN_FONT_SIZE) {
        onChange((preferences.fontSize - 1).coerceAtLeast(MIN_FONT_SIZE))
    }
}

internal fun stepUpFontSize(preferences: ReadingPreferences, onChange: (Int) -> Unit) {
    if (preferences.fontSize < MAX_FONT_SIZE) {
        onChange((preferences.fontSize + 1).coerceAtMost(MAX_FONT_SIZE))
    }
}

internal fun stepDownLineHeight(preferences: ReadingPreferences, onChange: (Int) -> Unit) {
    if (preferences.effectiveLineHeight > preferences.minLineHeight) {
        onChange((preferences.effectiveLineHeight - LINE_HEIGHT_STEP).coerceAtLeast(preferences.minLineHeight))
    }
}

internal fun stepUpLineHeight(preferences: ReadingPreferences, onChange: (Int) -> Unit) {
    if (preferences.effectiveLineHeight < MAX_LINE_HEIGHT) {
        onChange((preferences.effectiveLineHeight + LINE_HEIGHT_STEP).coerceAtMost(MAX_LINE_HEIGHT))
    }
}
