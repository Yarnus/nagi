package dev.nagi.sdk

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.ProtocolException

internal class NgaReadRedirectInterceptor(
    private val sessions: SessionStore,
    private val snapshot: SessionSnapshot,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val visited = mutableSetOf(request.method to request.url)
        var redirects = 0
        while (true) {
            if (chain.call().isCanceled()) throw IOException("Read cancelled")
            val current = try { runBlocking { sessions.snapshot() } }
                catch (_: Exception) { throw IOException("Unable to check NGA session") }
            if (current.revision != snapshot.revision) throw IOException("Account changed before read")
            val response = chain.proceed(request.withNgaSession(snapshot.session))
            if (response.code !in listOf(301, 302, 303, 307, 308)) return response
            val target = response.header("Location")?.let { request.url.resolve(it) }
            if (target == null || !isAllowedReadRedirect(request.url, target) || redirects++ >= 10) {
                response.close()
                throw ProtocolException("Unsafe or excessive NGA read redirect")
            }
            val builder = request.newBuilder().url(target)
                .header("Referer", target.newBuilder().query(null).build().toString())
            if (response.code in listOf(301, 302, 303) && request.method != "HEAD") {
                builder.get().removeHeader("Content-Type").removeHeader("Content-Length").removeHeader("Transfer-Encoding")
            }
            response.close()
            request = builder.build()
            if (!visited.add(request.method to request.url)) throw ProtocolException("NGA read redirect loop")
        }
    }
}

internal fun isAllowedReadRedirect(from: HttpUrl, to: HttpUrl): Boolean =
    isCredentialOrigin(from) && isCredentialOrigin(to) &&
        to.username.isEmpty() && to.password.isEmpty() &&
        from.encodedPath == to.encodedPath && from.encodedQuery == to.encodedQuery
