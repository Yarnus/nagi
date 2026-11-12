package dev.nagi.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.nagi.app.ui.post.ReaderScaffold
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.ui.theme.NagiThemeTokens
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hiddenReplyDoesNotInterceptTapsAndRapidTransitionsKeepGeometryStable() {
        var scrolling by mutableStateOf(false)
        var replies = 0
        var backgroundClicks = 0
        compose.setContent { NagiTheme {
            ReaderScaffold(onReply = { replies++ }, scrollInProgress = scrolling, topBar = {}) {
                Box(Modifier.fillMaxSize().testTag("reader-viewport").clickable { backgroundClicks++ })
            }
        } }
        val viewport = compose.onNodeWithTag("reader-viewport")
        val bounds = viewport.getUnclippedBoundsInRoot()
        val reply = compose.onNodeWithContentDescription("回复帖子").assertHasClickAction()
        val button = reply.getUnclippedBoundsInRoot()
        val slot = compose.onNodeWithTag("reader-reply-slot").getUnclippedBoundsInRoot()
        compose.runOnIdle { scrolling = true }
        compose.onNodeWithContentDescription("回复帖子").assertDoesNotExist()
        val density = org.robolectric.RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val tap = Offset(
            (button.left.value + button.right.value) / 2 * density,
            (button.top.value + button.bottom.value) / 2 * density,
        )
        viewport.performTouchInput { click(tap) }
        compose.runOnIdle {
            assertEquals(0, replies)
            assertEquals(1, backgroundClicks)
        }
        compose.mainClock.autoAdvance = false
        repeat(4) {
            compose.runOnIdle { scrolling = it % 2 == 0 }
            compose.mainClock.advanceTimeBy(32)
        }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { scrolling = false }
        compose.onNodeWithContentDescription("回复帖子").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, replies) }
        assertEquals(bounds, viewport.getUnclippedBoundsInRoot())
        assertEquals(slot, compose.onNodeWithTag("reader-reply-slot").getUnclippedBoundsInRoot())
    }

    @Test fun lightAndDarkReplyGlyphsShareGoldTextWithoutASolidGoldFill() {
        var theme by mutableStateOf("light")
        lateinit var view: View
        var foreground = Color.Unspecified
        var solid = Color.Unspecified
        compose.setContent { NagiTheme(theme) {
            view = LocalView.current
            foreground = NagiThemeTokens.colors.goldText
            solid = NagiThemeTokens.colors.goldSolidFill
            ReaderScaffold(onReply = {}, scrollInProgress = false, topBar = {}) {
                Box(Modifier.fillMaxSize())
            }
        } }
        for (appearance in listOf("light", "dark")) {
            compose.runOnIdle { theme = appearance }
            val bounds = compose.onNodeWithContentDescription("回复帖子")
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                .getUnclippedBoundsInRoot()
            compose.runOnIdle {
                val density = view.resources.displayMetrics.density
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val left = (bounds.left.value * density).roundToInt()
                val right = (bounds.right.value * density).roundToInt()
                val top = (bounds.top.value * density).roundToInt()
                val bottom = (bounds.bottom.value * density).roundToInt()
                val split = left + (35 * density).roundToInt()
                var iconGold = 0
                var textGold = 0
                var solidFill = 0
                for (y in top until bottom) for (x in left until right) {
                    val pixel = bitmap.getPixel(x, y)
                    if (pixel == foreground.toArgb()) {
                        if (x < split) iconGold++ else textGold++
                    }
                    if (pixel == solid.toArgb()) solidFill++
                }
                File("build/ui-captures/reader-reply-frosted-$appearance.png").apply { parentFile!!.mkdirs() }
                    .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                assertTrue("$appearance reply icon must use GoldText", iconGold > 0)
                assertTrue("$appearance reply label must use GoldText", textGold > 0)
                assertEquals("$appearance reply must not use a solid gold background", 0, solidFill)
                bitmap.recycle()
            }
        }
    }
}
