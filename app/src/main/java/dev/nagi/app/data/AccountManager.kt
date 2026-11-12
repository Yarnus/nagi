package dev.nagi.app.data

import dev.nagi.sdk.NgaDomain
import dev.nagi.sdk.SessionStore
import dev.nagi.sdk.parsePassportCookies
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class LoginAttempt(val revision: Long, val generation: Long)

data class AccountState(
    val ready: Boolean = false, val userId: String? = null, val revision: Long = 0,
    val accounts: List<String> = emptyList(), val busy: Boolean = false, val error: String? = null,
)

@Singleton
class AccountManager @Inject constructor(private val store: SessionStore, private val browser: BrowserCookies) {
    private val mutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loginGeneration = 0L
    private val _state = MutableStateFlow(AccountState())
    val state = _state.asStateFlow()

    suspend fun initialize() = mutex.withLock {
        try { refresh() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { _state.value = AccountState(error = "无法读取安全凭证，请检查设备安全存储后重试") }
    }
    private suspend fun refresh(error: String? = null) {
        val snapshot = store.snapshot()
        _state.value = AccountState(true, snapshot.session?.userId, snapshot.revision, store.getAccounts(), error = error)
    }
    private suspend fun <T> mutate(block: suspend () -> T): T = mutex.withLock {
        withContext(NonCancellable) {
            _state.value = _state.value.copy(busy = true, error = null)
            var error: String? = null
            try { withTimeout(15_000) { block() } }
            catch (failure: Exception) { error = "账号操作未完成，请重试"; throw failure }
            finally {
                try { refresh(error) }
                catch (_: Exception) { _state.value = AccountState(error = "无法读取安全凭证，请重试") }
            }
        }
    }
    suspend fun <T> withCurrentRevision(revision: Long, operation: suspend () -> T): T = mutex.withLock {
        if (store.snapshot().revision != revision) throw CancellationException("Account changed")
        operation()
    }
    suspend fun beginLogin(): LoginAttempt = mutate {
        loginGeneration++
        browser.clear()
        LoginAttempt(store.snapshot().revision, loginGeneration)
    }
    fun discardLogin(attempt: LoginAttempt) {
        cleanupScope.launch {
            mutex.withLock {
                if (attempt.generation == loginGeneration) {
                    loginGeneration++
                    runCatching { withTimeout(15_000) { browser.clear() } }
                }
            }
        }
    }
    suspend fun captureAndSave(attempt: LoginAttempt, url: String): Boolean = mutate {
        if (!isTrustedLoginUrl(url) || attempt.generation != loginGeneration || store.snapshot().revision != attempt.revision) return@mutate false
        val candidates = (listOf(url) + NgaDomain.entries.map { it.baseUrl }).distinct()
        val sessions = candidates.mapNotNull { browser.read(it)?.let(::parsePassportCookies) }
            .distinctBy { it.userId to it.token }
        if (sessions.size != 1) return@mutate false
        loginGeneration++
        try { store.saveSession(sessions.single()) }
        finally { browser.clear() }
        true
    }
    suspend fun switchAccount(userId: String) = mutate {
        loginGeneration++
        browser.clear()
        store.selectAccount(userId)
    }
    suspend fun logout() = mutate {
        loginGeneration++
        browser.clear()
        store.clearSession()
    }
}
