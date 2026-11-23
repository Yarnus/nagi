package dev.nagi.app

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.components.formatAuthorDisplayName
import dev.nagi.app.ui.components.isAnonymousHandle
import dev.nagi.app.ui.post.ReaderPost
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.sdk.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuthorDisplayNameTest {
    @get:Rule val compose = createComposeRule()

    @Test fun formatAuthorDisplayNameShortensAnonymousHandles() {
        assertEquals("匿名", formatAuthorDisplayName(null))
        assertEquals("匿名", formatAuthorDisplayName(""))
        assertEquals("匿名", formatAuthorDisplayName("   "))
        assertEquals("匿名", formatAuthorDisplayName("#anony_"))
        assertEquals("匿名 · ab", formatAuthorDisplayName("#anony_ab"))
        assertEquals(
            "匿名 · e3b0",
            formatAuthorDisplayName("#anony_e3b0c44298fc1c149afbf4c8996fb924"),
        )
        assertEquals("普通用户", formatAuthorDisplayName("  普通用户  "))

        assertTrue(isAnonymousHandle("#anony_e3b0c44298fc1c149afbf4c8996fb924"))
        assertFalse(isAnonymousHandle("普通用户"))
        assertFalse(isAnonymousHandle(""))
    }

    @Test fun readerPostAndAvatarRenderShortAnonymousLabelAndMonogram() {
        val handle = "#anony_e3b0c44298fc1c149afbf4c8996fb924"
        compose.setContent {
            NagiTheme("light") {
                ReaderPost(
                    post = Post("1", 0, handle, handle, "", "2026-11-23 16:30", emptyList()),
                    preferences = ReadingPreferences(),
                    isAuthor = false,
                    title = null,
                    onImage = {},
                )
            }
        }

        compose.onNodeWithText("匿名 · e3b0").assertIsDisplayed()
        compose.onNodeWithText(handle).assertDoesNotExist()
        compose.onNodeWithText("匿").assertIsDisplayed()
        compose.onNodeWithText("#").assertDoesNotExist()
    }
}
