package dev.nagi.app

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.*
import dev.nagi.app.ui.post.ReaderPost
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.viewmodel.UserTagsState
import dev.nagi.sdk.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UserTagsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun chineseAndLatinTagsAlignWithAuthorAndOwnerBadge() = checkAlignment("light", 1f)
    @Test fun darkTagsRemainNeutralAndAligned() = checkAlignment("dark", 1f)
    @Config(qualifiers = "w320dp-h850dp")
    @Test fun narrowLargeFontAuthorAndTagsWrapWithoutOverlap() = checkAlignment("light", 2f)

    private fun checkAlignment(theme: String, fontScale: Float) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { NagiTheme(theme) {
            CompositionLocalProvider(LocalUserTags provides UserTagAccess(UserTagsState(1, "7", mapOf("42" to "菜", "43" to "test"), loaded = true))) {
                Column {
                    ReaderPost(Post("0", 0, "Author", "42", "", "", emptyList()), ReadingPreferences(), true, null, {})
                    ReaderPost(Post("1", 1, "作者", "43", "", "", emptyList()), ReadingPreferences(), false, null, {})
                }
            }
        } }
        for ((author, tag) in listOf("Author" to "菜", "作者" to "test")) {
            val name = compose.onNodeWithText(author).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val badge = compose.onNodeWithContentDescription("用户标记：$tag").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertFalse("Tag must not overlap author", name.overlaps(badge))
            if (fontScale == 1f) assertTrue("Author and tag must be vertically centered", abs(name.center.y - badge.center.y) < 1.1f)
        }
        if (fontScale == 1f) {
            val owner = compose.onNodeWithText("楼主").fetchSemanticsNode().boundsInRoot
            val tag = compose.onNodeWithContentDescription("用户标记：菜").fetchSemanticsNode().boundsInRoot
            assertTrue(abs(owner.center.y - tag.center.y) < 1.1f)
        }
    }

    @Test fun postAndCommentAuthorsDisplayTagsButAnonymousAuthorsDoNot() {
        val comments = (1..4).map { Post("$it", 0, "评论作者", "43", "", "", listOf(ContentBlock.Text("Comment"))) }
        val post = Post("0", 0, "帖子作者", "42", "", "", listOf(ContentBlock.Text("Body")), comments)
        compose.setContent { NagiTheme {
            CompositionLocalProvider(LocalUserTags provides UserTagAccess(UserTagsState(1, "7",
                mapOf("42" to "攻略作者", "43" to "朋友", "-1" to "不应显示"), loaded = true))) {
                Column {
                    ReaderPost(post, ReadingPreferences(), true, null, {})
                    UserTagBadge("-1")
                    UserTagEditor("-1")
                }
            }
        } }
        compose.onNodeWithText("攻略作者").assertIsDisplayed()
        compose.onAllNodesWithText("朋友").assertCountEquals(3)
        compose.onNodeWithText("不应显示").assertDoesNotExist()
        compose.onNodeWithText("添加标记").assertDoesNotExist()
        compose.onNodeWithText("查看贴条（4）").performClick()
        compose.onNodeWithText("全部贴条").assertExists()
        assertTrue(compose.onAllNodesWithText("朋友").fetchSemanticsNodes().size > 3)
    }
}
