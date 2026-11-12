package dev.nagi.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.SuccessResult
import dev.nagi.app.ui.forum.ForumDirectoryIcon
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.ui.theme.NagiThemeTokens
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ForumIconTest {
    @get:Rule val compose = createComposeRule()

    @Test fun missingIconLeavesNeutralBackground() = checkIcon(null, false, "light", "missing")
    @Test fun failedIconLeavesFavoriteBackground() = checkIcon("https://img4.nga.cn/error.png", true, "light", "error")
    @Test fun loadingIconLeavesDarkBackground() = checkIcon("https://img4.nga.cn/loading.png", false, "dark", "loading")
    @Test fun successfulIconKeepsOriginalColor() = checkIcon("https://img4.nga.cn/success.png", false, "dark", "success")

    private fun checkIcon(url: String?, favorite: Boolean, theme: String, outcome: String) {
        val requested = AtomicBoolean(false)
        val loader = ImageLoader.Builder(RuntimeEnvironment.getApplication()).components {
            add(Interceptor { chain ->
                requested.set(true)
                when (outcome) {
                    "success" -> SuccessResult(ColorDrawable(AndroidColor.RED), chain.request, DataSource.MEMORY)
                    "loading" -> awaitCancellation()
                    else -> ErrorResult(null, chain.request, IllegalStateException("Image unavailable"))
                }
            })
        }.build()
        Coil.setImageLoader(loader)
        var background = Color.Unspecified
        lateinit var view: View
        compose.setContent {
            NagiTheme(theme) {
                view = LocalView.current
                background = if (favorite) NagiThemeTokens.colors.goldContainer else NagiThemeTokens.colors.insetContainer
                Box(Modifier.testTag("icon")) { ForumDirectoryIcon(url, favorite) }
            }
        }
        try {
            if (url != null) compose.waitUntil(5_000) { requested.get() }
            else assertFalse(requested.get())
            val expected = if (outcome == "success") Color.Red else background
            compose.waitUntil(5_000) {
                val bounds = compose.onNodeWithTag("icon").fetchSemanticsNode().boundsInRoot
                var pixel = 0
                compose.runOnIdle {
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    pixel = bitmap.getPixel(bounds.center.x.toInt(), bounds.center.y.toInt())
                    bitmap.recycle()
                }
                pixel == expected.toArgb()
            }
            compose.onNodeWithTag("icon").assertWidthIsEqualTo(32.dp)
            compose.onNodeWithTag("icon").assertHeightIsEqualTo(32.dp)
            compose.onAllNodes(hasText("", substring = true), useUnmergedTree = true).assertCountEquals(0)
        } finally {
            loader.shutdown()
            Coil.reset()
        }
    }
}
