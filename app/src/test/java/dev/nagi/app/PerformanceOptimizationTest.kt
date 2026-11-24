package dev.nagi.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.SuccessResult
import dev.nagi.app.ui.components.LocalHardwareBlurAllowed
import dev.nagi.app.ui.components.LocalNagiBlurEnabled
import dev.nagi.app.ui.components.ProgressiveBlurScaffold
import dev.nagi.app.ui.components.UserAvatar
import dev.nagi.app.ui.components.hardwareBlurAllowed
import dev.nagi.app.ui.post.contentTextColor
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.sdk.ContentColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PerformanceOptimizationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun userAvatarHidesMonogramOnSuccessAndRestoresOnError() {
        val app = RuntimeEnvironment.getApplication()
        var failNext = false
        val drawable = BitmapDrawable(app.resources, Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888))
        val loader = ImageLoader.Builder(app).components {
            add(Interceptor { chain ->
                if (failNext) ErrorResult(null, chain.request, IllegalStateException("Unavailable"))
                else SuccessResult(drawable, chain.request, DataSource.MEMORY)
            })
        }.build()
        Coil.setImageLoader(loader)
        var url by mutableStateOf("https://img.nga.cn/avatars/ok.png")
        try {
            compose.setContent {
                NagiTheme("light") {
                    UserAvatar(name = "艾泽拉斯", avatarUrl = url, uid = "7")
                }
            }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("艾").fetchSemanticsNodes().isEmpty()
            }
            failNext = true
            compose.runOnIdle { url = "https://img.nga.cn/avatars/bad.png" }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("艾").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("艾").assertIsDisplayed()
        } finally {
            loader.shutdown()
            Coil.reset()
        }
    }

    @Test
    fun localHardwareBlurAllowedOverridesScaffoldBlurState() {
        var allowed by mutableStateOf(false)
        var resolved = true
        var scaffoldBlur = true
        compose.setContent {
            NagiTheme("light") {
                CompositionLocalProvider(LocalHardwareBlurAllowed provides allowed) {
                    resolved = hardwareBlurAllowed()
                    ProgressiveBlurScaffold(
                        topBar = { Spacer(Modifier.fillMaxWidth().height(48.dp)) },
                    ) {
                        scaffoldBlur = LocalNagiBlurEnabled.current
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(false, resolved)
            assertEquals(false, scaffoldBlur)
            allowed = true
        }
        compose.runOnIdle {
            assertEquals(true, resolved)
            assertEquals(true, scaffoldBlur)
        }
    }

    @Test
    fun contentTextColorCachePreservesExactColorAcrossRepeatedCalls() {
        for (bg in listOf(Color.White, Color(0xFF18191D))) {
            for (name in ContentColors.rgb.keys) {
                val first = contentTextColor(name, bg)
                val second = contentTextColor(name, bg)
                assertEquals(first, second)
            }
        }
    }
}
