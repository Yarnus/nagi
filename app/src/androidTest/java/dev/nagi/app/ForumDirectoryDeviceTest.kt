package dev.nagi.app

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.nagi.app.ui.forum.ForumDirectory
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.viewmodel.ListState
import dev.nagi.sdk.Forum
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class ForumDirectoryDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun descriptionAndGroupExpansionRemainActionable() {
        var selected: String? = null
        val forums = listOf(
            Forum("7", "艾泽拉斯议事厅", "魔兽世界", "魔兽世界", description = "魔兽主讨论区"),
            Forum("8", "熊猫人之谜", "魔兽世界", "魔兽世界", description = "怀旧服讨论"),
            Forum("9", "家居装修", "生活与消费", "居家生活", description = "装修经验与日常生活"),
        )
        compose.setContent { NagiTheme {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                ForumDirectory(ListState(items = forums), {}, {}, { selected = it.id })
            }
        } }
        compose.onNodeWithText("魔兽主讨论区").assertIsDisplayed()
        fun capture(name: String) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "forums-ui").apply { mkdirs() }
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        capture("expanded")
        compose.onNodeWithText("魔兽世界").performClick()
        compose.onNodeWithText("艾泽拉斯议事厅").assertDoesNotExist()
        compose.onNodeWithText("家居装修").assertIsDisplayed()
        capture("collapsed")
        compose.onNodeWithText("魔兽世界").performClick()
        compose.onNodeWithText("艾泽拉斯议事厅").performClick()
        compose.runOnIdle { assertEquals("7", selected) }
    }
}
