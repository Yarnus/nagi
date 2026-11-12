package dev.nagi.app

import android.app.Application
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import dev.nagi.app.ui.theme.GoldAccent
import dev.nagi.app.ui.theme.NagiSemanticColors
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.ui.theme.NagiThemeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class GoldThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun goldTextAndSelectedControlsRemainReadableInBothThemes() {
        var theme by mutableStateOf("light")
        lateinit var colors: ColorScheme
        lateinit var tokens: NagiSemanticColors
        compose.setContent {
            NagiTheme(theme) {
                colors = MaterialTheme.colorScheme
                tokens = NagiThemeTokens.colors
            }
        }
        for (appearance in listOf("light", "dark")) {
            compose.runOnIdle { theme = appearance }
            compose.runOnIdle {
                assertEquals(if (appearance == "light") GoldAccent.lightText else GoldAccent.darkText, colors.primary)
                val backgrounds = listOf(colors.surface, colors.background, tokens.selectedRowFill,
                    tokens.selectedControlFill, tokens.chromeSurface.compositeOver(colors.background))
                backgrounds.forEach { background ->
                    assertReadable("$appearance accent on $background", colors.primary, background)
                }
                assertReadable("$appearance filled button", colors.onPrimary, colors.primary)
                assertReadable("$appearance selected label", tokens.selectedControlText, tokens.selectedControlFill)
                assertReadable("$appearance metadata", colors.onSurfaceVariant, colors.surface)
            }
        }
        assertTrue(contrast(GoldAccent.decorative, Color.White) < 4.5f)
    }

    private fun assertReadable(role: String, foreground: Color, background: Color) {
        assertTrue("$role must reach 4.5:1", contrast(foreground.compositeOver(background), background) >= 4.5f)
    }

    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
    }
}
