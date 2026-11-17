package dev.nagi.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.nagi.app.ui.components.ProgressiveBlurScaffold
import dev.nagi.app.ui.theme.NagiTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31)
class ProgressiveBlurTest {
    @get:Rule val compose = createComposeRule()

    @Test fun edgeStripsBlurWithoutBlurringBodyOrCapturingChrome() {
        var enabled by mutableStateOf(true)
        var phase by mutableIntStateOf(0)
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            NagiTheme("light") {
                Box(Modifier.fillMaxSize()) {
                    ProgressiveBlurScaffold(
                        blurEnabled = enabled,
                        topBar = { Spacer(Modifier.fillMaxWidth().height(56.dp)) },
                        bottomBar = { Spacer(Modifier.fillMaxWidth().height(64.dp)) },
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stripe = 8.dp.toPx()
                            val colorPhase = phase
                            for (x in 0..(size.width / stripe).toInt()) {
                                drawRect(
                                    if (colorPhase == 2) Color.Red else if ((x + colorPhase) % 2 == 0) Color.Black else Color.White,
                                    topLeft = Offset(x * stripe, 0f),
                                    size = Size(stripe, size.height),
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val blurred = compose.onRoot().captureToImage()
        compose.runOnIdle { enabled = false }
        compose.waitForIdle()
        val fallback = compose.onRoot().captureToImage()
        fun variation(image: androidx.compose.ui.graphics.ImageBitmap, y: Int): Float {
            val pixels = image.toPixelMap()
            val samples = (20 until image.width - 20).map { pixels[it, y].red }
            return requireNotNull(samples.maxOrNull()) - requireNotNull(samples.minOrNull())
        }
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "grouped-ui")
        output.mkdirs()
        listOf("gpu-blur" to blurred, "gradient-fallback" to fallback).forEach { (name, image) ->
            File(output, "$name.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val topY = (24 * density).toInt()
        val topMidY = (50 * density).toInt()
        val bottomY = blurred.height - (28 * density).toInt()
        val bottomMidY = blurred.height - (70 * density).toInt()
        val bodyY = blurred.height / 2
        val topBlurVar = variation(blurred, topY)
        val topMidVar = variation(blurred, topMidY)
        val bottomBlurVar = variation(blurred, bottomY)
        val bottomMidVar = variation(blurred, bottomMidY)
        val bodyVar = variation(blurred, bodyY)
        android.util.Log.i(
            "NagiBlurTest",
            "top=$topBlurVar/${variation(fallback, topY)}, topMid=$topMidVar, bottom=$bottomBlurVar/${variation(fallback, bottomY)}, bottomMid=$bottomMidVar, body=$bodyVar",
        )
        assertTrue("Top strip must soften the source", topBlurVar < variation(fallback, topY) * .9f)
        assertTrue("Bottom strip must soften the source", bottomBlurVar < variation(fallback, bottomY) * .9f)
        assertTrue("Body must remain sharp", bodyVar > .9f)
        assertTrue(
            "Top transition ($topMidVar) must sit strictly between blurred top ($topBlurVar) and sharp body ($bodyVar)",
            topMidVar > topBlurVar && topMidVar < bodyVar * .95f,
        )
        assertTrue(
            "Bottom transition ($bottomMidVar) must sit strictly between blurred bottom ($bottomBlurVar) and sharp body ($bodyVar)",
            bottomMidVar > bottomBlurVar && bottomMidVar < bodyVar * .95f,
        )

        compose.runOnIdle { enabled = true; phase = 1 }
        compose.waitForIdle()
        val updated = compose.onRoot().captureToImage()
        val middleX = (40 * density).toInt()
        assertTrue("Source must update", blurred.toPixelMap()[middleX, bodyY] != updated.toPixelMap()[middleX, bodyY])
        compose.runOnIdle { phase = 2 }
        compose.waitForIdle()
        val changedStrip = compose.onRoot().captureToImage().toPixelMap()[middleX, topY]
        assertTrue("A draw-only source change must invalidate the replayed strip", changedStrip.red > changedStrip.green + .05f)
    }
}
