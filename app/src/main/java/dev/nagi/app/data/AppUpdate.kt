package dev.nagi.app.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.nagi.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal const val LATEST_RELEASE_URL = "https://github.com/Yarnus/nagi/releases/latest"
internal const val RELEASE_DOWNLOAD_BASE_URL = "https://github.com/Yarnus/nagi/releases/download/"
internal const val UPDATE_CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L
internal val SUPPORTED_RELEASE_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64")

private val SEMVER_REGEX = Regex("""^\s*[vV]?(\d+)\.(\d+)\.(\d+)\s*$""")
private val SHA256_LINE_REGEX = Regex("""^\s*([0-9a-fA-F]{64})\s+\*?\.?/?([^\s]+)\s*$""")
private const val RELEASE_TAG_PREFIX = "/Yarnus/nagi/releases/tag/"
private const val MAX_DOWNLOAD_REDIRECTS = 5
private const val MAX_SHA256SUMS_BYTES = 64 * 1024
private const val MAX_APK_BYTES = 128L * 1024L * 1024L

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

internal fun selectReleaseAbi(supportedAbis: Array<String>): String? =
    supportedAbis.firstOrNull { it in SUPPORTED_RELEASE_ABIS }

internal fun parseSha256Sums(raw: String): Map<String, String> = buildMap {
    for (line in raw.lineSequence()) {
        val match = SHA256_LINE_REGEX.matchEntire(line) ?: continue
        val hash = match.groupValues[1].lowercase()
        val fileName = match.groupValues[2].substringAfterLast('/')
        if (fileName.isNotEmpty()) put(fileName, hash)
    }
}

internal fun isTrustedReleaseDownloadUrl(rawUrl: String): Boolean {
    val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    if (uri.userInfo != null) return false
    if (uri.port != -1 && uri.port != 443) return false
    val host = uri.host?.lowercase() ?: return false
    return host == "github.com" || host.endsWith(".githubusercontent.com")
}

internal fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(8192)
    file.inputStream().use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

enum class UpdateCheckStatus {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    FAILED,
}

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(
        val tag: String,
        val bytesDownloaded: Long,
        val totalBytes: Long?,
    ) : UpdateDownloadState
    data class Verifying(val tag: String) : UpdateDownloadState
    data class ReadyToInstall(
        val tag: String,
        val apkPath: String,
        val needsInstallPermission: Boolean = false,
    ) : UpdateDownloadState
    data class Failed(val tag: String, val message: String) : UpdateDownloadState
}

data class AppUpdateState(
    val latestTag: String? = null,
    val hasUpdate: Boolean = false,
    val checking: Boolean = false,
    val manualStatus: UpdateCheckStatus = UpdateCheckStatus.IDLE,
    val download: UpdateDownloadState = UpdateDownloadState.Idle,
)

sealed interface ReleaseDownloadResult {
    data class Ready(val apkPath: String) : ReleaseDownloadResult
    data class Rejected(val message: String) : ReleaseDownloadResult
    data class Failed(val message: String) : ReleaseDownloadResult
}

interface ReleaseDownloader {
    suspend fun downloadAndVerify(
        tag: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        onVerifying: () -> Unit = {},
    ): ReleaseDownloadResult

    fun canInstallPackages(): Boolean = true

    fun cleanupCachedUpdates() {}
}

object NoOpReleaseDownloader : ReleaseDownloader {
    override suspend fun downloadAndVerify(
        tag: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        onVerifying: () -> Unit,
    ): ReleaseDownloadResult = ReleaseDownloadResult.Failed("暂不支持应用内下载，请前往发布页下载")
}

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

/**
 * Downloads a release APK matching the device ABI from GitHub Releases without NGA credentials,
 * verifies its SHA-256 digest against `SHA256SUMS`, and validates package name and signing certs.
 */
