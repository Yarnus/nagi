package dev.nagi.app.ui.post

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.sdk.ContentParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorPopup(dismiss: () -> Unit, content: @Composable () -> Unit) {
    val colors = NagiThemeTokens.colors
    if (LocalConfiguration.current.screenWidthDp < 600) {
        val sheet = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden },
        )
        ModalBottomSheet(
            onDismissRequest = {},
            sheetState = sheet,
            dragHandle = null,
            shape = NagiThemeTokens.shapes.bottomSheet,
            containerColor = colors.groupedCanvas,
            tonalElevation = 0.dp,
            scrimColor = Color.Black.copy(alpha = 0.32f),
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        ) {
            BackHandler { dismiss() }
            content()
        }
    } else {
        Dialog(
            onDismissRequest = dismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Surface(
                modifier = Modifier
                    .imePadding()
                    .safeDrawingPadding()
                    .padding(Space.xl)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = colors.groupedCanvas,
                border = BorderStroke(1.dp, colors.cardBorder),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                content = content,
            )
        }
    }
}

@Composable
internal fun ComposerInput(
    value: TextFieldValue,
    edit: (TextFieldValue) -> Unit,
    placeholder: String,
    readOnly: Boolean,
    preview: Boolean,
    previewContent: String,
    preferences: ReadingPreferences,
    modifier: Modifier = Modifier,
    minLines: Int = 5,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    if (preview) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Space.lg),
        ) {
            SelectionContainer {
                ContentBlocks(
                    blocks = remember(previewContent) { ContentParser.parse(previewContent) },
                    preferences = preferences,
                    onImage = {},
                )
            }
        }
    } else {
        TextField(
            value = value,
            onValueChange = edit,
            modifier = modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = placeholder,
                    style = typography.postBody,
                    color = colors.textSecondary,
                )
            },
            minLines = minLines,
            maxLines = 12,
            readOnly = readOnly,
            textStyle = typography.postBody.copy(color = colors.textPrimary),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.cardSurface,
                unfocusedContainerColor = colors.cardSurface,
                disabledContainerColor = colors.cardSurface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = colors.goldAccent,
            ),
        )
    }
}

internal fun wrapBbcodeSelection(field: TextFieldValue, openTag: String, closeTag: String): TextFieldValue {
    val start = field.selection.min
    val end = field.selection.max
    val selected = field.text.substring(start, end)
    val replacement = "$openTag$selected$closeTag"
    return TextFieldValue(
        text = field.text.replaceRange(start, end, replacement),
        selection = TextRange(start + openTag.length + selected.length),
    )
}

internal fun boldSelection(field: TextFieldValue): TextFieldValue =
    wrapBbcodeSelection(field, "[b]", "[/b]")
