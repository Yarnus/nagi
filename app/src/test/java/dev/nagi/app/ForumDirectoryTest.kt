package dev.nagi.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import dev.nagi.app.ui.forum.ForumDirectory
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.viewmodel.ListState
import dev.nagi.sdk.Forum
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ForumDirectoryTest {
    @get:Rule val compose = createComposeRule()
    private val forums = listOf(
        Forum("7", "艾泽拉斯议事厅", "魔兽世界", "魔兽世界", description = "魔兽主讨论区"),
        Forum("8", "艾泽拉斯风纪委员会", "魔兽世界", "魔兽世界", description = "曝光违背公认准则的行为"),
        Forum("9", "熊猫人之谜", "魔兽世界", "魔兽世界", description = "怀旧服讨论"),
        Forum("10", "家居装修", "生活与消费", "居家生活", description = "装修经验与日常生活"),
    )
    @Test fun groupCollapsesRestoresAndRefreshDoesNotExpandIt() {
        val restoration = StateRestorationTester(compose)
        var state by mutableStateOf(ListState(items = forums))
        var selected: String? = null
        restoration.setContent { NagiTheme { ForumDirectory(state, {}, {}, { selected = it.id }) } }
        compose.onAllNodesWithText("魔兽世界").assertCountEquals(1)
        compose.onNodeWithText("魔兽主讨论区").assertIsDisplayed()
        compose.onNodeWithText("艾泽拉斯议事厅").performClick()
        compose.runOnIdle { assertEquals("7", selected) }
        compose.onNodeWithText("魔兽世界").performClick()
        compose.onNodeWithText("艾泽拉斯议事厅").assertDoesNotExist()
        compose.onNodeWithText("生活与消费 · 居家生活").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("艾泽拉斯议事厅").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(loading = true) }
        compose.runOnIdle { state = state.copy(loading = false, items = forums.toList()) }
        compose.onNodeWithText("艾泽拉斯议事厅").assertDoesNotExist()
        compose.onNodeWithText("魔兽世界").performClick()
        compose.onNodeWithText("魔兽主讨论区").assertIsDisplayed()
    }
    @Test fun captureLight() = capture("light", 1f)
    @Test fun captureDark() = capture("dark", 1f)
    @Config(qualifiers = "w320dp-h850dp")
    @Test fun captureNarrowLargeFont() = capture("light", 1.5f)
    private fun capture(theme: String, scale: Float) {
        RuntimeEnvironment.setFontScale(scale)
        lateinit var view: View
        compose.setContent { NagiTheme(theme) {
            view = LocalView.current
            ForumDirectory(ListState(items = forums), {}, {}, {})
        } }
        compose.onNodeWithText("魔兽主讨论区").assertIsDisplayed()
        fun save(suffix: String) = compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val width = RuntimeEnvironment.getApplication().resources.configuration.screenWidthDp
            File("build/ui-captures/forums-$width-$theme-$scale-$suffix.png").apply { parentFile?.mkdirs() }.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        save("expanded")
        compose.onNodeWithText("魔兽世界").performClick()
        save("collapsed")
    }
}
