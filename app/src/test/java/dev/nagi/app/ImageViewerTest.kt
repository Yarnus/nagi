package dev.nagi.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.SuccessResult
import dev.nagi.app.ui.post.DecodedImageFormat
import dev.nagi.app.ui.post.ImageTransform
import dev.nagi.app.ui.post.ImageViewer
import dev.nagi.app.ui.post.detectImageFormat
import dev.nagi.app.ui.theme.NagiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageViewerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun zoomAnchorsAtFingersAndClampsScaleAndPan() {
        val viewport = Size(400f, 800f)
        val image = Size(400f, 800f)
        val zoom = ImageTransform().zoom(Offset(250f, 450f), Offset.Zero, 2f, viewport, image)
        assertEquals(2f, zoom.scale)
        assertEquals(Offset(-50f, -50f), zoom.offset)
        val dragged = zoom.zoom(Offset.Zero, Offset(9999f, -9999f), 99f, viewport, image)
        assertEquals(5f, dragged.scale)
        assertEquals(Offset(800f, -1600f), dragged.offset)
        assertEquals(ImageTransform(), dragged.zoom(Offset.Zero, Offset.Zero, 0.001f, viewport, image))
    }

    @Test fun longAndWideImagesCannotPanIntoEmptySpace() {
        assertEquals(Offset(0f, 400f), ImageTransform(2f, Offset(900f, 900f))
            .bounded(Size(400f, 800f), Size(100f, 1000f)).offset)
        assertEquals(Offset(200f, 0f), ImageTransform(2f, Offset(900f, 900f))
            .bounded(Size(400f, 800f), Size(1000f, 100f)).offset)
        assertEquals(ImageTransform(), ImageTransform(3f).bounded(Size.Zero, Size.Zero))
    }

    @Test fun doubleTapPinchResetAndReopen() = withLoader {
        var open by mutableStateOf(true)
        compose.setContent { NagiTheme { if (open) ImageViewer("https://img4.nga.cn/test.png") { open = false } } }
        compose.onNodeWithText("还原").assertExists()
        val viewport = compose.onNodeWithTag("image-viewport")
        viewport.assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(600f))
        viewport.performTouchInput { doubleClick(center) }
        viewport.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "缩放 250%"))
        compose.onNodeWithText("还原").performClick()
        viewport.performTouchInput { pinch(center - Offset(20f, 0f), center + Offset(20f, 0f), center - Offset(80f, 0f), center + Offset(80f, 0f)) }
        viewport.assert(SemanticsMatcher("pinch zoomed") {
            it.config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription] != "缩放 100%"
        })
        compose.onNodeWithContentDescription("关闭").performClick()
        viewport.assertDoesNotExist()
        compose.runOnIdle { open = true }
        viewport.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "缩放 100%"))
    }

    @Test fun changingImageResetsZoomAndUsesNewTransformState() = withLoader {
        var url by mutableStateOf("https://img4.nga.cn/first.png")
        compose.setContent { NagiTheme { ImageViewer(url) {} } }
        compose.onNodeWithText("还原").assertExists()
        val viewport = compose.onNodeWithTag("image-viewport")
        viewport.performTouchInput { doubleClick(center) }
        compose.runOnIdle { url = "https://img4.nga.cn/second.png" }
        compose.onNodeWithText("还原").assertExists()
        viewport.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "缩放 100%"))
        viewport.performTouchInput { doubleClick(center) }
        viewport.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "缩放 250%"))
    }

    @Test fun loadFailureCanRetryAndClose() = withLoader(failFirst = true) {
        var open by mutableStateOf(true)
        compose.setContent { NagiTheme("dark") { if (open) ImageViewer("https://img4.nga.cn/test.png") { open = false } } }
        compose.onNodeWithText("图片加载失败").assertExists()
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithText("还原").assertExists()
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.onNodeWithTag("image-viewport").assertDoesNotExist()
    }

    @Test fun gallerySwipesBetweenImagesAndShowsSaveAndShare() = withLoader {
        val urls = listOf("https://img4.nga.cn/one.png", "https://img4.nga.cn/two.png", "https://img4.nga.cn/three.png")
        compose.setContent { NagiTheme { ImageViewer(urls = urls, initialIndex = 1) {} } }
        compose.onNodeWithText("2 / 3").assertExists()
        compose.onNodeWithText("保存").assertExists()
        compose.onNodeWithText("分享").assertExists()
        compose.onNodeWithTag("image-viewport").performTouchInput { swipeLeft() }
        compose.onNodeWithText("3 / 3").assertExists()
        compose.onNodeWithTag("image-viewport").performTouchInput { doubleClick(center) }
        compose.onNodeWithTag("image-viewport").performTouchInput { swipeRight() }
        compose.onNodeWithText("3 / 3").assertExists()
    }

    @Test fun detectsImageFormatsFromMagicBytes() {
        assertEquals(DecodedImageFormat("image/png", "png"), detectImageFormat(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertEquals(DecodedImageFormat("image/gif", "gif"), detectImageFormat("GIF89a".encodeToByteArray()))
        assertEquals(DecodedImageFormat("image/webp", "webp"), detectImageFormat("RIFF1234WEBP".encodeToByteArray()))
        assertEquals(DecodedImageFormat("image/jpeg", "jpg"), detectImageFormat(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
    }

    @Test fun streamingCopyPreservesHeaderAndEnforcesMaxSize() {
        val payload = "GIF89a".encodeToByteArray() + ByteArray(24_000) { (it % 251).toByte() }
        val input = payload.inputStream()
        val (header, headerLen) = dev.nagi.app.ui.post.readFormatHeader(input)
        assertEquals(DecodedImageFormat("image/gif", "gif"), detectImageFormat(header.copyOf(headerLen)))
        val out = java.io.ByteArrayOutputStream()
        dev.nagi.app.ui.post.copyStreamBounded(input, out, header, headerLen, maxBytes = 32_768L)
        assertArrayEquals(payload, out.toByteArray())

        val overflowInput = payload.inputStream()
        val (h2, hl2) = dev.nagi.app.ui.post.readFormatHeader(overflowInput)
        assertThrows(java.io.IOException::class.java) {
            dev.nagi.app.ui.post.copyStreamBounded(
                overflowInput,
                java.io.ByteArrayOutputStream(),
                h2,
                hl2,
                maxBytes = 1024L,
            )
        }
    }

    @Test fun fitWidthScalesTallImageToViewportWidthAndStartsAtTop() {
        val fitWidth = ImageTransform().fitWidth(Size(400f, 800f), Size(200f, 1600f))
        assertEquals(4f, fitWidth.scale, 0.001f)
        assertEquals(Offset(0f, 1200f), fitWidth.offset)

        withLoader(imageWidth = 200, imageHeight = 1600) {
            compose.setContent { NagiTheme { ImageViewer("https://img4.nga.cn/tall.png") {} } }
            val viewport = compose.onNodeWithTag("image-viewport")
            viewport.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "缩放 100%"))
            compose.onNodeWithText("适宽").assertExists().performClick()
            viewport.assert(SemanticsMatcher("fit width zoomed") {
                it.config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription] != "缩放 100%"
            })
            compose.onNodeWithText("还原").performClick()
            viewport.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "缩放 100%"))
        }
    }

    private fun withLoader(
        failFirst: Boolean = false,
        imageWidth: Int = 200,
        imageHeight: Int = 800,
        test: () -> Unit,
    ) {
        val app = RuntimeEnvironment.getApplication()
        val calls = AtomicInteger()
        val loader = ImageLoader.Builder(app).components {
            add(Interceptor { chain ->
                if (calls.getAndIncrement() == 0 && failFirst) ErrorResult(null, chain.request, IllegalStateException("Unavailable"))
                else SuccessResult(BitmapDrawable(app.resources, Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)), chain.request, DataSource.MEMORY)
            })
        }.build()
        Coil.setImageLoader(loader)
        try { test() } finally { loader.shutdown(); Coil.reset() }
    }
}
