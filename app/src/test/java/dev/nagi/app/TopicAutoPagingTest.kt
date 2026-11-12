package dev.nagi.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.*
import dev.nagi.sdk.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class PagingClient : NgaClient by FakeClient() {
    val pages = mutableListOf<Int>()
    var failSecond = false
    var secondGate: CompletableDeferred<Unit>? = null
    override suspend fun getTopics(forumId: String, page: Int, collectionId: String?, sort: TopicSort): TopicPage {
        pages += page
        if (page == 2) {
            secondGate?.await()
            if (failSecond) throw NgaException.Network()
        }
        return TopicPage((1..30).map { offset ->
            val id = (page - 1) * 30 + offset
            Topic("$id", "主题 $id", "测试作者", "7")
        }, page, 90, page < 3)
    }
}

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TopicAutoPagingTest {
    private val client = PagingClient()
    @get:Rule(order = 0) val fixture = object : ExternalResource() {
        override fun before() { FakeAppModule.clientOverride = client }
        override fun after() { FakeAppModule.clientOverride = null }
    }
    @get:Rule(order = 1) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()
    @Before fun inject() { hilt.inject() }

    @Test fun scrollingLoadsNextPageOnceAndStopsAtEnd() {
        val gate = CompletableDeferred<Unit>()
        client.secondGate = gate
        compose.onNodeWithText("Forum").performClick()
        compose.onNodeWithText("主题 1").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(1), client.pages) }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(27)
        compose.waitUntil { client.pages.size == 2 }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(1, 2), client.pages); gate.complete(Unit) }
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(57)
        compose.waitUntil { client.pages.size == 3 }
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(90)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(1, 2, 3), client.pages) }
        compose.onNodeWithText("加载更多").assertDoesNotExist()
        compose.onNodeWithText("主题 90").assertExists()
    }
    @Test fun paginationFailureKeepsItemsAndWaitsForExplicitRetry() {
        client.failSecond = true
        compose.onNodeWithText("Forum").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(28)
        compose.onNodeWithText("重试").performScrollTo().assertIsDisplayed()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(1, 2), client.pages); client.failSecond = false }
        compose.onNodeWithText("主题 30").assertExists()
        compose.onNodeWithText("重试").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(1, 2, 2), client.pages) }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(55)
        compose.onNodeWithText("主题 56").assertExists()
    }
}
