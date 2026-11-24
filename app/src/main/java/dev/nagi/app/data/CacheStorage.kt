package dev.nagi.app.data

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private const val SHARED_IMAGES_DIR = "shared-images"

@OptIn(ExperimentalCoilApi::class)
internal suspend fun calculateAppCacheBytes(context: Context): Long = withContext(Dispatchers.IO) {
    val imageCacheBytes = runCatching { context.imageLoader.diskCache?.size ?: 0L }.getOrDefault(0L)
    val sharedImagesBytes = directorySizeBytes(File(context.cacheDir, SHARED_IMAGES_DIR))
    (imageCacheBytes + sharedImagesBytes).coerceAtLeast(0L)
}

@OptIn(ExperimentalCoilApi::class)
internal suspend fun clearAppCache(context: Context): Long = withContext(Dispatchers.IO) {
    runCatching { context.imageLoader.memoryCache?.clear() }
    runCatching { context.imageLoader.diskCache?.clear() }
    runCatching { File(context.cacheDir, SHARED_IMAGES_DIR).deleteRecursively() }
    calculateAppCacheBytes(context)
}

internal fun formatCacheSize(bytes: Long): String {
    if (bytes <= 0L) return "暂无缓存"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb.coerceAtLeast(0.1))
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}

private fun directorySizeBytes(root: File): Long {
    if (!root.exists()) return 0L
    if (root.isFile) return root.length()
    var total = 0L
    root.walkTopDown().forEach { file ->
        if (file.isFile) total += file.length()
    }
    return total
}
