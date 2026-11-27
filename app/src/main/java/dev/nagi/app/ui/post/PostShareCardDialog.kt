package dev.nagi.app.ui.post

import android.graphics.Picture
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.ImageLoadTracker
import dev.nagi.app.ui.components.LocalSlotImageLoadTracker
import dev.nagi.app.ui.components.NagiDialog
import dev.nagi.app.ui.components.NagiDialogActions
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.SlotImageLoadTracker
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.viewmodel.MainPostShareState
import dev.nagi.sdk.Post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PostShareCardDialog(
    topicId: String,
    topicTitle: String,
    post: Post?,
    shareState: MainPostShareState,
    preferences: ReadingPreferences,
    onRetry: () -> Unit,
    close: () -> Unit,
) {
    val context = LocalContext.current
    val currentColors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val scope = rememberCoroutineScope()
    val pictureRecorder = remember(post?.id, shareState.earlyReplies) { ShareCardPictureRecorder() }
    val imageTracker = remember(post?.id, shareState.earlyReplies) { ImageLoadTracker() }
    var cardDark by rememberSaveable { mutableStateOf(currentColors.isDark) }
    var exporting by remember { mutableStateOf(false) }
    var captureNonce by remember { mutableIntStateOf(0) }

    val trimmed = remember(post?.id, post?.content) {
        post?.let { trimShareCardBlocks(it.content) } ?: ShareCardContent(emptyList(), false)
    }

    NagiDialog(
        onDismissRequest = { if (!exporting) close() },
        modifier = Modifier.testTag("share-card-dialog"),
        maxWidth = 440.dp,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Space.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "分享主楼卡片",
                style = typography.sectionTitle,
                color = currentColors.textPrimary,
            )
            if (post != null) {
                CapsuleButton(
                    text = if (cardDark) "深色卡片" else "浅色卡片",
                    onClick = { cardDark = !cardDark },
                    modifier = Modifier.testTag("share-card-theme-toggle"),
                    icon = NagiIcons.Tuning,
                    style = CapsuleStyle.Neutral,
                    enabled = !exporting,
                    stateDescription = if (cardDark) "已选深色，点击切换为浅色" else "已选浅色，点击切换为深色",
                )
            }
        }

        when {
            shareState.loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = currentColors.goldAccent,
                    )
                    Text(
                        text = "正在加载主楼内容…",
                        style = typography.metadata,
                        color = currentColors.textSecondary,
                    )
                }
            }

            post == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Text(
                        text = shareState.error ?: "无法加载主楼内容",
                        style = typography.body,
                        color = currentColors.textSecondary,
                    )
                    CapsuleButton(
                        text = "重试",
                        onClick = onRetry,
                        style = CapsuleStyle.SelectedGold,
                    )
                }
            }

            else -> {
                if (imageTracker.hasPending) {
                    Text(
                        text = "正在准备图片…",
                        style = typography.metadata,
                        color = currentColors.textSecondary,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(16.dp))
                        .verticalScroll(rememberScrollState()),
                ) {
                    HeightBoundedShareCard(
                        topicId = topicId,
                        topicTitle = topicTitle,
                        post = post,
                        earlyReplies = shareState.earlyReplies,
                        preTrimmed = trimmed,
                        preferences = preferences,
                        darkCard = cardDark,
                        pictureRecorder = pictureRecorder,
                        imageTracker = imageTracker,
                        captureNonce = captureNonce,
                    )
                }
            }
        }

        if (post != null && !shareState.loading) {
            NagiDialogActions(
                confirmLabel = if (exporting) "生成中…" else "分享图片",
                onConfirm = {
                    if (exporting) return@NagiDialogActions
                    exporting = true
                    scope.launch {
                        try {
                            imageTracker.awaitSettled(SHARE_CARD_IMAGE_WAIT_TIMEOUT_MS)
                            // Force the root drawWithContent modifier to re-record the Picture after
                            // child image nodes finish drawing, then wait for the frame to commit.
                            captureNonce++
                            withFrameNanos {}
                            withFrameNanos {}
                            exportShareCardImage(context, topicId, pictureRecorder)
                            close()
                        } catch (t: Throwable) {
                            if (t is CancellationException) throw t
                            withContext(Dispatchers.Main.immediate) {
                                Toast.makeText(context, "卡片内容过长或生成失败", Toast.LENGTH_SHORT).show()
                            }
                        } finally {
                            exporting = false
                        }
                    }
                },
                confirmStyle = CapsuleStyle.SolidGold,
                confirmEnabled = !exporting,
                dismissLabel = "关闭",
                onDismiss = close,
                dismissEnabled = !exporting,
            )
        } else {
            NagiDialogActions(
                confirmLabel = "关闭",
                onConfirm = close,
                confirmStyle = CapsuleStyle.Neutral,
            )
        }
    }
}

