package dev.nagi.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.nagi.app.ui.components.LocalAccountIdentity
import dev.nagi.app.ui.library.UserProfileCard
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.viewmodel.AccountIdentity
import dev.nagi.app.viewmodel.UserProfileState
import dev.nagi.sdk.NgaUser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileMessageButtonStyleTest {
    @get:Rule val compose = createComposeRule()
    @Test fun lightMessageActionHasGoldIconAndTextWithoutSolidFill() = render("light", 1f)
    @Test fun darkMessageActionHasGoldIconAndTextWithoutSolidFill() = render("dark", 1f)
    @Config(qualifiers = "w320dp-h850dp")
    @Test fun narrowLargeTextMessageActionRemainsReadable() = render("light", 2f)

    private fun render(theme: String, scale: Float) {
        RuntimeEnvironment.setFontScale(scale)
        lateinit var view: View
        var foreground = Color.Unspecified
        var solid = Color.Unspecified
        var target = ""
        compose.setContent { NagiTheme(theme) {
            view = LocalView.current
            foreground = NagiThemeTokens.colors.goldText
            solid = NagiThemeTokens.colors.goldSolidFill
            CompositionLocalProvider(LocalAccountIdentity provides AccountIdentity(1, "7")) {
                Surface(Modifier.fillMaxSize(), color = NagiThemeTokens.colors.groupedCanvas) {
                    UserProfileCard(UserProfileState(NgaUser("8", "测试用户", "", 10)), {}, { target = it })
                }
            }
        } }
        val button = compose.onNodeWithText("发送短消息")
            .assertIsDisplayed().assertHasClickAction()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val bounds = button.getUnclippedBoundsInRoot()
        compose.runOnIdle {
            val density = view.resources.displayMetrics.density
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val left = (bounds.left.value * density).roundToInt()
            val right = (bounds.right.value * density).roundToInt()
            val top = (bounds.top.value * density).roundToInt()
            val bottom = (bounds.bottom.value * density).roundToInt()
            val split = left + (32 * density).roundToInt()
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
            File("build/ui-captures/community/profile-message-$theme-$scale.png").apply { parentFile!!.mkdirs() }
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertEquals("Must not use the solid-gold button fill", 0, solidFill)
            assertTrue("Message icon must use GoldText", iconGold > 0)
            assertTrue("Message label must use GoldText", textGold > 0)
            bitmap.recycle()
        }
        button.performClick()
        compose.runOnIdle { assertEquals("测试用户", target) }
    }
}