class GitHubReleaseDownloader(
    @param:ApplicationContext private val context: Context,
    private val downloadBaseUrl: String = RELEASE_DOWNLOAD_BASE_URL,
    private val userAgent: String = "Nagi/${BuildConfig.VERSION_NAME}",
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
    private val supportedAbisProvider: () -> Array<String> = { Build.SUPPORTED_ABIS },
    httpClient: OkHttpClient = defaultUpdateOkHttpClient(),
) : ReleaseDownloader {
    private val client = httpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(connectTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
        .build()

    private val updatesDir: File
        get() = File(context.cacheDir, "updates")

    override fun canInstallPackages(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    override fun cleanupCachedUpdates() {
        runCatching {
            updatesDir.listFiles()?.forEach { it.delete() }
        }
    }

    override suspend fun downloadAndVerify(
        tag: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        onVerifying: () -> Unit,
    ): ReleaseDownloadResult = withContext(Dispatchers.IO) {
        val parsed = parseSemVer(tag)
            ?: return@withContext ReleaseDownloadResult.Failed("无效的版本标签：$tag")
        val canonicalTag = "v${parsed.major}.${parsed.minor}.${parsed.patch}"
        val abi = selectReleaseAbi(supportedAbisProvider())
            ?: return@withContext ReleaseDownloadResult.Rejected("当前设备架构暂不支持应用内更新，请前往发布页手动下载")

        val fileName = "nagi-$canonicalTag-$abi.apk"
        val sumsUrl = "${downloadBaseUrl}$canonicalTag/SHA256SUMS"
        val apkUrl = "${downloadBaseUrl}$canonicalTag/$fileName"

        val dir = updatesDir
        if (!dir.exists() && !dir.mkdirs()) {
            return@withContext ReleaseDownloadResult.Failed("无法创建更新缓存目录")
        }
        cleanupCachedUpdates()

        val tempFile = File(dir, "$fileName.part")
        val targetFile = File(dir, fileName)
        try {
            val sumsText = fetchText(sumsUrl, MAX_SHA256SUMS_BYTES)
                ?: return@withContext ReleaseDownloadResult.Failed("无法获取校验文件，请检查网络后重试")
            val expectedHash = parseSha256Sums(sumsText)[fileName]
                ?: return@withContext ReleaseDownloadResult.Rejected("发布清单中缺少 $fileName 校验信息")

            downloadFile(apkUrl, tempFile, onProgress)
            currentCoroutineContext().ensureActive()
            onVerifying()

            val actualHash = sha256Hex(tempFile)
            if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                tempFile.delete()
                return@withContext ReleaseDownloadResult.Rejected("安装包 SHA-256 校验未通过，已放弃安装")
            }

            val archiveFailure = verifyPackageAndSignature(tempFile, parsed)
            if (archiveFailure != null) {
                tempFile.delete()
                return@withContext ReleaseDownloadResult.Rejected(archiveFailure)
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            ReleaseDownloadResult.Ready(targetFile.absolutePath)
        } catch (error: CancellationException) {
            tempFile.delete()
            targetFile.delete()
            throw error
        } catch (_: Exception) {
            tempFile.delete()
            targetFile.delete()
            ReleaseDownloadResult.Failed("下载更新失败，请检查网络或前往发布页下载")
        }
    }

    private suspend fun openTrustedResponse(initialUrl: String): Response {
        var currentUrl = initialUrl
        for (hop in 0..MAX_DOWNLOAD_REDIRECTS) {
            currentCoroutineContext().ensureActive()
            require(isTrustedReleaseDownloadUrl(currentUrl)) { "Untrusted update download URL" }
            val request = Request.Builder()
                .url(currentUrl)
                .get()
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("User-Agent", userAgent)
                .build()
            val response = client.newCall(request).awaitResponse()
            val code = response.code
            if (code in 300..399) {
                val location = response.header("Location")
                response.close()
                if (location.isNullOrBlank()) throw IllegalStateException("Empty redirect location")
                currentUrl = URI(currentUrl).resolve(location).toString()
                continue
            }
            if (code !in 200..299) {
                response.close()
                throw IllegalStateException("HTTP $code")
            }
            return response
        }
        throw IllegalStateException("Too many redirects")
    }

    private suspend fun fetchText(url: String, maxBytes: Int): String? =
        openTrustedResponse(url).use { response ->
            val body = response.body ?: throw IllegalStateException("Empty checksum response")
            val length = body.contentLength()
            if (length > maxBytes) throw IllegalStateException("Checksum payload too large")
            val bytes = body.byteStream().use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(4096)
                var total = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buf)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) throw IllegalStateException("Checksum payload too large")
                    out.write(buf, 0, read)
                }
                out.toByteArray()
            }
            bytes.toString(Charsets.UTF_8)
        }

    private suspend fun downloadFile(
        url: String,
        dest: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ) {
        openTrustedResponse(url).use { response ->
            val body = response.body ?: throw IllegalStateException("Empty download response")
            val totalBytes = body.contentLength().takeIf { it > 0L }
            if (totalBytes != null && totalBytes > MAX_APK_BYTES) {
                throw IllegalStateException("APK exceeds maximum size")
            }
            var downloaded = 0L
            onProgress(0L, totalBytes)
            body.byteStream().use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var lastReported = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        downloaded += read
                        if (downloaded > MAX_APK_BYTES) {
                            throw IllegalStateException("APK exceeds maximum size")
                        }
                        output.write(buffer, 0, read)
                        if (downloaded - lastReported >= 64 * 1024 || (totalBytes != null && downloaded >= totalBytes)) {
                            lastReported = downloaded
                            onProgress(downloaded, totalBytes)
                        }
                    }
                    output.flush()
                }
            }
            onProgress(downloaded, totalBytes)
        }
    }

    private fun verifyPackageAndSignature(apkFile: File, expectedVersion: SemVer): String? {
        val pm = context.packageManager
        val archiveInfo = getPackageArchiveInfoCompat(pm, apkFile.absolutePath)
            ?: return "无法解析下载的安装包"
        if (archiveInfo.packageName != context.packageName) {
            return "安装包应用标识不匹配，已放弃安装"
        }
        val archiveSemVer = archiveInfo.versionName?.let(::parseSemVer)
        if (archiveSemVer == null || archiveSemVer != expectedVersion) {
            return "安装包版本号与目标版本不一致，已放弃安装"
        }
        val installedInfo = getInstalledPackageInfoCompat(pm, context.packageName)
            ?: return "无法核对当前应用签名"
        if (PackageInfoCompat.getLongVersionCode(archiveInfo) < PackageInfoCompat.getLongVersionCode(installedInfo)) {
            return "安装包版本低于当前已安装版本，已放弃安装"
        }
        val expectedCerts = signingCertDigests(installedInfo)
        val archiveCerts = signingCertDigests(archiveInfo)
        if (expectedCerts.isEmpty() || archiveCerts.isEmpty() || expectedCerts != archiveCerts) {
            return "当前已安装版本与下载包签名不一致（例如调试版），无法直接覆盖安装"
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun getPackageArchiveInfoCompat(pm: PackageManager, path: String): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)
        }

    @Suppress("DEPRECATION")
    private fun getInstalledPackageInfoCompat(pm: PackageManager, packageName: String): PackageInfo? =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
        }.getOrNull()

    @Suppress("DEPRECATION")
    private fun signingCertDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { signing ->
                if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
            }
        } else {
            info.signatures
        } ?: return emptySet()
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.map { sig ->
            digest.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}

