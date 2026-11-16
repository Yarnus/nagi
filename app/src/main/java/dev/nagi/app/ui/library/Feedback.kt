package dev.nagi.app.ui.library

import android.net.Uri
import androidx.core.net.toUri
import dev.nagi.app.data.LATEST_RELEASE_URL

internal fun feedbackUri(version: String, androidVersion: String, device: String): Uri =
    "https://github.com/Yarnus/nagi/issues/new/choose".toUri().buildUpon()
        .appendQueryParameter("app_version", version)
        .appendQueryParameter("android_version", androidVersion)
        .appendQueryParameter("device", device)
        .build()

internal fun latestReleaseUri(): Uri = LATEST_RELEASE_URL.toUri()
