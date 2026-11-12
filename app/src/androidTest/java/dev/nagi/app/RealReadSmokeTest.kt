package dev.nagi.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.nagi.app.data.AccountDatabases
import dev.nagi.app.data.AndroidSessionStore
import dev.nagi.sdk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealReadSmokeTest {
    @Test fun previouslyTruncatedPostReadRecovery(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("nagiReadSmoke") == "true")
        val store = AndroidSessionStore(ApplicationProvider.getApplicationContext())
        try {
            assumeTrue("An encrypted device session is required", store.snapshot().session != null)
            val client = createNgaClient(NgaConfig(timeoutSeconds = 25), store)
            val first = client.getPosts("47856177")
            assertTrue(first.items.isNotEmpty() && first.title.isNotBlank() && first.hasMore)
            assertTrue(first.items.first().content.isNotEmpty())
            val next = client.getPosts("47856177", 2)
            assertTrue(next.page == 2 && next.items.isNotEmpty() && next.items.first().floor > first.items.last().floor)
            assertTrue(next.total == first.total && next.authorId == first.authorId)
            val authorOnly = client.getPosts("47856177", authorId = first.authorId)
            assertTrue(authorOnly.items.isNotEmpty() && authorOnly.items.all { it.authorId == first.authorId })
            InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
                putString("nagi_html_fallback_result", "Read recovery passed for first page, next page and author filter; response format not asserted; no content or credentials logged")
            })
        } finally { store.close() }
    }

    @Test fun authenticatedReadContracts(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("nagiReadSmoke") == "true")
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = AndroidSessionStore(context)
        val databases = AccountDatabases(context)
        try {
            val session = store.snapshot().session
            assumeTrue("A manually established encrypted session is required", session != null)
            val identity = requireNotNull(session)
            val result = JSONObject()
            val client = createNgaClient(NgaConfig(timeoutSeconds = 10), store)
            val checks = mutableMapOf<String, Boolean>()
            suspend fun checkRead(name: String, operation: suspend () -> Boolean) {
                checks[name] = try { operation() } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    result.put("${name}ErrorType", error.javaClass.simpleName)
                    false
                }
            }
            var directory = emptyList<Forum>()
            checkRead("directory") { directory = client.getForums(); directory.isNotEmpty() }
            checkRead("catalogScopedSearch") {
                val scope = directory.first { it.name == "网事杂谈" }
                client.searchTopics("Android", scope.id, collectionId = scope.collectionId).items.isNotEmpty()
            }
            val favorite = databases.forUser(identity.userId).favorites().first().firstOrNull()
            if (favorite != null) checkRead("savedFavoriteAuthorOnly") {
                val original = client.getPosts(favorite.topicId)
                client.getPosts(favorite.topicId, authorId = original.authorId).items.isNotEmpty()
            }
            checkRead("userProfile") { client.getUser(identity.userId).id == identity.userId }
            checkRead("scopedSearch") {
                val first = client.searchTopics("Android", "-7")
                val chinese = client.searchTopics("安卓", "-7")
                result.put("searchPage2Checked", first.hasMore)
                if (first.hasMore) {
                    val next = client.searchTopics("Android", "-7", 2)
                    first.items.isNotEmpty() && chinese.items.isNotEmpty() && next.page == 2 && next.items.isNotEmpty()
                } else first.items.isNotEmpty() && chinese.items.isNotEmpty()
            }
            checkRead("topicAndPostPages") {
                val topic = client.getTopics("-7").items.first {
                    it.replies > 30 && !it.recommended && (it.authorId.toLongOrNull() ?: 0) > 0
                }
                val first = client.getPosts(topic.id)
                val next = client.getPosts(topic.id, 2)
                val onlyAuthor = client.getPosts(topic.id, authorId = first.authorId)
                first.items.isNotEmpty() && next.items.isNotEmpty() && next.page == 2 &&
                    onlyAuthor.items.isNotEmpty() && onlyAuthor.items.all { it.authorId == first.authorId }
            }
            result.put("checks", JSONObject(checks as Map<*, *>))
            InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply {
                putString("nagi_read_only_result", result.toString())
            })
            assertTrue("Real read contracts failed; inspect only the redacted result summary", checks.values.all { it })
        } finally { databases.close(); store.close() }
    }
}
