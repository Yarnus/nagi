package dev.nagi.app

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nagi.app.data.AndroidSessionStore
import dev.nagi.app.data.SessionStorageException
import dev.nagi.sdk.NgaSession
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class SecureSessionStoreTest {
    @Test fun keystoreSessionsReopenWithoutPlaintextTokens() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "session-test-reopen"
        context.preferencesDataStoreFile(name).delete()
        var store = AndroidSessionStore(context, name)
        try {
            store.saveSession(NgaSession("7", "test-token-seven"))
            store.saveSession(NgaSession("8", "test-token-eight"))
            store.selectAccount("7")
            val revision = store.snapshot().revision
            store.close()
            val bytes = context.preferencesDataStoreFile(name).readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(bytes.contains("test-token-seven"))
            assertFalse(bytes.contains("test-token-eight"))
            store = AndroidSessionStore(context, name)
            assertEquals("7", store.snapshot().session!!.userId)
            assertEquals(setOf("7", "8"), store.getAccounts().toSet())
            assertEquals(revision, store.snapshot().revision)
            store.clearSession()
            assertNull(store.snapshot().session)
            assertEquals(listOf("8"), store.getAccounts())
            assertTrue(store.snapshot().revision > revision)
        } finally {
            store.close()
            context.preferencesDataStoreFile(name).delete()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("dev.nagi.sessions.$name") }
        }
    }
    @Test fun corruptedCiphertextFailsClosedWithoutSecretDiagnostics() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "session-test-corrupt"
        context.preferencesDataStoreFile(name).delete()
        var store = AndroidSessionStore(context, name)
        try {
            store.saveSession(NgaSession("7", "test-private-token"))
            store.close()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val editor = PreferenceDataStoreFactory.create(scope = scope) { context.preferencesDataStoreFile(name) }
            editor.edit {
                val key = stringPreferencesKey("encrypted-session-v1")
                val bytes = Base64.decode(requireNotNull(it[key]), Base64.NO_WRAP)
                bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
                it[key] = Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
            scope.coroutineContext.job.cancelAndJoin()
            store = AndroidSessionStore(context, name)
            val error = runCatching { store.snapshot() }.exceptionOrNull()
            assertTrue(error is SessionStorageException)
            assertFalse(error.toString().contains("test-private-token"))
            assertNull(error!!.cause)
        } finally {
            store.close()
            context.preferencesDataStoreFile(name).delete()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("dev.nagi.sessions.$name") }
        }
    }
}