@Singleton
class ReleaseUpdateRepository(
    private val checker: ReleaseChecker,
    private val cacheStore: UpdateCacheStore,
    private val currentVersion: () -> String = { BuildConfig.VERSION_NAME },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val downloader: ReleaseDownloader = NoOpReleaseDownloader,
) {
    private val _state = MutableStateFlow(AppUpdateState())
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()
    private val mutex = Mutex()
    private val downloadMutex = Mutex()
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
            if (!cachedHasUpdate) downloader.cleanupCachedUpdates()
            _state.value = _state.value.copy(
                latestTag = cache.latestTag.takeIf { cachedHasUpdate },
                hasUpdate = cachedHasUpdate,
                download = if (cachedHasUpdate) _state.value.download else UpdateDownloadState.Idle,
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
            if (!hasUpdate) downloader.cleanupCachedUpdates()
            _state.value = _state.value.copy(
                latestTag = remoteTag.takeIf { hasUpdate },
                hasUpdate = hasUpdate,
                download = if (hasUpdate) _state.value.download else UpdateDownloadState.Idle,
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
            if (!hasUpdate) downloader.cleanupCachedUpdates()
            lastAttemptMillis = now
            _state.value = _state.value.copy(
                latestTag = remoteTag.takeIf { hasUpdate },
                hasUpdate = hasUpdate,
                checking = false,
                manualStatus = if (hasUpdate) UpdateCheckStatus.UPDATE_AVAILABLE else UpdateCheckStatus.UP_TO_DATE,
                download = if (hasUpdate) _state.value.download else UpdateDownloadState.Idle,
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

    suspend fun downloadUpdate(): UpdateDownloadState? {
        val tag = _state.value.latestTag?.takeIf { _state.value.hasUpdate } ?: return null
        return downloadMutex.withLock {
            _state.value = _state.value.copy(
                download = UpdateDownloadState.Downloading(tag, 0L, null),
            )
            val result = try {
                downloader.downloadAndVerify(
                    tag = tag,
                    onProgress = { downloaded, total ->
                        _state.value = _state.value.copy(
                            download = UpdateDownloadState.Downloading(tag, downloaded, total),
                        )
                    },
                    onVerifying = {
                        _state.value = _state.value.copy(
                            download = UpdateDownloadState.Verifying(tag),
                        )
                    },
                )
            } catch (error: CancellationException) {
                _state.value = _state.value.copy(download = UpdateDownloadState.Idle)
                throw error
            }
            val next = when (result) {
                is ReleaseDownloadResult.Ready -> UpdateDownloadState.ReadyToInstall(
                    tag = tag,
                    apkPath = result.apkPath,
                    needsInstallPermission = !downloader.canInstallPackages(),
                )
                is ReleaseDownloadResult.Rejected -> UpdateDownloadState.Failed(tag, result.message)
                is ReleaseDownloadResult.Failed -> UpdateDownloadState.Failed(tag, result.message)
            }
            _state.value = _state.value.copy(download = next)
            next
        }
    }

    fun refreshInstallPermission() {
        val current = _state.value.download as? UpdateDownloadState.ReadyToInstall ?: return
        val needs = !downloader.canInstallPackages()
        if (current.needsInstallPermission != needs) {
            _state.value = _state.value.copy(download = current.copy(needsInstallPermission = needs))
        }
    }

    fun resetManualStatus() {
        if (!_state.value.checking) {
            _state.value = _state.value.copy(manualStatus = UpdateCheckStatus.IDLE)
        }
    }
}
