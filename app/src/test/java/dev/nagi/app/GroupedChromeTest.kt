package dev.nagi.app

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.nagi.app.ui.components.CardGroupPosition
import dev.nagi.app.ui.components.ProgressiveBlurScaffold
import dev.nagi.app.ui.components.SectionCardItem
import dev.nagi.app.ui.theme.NagiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class GroupedChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun positionsPreserveSectionOuterCorners() {
        assertEquals(CardGroupPosition.Single, CardGroupPosition.at(0, 1))
        assertEquals(CardGroupPosition.First, CardGroupPosition.at(0, 3))
        assertEquals(CardGroupPosition.Middle, CardGroupPosition.at(1, 3))
        assertEquals(CardGroupPosition.Last, CardGroupPosition.at(2, 3))
    }

    @Test fun preAndroid12UsesGradientWithoutLosingContentOrTouchTargets() {
        compose.setContent {
            NagiTheme("dark") {
                ProgressiveBlurScaffold(
                    topBar = { Text("顶栏", Modifier.height(56.dp)) },
                    bottomBar = { Text("底栏", Modifier.height(64.dp)) },
                ) { padding ->
                    Column(Modifier.padding(padding)) {
                        SectionCardItem(CardGroupPosition.Single) { Text("分组内容") }
                    }
                }
            }
        }
        compose.onNodeWithText("顶栏").assertIsDisplayed()
        compose.onNodeWithText("底栏").assertIsDisplayed()
        compose.onNodeWithText("分组内容").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35])
    fun powerSaveRetainsChrome() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsPowerSaveMode(true)
        compose.setContent {
            NagiTheme {
                ProgressiveBlurScaffold(topBar = { Text("节能顶栏") }) {
                    Text("内容保留", Modifier.padding(it))
                }
            }
        }
        compose.onNodeWithText("内容保留").assertIsDisplayed()
        compose.onNodeWithText("节能顶栏").assertIsDisplayed()
    }
}
