package dev.nagi.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.nagi.sdk.NgaSession
import dev.nagi.sdk.SessionSnapshot
import dev.nagi.sdk.SessionStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidSessionStore(context: Context, fileName: String = "nga-sessions") : SessionStore {
    init { require(fileName.matches(Regex("[a-zA-Z0-9_-]+"))) }
    private val applicationContext = context.applicationContext
    private val alias = "dev.nagi.sessions.$fileName"
    private val encrypted = stringPreferencesKey("encrypted-session-v1")
    private val job = SupervisorJob()
    private val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) {
        applicationContext.preferencesDataStoreFile(fileName)
    }
    private class Accounts(val revision: Long, val active: String?, val sessions: Map<String, NgaSession>)

    override suspend fun snapshot(): SessionSnapshot = access {
        val accounts = decode(dataStore.data.first())
        SessionSnapshot(accounts.revision, accounts.active?.let { accounts.sessions.getValue(it) })
    }
    override suspend fun getAccounts(): List<String> = access { decode(dataStore.data.first()).sessions.keys.toList() }
    override suspend fun saveSession(session: NgaSession) = update { old ->
        Accounts(old.revision + 1, session.userId, old.sessions + (session.userId to session))
    }
    override suspend fun selectAccount(userId: String) = update { old ->
        require(userId in old.sessions) { "Unknown account" }
        Accounts(old.revision + 1, userId, old.sessions)
    }
    override suspend fun clearSession() = update { old ->
        Accounts(old.revision + 1, null, old.sessions - listOfNotNull(old.active).toSet())
    }
    private suspend fun update(transform: (Accounts) -> Accounts) {
        access { dataStore.edit { preferences -> preferences[encrypted] = encode(transform(decode(preferences))) } }
    }
    private suspend fun <T> access(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try { block() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { throw SessionStorageException() }
    }
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "Credential key unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encode(accounts: Accounts): String {
        val users = JSONArray()
        accounts.sessions.values.forEach { session -> users.put(JSONObject().put("uid", session.userId).put("token", session.token)) }
        val json = JSONObject().put("revision", accounts.revision).put("active", accounts.active ?: JSONObject.NULL).put("accounts", users)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(create = true)) }
        val plain = json.toString().toByteArray(Charsets.UTF_8)
        val bytes = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        check(cipher.iv.size == 12)
        return Base64.encodeToString(cipher.iv + bytes, Base64.NO_WRAP)
    }
    private fun decode(preferences: Preferences): Accounts {
        val value = preferences[encrypted] ?: return Accounts(0, null, emptyMap())
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        val plain = cipher.doFinal(bytes, 12, bytes.size - 12)
        val json = try { JSONObject(String(plain, Charsets.UTF_8)) } finally { plain.fill(0) }
        val users = json.getJSONArray("accounts")
        val accounts = (0 until users.length()).associate { index ->
            val user = users.getJSONObject(index)
            val session = NgaSession(user.getString("uid"), user.getString("token"))
            session.userId to session
        }
        val active = if (json.isNull("active")) null else json.getString("active")
        require(active == null || active in accounts)
        return Accounts(json.getLong("revision"), active, accounts)
    }
    suspend fun close() { job.cancelAndJoin() }
}

class SessionStorageException : IllegalStateException("Stored NGA credentials are unavailable")
