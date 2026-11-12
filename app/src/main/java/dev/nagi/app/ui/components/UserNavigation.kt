package dev.nagi.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

val LocalOpenUser = staticCompositionLocalOf<((String) -> Unit)?> { null }

private val publicUserId = Regex("[1-9][0-9]*")
internal fun isPublicUserId(id: String): Boolean = id.matches(publicUserId)
