package dev.nagi.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.post.ReaderPost
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.viewmodel.ComposePhase
import dev.nagi.app.viewmodel.PostRatingState
import dev.nagi.sdk.PostRating
import dev.nagi.sdk.ContentBlock
import dev.nagi.sdk.Post
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
class ReaderActionsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun quietActionsShareMetadataRowAndRetainIndependentTargets() = renderActions("light", 1f)
    @Test fun darkMenuAndActionsRemainReadable() = renderActions("dark", 1f)
    @Config(qualifiers = "w320dp-h850dp")
    @Test fun narrowLargeFontWrapsMetadataWithoutShrinkingTargets() = renderActions("light", 1.5f)

    @Test fun scoreUsesOnlyConfirmedServerDeltaIncludingVoteRemoval() {
        var status by mutableStateOf<PostRatingState?>(null)
        compose.setContent { NagiTheme {
            ReaderPost(
                Post("12", 3, "作者", "7", "", "", listOf(ContentBlock.Text("正文")), score = 33),
                ReadingPreferences(), false, null, {},
                onReply = {}, onRating = {}, ratingState = status,
            )
        } }
        compose.onNodeWithText("33").assertExists()
        listOf(ComposePhase.Submitting, ComposePhase.Unconfirmed, ComposePhase.Rejected).forEach { phase ->
            compose.runOnIdle { status = PostRatingState(phase) }
            compose.onNodeWithText("33").assertExists()
            compose.onNodeWithText("35").assertDoesNotExist()
        }
        compose.runOnIdle { status = PostRatingState(ComposePhase.Confirmed, PostRating.UP, scoreDelta = 2) }
        compose.onNodeWithText("35").assertExists()
        listOf(ComposePhase.Submitting, ComposePhase.Unconfirmed, ComposePhase.Rejected).forEach { phase ->
            compose.runOnIdle { status = PostRatingState(phase, PostRating.UP, scoreDelta = 2) }
            compose.onNodeWithText("35").assertExists()
        }
        compose.runOnIdle { status = PostRatingState(ComposePhase.Confirmed, null, scoreDelta = -1) }
        compose.onNodeWithText("32").assertExists()
        compose.onNodeWithText("点赞已取消").assertDoesNotExist()
    }

    @Test fun confirmedUpAndDownButtonsRemainClickableAndCommentMenuIsPreserved() {
        var status by mutableStateOf<PostRatingState?>(null)
        val clicks = mutableListOf<PostRating>()
        var comments = 0
        compose.setContent { NagiTheme {
            ReaderPost(
                Post("12", 3, "作者", "7", "", "", listOf(ContentBlock.Text("正文")), score = 33),
                ReadingPreferences(), false, null, {},
                onReply = {}, onComment = { comments++ }, ratingState = status,
                onRating = { direction ->
                    clicks += direction
                    val vote = if (status?.rating == direction) null else direction
                    status = PostRatingState(ComposePhase.Confirmed, vote, vote?.value ?: 0)
                },
            )
        } }
        listOf(PostRating.UP, PostRating.UP, PostRating.DOWN, PostRating.DOWN).forEachIndexed { index, direction ->
            compose.onNodeWithContentDescription(if (direction == PostRating.UP) "点赞第 3 楼" else "点踩第 3 楼").performClick()
            compose.runOnIdle { assertEquals(index + 1, clicks.size) }
            compose.onNodeWithText((33 + (status?.scoreDelta ?: 0)).toString()).assertExists()
        }
        compose.onNodeWithContentDescription("第 3 楼更多操作").performClick()
        compose.onNodeWithText("贴条评论").performClick()
        compose.runOnIdle { assertEquals(1, comments) }
    }

    @Test fun ratingFeedbackStaysInsideButtonsWithoutChangingPostHeight() = renderRatingFeedback("light", 1f)
    @Test fun darkRatingFeedbackStaysInsideButtons() = renderRatingFeedback("dark", 1f)
    @Config(qualifiers = "w320dp-h850dp")
    @Test fun narrowLargeTextRatingFeedbackStaysInsideButtons() = renderRatingFeedback("light", 2f)

    private fun renderRatingFeedback(theme: String, scale: Float) {
        org.robolectric.RuntimeEnvironment.setFontScale(scale)
        var status by mutableStateOf<PostRatingState?>(null)
        var clicks = 0
        lateinit var view: View
        compose.setContent { NagiTheme(theme) {
            view = LocalView.current
            Surface(Modifier.fillMaxSize()) {
                Column {
                    Box(Modifier.testTag("post")) {
                        ReaderPost(
                            Post("12", 3, "作者", "7", "", "", listOf(ContentBlock.Text("正文")), score = 33),
                            ReadingPreferences(), false, null, {},
                            onReply = {}, onRating = { clicks++ }, ratingState = status,
                        )
                    }
                }
            }
        } }
        val bounds = compose.onNodeWithTag("post").getUnclippedBoundsInRoot()
        listOf(ComposePhase.Submitting, ComposePhase.Confirmed, ComposePhase.Rejected, ComposePhase.Unconfirmed).forEach { phase ->
            compose.runOnIdle { status = PostRatingState(phase, PostRating.UP, pendingRating = PostRating.DOWN.takeIf { phase == ComposePhase.Submitting }) }
            val up = compose.onNodeWithContentDescription("点赞第 3 楼")
            val down = compose.onNodeWithContentDescription("点踩第 3 楼")
            up.assertIsSelected(); down.assertIsNotSelected()
            if (phase == ComposePhase.Submitting || phase == ComposePhase.Unconfirmed) {
                up.assertIsNotEnabled(); down.assertIsNotEnabled()
                up.performClick(); down.performClick()
            } else {
                up.assertIsEnabled(); down.assertIsEnabled()
            }
            assertEquals(bounds, compose.onNodeWithTag("post").getUnclippedBoundsInRoot())
            compose.onNodeWithText("已确认", substring = true).assertDoesNotExist()
            compose.onNodeWithText("处理中", substring = true).assertDoesNotExist()
            compose.mainClock.advanceTimeBy(250)
            compose.runOnIdle {
                val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(image))
                File("build/ui-captures/reader-rating-$theme-$scale-$phase.png").apply { parentFile?.mkdirs() }.outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        compose.runOnIdle { assertEquals(0, clicks) }
        compose.onNodeWithContentDescription("第 3 楼更多操作").performClick()
        compose.onNodeWithText("取消点赞").assertIsNotEnabled()
    }

    @Test fun editMenuAppearsOnlyOnCurrentUsersPost() {
        var viewer by mutableStateOf<String?>("7")
        var edits = 0
        compose.setContent { NagiTheme {
            ReaderPost(
                Post("12", 3, "作者", "7", "", "", listOf(ContentBlock.Text("正文"))),
                ReadingPreferences(), false, null, {}, onReply = {}, onComment = {},
                viewerId = viewer, onEdit = { edits++ },
            )
        } }
        compose.onNodeWithContentDescription("第 3 楼更多操作").performClick()
        compose.onNodeWithText("编辑回复").performClick()
        compose.runOnIdle { assertEquals(1, edits); viewer = "8" }
        compose.onNodeWithContentDescription("第 3 楼更多操作").performClick()
        compose.onNodeWithText("编辑回复").assertDoesNotExist()
        compose.onNodeWithText("贴条评论").performClick()
        compose.runOnIdle { viewer = null }
        compose.onNodeWithContentDescription("第 3 楼更多操作").performClick()
        compose.onNodeWithText("编辑回复").assertDoesNotExist()
    }

    private fun renderActions(theme: String, scale: Float) {
        org.robolectric.RuntimeEnvironment.setFontScale(scale)
        val width = org.robolectric.RuntimeEnvironment.getApplication().resources.configuration.screenWidthDp
        var replies = 0
        lateinit var view: View
        compose.setContent { NagiTheme(theme) {
            view = LocalView.current
            Surface(Modifier.fillMaxSize()) {
                Column {
                    listOf("阅读应当以正文为主，不让重复操作抢走注意力。", "简短回复。").forEachIndexed { index, text ->
                        ReaderPost(
                            Post(
                                id = "${index + 5}",
                                floor = index + 5,
                                author = "测试作者",
                                authorId = "7",
                                avatar = "",
                                date = "2026-11-06 12:51",
                                content = listOf(ContentBlock.Text(text)),
                                score = if (index == 0) 108 else 0,
                                authorLevel = "学徒",
                                authorReputation = "1.0",
                                authorPosts = 560,
                                clientPlatform = "Android",
                            ),
                            ReadingPreferences(),
                            false,
                            null,
                            {},
                            onReply = { replies++ },
                            onComment = {},
                            onRating = {},
                        )
                    }
                }
            }
        } }
        compose.onNodeWithText("108").assertExists()
        compose.onAllNodesWithText("威望: 1.0  发帖: 560").onFirst().assertExists()
        compose.onAllNodesWithText("级别:", substring = true).assertCountEquals(0)
        val up = compose.onNodeWithContentDescription("点赞第 5 楼")
        val down = compose.onNodeWithContentDescription("点踩第 5 楼")
        val reply = compose.onNodeWithContentDescription("回复第 5 楼")
        val more = compose.onNodeWithContentDescription("第 5 楼更多操作")
        val u = up.getUnclippedBoundsInRoot()
        val d = down.getUnclippedBoundsInRoot()
        val a = reply.getUnclippedBoundsInRoot()
        val b = more.getUnclippedBoundsInRoot()
        assertEquals(48.dp, a.right - a.left); assertEquals(48.dp, a.bottom - a.top)
        assertTrue(u.right <= d.left)
        assertTrue(d.right <= a.left)
        assertTrue(a.right <= b.left)
        val expectedDate = dev.nagi.app.ui.topic.formatTimeText("2026-11-06 12:51")
        val date = compose.onAllNodesWithText(expectedDate)[0].getUnclippedBoundsInRoot()
        assertTrue(date.bottom <= a.top || (date.top >= a.top && date.bottom <= a.bottom))
        reply.performClick(); compose.runOnIdle { assertEquals(1, replies) }
        more.performClick(); compose.onNodeWithText("引用回复").assertDoesNotExist()
        compose.onNodeWithText("点赞").assertExists()
        compose.onNodeWithText("踩").assertExists()
        compose.onNodeWithText("贴条评论").assertExists()
        compose.runOnIdle {
            val menu = android.view.inspector.WindowInspector.getGlobalWindowViews().last()
            val image = Bitmap.createBitmap(menu.width, menu.height, Bitmap.Config.ARGB_8888)
            menu.draw(Canvas(image))
            File("build/ui-captures/reader-menu-$width-$theme-$scale.png").apply { parentFile?.mkdirs() }.outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithText("点赞").performClick()
        compose.runOnIdle {
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            File("build/ui-captures/reader-actions-$width-$theme-$scale.png").apply { parentFile?.mkdirs() }.outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
