package dev.nagi.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dev.nagi.sdk.ContentBlock
import dev.nagi.sdk.ContentParser
import dev.nagi.sdk.NgaClient
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderTopicNavigationTest {
    private val base = FakeClient()
    private val fixture = object : NgaClient by base {
        override suspend fun getPosts(topicId: String, page: Int, authorId: String?) =
            base.getPosts(topicId, page, authorId).let { result ->
                result.copy(items = result.items.take(1).map { post ->
                    post.copy(content = listOf(ContentBlock.Text("Topic body $topicId"), ContentBlock.Divider()) +
                        ContentParser.parse("[url=https://ngabbs.com/read.php?tid=2]Open topic 2[/url]"))
                })
            }
    }
    @get:Rule(order = 0) val clientRule = object : ExternalResource() {
        override fun before() { FakeAppModule.clientOverride = fixture }
        override fun after() { FakeAppModule.clientOverride = null }
    }
    @get:Rule(order = 1) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun topicLinksDeduplicateSameTopicAndKeepDifferentTopicOnBackStack() {
        compose.onNodeWithText("Forum").performClick()
        compose.onNodeWithText("Title 1").performClick()
        compose.onNodeWithText("Topic body 1", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Open topic 2").performTouchInput { click(center) }
        compose.onNodeWithText("Topic body 2", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Open topic 2").performTouchInput { click(center) }
        compose.onNodeWithText("Topic body 2", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("Topic body 1", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("Title 1").assertIsDisplayed()
    }
}
