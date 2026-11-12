package dev.nagi.app

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import dev.nagi.app.data.AccountManager
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.post.ReaderScreen
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.viewmodel.*
import dev.nagi.sdk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h850dp")
class ReaderWriteRefreshTest {
    @get:Rule val compose = createComposeRule()

    @Test fun commentSuccessClosesPopupAndRefreshesWithoutConfirmation() = check(PublishAction.COMMENT, WriteResult.Confirmed("Accepted"))
    @Test fun replySuccessClosesPopupAndRefreshesWithoutConfirmation() = check(PublishAction.REPLY, WriteResult.Confirmed("Accepted"))
    @Test fun rejectedCommentKeepsDraftWithoutRefreshing() = check(PublishAction.COMMENT, WriteResult.Rejected(NgaException.Restricted()))
    @Test fun uncertainReplyKeepsDraftWithoutRefreshing() = check(PublishAction.REPLY, WriteResult.Unconfirmed())

    private fun check(action: PublishAction, result: WriteResult) {
        val sessions = MemorySessionStore()
        val accounts = runBlocking {
            sessions.saveSession(NgaSession("7", "test"))
            AccountManager(sessions, FakeBrowserCookies()).also { it.initialize() }
        }
        var reads = 0
        var writes = 0
        var sentPage = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun getPosts(topicId: String, page: Int, authorId: String?): PostPage {
                reads++
                sentPage = page
                return PostPage(listOf(Post("12", 3, "Author", "7", "", "", listOf(ContentBlock.Text("Read $reads")))),
                    "Title", "7", page, 10, false, 10, 1)
            }
            override suspend fun preparePublish(target: PublishTarget) = PublishPreparation("", "")
            override suspend fun publish(request: PublishRequest): WriteResult { writes++; return result }
        }
        val reader = ReaderViewModel(client, FakeDaoProvider(), sessions, accounts)
        val replies = ReplySessionViewModel(client, accounts, SavedStateHandle())
        val ratings = PostRatingViewModel(client, accounts, SavedStateHandle())
        var appearance by mutableStateOf("light")
        compose.setContent { NagiTheme(appearance) {
            ReaderScreen("123", ReadingPreferences(), {}, reader, replies, ratings)
        } }
        compose.onNodeWithText("Read 1").assertExists()
        compose.runOnIdle { reader.load(page = 3) }
        compose.onNodeWithText("Read 2").assertExists()
        compose.runOnIdle { replies.open(PublishTarget(action, topicId = "123", postId = "12"), "Synthetic reply") }
        compose.waitUntil { replies.session.value?.phase == ComposePhase.Editing }
        compose.runOnIdle { replies.edit(TextFieldValue("Draft")); replies.send() }
        if (result is WriteResult.Confirmed) {
            compose.onNodeWithText("Read 3").assertExists()
            compose.runOnIdle { assertNull(replies.session.value); assertEquals(3, sentPage) }
        } else {
            compose.onNodeWithText("Read 2").assertExists()
            compose.runOnIdle { assertEquals("Draft", replies.session.value!!.input.text); assertEquals(2, reads) }
        }
        compose.onNodeWithText("刷新帖子").assertDoesNotExist()
        compose.onNodeWithText("继续阅读").assertDoesNotExist()
        compose.runOnIdle { appearance = "dark" }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(if (result is WriteResult.Confirmed) 3 else 2, reads); assertEquals(1, writes) }
    }
}
