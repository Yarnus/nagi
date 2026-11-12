package dev.nagi.sdk

import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ReadRedirectTest {
    private val url = "https://bbs.nga.cn/read.php?tid=123&page=2&authorid=7"
    private val alternate = url.replace("bbs.nga.cn", "ngabbs.com")

    private fun session() = MemorySessionStore().also {
        runBlocking { it.saveSession(NgaSession("7", "test-token")) }
    }

    private fun client(store: SessionStore, respond: (Request) -> Response): OkHttpClient = runBlocking {
        OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .addInterceptor(NgaReadRedirectInterceptor(store, store.snapshot()))
            .addInterceptor { respond(it.request()) }.build()
    }

    private fun response(request: Request, code: Int, location: String? = null): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
            .body("".toResponseBody()).apply { location?.let { header("Location", it) } }.build()

    private fun request() = Request.Builder().url(url)
        .post(FormBody.Builder().add("key", "value").build()).build()

    @Test fun defaultAndLegacyOriginsUsePrimary() {
        assertEquals(NgaDomain.PRIMARY, NgaConfig().domain)
        assertEquals(NgaDomain.PRIMARY.readOrigin(), NgaDomain.ALTERNATE.readOrigin())
        assertEquals("ngabbs.com", NgaDomain.APP.readOrigin().host)
    }

    @Test fun canonical301302303BecomeGetWithCookieButNoCredentialUrlOrBody() {
        for (status in listOf(301, 302, 303)) {
            val requests = mutableListOf<Request>()
            val client = client(session()) { req ->
                requests.add(req)
                response(req, if (requests.size == 1) status else 200, alternate)
            }
            client.newCall(request()).execute().use { assertEquals(200, it.code) }
            assertEquals(2, requests.size)
            assertEquals("POST", requests[0].method)
            assertEquals("GET", requests[1].method)
            assertNull(requests[1].body)
            assertEquals(alternate, requests[1].url.toString())
            for (req in requests) assertEquals("ngaPassportUid=7; ngaPassportCid=test-token", req.header("Cookie"))
            assertEquals(3, (requests[0].body as FormBody).size)
        }
    }

    @Test fun canonical307308RetainFormWithoutDuplicatingCredentials() {
        for (status in listOf(307, 308)) {
            val requests = mutableListOf<Request>()
            val client = client(session()) { req ->
                requests.add(req)
                response(req, if (requests.size == 1) status else 200, alternate)
            }
            client.newCall(request()).execute().close()
            assertEquals(2, requests.size)
            for (req in requests) {
                assertEquals("POST", req.method)
                val form = req.body as FormBody
                assertEquals(3, form.size)
                assertEquals("access_uid", form.name(1))
                assertEquals("7", form.value(1))
                assertEquals("access_token", form.name(2))
                assertEquals("test-token", form.value(2))
            }
        }
    }

    @Test fun unsafeMissingAndActionChangingLocationsAreNotSent() {
        for (location in listOf(
            null, "https://evil.example/read.php?tid=123", alternate.replace("https:", "http:"),
            alternate.replace("ngabbs.com", "ngabbs.com:444"),
            alternate.replace("ngabbs.com", "ngabbs.com.evil.example"),
            alternate.replace("ngabbs.com", "user:pass@ngabbs.com"),
            alternate.replace("read.php", "post.php"), alternate + "&__act=delete",
        )) {
            var count = 0
            val client = client(session()) { req -> count++; response(req, 307, location) }
            assertTrue(runCatching { client.newCall(request()).execute().close() }.exceptionOrNull() is IOException)
            assertEquals(1, count)
        }
    }

    @Test fun relativeRedirectAndLoopAreBounded() {
        var count = 0
        val client = client(session()) { req ->
            count++
            response(req, if (count == 1) 303 else 200, "/read.php?tid=123&page=2&authorid=7")
        }
        client.newCall(request()).execute().close()
        assertEquals(2, count)
        count = 0
        val loop = client(session()) { req ->
            count++
            response(req, 307, if (count % 2 == 1) alternate else url)
        }
        assertTrue(runCatching { loop.newCall(request()).execute().close() }.exceptionOrNull() is IOException)
        assertEquals(2, count)
    }

    @Test fun redirectLimitStopsLongChainsEvenWithDistinctFragments() {
        var count = 0
        val client = client(session()) { req ->
            count++
            response(req, 307, "$alternate#hop$count")
        }
        assertTrue(runCatching { client.newCall(request()).execute().close() }.exceptionOrNull() is IOException)
        assertEquals(11, count)
    }

    @Test fun accountSwitchAndCancellationStopBeforeNextHop() {
        for (cancel in listOf(false, true)) {
            val store = session()
            var count = 0
            lateinit var call: okhttp3.Call
            val client = client(store) { req ->
                count++
                if (cancel) call.cancel() else runBlocking { store.saveSession(NgaSession("8", "other-token")) }
                response(req, 307, alternate)
            }
            call = client.newCall(request())
            assertTrue(runCatching { call.execute().close() }.exceptionOrNull() is IOException)
            assertEquals(1, count)
        }
    }

    @Test fun actualPostReadRejectsUntrustedRedirectWithoutFallback() = runBlocking {
        MockWebServer().use { server -> MockWebServer().use { target ->
            server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", target.url("/read.php")))
            val error = runCatching { HttpNgaClient(server.url("/"), session()).getPosts("123") }.exceptionOrNull()
            assertTrue(error is NgaException.Network)
            assertEquals(1, server.requestCount)
            assertEquals(0, target.requestCount)
        } }
    }
}
