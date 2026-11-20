package dev.nagi.app

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.post.PostShareCardDialog
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.viewmodel.MainPostShareState
import dev.nagi.sdk.ContentBlock
import dev.nagi.sdk.Post
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostShareCardDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exportsValidPngFromRealRenderThread() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val shareDir = File(targetContext.cacheDir, "shared-images")
        shareDir.deleteRecursively()

        val startedIntents = mutableListOf<Intent>()
        val post = Post(
            id = "1001",
            floor = 0,
            author = "模拟器测试楼主",
            authorId = "7",
            avatar = "",
            date = "2026-11-17 12:00",
            content = listOf(
                ContentBlock.Text("这是在真实 Android RenderThread 下生成的主楼分享卡片。"),
                ContentBlock.Quote(listOf(ContentBlock.Text("引用内容预览")), author = "引用作者"),
            ),
            authorReputation = "12",
            authorPosts = 34,
        )

        compose.setContent {
            val base = LocalContext.current
            val wrapped = object : android.content.ContextWrapper(base) {
                override fun startActivity(intent: Intent) {
                    startedIntents += intent
                }
            }
            CompositionLocalProvider(LocalContext provides wrapped) {
                NagiTheme {
                    PostShareCardDialog(
                        topicId = "47841712",
                        topicTitle = "模拟器真机级渲染测试标题",
                        post = post,
                        shareState = MainPostShareState(open = true),
                        preferences = ReadingPreferences(),
                        onRetry = {},
                        close = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("share-card-dialog").assertIsDisplayed()
        compose.onNodeWithText("模拟器真机级渲染测试标题").assertIsDisplayed()
        compose.onNodeWithTag("share-card-theme-toggle").performClick()
        compose.onNodeWithText("深色卡片").assertIsDisplayed()

        compose.onNodeWithText("分享图片").assertIsDisplayed().performClick()
        compose.waitUntil(timeoutMillis = 5_000) { startedIntents.isNotEmpty() }

        val files = shareDir.listFiles().orEmpty()
        assertEquals(1, files.size)
        val bitmap = BitmapFactory.decodeFile(files.single().absolutePath)
        assertNotNull(bitmap)
        assertTrue(bitmap.width > 200 && bitmap.height > 200)
        bitmap.recycle()

        val chooser = startedIntents.single()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(send)
        assertEquals(Intent.ACTION_SEND, send!!.action)
        assertEquals("image/jpeg", send.type)
    }
}
