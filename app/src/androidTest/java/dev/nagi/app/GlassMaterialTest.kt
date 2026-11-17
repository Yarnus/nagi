package dev.nagi.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import dev.nagi.app.ui.components.FrostedSurface
import dev.nagi.app.ui.components.ProgressiveBlurScaffold
import dev.nagi.app.ui.theme.DarkSemanticColors
import dev.nagi.app.ui.theme.LightSemanticColors
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.ui.theme.NagiThemeTokens
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31)
class GlassMaterialTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun capsuleTextMeetsContrastOverWhiteBlackAndSaturatedColorsInBothThemesAndFallback() {
        var theme by mutableStateOf("light")
        var blurEnabled by mutableStateOf(true)
        var backdrop by mutableStateOf(Color.White)
        var textColor = Color.Unspecified
        var goldColor = Color.Unspecified

        compose.setContent {
            NagiTheme(theme) {
                textColor = NagiThemeTokens.colors.textPrimary
                goldColor = NagiThemeTokens.colors.goldText
                ProgressiveBlurScaffold(
                    blurEnabled = blurEnabled,
                    topBar = {},
                    bottomBar = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            FrostedSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("glass-capsule"),
                                shape = CircleShape,
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("测试胶囊标题", color = NagiThemeTokens.colors.textPrimary)
                                }
                            }
                        }
                    },
                ) {
                    Box(Modifier.fillMaxSize().background(backdrop))
                }
            }
        }

        val saturatedBackdrops = listOf(
            "white" to Color.White,
            "black" to Color.Black,
            "red" to Color(0xFFE11D48),
            "blue" to Color(0xFF2563EB),
            "green" to Color(0xFF16A34A),
            "yellow" to Color(0xFFFACC15),
        )

        for (appearance in listOf("light", "dark")) {
            val canvasColor = if (appearance == "dark") DarkSemanticColors.groupedCanvas else LightSemanticColors.groupedCanvas
            for (blur in listOf(true, false)) {
                val standardBackdrops = saturatedBackdrops + ("groupedCanvas" to canvasColor)
                for ((bgName, bgColor) in standardBackdrops) {
                    compose.runOnIdle {
                        theme = appearance
                        blurEnabled = blur
                        backdrop = bgColor
                    }
                    compose.waitForIdle()
                    val bounds = compose.onNodeWithTag("glass-capsule").getUnclippedBoundsInRoot()
                    val image = compose.onRoot().captureToImage()
                    val pm = image.toPixelMap()
                    val density = image.width.toFloat() / compose.onRoot().getUnclippedBoundsInRoot().right.value
                    val sampleX = ((bounds.left.value + 16f) * density).toInt().coerceIn(0, image.width - 1)
                    val sampleY = (((bounds.top.value + bounds.bottom.value) / 2f) * density).toInt().coerceIn(0, image.height - 1)
                    val compositedBg = pm[sampleX, sampleY]
                    val primaryContrast = contrast(textColor, compositedBg)
                    assertTrue(
                        "$appearance blur=$blur over $bgName primary contrast=$primaryContrast must be >= 4.5 (bg=$compositedBg, fg=$textColor)",
                        primaryContrast >= 4.5f,
                    )
                    val checkGold = bgName == "groupedCanvas" ||
                        (appearance == "light" && bgName == "white") ||
                        (appearance == "dark" && bgName == "black")
                    if (checkGold) {
                        val goldContrast = contrast(goldColor, compositedBg)
                        assertTrue(
                            "$appearance blur=$blur over $bgName goldText contrast=$goldContrast must be >= 4.5 (bg=$compositedBg, fg=$goldColor)",
                            goldContrast >= 4.5f,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun capsuleBackdropActuallyBlursStripedContentAndFallsBackCleanly() {
        var blurEnabled by mutableStateOf(true)
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            NagiTheme("light") {
                ProgressiveBlurScaffold(
                    blurEnabled = blurEnabled,
                    topBar = {},
                    bottomBar = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            FrostedSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("glass-capsule"),
                                shape = CircleShape,
                            ) {
                                Box(Modifier.fillMaxSize())
                            }
                        }
                    },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stripe = 8.dp.toPx()
                        for (x in 0..(size.width / stripe).toInt()) {
                            drawRect(
                                color = if (x % 2 == 0) Color.Black else Color.White,
                                topLeft = Offset(x * stripe, 0f),
                                size = Size(stripe, size.height),
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val blurredImg = compose.onRoot().captureToImage()
        val bounds = compose.onNodeWithTag("glass-capsule").getUnclippedBoundsInRoot()
        val y = (((bounds.top.value + bounds.bottom.value) / 2f) * density).toInt()
        val left = ((bounds.left.value + 24f) * density).toInt()
        val right = ((bounds.right.value - 24f) * density).toInt()
        val blurredPm = blurredImg.toPixelMap()
        val blurredSamples = (left until right).map { blurredPm[it, y].red }
        val blurredVar = (blurredSamples.maxOrNull() ?: 0f) - (blurredSamples.minOrNull() ?: 0f)
        assertTrue("Capsule blur should smooth 8dp stripes (var=$blurredVar)", blurredVar < 0.15f)

        compose.runOnIdle { blurEnabled = false }
        compose.waitForIdle()
        val fallbackImg = compose.onRoot().captureToImage()
        val fallbackPm = fallbackImg.toPixelMap()
        val fallbackSamples = (left until right).map { fallbackPm[it, y].red }
        val fallbackVar = (fallbackSamples.maxOrNull() ?: 0f) - (fallbackSamples.minOrNull() ?: 0f)
        assertTrue("Fallback capsule must be opaque and uniform (var=$fallbackVar)", fallbackVar < 0.05f)
    }

    private fun contrast(a: Color, b: Color): Float {
        val l1 = a.luminance()
        val l2 = b.luminance()
        return (maxOf(l1, l2) + 0.05f) / (minOf(l1, l2) + 0.05f)
    }
}