@Composable
internal fun HeightBoundedShareCard(
    topicId: String,
    topicTitle: String,
    post: Post,
    earlyReplies: List<Post> = emptyList(),
    preTrimmed: ShareCardContent,
    preferences: ReadingPreferences,
    darkCard: Boolean,
    pictureRecorder: ShareCardPictureRecorder,
    imageTracker: ImageLoadTracker? = null,
    captureNonce: Int = 0,
) {
    val exportDensity = Density(SHARE_CARD_EXPORT_DENSITY, LocalDensity.current.fontScale)
    SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
        val previewWidthPx = constraints.maxWidth.coerceAtLeast(1)
        val previewScale = previewWidthPx.toFloat() / SHARE_CARD_EXPORT_WIDTH_PX.toFloat()
        val exportConstraints = Constraints.fixedWidth(SHARE_CARD_EXPORT_WIDTH_PX)
        var probeCounter = 0

        fun measureCandidate(
            slotId: Any,
            candidate: ShareCardContent,
            isProbe: Boolean = false,
        ): Placeable {
            val slotTracker = if (!isProbe && imageTracker != null) {
                SlotImageLoadTracker(imageTracker, slotId.toString())
            } else {
                null
            }
            return subcompose(slotId) {
                CompositionLocalProvider(
                    LocalDensity provides exportDensity,
                    LocalSlotImageLoadTracker provides slotTracker,
                ) {
                    PostShareCardSurface(
                        topicId = topicId,
                        topicTitle = topicTitle,
                        post = post,
                        earlyReplies = earlyReplies,
                        content = candidate,
                        preferences = preferences,
                        darkCard = darkCard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isProbe) {
                                    Modifier.clearAndSetSemantics {}
                                } else {
                                    Modifier
                                        .testTag("share-card-canvas")
                                        .drawWithContent {
                                            // Subscribe to image settle & explicit capture ticks so
                                            // child image loads re-record the outer Picture.
                                            imageTracker?.settledRevision
                                            captureNonce
                                            val widthPx = size.width.toInt().coerceAtLeast(1)
                                            val heightPx = size.height.toInt().coerceAtLeast(1)
                                            val picture = Picture()
                                            val recordingCanvas = picture.beginRecording(widthPx, heightPx)
                                            val previousCanvas = drawContext.canvas
                                            drawContext.canvas = androidx.compose.ui.graphics.Canvas(recordingCanvas)
                                            try {
                                                this@drawWithContent.drawContent()
                                            } finally {
                                                picture.endRecording()
                                                drawContext.canvas = previousCanvas
                                            }
                                            pictureRecorder.picture = picture
                                            previousCanvas.nativeCanvas.drawPicture(picture)
                                        }
                                },
                            ),
                    )
                }
            }.single().measure(exportConstraints)
        }

        fun probeFits(candidate: ShareCardContent): Boolean =
            measureCandidate("probe-${probeCounter++}", candidate, isProbe = true).height <= MAX_CARD_EXPORT_HEIGHT_PX

        val initialPlaceable = measureCandidate("initial", preTrimmed)
        val finalPlaceable = if (initialPlaceable.height <= MAX_CARD_EXPORT_HEIGHT_PX) {
            imageTracker?.setActiveSlot("initial")
            initialPlaceable
        } else {
            val fittedContent = fitShareCardByHeight(preTrimmed.blocks, ::probeFits)
            imageTracker?.setActiveSlot("fitted")
            measureCandidate("fitted", fittedContent)
        }

        val previewHeightPx = kotlin.math.ceil(finalPlaceable.height * previewScale).toInt().coerceAtLeast(1)
        layout(previewWidthPx, previewHeightPx) {
            finalPlaceable.placeWithLayer(0, 0) {
                scaleX = previewScale
                scaleY = previewScale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

