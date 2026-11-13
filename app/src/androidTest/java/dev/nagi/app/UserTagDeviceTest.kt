package dev.nagi.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nagi.app.ui.components.*
import dev.nagi.app.ui.library.UserProfileCard
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.viewmodel.*
import dev.nagi.sdk.NgaUser
import dev.nagi.sdk.Post
import dev.nagi.sdk.ContentBlock
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.post.ReaderPost
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserTagDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun readerTagAlignmentUsesSyntheticAuthors() {
        compose.setContent { NagiTheme("light") {
            CompositionLocalProvider(LocalUserTags provides UserTagAccess(UserTagsState(1, "7", mapOf("42" to "菜", "43" to "test"), loaded = true))) {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Column {
                        ReaderPost(Post("0", 0, "攻略作者", "42", "", "2026-11-13", listOf(ContentBlock.Text("主楼正文"))),
                            ReadingPreferences(), true, "用户标记预览", {})
                        ReaderPost(Post("1", 1, "测试用户", "43", "", "2026-11-13", listOf(ContentBlock.Text("回复正文"))),
                            ReadingPreferences(), false, null, {})
                    }
                }
            }
        } }
        val name = compose.onNodeWithText("攻略作者").fetchSemanticsNode().boundsInRoot
        val badge = compose.onNodeWithContentDescription("用户标记：菜").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(name.center.y - badge.center.y) < 2f)
        compose.onNodeWithContentDescription("用户标记：test").assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "user-tags").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { image ->
            File(output, "reader-light.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
    }

    @Test fun profileTagEditorAddsEditsValidatesAndDeletesWithoutRealWrites() {
        var state by mutableStateOf(UserTagsState(1, "7", loaded = true))
        compose.setContent { NagiTheme("light") {
            CompositionLocalProvider(LocalUserTags provides UserTagAccess(state) { id, text, done ->
                assertEquals("42", id)
                state = state.copy(tags = if (text.isBlank()) emptyMap() else mapOf(id to text.trim()))
                done()
            }) {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    UserProfileCard(UserProfileState(user = NgaUser("42", "Synthetic author", "", 1)), {}, {})
                }
            }
        } }
        compose.onNodeWithText("添加标记").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("攻略作者")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("攻略作者").assertIsDisplayed()
        compose.onNodeWithText("编辑标记").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("x".repeat(25))
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("朋友")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("朋友").assertIsDisplayed()
        compose.onNodeWithText("编辑标记").performClick()
        compose.onNodeWithText("删除标记").performClick()
        compose.onNodeWithText("朋友").assertDoesNotExist()
        compose.onNodeWithText("添加标记").assertIsDisplayed()
    }
}
