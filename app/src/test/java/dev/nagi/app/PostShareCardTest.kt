package dev.nagi.app

import android.app.Application
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.LocalUserTags
import dev.nagi.app.ui.components.UserTagAccess
import dev.nagi.app.ui.post.HeightBoundedShareCard
import dev.nagi.app.ui.post.MAX_CARD_EXPORT_HEIGHT_PX
import dev.nagi.app.ui.post.PostShareCardDialog
import dev.nagi.app.ui.post.ReaderPost
import dev.nagi.app.ui.post.ReaderTopBar
import dev.nagi.app.ui.post.SHARE_CARD_EXPORT_WIDTH_PX
import dev.nagi.app.ui.post.ShareCardPictureRecorder
import dev.nagi.app.ui.post.trimShareCardBlocks
import kotlinx.coroutines.runBlocking
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.viewmodel.MainPostShareState
import dev.nagi.app.viewmodel.UserTagsState
import dev.nagi.sdk.ContentBlock
import dev.nagi.sdk.InlineSmile
import dev.nagi.sdk.NgaSmile
import dev.nagi.sdk.Post
import dev.nagi.sdk.TextSpan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
class PostShareCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun trimShareCardBlocksPreservesShortContentAndTruncatesLongContentSafely() {
        val short = listOf(ContentBlock.Text("短内容"), ContentBlock.Image("https://img.nga.cn/a.png"))
        val kept = trimShareCardBlocks(short)
        assertFalse(kept.truncated)
        assertEquals(short, kept.blocks)

        val smile = NgaSmile("ac", "茶", "https://img.nga.cn/ac.png")
        val text = "A".repeat(130) + "[s:ac:茶]" + "B".repeat(200)
        val longText = ContentBlock.Text(
            value = text,
            spans = listOf(TextSpan(0, text.length, bold = true)),
            smiles = listOf(InlineSmile(130, 137, smile)),
        )
        val trimmed = trimShareCardBlocks(listOf(longText), maxChars = 133)
        assertTrue(trimmed.truncated)
        val first = trimmed.blocks.single() as ContentBlock.Text
        assertEquals("A".repeat(130) + "…", first.value)
        assertTrue(first.smiles.isEmpty())
        assertEquals(130, first.spans.single().end)

