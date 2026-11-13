package dev.nagi.app.ui.post

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.LocalAccountIdentity
import dev.nagi.sdk.ImageUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal suspend fun readUploadImage(context: Context, uri: Uri): ImageUpload = withContext(Dispatchers.IO) {
    require(uri.scheme == "content")
    val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            coroutineContext.ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= ImageUpload.MAX_BYTES)
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
    ImageUpload(bytes)
}

@Composable
internal fun ImageUploadButton(
    enabled: Boolean, busy: Boolean, scopeKey: String,
    upload: (suspend () -> ImageUpload) -> Unit,
) {
    val context = LocalContext.current
    val identity = LocalAccountIdentity.current
    val scope = "${identity.revision}:$scopeKey"
    var launchedFor by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && enabled && launchedFor == scope) upload { readUploadImage(context, uri) }
        launchedFor = null
    }
    CapsuleButton(
        text = if (busy) "上传中…" else "图片",
        enabled = enabled && !busy,
        style = CapsuleStyle.Neutral,
        onClick = { launchedFor = scope; launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    )
}
