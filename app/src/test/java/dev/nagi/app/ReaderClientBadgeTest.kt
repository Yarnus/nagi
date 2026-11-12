package dev.nagi.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.post.ReaderClientBadge
import dev.nagi.app.ui.post.ReaderPost
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.sdk.ContentBlock
import dev.nagi.sdk.Post
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h850dp")
class ReaderClientBadgeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun knownPlatformsUseNonInteractiveIconsWithAccessibleLabels() {
        var platform by mutableStateOf("/")
        compose.setContent { NagiTheme { ReaderClientBadge(platform) } }
        for ((value, description) in listOf(
            "/" to "网页版", "Android" to "Android 客户端", "iOS" to "iOS 客户端",
        )) {
            compose.runOnIdle { platform = value }
            compose.onNodeWithContentDescription(description).assertExists().assertHasNoClickAction()
            compose.onNodeWithText(value).assertDoesNotExist()
        }
    }

    @Test fun missingValuesDoNotBecomeWebAndUnknownPlatformsRetainTheirLabel() {
        var platform by mutableStateOf<String?>(null)
        compose.setContent { NagiTheme { ReaderClientBadge(platform) } }
        for (value in listOf(null, "", "   ")) {
            compose.runOnIdle { platform = value }
            compose.onNodeWithContentDescription("网页版").assertDoesNotExist()
            compose.onAllNodes(hasContentDescription("客户端", substring = true)).assertCountEquals(0)
        }
        compose.runOnIdle { platform = "Windows" }
        compose.onNodeWithText("Windows").assertExists()
        compose.onNodeWithContentDescription("网页版").assertDoesNotExist()
    }

    @Test fun compactFloorKeepsTheSpokenFloorDescription() {
        compose.setContent { NagiTheme {
            ReaderPost(
                post = Post("7", 3, "测试作者", "8", "", "", listOf(ContentBlock.Text("简短回复。")), clientPlatform = "/"),
                preferences = ReadingPreferences(),
                isAuthor = false,
                title = null,
                onImage = {},
            )
        } }
        compose.onNode(hasText("#3") and hasContentDescription("第 3 楼")).assertExists()
        compose.onNodeWithContentDescription("网页版").assertExists()
        compose.onNodeWithText("[3 楼]").assertDoesNotExist()
    }
}
