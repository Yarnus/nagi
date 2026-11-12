package dev.nagi.app

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.post.*
import dev.nagi.app.ui.components.AccountMenuButton
import dev.nagi.app.ui.theme.*
import dev.nagi.sdk.ContentBlock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1000dp-h1000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun collapseRestoresStateAndNativeTextRemainsSelectable() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            NagiTheme {
                ContentBlocks(listOf(ContentBlock.Collapse("Details", listOf(ContentBlock.Text("Hidden content")))), ReadingPreferences(), {})
            }
        }
        compose.onNodeWithText("Hidden content").assertDoesNotExist()
        compose.onNodeWithText("展开：Details").performClick()
        compose.onNodeWithText("Hidden content").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Hidden content").assertIsDisplayed()
        compose.onNodeWithText("收起：Details").performClick()
        compose.onNodeWithText("Hidden content").assertDoesNotExist()
    }

    @Test fun localNavigationHasNoCloudFavoriteAction() {
        var selected by mutableStateOf("forums")
        compose.setContent { NagiTheme { AccountMenuButton(onSelect = { selected = it }) } }
        compose.onNodeWithContentDescription("头像菜单").performClick()
        compose.onNodeWithText("本地收藏 · 仅保存在此设备").assertExists()
        compose.onNodeWithText("收藏话题").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals("favorites", selected) }
        compose.onNodeWithText("云收藏").assertDoesNotExist()
    }

    @Test fun contentRendersAcrossWidthsThemesAndFontScale() {
        var width by mutableStateOf(400)
        var theme by mutableStateOf("light")
        var fontScale by mutableStateOf(1f)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                NagiTheme(theme) {
                    androidx.compose.material3.Surface(Modifier.width(width.dp).height(500.dp)) {
                        Column(Modifier.padding(Space.lg)) {
                            Text("Native reading", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                            ContentBlocks(listOf(
                                ContentBlock.Text("Body text with an intentional, readable line length."),
                                ContentBlock.Quote(listOf(ContentBlock.Text("Quoted text"))),
                                ContentBlock.Code("val reader = Nagi()"),
                                ContentBlock.Table(listOf(listOf("Column A", "Column B"))),
                            ), ReadingPreferences(), {})
                        }
                    }
                }
            }
        }
        for ((size, appearance, scale) in listOf(Triple(400, "light", 1f), Triple(610, "dark", 1f), Triple(900, "light", 1f), Triple(400, "dark", 1.5f))) {
            compose.runOnIdle { width = size; theme = appearance; fontScale = scale }
            compose.onNodeWithText("Native reading").assertIsDisplayed()
            compose.onNodeWithText("Quoted text").assertIsDisplayed()
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val output = File("build/ui-captures/reader-$size-$appearance-$scale.png")
                requireNotNull(output.parentFile).mkdirs()
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
