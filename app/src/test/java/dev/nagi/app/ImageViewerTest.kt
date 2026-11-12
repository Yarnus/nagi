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
import dev.nagi.app.ui.post.ImageTransform
import dev.nagi.app.ui.post.ImageViewer
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
        compose.onNodeWithText("关闭").performClick()
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
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("image-viewport").assertDoesNotExist()
    }

    private fun withLoader(failFirst: Boolean = false, test: () -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        val calls = AtomicInteger()
        val loader = ImageLoader.Builder(app).components {
            add(Interceptor { chain ->
                if (calls.getAndIncrement() == 0 && failFirst) ErrorResult(null, chain.request, IllegalStateException("Unavailable"))
                else SuccessResult(BitmapDrawable(app.resources, Bitmap.createBitmap(200, 800, Bitmap.Config.ARGB_8888)), chain.request, DataSource.MEMORY)
            })
        }.build()
        Coil.setImageLoader(loader)
        try { test() } finally { loader.shutdown(); Coil.reset() }
    }
}
