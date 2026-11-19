package dev.nagi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import dev.nagi.app.ui.theme.NagiSemanticColors
import dev.nagi.app.ui.theme.NagiThemeTokens

private data class AvatarTone(val background: Color, val foreground: Color)

private val LightAvatarTones = listOf(
    AvatarTone(Color(0xFFF6EFE2), Color(0xFF8C651E)),
    AvatarTone(Color(0xFFEAF0F6), Color(0xFF335C81)),
    AvatarTone(Color(0xFFEBF3EE), Color(0xFF316649)),
    AvatarTone(Color(0xFFF7ECEA), Color(0xFF8C463B)),
    AvatarTone(Color(0xFFEFEEF6), Color(0xFF4E497A)),
    AvatarTone(Color(0xFFF0EFEA), Color(0xFF57534E)),
)

private val DarkAvatarTones = listOf(
    AvatarTone(Color(0xFF2E2619), Color(0xFFE5C494)),
    AvatarTone(Color(0xFF1E2936), Color(0xFF9BC2E6)),
    AvatarTone(Color(0xFF1D2B24), Color(0xFF96CFAF)),
    AvatarTone(Color(0xFF31211F), Color(0xFFE6A59C)),
    AvatarTone(Color(0xFF252433), Color(0xFFB8B3E0)),
    AvatarTone(Color(0xFF272624), Color(0xFFC7C2BA)),
)

private fun avatarToneFor(seed: String?, colors: NagiSemanticColors): AvatarTone {
    val normalized = seed?.trim().orEmpty()
    if (normalized.isEmpty()) {
        return AvatarTone(colors.insetContainer, colors.textSecondary)
    }
    val palette = if (colors.isDark) DarkAvatarTones else LightAvatarTones
    val index = ((normalized.hashCode().toLong() and 0x7FFFFFFFL) % palette.size).toInt()
    return palette[index]
}

/** Shared circular user avatar with UID-seeded muted monogram fallback. */
@Composable
fun UserAvatar(
    name: String?,
    avatarUrl: String?,
    uid: String? = null,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier,
    colors: NagiSemanticColors = NagiThemeTokens.colors,
    imageModel: Any? = avatarUrl,
    revisionKey: Any? = Unit,
    showBorder: Boolean = true,
) {
    val typography = NagiThemeTokens.typography
    val initialChar = name?.trim()?.firstOrNull()?.let { ch ->
        if (ch in 'a'..'z') ch.uppercaseChar() else ch
    }
    val tone = avatarToneFor(uid?.takeIf { it.isNotBlank() } ?: name, colors)
    val baseModifier = modifier
        .size(size)
        .clip(CircleShape)
        .background(tone.background)
        .let { m -> if (showBorder) m.border(0.75.dp, colors.cardBorder, CircleShape) else m }

    Box(
        modifier = baseModifier,
        contentAlignment = Alignment.Center,
    ) {
        @Composable
        fun Fallback() {
            if (initialChar != null) {
                val fontSize = (size.value * 0.42f).sp
                Text(
                    text = initialChar.toString(),
                    style = typography.capsuleLabel.copy(
                        fontSize = fontSize,
                        lineHeight = fontSize,
                        fontWeight = FontWeight.SemiBold,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    color = tone.foreground,
                )
            } else {
                Icon(
                    imageVector = NagiIcons.Person,
                    contentDescription = null,
                    tint = tone.foreground,
                    modifier = Modifier.size(size * 0.52f),
                )
            }
        }

        val url = avatarUrl?.trim().orEmpty()
        key(revisionKey) {
            if (url.isEmpty() || imageModel == null) {
                Fallback()
            } else {
                SubcomposeAsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = { Fallback() },
                    error = { Fallback() },
                )
            }
        }
    }
}
