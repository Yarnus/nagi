package dev.nagi.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import dev.nagi.app.data.AccountManager
import dev.nagi.app.viewmodel.*
import dev.nagi.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class DraftImageUploadTest {
    private val dispatcher = StandardTestDispatcher()
    private val attachment = UploadedAttachment("aid~one", "check", "./mon_202611/one.jpg")
    private fun image() = ImageUpload(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0))
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()
    private suspend fun accounts() = AccountManager(MemorySessionStore().apply { saveSession(NgaSession("7", "test")) }, FakeBrowserCookies()).also { it.initialize() }
    private fun client(published: (PublishRequest) -> Unit = {}) = object : NgaClient by FakeClient() {
        override suspend fun preparePublish(target: PublishTarget) = PublishPreparation("", "Context")
        override suspend fun uploadImage(target: PublishTarget, image: ImageUpload) = ImageUploadResult(WriteResult.Confirmed("OK"), attachment)
        override suspend fun publish(request: PublishRequest): WriteResult { published(request); return WriteResult.Confirmed("OK") }
    }

    @Test fun newTopicInsertsAtSelectionRestoresChecksAndPublishesAttachment() = runTest(dispatcher) {
        val accounts = accounts()
        val saved = SavedStateHandle()
        var published: PublishRequest? = null
        val client = client { published = it }
        val vm = ComposerViewModel(client, accounts, saved)
        vm.open(PublishTarget(PublishAction.NEW_TOPIC, "7"), "New topic")
        vm.details("Title", "")
        vm.editValue(TextFieldValue("Before REPLACE after", TextRange(14, 7)))
        vm.uploadImage { image() }; runCurrent()
        assertEquals("Before ${attachment.bbcode} after", vm.state.value.content)
        assertFalse(vm.state.value.images.uploading)
        val restored = ComposerViewModel(client, accounts, saved)
        assertEquals("check", restored.state.value.images.attachments.single().check)
        restored.send(); runCurrent()
        assertEquals("aid~one", published!!.attachments.single().name)
    }

    @Test fun repliesPreserveQuoteAndDiscardRemovedImageFromSubmission() = runTest(dispatcher) {
        var published: PublishRequest? = null
        val vm = ReplySessionViewModel(client { published = it }, accounts(), SavedStateHandle())
        vm.open(PublishTarget(PublishAction.QUOTE, topicId = "123", postId = "4"), "Reply"); runCurrent()
        vm.edit(TextFieldValue("Reply"))
        vm.uploadImage { image() }; runCurrent()
        assertTrue(vm.session.value!!.content.startsWith("Context"))
        assertTrue(vm.session.value!!.content.contains(attachment.bbcode))
        vm.edit(TextFieldValue("Removed image"))
        vm.send(); runCurrent()
        assertTrue(published!!.attachments.isEmpty())
    }

    @Test fun submittingAndClosingAreBlockedUntilUploadFinishes() = runTest(dispatcher) {
        val gate = CompletableDeferred<ImageUploadResult>()
        var uploads = 0
        val client = object : NgaClient by client() {
            override suspend fun uploadImage(target: PublishTarget, image: ImageUpload): ImageUploadResult { uploads++; return gate.await() }
        }
        val vm = ReplySessionViewModel(client, accounts(), SavedStateHandle())
        vm.open(PublishTarget(PublishAction.REPLY, topicId = "123"), "Reply"); runCurrent()
        vm.edit(TextFieldValue("Text"))
        vm.uploadImage { image() }; runCurrent()
        assertFalse(vm.session.value!!.canSend)
        vm.uploadImage { image() }; vm.close(); runCurrent()
        assertNotNull(vm.session.value)
        assertEquals(1, uploads)
        gate.complete(ImageUploadResult(WriteResult.Confirmed("OK"), attachment)); runCurrent()
        assertTrue(vm.session.value!!.canSend)
    }

    @Test fun unconfirmedUploadCannotReplayAfterReopeningButRejectedAndInvalidInputCanRetry() = runTest(dispatcher) {
        var result = ImageUploadResult(WriteResult.Rejected(NgaException.Business(NgaRejectionReason.CONTENT_LENGTH)))
        var uploads = 0
        val client = object : NgaClient by client() {
            override suspend fun uploadImage(target: PublishTarget, image: ImageUpload): ImageUploadResult { uploads++; return result }
        }
        val saved = SavedStateHandle()
        val vm = ReplySessionViewModel(client, accounts(), saved)
        val target = PublishTarget(PublishAction.REPLY, topicId = "123")
        vm.open(target, "Reply"); runCurrent()
        vm.uploadImage { throw IllegalArgumentException() }; runCurrent()
        assertEquals(0, uploads)
        assertFalse(vm.session.value!!.images.blocked)
        vm.uploadImage { image() }; runCurrent()
        assertFalse(vm.session.value!!.images.blocked)
        result = ImageUploadResult(WriteResult.Unconfirmed())
        vm.uploadImage { image() }; runCurrent()
        assertTrue(vm.session.value!!.images.blocked)
        vm.close(); vm.open(target, "Reply"); runCurrent()
        vm.uploadImage { image() }; runCurrent()
        assertEquals(2, uploads)
        assertTrue(vm.session.value!!.images.blocked)
    }

    @Test fun lateUploadCannotInsertIntoAnotherAccount() = runTest(dispatcher) {
        val store = MemorySessionStore().apply { saveSession(NgaSession("7", "test")); saveSession(NgaSession("8", "other")); selectAccount("7") }
        val accounts = AccountManager(store, FakeBrowserCookies()).also { it.initialize() }
        val gate = CompletableDeferred<Unit>()
        val client = object : NgaClient by client() {
            override suspend fun uploadImage(target: PublishTarget, image: ImageUpload): ImageUploadResult {
                withContext(NonCancellable) { gate.await() }
                return ImageUploadResult(WriteResult.Confirmed("OK"), attachment)
            }
        }
        val vm = ReplySessionViewModel(client, accounts, SavedStateHandle())
        vm.open(PublishTarget(PublishAction.REPLY, topicId = "123"), "Reply"); runCurrent()
        vm.uploadImage { image() }; runCurrent()
        accounts.switchAccount("8"); runCurrent()
        gate.complete(Unit); runCurrent()
        assertNull(vm.session.value)
    }
}
