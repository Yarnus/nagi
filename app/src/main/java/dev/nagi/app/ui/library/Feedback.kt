package dev.nagi.app.ui.library

import android.net.Uri
import androidx.core.net.toUri

internal fun feedbackUri(version: String, androidVersion: String, device: String): Uri =
    "https://github.com/Yarnus/nagi/issues/new/choose".toUri().buildUpon()
        .appendQueryParameter("app_version", version)
        .appendQueryParameter("android_version", androidVersion)
        .appendQueryParameter("device", device)
        .build()
