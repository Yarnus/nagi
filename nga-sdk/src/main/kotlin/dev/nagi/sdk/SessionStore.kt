package dev.nagi.sdk

class NgaSession(val userId: String, val token: String) {
    init {
        require(userId.isNotEmpty() && userId.all { it in '0'..'9' })
        require(token.isNotEmpty() && token.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "._-" })
    }
    override fun toString(): String = "NgaSession(redacted)"
}

data class SessionSnapshot(val revision: Long, val session: NgaSession?)

/** Implementations must advance the revision on every login, logout or account switch, including A -> B -> A. */
interface SessionStore {
    suspend fun snapshot(): SessionSnapshot
    suspend fun getAccounts(): List<String>
    suspend fun saveSession(session: NgaSession)
    suspend fun selectAccount(userId: String)
    suspend fun clearSession()
}

class MemorySessionStore : SessionStore {
    private val accounts = mutableMapOf<String, NgaSession>()
    private var current: NgaSession? = null
    private var revision = 0L
    override suspend fun snapshot(): SessionSnapshot = synchronized(this) { SessionSnapshot(revision, current) }
    override suspend fun getAccounts(): List<String> = synchronized(this) { accounts.keys.toList() }
    override suspend fun saveSession(session: NgaSession) { synchronized(this) {
        accounts[session.userId] = session; current = session; revision++
    } }
    override suspend fun selectAccount(userId: String) { synchronized(this) {
        current = requireNotNull(accounts[userId]); revision++
    } }
    override suspend fun clearSession() { synchronized(this) {
        current?.let { accounts.remove(it.userId) }
        current = null; revision++
    } }
}
