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

    @Test fun accountMenuNavigatesToFavoritesWithScopeSummary() {
        var selected by mutableStateOf("forums")
        compose.setContent { NagiTheme { AccountMenuButton(onSelect = { selected = it }) } }
        compose.onNodeWithContentDescription("头像菜单").performClick()
        compose.onNodeWithText("本地收藏 · NGA 收藏版块").assertExists()
        compose.onNodeWithText("收藏").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals("favorites", selected) }
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
                                ContentBlock.Table(listOf(listOf(listOf(ContentBlock.Text("Column A")), listOf(ContentBlock.Text("Column B"))))),
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

    @Test fun contentHelpersCollectNestedImagesAndExtractPlainText() {
        val blocks = listOf(
            ContentBlock.Heading(listOf(ContentBlock.Text("标题"))),
            ContentBlock.Text("正文"),
            ContentBlock.Image("https://img4.nga.cn/a.png"),
            ContentBlock.Collapse("折叠", listOf(ContentBlock.Image("https://img4.nga.cn/b.png"), ContentBlock.Text("内部"))),
            ContentBlock.Quote(listOf(ContentBlock.Image("https://img4.nga.cn/ignored-quote.png"), ContentBlock.Text("引用")), author = "A"),
            ContentBlock.ListBlock(listOf(listOf(ContentBlock.Text("一")), listOf(ContentBlock.Text("二"))), ordered = true),
        )
        org.junit.Assert.assertEquals(
            listOf("https://img4.nga.cn/a.png", "https://img4.nga.cn/b.png"),
            collectContentImages(blocks),
        )
        val plain = contentPlainText(blocks)
        org.junit.Assert.assertTrue(plain.contains("标题"))
        org.junit.Assert.assertTrue(plain.contains("1. 一"))
        org.junit.Assert.assertTrue(plain.contains("[折叠]\n[图片] https://img4.nga.cn/b.png\n内部"))
    }

    @Test fun floorMenuExposesFilterAuthorAndCopyActionsAndMainActivityExtractsDeepLink() {
        var filtered = false
        var copiedText = false
        var copiedLink = false
        val post = dev.nagi.sdk.Post(
            id = "100",
            floor = 1,
            author = "层主",
            authorId = "88",
            avatar = "",
            date = "2026-11-16",
            content = listOf(ContentBlock.Text("层主正文")),
        )
        compose.setContent {
            NagiTheme {
                ReaderPost(
                    post = post,
                    preferences = ReadingPreferences(),
                    isAuthor = false,
                    title = null,
                    onImage = {},
                    onReply = {},
                    onFilterAuthor = { filtered = true },
                    onCopyText = { copiedText = true },
                    onCopyLink = { copiedLink = true },
                )
            }
        }
        compose.onNodeWithContentDescription("第 1 楼更多操作").performClick()
        compose.onNodeWithText("只看该层主").performClick()
        compose.onNodeWithContentDescription("第 1 楼更多操作").performClick()
        compose.onNodeWithText("复制内容").performClick()
        compose.onNodeWithContentDescription("第 1 楼更多操作").performClick()
        compose.onNodeWithText("复制楼层链接").performClick()
        compose.runOnIdle {
            org.junit.Assert.assertTrue(filtered)
            org.junit.Assert.assertTrue(copiedText)
            org.junit.Assert.assertTrue(copiedLink)
        }

        val viewIntent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("https://bbs.nga.cn/read.php?tid=45905669"),
        )
        val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
        org.junit.Assert.assertEquals("https://bbs.nga.cn/read.php?tid=45905669", MainActivity.extractDeepLink(viewIntent))
        org.junit.Assert.assertNull(MainActivity.extractDeepLink(mainIntent))
    }

    @Test fun clipboardLinkDetectorTracksHandledLinksAndRendersPromptBanner() {
        val state = dev.nagi.app.navigation.ClipboardLinkState()
        org.junit.Assert.assertNull(state.inspectText(null, timestamp = 50L))
        org.junit.Assert.assertFalse(state.shouldSkipByTimestamp(50L))
        org.junit.Assert.assertNull(state.inspectText("   ", timestamp = 60L))
        org.junit.Assert.assertFalse(state.shouldSkipByTimestamp(60L))

        val first = state.inspectText("分享帖子 https://bbs.nga.cn/read.php?tid=998877&page=2", timestamp = 100L)
        org.junit.Assert.assertNotNull(first)
        org.junit.Assert.assertTrue(state.shouldSkipByTimestamp(100L))
        org.junit.Assert.assertFalse(state.shouldSkipByTimestamp(200L))

        state.markHandled(first!!.url)
        org.junit.Assert.assertNull(state.inspectText("https://bbs.nga.cn/read.php?tid=998877&page=2", timestamp = 200L))

        // Timestamp-scoped handling (local copy or banner dismiss) allows re-prompting on a newer external copy.
        state.markHandled("https://bbs.nga.cn/read.php?tid=112233", timestamp = 300L)
        org.junit.Assert.assertNull(state.inspectText("https://bbs.nga.cn/read.php?tid=112233", timestamp = 300L))
        org.junit.Assert.assertNotNull(state.inspectText("https://bbs.nga.cn/read.php?tid=112233", timestamp = 400L))

        var opened = false
        var ignored = false
        compose.setContent {
            NagiTheme {
                dev.nagi.app.navigation.ClipboardLinkPromptDialog(
                    link = first,
                    onOpen = { opened = true },
                    onIgnore = { ignored = true },
                )
            }
        }
        compose.onNodeWithText("剪贴板链接").assertIsDisplayed()
        compose.onNodeWithText("帖子 #998877 · 第 2 页").assertIsDisplayed()
        compose.onNodeWithText("打开").performClick()
        compose.onNodeWithText("忽略").performClick()
        compose.runOnIdle {
            org.junit.Assert.assertTrue(opened)
            org.junit.Assert.assertTrue(ignored)
        }
    }

    @Test fun clipboardDetectorReadsUriListClipsAndForbidsRawMaterialAlertDialog() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        val uriClip = android.content.ClipData(
            android.content.ClipDescription("NGA", arrayOf(android.content.ClipDescription.MIMETYPE_TEXT_URILIST)),
            android.content.ClipData.Item(android.net.Uri.parse("https://bbs.nga.cn/read.php?tid=47931570")),
        )
        clipboard.setPrimaryClip(uriClip)
        val state = dev.nagi.app.navigation.ClipboardLinkState()
        val result = state.inspectClipboardResult(context)
        org.junit.Assert.assertTrue(result.readAttempted)
        org.junit.Assert.assertEquals("https://bbs.nga.cn/read.php?tid=47931570", result.link?.url)

        val sourceRoot = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val rawAlertDialogFiles = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> file.readText().contains("AlertDialog") }
            .map { it.relativeTo(sourceRoot).path }
            .toList()
        org.junit.Assert.assertEquals(emptyList<String>(), rawAlertDialogFiles)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun nagiConfirmDialogAndClipboardBannerRenderInDarkLargeText() {
        var confirmed = false
        compose.setContent {
            NagiTheme(theme = "dark") {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                    dev.nagi.app.ui.components.NagiConfirmDialog(
                        onDismissRequest = {},
                        title = "确认操作",
                        message = "窄屏大字号下按钮应自动换行且不裁切。",
                        confirmLabel = "确认继续",
                        onConfirm = { confirmed = true },
                        dismissLabel = "暂不",
                    )
                }
            }
        }
        compose.onNodeWithText("确认操作").assertIsDisplayed()
        compose.onNodeWithText("确认继续").assertIsDisplayed().performClick()
        compose.onNodeWithText("暂不").assertIsDisplayed()
        compose.runOnIdle { org.junit.Assert.assertTrue(confirmed) }
    }
}