        val manyImages = List(8) { ContentBlock.Image("https://img.nga.cn/$it.png") }
        val trimmedImages = trimShareCardBlocks(manyImages, maxImages = 3)
        assertTrue(trimmedImages.truncated)
        assertEquals(3, trimmedImages.blocks.size)
    }

    @Test fun topBarAndMainPostMenuExposeShareCardAction() {
        var topBarClicks = 0
        var mainPostClicks = 0
        var replyPostClicks = 0
        compose.setContent {
            NagiTheme {
                androidx.compose.foundation.layout.Column {
                    ReaderTopBar(
                        title = "测试主题",
                        showTitle = true,
                        favorite = false,
                        authorOnly = false,
                        enabled = true,
                        canFilter = true,
                        back = {},
                        onFavorite = {},
                        onFilter = {},
                        onRefresh = {},
                        onShareCard = { topBarClicks++ },
                    )
                    ReaderPost(
                        post = Post("1", 0, "楼主", "7", "", "2026-11-17 12:00", listOf(ContentBlock.Text("主楼正文"))),
                        preferences = ReadingPreferences(),
                        isAuthor = true,
                        title = "测试主题",
                        onImage = {},
                        onReply = {},
                        onShareCard = { mainPostClicks++ },
                    )
                    ReaderPost(
                        post = Post("2", 1, "层主", "8", "", "2026-11-17 12:01", listOf(ContentBlock.Text("一楼回复"))),
                        preferences = ReadingPreferences(),
                        isAuthor = false,
                        title = null,
                        onImage = {},
                        onReply = {},
                        onShareCard = null,
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("主题操作").performClick()
        compose.onNodeWithText("分享主楼卡片").assertExists().performClick()
        compose.runOnIdle { assertEquals(1, topBarClicks) }

        compose.onNodeWithContentDescription("第 0 楼更多操作").performClick()
        compose.onNodeWithText("分享主楼卡片").assertExists().performClick()
        compose.runOnIdle { assertEquals(1, mainPostClicks) }

        compose.onNodeWithContentDescription("第 1 楼更多操作").performClick()
        compose.onNodeWithText("分享主楼卡片").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, replyPostClicks) }
    }

    @Test fun shareCardDialogRendersAcrossThemesOmitsPrivateTagsAndExportsPng() {
        val startedIntents = mutableListOf<Intent>()
        val cacheDir = RuntimeEnvironment.getApplication().cacheDir
        File(cacheDir, "shared-images").deleteRecursively()

        val longBlocks = List(110) { index -> ContentBlock.Text("第 $index 段主楼内容") }
        val mainPost = Post(
            id = "1001",
            floor = 0,
            author = "测试楼主",
            authorId = "77",
            avatar = "",
            date = "2026-11-17 12:00",
            content = longBlocks,
            authorReputation = "100",
            authorPosts = 42,
        )

        var fontScale by mutableStateOf(1f)
        compose.setContent {
            val base = LocalContext.current
            val wrapped = remember(base) {
                object : ContextWrapper(base) {
                    override fun startActivity(intent: Intent) {
                        startedIntents += intent
                    }
                }
            }
            CompositionLocalProvider(
                LocalContext provides wrapped,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalUserTags provides UserTagAccess(UserTagsState(tags = mapOf("77" to "私人备注"))),
            ) {
                NagiTheme("light") {
                    PostShareCardDialog(
                        topicId = "47841712",
                        topicTitle = "主楼卡片标题",
                        post = mainPost,
                        shareState = MainPostShareState(open = true),
                        preferences = ReadingPreferences(),
                        onRetry = {},
                        close = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("share-card-dialog").assertExists()
        compose.onNodeWithText("主楼卡片标题").assertIsDisplayed()
        compose.onNodeWithText("测试楼主").assertIsDisplayed()
        compose.onNodeWithText("私人备注").assertDoesNotExist()
        compose.onNodeWithText("内容已节选 · 完整内容请查看原帖").assertExists()
        compose.onNodeWithText("NGA · tid 47841712").assertExists()

        compose.onNodeWithTag("share-card-theme-toggle").performClick()
        compose.onNodeWithText("深色卡片").assertExists()

        compose.runOnIdle {
            WindowInspector.getGlobalWindowViews().forEach { windowView ->
                if (windowView.width > 0 && windowView.height > 0) {
                    val scratch = Bitmap.createBitmap(windowView.width, windowView.height, Bitmap.Config.ARGB_8888)
                    windowView.draw(Canvas(scratch))
                    scratch.recycle()
                }
            }
        }

        compose.onNodeWithText("分享图片").assertExists().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            startedIntents.isNotEmpty() || org.robolectric.shadows.ShadowToast.getTextOfLatestToast() != null
        }
        val toast = org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
        assertTrue("Unexpected error toast: $toast", startedIntents.isNotEmpty())

        val shareFiles = File(cacheDir, "shared-images").listFiles().orEmpty()
        assertEquals(1, shareFiles.size)
        val imageFile = shareFiles.single()
        assertTrue(imageFile.name.endsWith(".jpg"))
        val decoded = BitmapFactory.decodeFile(imageFile.absolutePath)
        assertNotNull(decoded)
        assertEquals(SHARE_CARD_EXPORT_WIDTH_PX, decoded.width)
        assertTrue(decoded.height in 1..MAX_CARD_EXPORT_HEIGHT_PX)
        decoded.recycle()

        assertEquals(1, startedIntents.size)
        val chooser = startedIntents.single()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val target = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(target)
        assertEquals(Intent.ACTION_SEND, target!!.action)
        assertEquals("image/jpeg", target.type)
        assertTrue((target.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun shareCardDialogRemainsUsableOnNarrowLargeFont() {
        val mainPost = Post(
            id = "1001",
            floor = 0,
            author = "长昵称作者",
            authorId = "77",
            avatar = "",
            date = "2026-11-17 12:00",
            content = List(20) { ContentBlock.Text("第 $it 段窄屏大字号测试正文") },
        )
        var closed = false
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 2f),
            ) {
                NagiTheme("dark") {
                    PostShareCardDialog(
                        topicId = "47841712",
                        topicTitle = "窄屏大字号主楼分享卡片标题",
                        post = mainPost,
                        shareState = MainPostShareState(open = true),
                        preferences = ReadingPreferences(),
                        onRetry = {},
                        close = { closed = true },
                    )
                }
            }
        }
        compose.onNodeWithText("分享图片").assertIsDisplayed()
        compose.onNodeWithText("关闭").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test fun shareCardDialogShowsLoadingAndErrorRetryStates() {
        var shareState by mutableStateOf(MainPostShareState(open = true, loading = true))
        var retries = 0
        compose.setContent {
            NagiTheme {
                PostShareCardDialog(
                    topicId = "99",
                    topicTitle = "标题",
                    post = null,
                    shareState = shareState,
                    preferences = ReadingPreferences(),
                    onRetry = { retries++ },
                    close = {},
                )
            }
        }
        compose.onNodeWithText("正在加载主楼内容…").assertIsDisplayed()
        compose.runOnIdle { shareState = MainPostShareState(open = true, error = "网络超时") }
        compose.onNodeWithText("网络超时").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun shareCardDialogExportsFixedWidthAndTruncatesByActualHeightOnHighDensity() {
        val longParagraphs = List(30) { index ->
            ContentBlock.Text("第 $index 段：" + "超长测试文本内容换行验证".repeat(8))
        }
        val mainPost = Post(
            id = "2002",
            floor = 0,
            author = "高分屏测试",
            authorId = "88",
            avatar = "",
            date = "2026-11-20 14:00",
            content = longParagraphs,
        )
        val preTrimmed = trimShareCardBlocks(mainPost.content)
        val recorder = ShareCardPictureRecorder()

        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 4f, fontScale = 2f),
            ) {
                NagiTheme("light") {
                    HeightBoundedShareCard(
                        topicId = "47841713",
                        topicTitle = "高密度大字号高度精准截断测试",
                        post = mainPost,
                        preTrimmed = preTrimmed,
                        preferences = ReadingPreferences(),
                        darkCard = false,
                        pictureRecorder = recorder,
                    )
                }
            }
        }

        compose.onNodeWithText("内容已节选 · 完整内容请查看原帖").assertExists()

        compose.runOnIdle {
            WindowInspector.getGlobalWindowViews().forEach { windowView ->
                if (windowView.width > 0 && windowView.height > 0) {
                    val scratch = Bitmap.createBitmap(windowView.width, windowView.height, Bitmap.Config.ARGB_8888)
                    windowView.draw(Canvas(scratch))
                    scratch.recycle()
                }
            }
        }

        val picture = recorder.picture
        assertNotNull(picture)
        assertEquals(SHARE_CARD_EXPORT_WIDTH_PX, picture!!.width)
        assertTrue(
            "Expected height in 1..$MAX_CARD_EXPORT_HEIGHT_PX, got ${picture.height}",
            picture.height in 1..MAX_CARD_EXPORT_HEIGHT_PX,
        )
    }

    @Test fun shareCardDialogRendersCommentsHotRepliesAndEarlyFloors() {
        val mainPost = Post(
            id = "3001",
            floor = 0,
            author = "楼主",
            authorId = "1",
            avatar = "",
            date = "2026-11-20 15:00",
            content = listOf(ContentBlock.Text("主楼完整正文")),
            comments = listOf(
                Post("3002", 0, "贴条路人", "2", "", "2026-11-20 15:01", listOf(ContentBlock.Text("主楼贴条内容"))),
            ),
            hotReplies = listOf(
                Post("3003", 5, "热评大佬", "3", "", "2026-11-20 15:02", listOf(ContentBlock.Text("高赞热评正文")), score = 128),
            ),
        )
        val earlyReplies = listOf(
            Post("3004", 1, "沙发", "4", "", "2026-11-20 15:03", listOf(ContentBlock.Text("一楼评论"))),
            Post("3005", 2, "板凳", "5", "", "2026-11-20 15:04", listOf(ContentBlock.Text("二楼评论"))),
            Post("3006", 3, "地板", "6", "", "2026-11-20 15:05", listOf(ContentBlock.Text("三楼评论"))),
        )

        compose.setContent {
            NagiTheme("light") {
                PostShareCardDialog(
                    topicId = "47841714",
                    topicTitle = "含贴条热评与前排回复的卡片",
                    post = mainPost,
                    shareState = MainPostShareState(open = true, earlyReplies = earlyReplies),
                    preferences = ReadingPreferences(),
                    onRetry = {},
                    close = {},
                )
            }
        }

        compose.onNodeWithText("主楼完整正文").assertExists()
        compose.onNodeWithText("贴条 · 1").assertExists()
        compose.onNodeWithText("主楼贴条内容").assertExists()
        compose.onNodeWithText("热门回复").assertExists()
        compose.onNodeWithText("赞 128").assertExists()
        compose.onNodeWithText("高赞热评正文").assertExists()
        compose.onNodeWithText("前排回复").assertExists()
        compose.onNodeWithText("一楼评论").assertExists()
        compose.onNodeWithText("二楼评论").assertExists()
        compose.onNodeWithText("三楼评论").assertExists()
    }
}
