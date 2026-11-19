package dev.nagi.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.nagi.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal const val LATEST_RELEASE_URL = "https://github.com/Yarnus/nagi/releases/latest"
internal const val UPDATE_CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L

private val SEMVER_REGEX = Regex("""^\s*[vV]?(\d+)\.(\d+)\.(\d+)\s*$""")
private const val RELEASE_TAG_PREFIX = "/Yarnus/nagi/releases/tag/"

internal data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int =
        compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)
}

internal fun parseSemVer(raw: String): SemVer? {
    val match = SEMVER_REGEX.matchEntire(raw) ?: return null
    val major = match.groupValues[1].toIntOrNull() ?: return null
    val minor = match.groupValues[2].toIntOrNull() ?: return null
    val patch = match.groupValues[3].toIntOrNull() ?: return null
    return SemVer(major, minor, patch)
}

internal fun isNewerRelease(currentVersion: String, latestTag: String?): Boolean {
    val current = parseSemVer(currentVersion) ?: return false
    val remote = latestTag?.let(::parseSemVer) ?: return false
    return remote > current
}

internal fun parseReleaseTagFromLocation(location: String?): String? {
    val raw = location?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val uri = runCatching { URI(raw) }.getOrNull() ?: return null
    if (uri.userInfo != null) return null
    if (uri.scheme != null) {
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (!uri.host.equals("github.com", ignoreCase = true)) return null
        if (uri.port != -1 && uri.port != 443) return null
    } else if (uri.authority != null) {
        return null
    }
    val path = uri.path ?: return null
    if (!path.startsWith(RELEASE_TAG_PREFIX, ignoreCase = true)) return null
    val tagSegment = path.substring(RELEASE_TAG_PREFIX.length).trimEnd('/')
    if (tagSegment.isEmpty() || '/' in tagSegment) return null
    val version = parseSemVer(tagSegment) ?: return null
    return "v${version.major}.${version.minor}.${version.patch}"
}

enum class UpdateCheckStatus {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    FAILED,
}

data class AppUpdateState(
    val latestTag: String? = null,
    val hasUpdate: Boolean = false,
    val checking: Boolean = false,
    val manualStatus: UpdateCheckStatus = UpdateCheckStatus.IDLE,
)

fun interface ReleaseChecker {
    /** Fetches the latest stable release tag (e.g. "v0.2.1"), or null on failure. */
    suspend fun fetchLatestReleaseTag(): String?
}

data class UpdateCheckCache(
    val lastCheckedAtMillis: Long = 0L,
    val latestTag: String? = null,
)

interface UpdateCacheStore {
    suspend fun read(): UpdateCheckCache
    suspend fun write(cache: UpdateCheckCache)
}

class InMemoryUpdateCacheStore(
    initial: UpdateCheckCache = UpdateCheckCache(),
) : UpdateCacheStore {
    private var current = initial
    override suspend fun read(): UpdateCheckCache = current
    override suspend fun write(cache: UpdateCheckCache) {
        current = cache
    }
}

private val Context.updateCheckDataStore by preferencesDataStore("app_update")

@Singleton
class DataStoreUpdateCacheStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : UpdateCacheStore {
    private val lastCheckedKey = longPreferencesKey("last_checked_at")
    private val latestTagKey = stringPreferencesKey("latest_tag")

    override suspend fun read(): UpdateCheckCache {
        val prefs = context.updateCheckDataStore.data.first()
        return UpdateCheckCache(
            lastCheckedAtMillis = prefs[lastCheckedKey] ?: 0L,
            latestTag = prefs[latestTagKey],
        )
    }

    override suspend fun write(cache: UpdateCheckCache) {
        context.updateCheckDataStore.edit { prefs ->
            prefs[lastCheckedKey] = cache.lastCheckedAtMillis
            if (cache.latestTag != null) prefs[latestTagKey] = cache.latestTag
            else prefs.remove(latestTagKey)
        }
    }
}

private fun defaultUpdateOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .retryOnConnectionFailure(true)
    .build()

private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, _, _ -> response.close() }
        }
    })
}

/**
 * Checks the latest GitHub release silently via an unauthenticated HTTPS redirect
 * without sending any NGA session or device metadata.
 */
