package dev.nagi.sdk

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class SessionPeekTest {
    private class CountingSessionStore(
        private var peekValue: Long?,
        private var revision: Long = 1L,
    ) : SessionStore {
        val snapshotCalls = AtomicInteger(0)
        val snapshotThreads = mutableListOf<String>()
        private var session: NgaSession? = NgaSession("7", "test-token")

        fun setPeek(value: Long?) {
            peekValue = value
        }

        override fun peekRevision(): Long? = peekValue

        override suspend fun snapshot(): SessionSnapshot {
            snapshotCalls.incrementAndGet()
            synchronized(snapshotThreads) { snapshotThreads += Thread.currentThread().name }
            return SessionSnapshot(revision, session)
        }

        override suspend fun getAccounts(): List<String> = listOfNotNull(session?.userId)
        override suspend fun saveSession(session: NgaSession) {
            this.session = session
            revision++
            peekValue = revision
        }

        override suspend fun selectAccount(userId: String) {
            revision++
            peekValue = revision
        }

        override suspend fun clearSession() {
            session = null
            revision++
            peekValue = revision
        }
    }

    @Test
    fun `HttpNgaClient skips snapshot in interceptors when peekRevision is present`() = runBlocking {
        val body = "<root><__T><item><tid>123</tid><subject>hello</subject></item></__T><__ROWS>1</__ROWS><__T__ROWS_PAGE>20</__T__ROWS_PAGE></root>"
        val withPeek = CountingSessionStore(peekValue = 1L, revision = 1L)
        val withoutPeek = CountingSessionStore(peekValue = null, revision = 1L)
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))
            assertEquals("123", HttpNgaClient(server.url("/"), withPeek).getTopics("-7").items.single().id)
            assertEquals("123", HttpNgaClient(server.url("/"), withoutPeek).getTopics("-7").items.single().id)
        }
        // Without peekRevision, both NgaReadRedirectInterceptor and the network interceptor call snapshot().
        assertEquals(2, withoutPeek.snapshotCalls.get() - withPeek.snapshotCalls.get())
        assertTrue(withPeek.snapshotThreads.none { it.startsWith("OkHttp") })
    }

    @Test
    fun `HttpNgaClient blocks transmission when peekRevision mismatches`() = runBlocking {
        val store = CountingSessionStore(peekValue = 2L, revision = 1L)
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            val client = HttpNgaClient(server.url("/"), store)
            val error = runCatching { client.getTopics("-7") }.exceptionOrNull()
            assertTrue(error is NgaException.Network)
            assertTrue(generateSequence<Throwable>(error) { it.cause }.any { it.message?.contains("Account changed") == true })
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun `NgaReadRedirectInterceptor uses peekRevision without calling snapshot`() {
        val store = CountingSessionStore(peekValue = 1L, revision = 1L)
        val client = OkHttpClient.Builder()
            .addInterceptor(NgaReadRedirectInterceptor(store, SessionSnapshot(1L, NgaSession("7", "test-token"))))
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("ok".toResponseBody())
                    .build()
            }
            .build()
        client.newCall(Request.Builder().url("https://bbs.nga.cn/read.php?tid=1").build()).execute().use {
            assertEquals(200, it.code)
        }
        assertEquals(0, store.snapshotCalls.get())
    }

    @Test
    fun `NgaReadRedirectInterceptor throws when peekRevision mismatches`() {
        val store = CountingSessionStore(peekValue = 2L, revision = 2L)
        val client = OkHttpClient.Builder()
            .addInterceptor(NgaReadRedirectInterceptor(store, SessionSnapshot(1L, NgaSession("7", "test-token"))))
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("ok".toResponseBody())
                    .build()
            }
            .build()
        val error = runCatching {
            client.newCall(Request.Builder().url("https://bbs.nga.cn/read.php?tid=1").build()).execute().close()
        }.exceptionOrNull()
        assertTrue(error is IOException)
        assertTrue(error!!.message!!.contains("Account changed"))
        assertEquals(0, store.snapshotCalls.get())
    }

    @Test
    fun `NgaReadRedirectInterceptor falls back to snapshot when peekRevision is null`() {
        val store = CountingSessionStore(peekValue = null, revision = 1L)
        val client = OkHttpClient.Builder()
            .addInterceptor(NgaReadRedirectInterceptor(store, SessionSnapshot(1L, NgaSession("7", "test-token"))))
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("ok".toResponseBody())
                    .build()
            }
            .build()
        client.newCall(Request.Builder().url("https://bbs.nga.cn/read.php?tid=1").build()).execute().use {
            assertEquals(200, it.code)
        }
        assertEquals(1, store.snapshotCalls.get())
    }
}
