package dev.nagi.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.nagi.app.ui.components.*
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.viewmodel.AccountIdentity
import dev.nagi.sdk.NgaUser
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountMenuTest {
    @get:Rule val compose = createComposeRule()
    @Test fun currentIdentityAndDistinctRoutes() = render("light", 1f)
    @Test fun darkMenu() = render("dark", 1f)
    @Config(qualifiers = "w320dp-h850dp")
    @Test fun narrowLargeFontMenu() = render("light", 1.5f)

    @Test fun guestCanReachSavedAccounts() {
        var route = ""
        compose.setContent {
            NagiTheme("light") {
                CompositionLocalProvider(LocalAccountIdentity provides AccountIdentity(1, null)) {
                    AccountMenuButton { route = it }
                }
            }
        }
        compose.onNodeWithContentDescription("头像菜单").performClick()
        compose.onNodeWithText("管理账号").performClick()
        compose.runOnIdle { assertEquals("accounts", route) }
        compose.onNodeWithContentDescription("头像菜单").performClick()
        compose.onNodeWithText("未登录").performClick()
        compose.runOnIdle { assertEquals("login", route) }
    }

    private fun render(theme: String, scale: Float) {
        org.robolectric.RuntimeEnvironment.setFontScale(scale)
        var route = ""
        compose.setContent {
            NagiTheme(theme) {
                CompositionLocalProvider(LocalAccountIdentity provides AccountIdentity(1, "77", NgaUser("77", "测试用户", "", 3))) {
                    Surface(Modifier.fillMaxSize()) {
                        Box { AccountMenuButton { route = it } }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("头像菜单").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("测试用户").assertIsDisplayed()
        compose.onNodeWithText("管理账号").assertHeightIsAtLeast(48.dp)
        compose.runOnIdle {
            val menu = android.view.inspector.WindowInspector.getGlobalWindowViews().last()
            val bitmap = Bitmap.createBitmap(menu.width, menu.height, Bitmap.Config.ARGB_8888)
            menu.draw(Canvas(bitmap))
            val width = org.robolectric.RuntimeEnvironment.getApplication().resources.configuration.screenWidthDp
            File("build/ui-captures/account-menu-$width-$theme-$scale.png").apply { parentFile?.mkdirs() }.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithText("管理账号").performClick()
        compose.runOnIdle { assertEquals("accounts", route) }
        compose.onNodeWithContentDescription("头像菜单").performClick()
        compose.onNodeWithText("设置").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("settings", route) }
    }
}
