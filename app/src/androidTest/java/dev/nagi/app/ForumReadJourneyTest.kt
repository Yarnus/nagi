package dev.nagi.app

import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.ExternalResource
import java.io.File

@RunWith(AndroidJUnit4::class)
class ForumReadJourneyTest {
    @get:Rule(order = 0) val optIn = object : ExternalResource() {
        override fun before() {
            assumeTrue(InstrumentationRegistry.getArguments().getString("nagiForumJourney") == "true")
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private val reader = hasContentDescription("主题操作")
    private val pagination = SemanticsMatcher("reader page control") {
        it.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("第 ") == true
    }
    private val readerError = hasTestTag("reader-error")

    private fun exists(matcher: SemanticsMatcher): Boolean =
        compose.onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(40_000) { exists(matcher) }
    }

    private fun errorCode(): String {
        val text = compose.onAllNodes(readerError or hasAnyAncestor(readerError), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap {
            it.config.getOrNull(SemanticsProperties.Text).orEmpty()
        }.joinToString(" ") { it.text }
        return Regex("P-\\d+(?:>P-\\d+)*").find(text)?.value ?: when {
            text.contains("请先登录") -> "authentication"
            text.contains("限制") -> "restricted"
            text.contains("暂时不可用") -> "http"
            text.contains("未找到") -> "not-found"
            text.contains("拒绝") -> "business"
            else -> "network"
        }
    }

    @Test fun browseFirstFiftyTopics() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "forum-read-journey").apply { mkdirs() }
        val journal = JSONArray()
        val visited = linkedSetOf<String>()
        val topicRow = SemanticsMatcher("topic row") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("topic-") == true
        }
        waitFor(hasContentDescription("搜索"))
        waitFor(hasText("网事杂谈"))
        compose.onAllNodesWithText("网事杂谈").onLast().performSemanticsAction(SemanticsActions.OnClick) { it() }
        waitFor(hasContentDescription("搜索当前版面"))
        var restricted = 0
        for (index in 0 until 50) {
            var tag: String? = null
            for (attempt in 1..10) {
                try {
                    compose.onNode(hasScrollToIndexAction()).performScrollToIndex(index + 1)
                    tag = compose.onAllNodes(topicRow).fetchSemanticsNodes()
                        .filter { it.boundsInRoot.height > 0 }
                        .sortedBy { it.boundsInRoot.top }
                        .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
                        .firstOrNull { it !in visited }
                    if (tag != null) break
                } catch (_: IllegalArgumentException) {
                }
                Thread.sleep(1000)
            }
            assertTrue("No next topic after pagination", tag != null)
            val topicId = requireNotNull(tag).removePrefix("topic-")
            visited.add(requireNotNull(tag))
            val record = JSONObject().put("index", index + 1).put("topicId", topicId)
            run {
                compose.onNodeWithTag(requireNotNull(tag)).performSemanticsAction(SemanticsActions.OnClick) { it() }
                compose.waitUntil(40_000) { exists(reader) || exists(readerError) }
                if (exists(readerError)) {
                    val firstError = errorCode()
                    record.put("firstError", firstError)
                    if (firstError == "restricted") restricted++ else restricted = 0
                    instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                        File(output, "failure-${index + 1}-$topicId.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    }
                    compose.onNode(hasText("重试") and hasAnyAncestor(readerError)).performClick()
                    compose.waitUntil(40_000) { exists(reader) || exists(readerError) }
                    record.put("result", if (exists(readerError)) "retry-error:${errorCode()}" else "retry-ok")
                } else {
                    restricted = 0
                    record.put("result", "ok")
                }
                if (exists(reader)) {
                    compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp(durationMillis = 650) }
                    compose.waitForIdle()
                    record.put("scroll", if (exists(readerError)) "error:${errorCode()}" else "ok")
                    record.put("paginationControl", exists(pagination))
                }
                compose.onNodeWithContentDescription("返回").performClick()
                waitFor(hasContentDescription("搜索当前版面"))
            }
            journal.put(record)
            File(output, "results.json").writeText(journal.toString(2))
            File(output, "snapshot.json").writeText(JSONArray(visited.map { it.removePrefix("topic-") }).toString())
            instrumentation.sendStatus(2, Bundle().apply { putString("nagi_forum_journey", record.toString()) })
            assertTrue("Stop on repeated server restrictions", restricted < 2)
        }
        assertEquals(50, journal.length())
        assertTrue("Read errors found; inspect the sanitized journey journal", (0 until journal.length()).all {
            val record = journal.getJSONObject(it)
            record.getString("result") == "ok" && record.optString("scroll") == "ok" &&
                record.optBoolean("paginationControl")
        })
    }
}
