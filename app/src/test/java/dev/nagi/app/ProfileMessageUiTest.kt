package dev.nagi.app

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.nagi.app.data.AccountManager
import dev.nagi.app.data.ReadingPreferences
import dev.nagi.app.ui.post.NewMessageScreen
import dev.nagi.app.ui.theme.NagiTheme
import dev.nagi.app.viewmodel.MessagesViewModel
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
class ProfileMessageUiTest {
    @get:Rule val compose = createComposeRule()

    private fun account() = runBlocking {
        AccountManager(MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }, FakeBrowserCookies()).also { it.initialize() }
    }

    @Test fun discardReturnsToCallerWithoutFetchingInbox() {
        var reads = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun getMessageConversations(page: Int): MessageConversationPage {
                reads++
                return MessageConversationPage(emptyList(), page, false)
            }
        }
        val vm = MessagesViewModel(client, account())
        var returned = 0
        compose.setContent { NagiTheme { NewMessageScreen("目标用户", ReadingPreferences(), { returned++ }, vm) } }
        compose.onNodeWithText("新建私信").assertIsDisplayed()
        compose.runOnIdle { vm.edit("草稿", 2) }
        compose.onNodeWithContentDescription("关闭新建私信").performClick()
        compose.onNodeWithText("继续编辑").performClick()
        compose.runOnIdle { assertEquals(0, returned) }
        compose.onNodeWithContentDescription("关闭新建私信").performClick()
        compose.onNodeWithText("放弃并返回").performClick()
        compose.runOnIdle {
            assertEquals(1, returned)
            assertEquals(0, reads)
        }
    }

    @Test fun prefilledRecipientAloneClosesWithoutConfirmation() {
        val vm = MessagesViewModel(FakeClient(), account())
        var returned = 0
        compose.setContent { NagiTheme { NewMessageScreen("目标用户", ReadingPreferences(), { returned++ }, vm) } }
        compose.onNodeWithText("收件人").assertExists()
        compose.onNodeWithText("标题", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("发送给 目标用户").assertDoesNotExist()
        compose.onNodeWithContentDescription("关闭新建私信").performClick()
        compose.onNodeWithText("放弃未发送的私信？").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, returned) }
    }

    @Test fun changingOnlyRecipientRequiresConfirmation() {
        val vm = MessagesViewModel(FakeClient(), account())
        var returned = 0
        compose.setContent { NagiTheme { NewMessageScreen("目标用户", ReadingPreferences(), { returned++ }, vm) } }
        compose.onNodeWithText("新建私信").assertIsDisplayed()
        compose.runOnIdle { vm.details("", "另一用户") }
        compose.onNodeWithContentDescription("关闭新建私信").performClick()
        compose.onNodeWithText("放弃未发送的私信？").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, returned) }
    }

    @Test fun acceptedMessageReturnsToCallerOnceWithoutFetchingInbox() = submit(WriteResult.Confirmed("Accepted"), true)
    @Test fun uncertainMessageKeepsComposerAndDoesNotReturnOrReplay() = submit(WriteResult.Unconfirmed(), false)

    private fun submit(outcome: WriteResult, accepted: Boolean) {
        var reads = 0
        var writes = 0
        val client = object : NgaClient by FakeClient() {
            override suspend fun getMessageConversations(page: Int): MessageConversationPage { reads++; error("Inbox is not part of this route") }
            override suspend fun sendMessage(request: MessageRequest): WriteResult { writes++; return outcome }
        }
        val vm = MessagesViewModel(client, account())
        var returned = 0
        compose.setContent { NagiTheme { NewMessageScreen("目标用户", ReadingPreferences(), { returned++ }, vm) } }
        compose.onNodeWithText("新建私信").assertIsDisplayed()
        compose.runOnIdle { vm.details("合成标题", "目标用户"); vm.edit("合成正文", 4) }
        compose.onNodeWithText("发送", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(if (accepted) 1 else 0, returned)
            assertEquals(0, reads)
            assertEquals(1, writes)
        }
        if (!accepted) {
            compose.onNodeWithText("新建私信").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals("合成正文", vm.draft.value.content)
                assertFalse(vm.draft.value.canSend)
                vm.send()
                assertEquals(1, writes)
            }
        }
    }
}
