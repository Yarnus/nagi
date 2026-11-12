package dev.nagi.app

import android.app.Application
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.navigation.rememberTopicLinkHandler
import dev.nagi.app.ui.post.ContentBlocks
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.sdk.ContentParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TopicLinkNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun inlineTopicOpensInAppAndBackRestoresSourceScroll() {
        var back: () -> Unit = {}
        var sourcePosition: () -> Pair<Int, Int> = { 0 to 0 }
        val external = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { external += uri }
            }) {
                val nav = rememberNavController()
                val handler = rememberTopicLinkHandler { nav.navigate("read/$it") }
                back = { nav.popBackStack() }
                NagiTheme("light") {
                    CompositionLocalProvider(LocalUriHandler provides handler) {
                        NavHost(nav, startDestination = "source") {
                            composable("source") {
                                val list = rememberLazyListState()
                                sourcePosition = { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
                                LazyColumn(state = list) {
                                    items(40, key = { it }) { Text("Paragraph $it") }
                                    item(key = "link") {
                                        ContentBlocks(ContentParser.parse("{[url]https://ngabbs.com/read.php?tid=45905669}[/url]"), ReadingPreferences(), {})
                                    }
                                }
                            }
                            composable("read/{id}") { Text("Target ${it.arguments!!.getString("id")}") }
                        }
                    }
                }
            }
        }
        val url = "https://ngabbs.com/read.php?tid=45905669"
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(40)
        compose.waitForIdle()
        var saved = 0 to 0
        compose.runOnIdle { saved = sourcePosition(); assertTrue(saved.first > 0) }
        compose.onNodeWithText(url).performTouchInput { click(center) }
        compose.onNodeWithText("Target 45905669").assertIsDisplayed()
        compose.runOnIdle { assertTrue(external.isEmpty()); back() }
        compose.onNodeWithText(url).assertIsDisplayed()
        compose.runOnIdle { assertEquals(saved, sourcePosition()) }
    }

    @Test fun otherLinksUseOriginalUriHandler() {
        val external = mutableListOf<String>()
        val internal = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { external += uri }
            }) {
                val handler = rememberTopicLinkHandler { internal += it }
                NagiTheme("light") {
                    CompositionLocalProvider(LocalUriHandler provides handler) {
                        ContentBlocks(ContentParser.parse("[url=https://example.com/]External[/url]"), ReadingPreferences(), {})
                    }
                }
            }
        }
        compose.onNodeWithText("External").performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals(listOf("https://example.com/"), external)
            assertTrue(internal.isEmpty())
        }
    }
}
