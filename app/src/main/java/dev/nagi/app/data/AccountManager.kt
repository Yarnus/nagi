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
    private val lockStateMutex = Mutex()
    private var activeReaders = 0
    private var writerActive = false
    private var waitingWriters = 0
    private val readerWaiters = ArrayDeque<CompletableDeferred<Unit>>()
    private val writerWaiters = ArrayDeque<CompletableDeferred<Unit>>()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loginGeneration = 0L
    private val _state = MutableStateFlow(AccountState())
    val state = _state.asStateFlow()

    private fun drainReaders(): List<CompletableDeferred<Unit>> {
        if (readerWaiters.isEmpty()) return emptyList()
        val drained = readerWaiters.toList()
        readerWaiters.clear()
        return drained
    }

    private suspend fun <T> withReadLock(block: suspend () -> T): T {
        while (true) {
            val waiter = lockStateMutex.withLock {
                if (!writerActive && waitingWriters == 0) {
                    activeReaders++
                    null
                } else {
                    CompletableDeferred<Unit>().also { readerWaiters.addLast(it) }
                }
            }
            if (waiter == null) break
            waiter.await()
        }
        return try {
            block()
        } finally {
            withContext(NonCancellable) {
                val nextWriter = lockStateMutex.withLock {
                    activeReaders--
                    if (activeReaders == 0 && !writerActive) writerWaiters.removeFirstOrNull() else null
                }
                nextWriter?.complete(Unit)
            }
        }
    }

    private suspend fun <T> withWriteLock(block: suspend () -> T): T {
        lockStateMutex.withLock { waitingWriters++ }
        try {
            while (true) {
                val waiter = lockStateMutex.withLock {
                    if (!writerActive && activeReaders == 0) {
                        waitingWriters--
                        writerActive = true
                        null
                    } else {
                        CompletableDeferred<Unit>().also { writerWaiters.addLast(it) }
                    }
                }
                if (waiter == null) break
                waiter.await()
            }
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                val (nextWriter, wakeReaders) = lockStateMutex.withLock {
                    waitingWriters--
                    when {
                        !writerActive && activeReaders == 0 && waitingWriters > 0 ->
                            writerWaiters.removeFirstOrNull() to emptyList()
                        !writerActive && waitingWriters == 0 ->
                            null to drainReaders()
                        else -> null to emptyList()
                    }
                }
                nextWriter?.complete(Unit)
                wakeReaders.forEach { it.complete(Unit) }
            }
            throw error
        }
        return try {
            block()
        } finally {
            withContext(NonCancellable) {
                val (nextWriter, readers) = lockStateMutex.withLock {
                    writerActive = false
                    val next = writerWaiters.removeFirstOrNull()
                    if (next != null) next to emptyList()
                    else if (waitingWriters == 0) null to drainReaders()
                    else null to emptyList()
                }
                nextWriter?.complete(Unit)
                readers.forEach { it.complete(Unit) }
            }
        }
    }

    suspend fun initialize() = withWriteLock {
        try { refresh() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { _state.value = AccountState(error = "无法读取安全凭证，请检查设备安全存储后重试") }
    }
    private suspend fun refresh(error: String? = null) {
        val snapshot = store.snapshot()
        _state.value = AccountState(true, snapshot.session?.userId, snapshot.revision, store.getAccounts(), error = error)
    }
    private suspend fun <T> mutate(block: suspend () -> T): T = withWriteLock {
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
    suspend fun <T> withCurrentRevision(revision: Long, operation: suspend () -> T): T = withReadLock {
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
            withWriteLock {
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
