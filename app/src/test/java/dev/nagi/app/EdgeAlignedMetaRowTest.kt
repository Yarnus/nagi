package dev.nagi.app

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import dev.nagi.app.ui.components.EdgeAlignedMetaRow
import dev.nagi.app.ui.theme.NagiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class EdgeAlignedMetaRowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun missingTrailingMetadataDoesNotCrash() {
        compose.setContent {
            NagiTheme("light") {
                Box(Modifier.width(320.dp)) {
                    EdgeAlignedMetaRow(leading = { Text("查看主题 · 回复 123") }, trailing = {})
                }
            }
        }
        compose.onNodeWithText("查看主题 · 回复 123").assertIsDisplayed()
    }

    @Test fun missingLeadingMetadataDoesNotCrash() {
        compose.setContent {
            NagiTheme("dark") {
                Box(Modifier.width(320.dp)) {
                    EdgeAlignedMetaRow(leading = {}, trailing = { Text("时间") })
                }
            }
        }
        compose.onNodeWithText("时间").assertIsDisplayed()
    }
}