class GitHubReleaseChecker(
    private val url: String = LATEST_RELEASE_URL,
    private val userAgent: String = "Nagi/${BuildConfig.VERSION_NAME}",
    private val timeoutMillis: Int = 10_000,
    httpClient: OkHttpClient = defaultUpdateOkHttpClient(),
) : ReleaseChecker {
    private val client = httpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
        .callTimeout(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
        .build()

    override suspend fun fetchLatestReleaseTag(): String? = withContext(Dispatchers.IO) {
        try {
            val target = url.toHttpUrlOrNull()
            require(target != null && target.isHttps) { "HTTPS required" }
            val (code, location) = checkRedirect("HEAD", target)
            if (code in 300..399) {
                parseReleaseTagFromLocation(location)
            } else if (code == 405 || code == 501) {
                val (getCode, getLocation) = checkRedirect("GET", target)
                if (getCode in 300..399) parseReleaseTagFromLocation(getLocation) else null
            } else {
                null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun checkRedirect(method: String, target: HttpUrl): Pair<Int, String?> {
        val request = Request.Builder()
            .url(target)
            .method(method, null)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("User-Agent", userAgent)
            .build()
        return client.newCall(request).awaitResponse().use { response ->
            response.code to response.header("Location")
        }
    }
}

@Singleton
class ReleaseUpdateRepository(
    private val checker: ReleaseChecker,
    private val cacheStore: UpdateCacheStore,
    private val currentVersion: () -> String = { BuildConfig.VERSION_NAME },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(AppUpdateState())
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()
    private val mutex = Mutex()
    private var lastAttemptMillis: Long? = null

    suspend fun checkIfNeeded() {
        mutex.withLock {
            val current = currentVersion()
            val cache = try {
                cacheStore.read()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                UpdateCheckCache()
            }
            val cachedHasUpdate = isNewerRelease(current, cache.latestTag)
            _state.value = _state.value.copy(
                latestTag = cache.latestTag.takeIf { cachedHasUpdate },
                hasUpdate = cachedHasUpdate,
            )

            val now = nowMillis()
            val checkedRecently = cache.lastCheckedAtMillis > 0L &&
                now >= cache.lastCheckedAtMillis &&
                now - cache.lastCheckedAtMillis < UPDATE_CHECK_INTERVAL_MILLIS
            val attemptedRecently = lastAttemptMillis?.let {
                now >= it && now - it < UPDATE_CHECK_INTERVAL_MILLIS
            } == true
            if (checkedRecently || attemptedRecently) return

            lastAttemptMillis = now
            val remoteTag = checker.fetchLatestReleaseTag() ?: return
            val hasUpdate = isNewerRelease(current, remoteTag)
            _state.value = _state.value.copy(
                latestTag = remoteTag.takeIf { hasUpdate },
                hasUpdate = hasUpdate,
            )
            try {
                cacheStore.write(
                    UpdateCheckCache(
                        lastCheckedAtMillis = now,
                        latestTag = remoteTag,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
        }
    }

    suspend fun checkNow() {
        mutex.withLock {
            _state.value = _state.value.copy(
                checking = true,
                manualStatus = UpdateCheckStatus.CHECKING,
            )
            val remoteTag = checker.fetchLatestReleaseTag()
            val current = currentVersion()
            if (remoteTag == null) {
                val preservedHasUpdate = _state.value.hasUpdate && isNewerRelease(current, _state.value.latestTag)
                _state.value = _state.value.copy(
                    latestTag = _state.value.latestTag.takeIf { preservedHasUpdate },
                    hasUpdate = preservedHasUpdate,
                    checking = false,
                    manualStatus = UpdateCheckStatus.FAILED,
                )
                return
            }
            val now = nowMillis()
            val hasUpdate = isNewerRelease(current, remoteTag)
            lastAttemptMillis = now
            _state.value = _state.value.copy(
                latestTag = remoteTag.takeIf { hasUpdate },
                hasUpdate = hasUpdate,
                checking = false,
                manualStatus = if (hasUpdate) UpdateCheckStatus.UPDATE_AVAILABLE else UpdateCheckStatus.UP_TO_DATE,
            )
            try {
                cacheStore.write(
                    UpdateCheckCache(
                        lastCheckedAtMillis = now,
                        latestTag = remoteTag,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
        }
    }

    fun resetManualStatus() {
        if (!_state.value.checking) {
            _state.value = _state.value.copy(manualStatus = UpdateCheckStatus.IDLE)
        }
    }
}
