package dev.nagi.app.ui.post

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.GoldSwitch
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.sdk.ContentBlock
import dev.nagi.sdk.ContentParser
import dev.nagi.sdk.NgaSmile
import dev.nagi.sdk.NgaSmiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ASYNC_PREVIEW_THRESHOLD = 2_000

private val INLINE_SMILE_TAG = Regex("""\[s:([^\]\s]+)\]""")

private class SmileHighlightVisualTransformation(
    private val smileColor: Color,
    private val smileBackground: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if ('[' !in raw) return TransformedText(text, OffsetMapping.Identity)
        val styled = buildAnnotatedString {
            append(raw)
            INLINE_SMILE_TAG.findAll(raw).forEach { match ->
                if (NgaSmiles.resolve(match.groupValues[1]) != null) {
                    addStyle(
                        SpanStyle(
                            color = smileColor,
                            background = smileBackground,
                            fontWeight = FontWeight.Medium,
                        ),
                        match.range.first,
                        match.range.last + 1,
                    )
                }
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

internal fun extractInsertedSmiles(text: String): List<NgaSmile> {
    if ('[' !in text) return emptyList()
    val seen = LinkedHashSet<String>()
    val result = mutableListOf<NgaSmile>()
    INLINE_SMILE_TAG.findAll(text).forEach { match ->
        val smile = NgaSmiles.resolve(match.groupValues[1]) ?: return@forEach
        if (seen.add(smile.key)) result += smile
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorPopup(dismiss: () -> Unit, content: @Composable () -> Unit) {
    val colors = NagiThemeTokens.colors
    val density = LocalDensity.current
    val containerWidthDp = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    if (containerWidthDp < 600.dp) {
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
            androidx.activity.compose.BackHandler { dismiss() }
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
    fillArea: Boolean = false,
    focusRequester: FocusRequester? = null,
    onInputTap: (() -> Unit)? = null,
    onTogglePreview: (() -> Unit)? = null,
    anonymous: Boolean = false,
    onAnonymousChange: ((Boolean) -> Unit)? = null,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val editorBodyStyle = typography.postBody.copy(
        fontSize = preferences.fontSize.sp,
        lineHeight = preferences.effectiveLineHeight.sp,
    )
    val insertedSmiles = remember(value.text) { extractInsertedSmiles(value.text) }
    val showSubrow = (!preview && insertedSmiles.isNotEmpty()) || onAnonymousChange != null

    Column(modifier = modifier.fillMaxWidth()) {
        if (preview) {
            val parseSynchronously = previewContent.length <= ASYNC_PREVIEW_THRESHOLD
            val syncBlocks = remember(previewContent, parseSynchronously) {
                if (parseSynchronously) ContentParser.parse(previewContent) else null
            }
            val asyncBlocks by produceState<List<ContentBlock>?>(
                initialValue = null,
                key1 = previewContent,
                key2 = parseSynchronously,
            ) {
                this.value = if (parseSynchronously) {
                    null
                } else {
                    withContext(Dispatchers.Default) { ContentParser.parse(previewContent) }
                }
            }
            val resolvedBlocks = syncBlocks ?: asyncBlocks
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = fillArea)
                    .verticalScroll(rememberScrollState())
                    .padding(Space.lg),
            ) {
                if (resolvedBlocks == null) {
                    LoadingLine()
                } else {
                    SelectionContainer {
                        ContentBlocks(
                            blocks = resolvedBlocks,
                            preferences = preferences,
                            onImage = {},
                        )
                    }
                }
            }
        } else {
            val interactionSource = remember { MutableInteractionSource() }
            LaunchedEffect(interactionSource, onInputTap) {
                if (onInputTap != null) {
                    interactionSource.interactions.collect { interaction ->
                        if (interaction is PressInteraction.Press) onInputTap()
                    }
                }
            }
            val highlightTransformation = remember(colors.goldText, colors.goldContainer) {
                SmileHighlightVisualTransformation(colors.goldText, colors.goldContainer)
            }
            TextField(
                value = value,
                onValueChange = edit,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = fillArea)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .then(
                        if (onInputTap != null) {
                            Modifier.onFocusChanged { if (it.isFocused) onInputTap() }
                        } else {
                            Modifier
                        },
                    ),
                interactionSource = interactionSource,
                placeholder = {
                    Text(
                        text = placeholder,
                        style = editorBodyStyle,
                        color = colors.textSecondary,
                    )
                },
                minLines = minLines,
                maxLines = 12,
                readOnly = readOnly,
                visualTransformation = highlightTransformation,
                textStyle = editorBodyStyle.copy(color = colors.textPrimary),
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

        if (showSubrow) {
            ComposerCardSubrow(
                smiles = if (preview) emptyList() else insertedSmiles,
                onPreview = onTogglePreview,
                anonymous = anonymous,
                onAnonymousChange = onAnonymousChange,
                anonymousEnabled = !readOnly,
            )
        }
    }
}

@Composable
private fun ComposerCardSubrow(
    smiles: List<NgaSmile>,
    onPreview: (() -> Unit)?,
    anonymous: Boolean,
    onAnonymousChange: ((Boolean) -> Unit)?,
    anonymousEnabled: Boolean,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    HorizontalDivider(thickness = 0.5.dp, color = colors.cardBorder)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.cardSurface)
            .padding(horizontal = Space.md, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        if (smiles.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onPreview != null) {
                            Modifier
                                .heightIn(min = 48.dp)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "预览已插入的表情",
                                    onClick = onPreview,
                                )
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "已插入 ${smiles.size} 个表情，点击预览"
                                }
                        } else {
                            Modifier.semantics(mergeDescendants = true) {
                                contentDescription = "已插入 ${smiles.size} 个表情"
                            }
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "回显",
                    style = typography.metadata,
                    color = colors.textSecondary,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberScrollState())
                        .clearAndSetSemantics {},
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    smiles.take(8).forEach { smile ->
                        SmileImage(
                            smile = smile,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        } else {
            Box(Modifier.weight(1f))
        }

        if (onAnonymousChange != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = NagiIcons.AnonymousMask,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (anonymous) colors.goldText else colors.textSecondary,
                )
                Text(
                    text = "匿名",
                    style = typography.metadata,
                    color = if (anonymous) colors.goldText else colors.textSecondary,
                )
                GoldSwitch(
                    checked = anonymous,
                    onCheckedChange = onAnonymousChange,
                    enabled = anonymousEnabled,
                    contentDescription = "匿名发布",
                )
            }
        }
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

internal fun strikeSelection(field: TextFieldValue): TextFieldValue =
    wrapBbcodeSelection(field, "[del]", "[/del]")

internal fun colorSelection(field: TextFieldValue, colorName: String): TextFieldValue =
    wrapBbcodeSelection(field, "[color=$colorName]", "[/color]")
