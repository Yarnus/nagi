package dev.nagi.app.ui.theme

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val dockHeight = 52.dp
}

object GoldAccent {
    val decorative = Color(0xFFC09D73)
    val lightAccent = Color(0xFFB58838)
    val lightSolidFill = Color(0xFFB8863B)
    val lightText = Color(0xFF8C651E)
    val darkAccent = Color(0xFFD6BA8B)
    val darkText = Color(0xFFE5C494)
}

@Immutable
data class NagiSemanticColors(
    val isDark: Boolean,
    val groupedCanvas: Color,
    val cardSurface: Color,
    val cardBorder: Color,
    val insetContainer: Color,
    val frostedChrome: Color,
    val frostedDropdown: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val goldAccent: Color,
    val goldSolidFill: Color,
    val goldSolidContent: Color,
    val goldSolidText: Color,
    val goldContainer: Color,
    val goldText: Color,
    val goldBorder: Color,
    val destructive: Color,
    val destructiveContainer: Color,
    val switchInactiveTrack: Color,
    val switchActiveTrack: Color,
) {
    val chromeSurface: Color get() = frostedChrome
    val subtleContainer: Color get() = insetContainer
    val selectedRowFill: Color get() = goldContainer
    val selectedControlFill: Color get() = goldContainer
    val selectedControlText: Color get() = goldText
}

val LightSemanticColors = NagiSemanticColors(
    isDark = false,
    groupedCanvas = Color(0xFFF6F7F9),
    cardSurface = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE8EAED),
    insetContainer = Color(0xFFF0F2F5),
    frostedChrome = Color(0xFFFFFFFF).copy(alpha = 0.85f),
    frostedDropdown = Color(0xFFFFFFFF).copy(alpha = 0.96f),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF4B5563),
    textMuted = Color(0xFF9CA3AF),
    goldAccent = GoldAccent.lightAccent,
    goldSolidFill = GoldAccent.lightSolidFill,
    goldSolidContent = Color(0xFFFFFFFF),
    goldSolidText = Color(0xFF18120A),
    goldContainer = Color(0xFFFBF5EB),
    goldText = GoldAccent.lightText,
    goldBorder = Color(0xFFEAD9BA),
    destructive = Color(0xFFDC2626),
    destructiveContainer = Color(0xFFFEF2F2),
    switchInactiveTrack = Color(0xFFE5E7EB),
    switchActiveTrack = Color(0xFFC09D73),
)

val DarkSemanticColors = NagiSemanticColors(
    isDark = true,
    groupedCanvas = Color(0xFF0D0E11),
    cardSurface = Color(0xFF18191D),
    cardBorder = Color(0xFF26282E),
    insetContainer = Color(0xFF22242A),
    frostedChrome = Color(0xFF18191D).copy(alpha = 0.88f),
    frostedDropdown = Color(0xFF18191D).copy(alpha = 0.96f),
    textPrimary = Color(0xFFF3F4F6),
    textSecondary = Color(0xFF9CA3AF),
    textMuted = Color(0xFF6B7280),
    goldAccent = GoldAccent.darkAccent,
    goldSolidFill = GoldAccent.darkAccent,
    goldSolidContent = Color(0xFF18120A),
    goldSolidText = Color(0xFF18120A),
    goldContainer = Color(0xFF2D251A),
    goldText = GoldAccent.darkText,
    goldBorder = Color(0xFF4A3B24),
    destructive = Color(0xFFF87171),
    destructiveContainer = Color(0xFF2E1719),
    switchInactiveTrack = Color(0xFF2A2C33),
    switchActiveTrack = Color(0xFFD6BA8B),
)

@Immutable
data class NagiTypographyTokens(
    val largeTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    val appBarTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    val sectionTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    val sectionHeader: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
    ),
    val topicTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.5.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    val cardTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    val postBody: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    val bodyText: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    val tagBadge: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    val replyCount: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
    ),
    val meta: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    val metaTabular: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
    ),
    val capsuleButton: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
    ),
) {
    val body: TextStyle get() = bodyText
    val metadata: TextStyle get() = metaTabular
    val capsuleLabel: TextStyle get() = capsuleButton
    val badge: TextStyle get() = replyCount
}

@Immutable
data class NagiShapeTokens(
    val capsule: RoundedCornerShape = CircleShape,
    val flatCard: RoundedCornerShape = RoundedCornerShape(20.dp),
    val topicCard: RoundedCornerShape = RoundedCornerShape(16.dp),
    val innerCard: RoundedCornerShape = RoundedCornerShape(12.dp),
    val dropdown: RoundedCornerShape = RoundedCornerShape(20.dp),
    val sheet: RoundedCornerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
) {
    val cardGroup: RoundedCornerShape get() = flatCard
    val bottomSheet: RoundedCornerShape get() = sheet
}

val LocalNagiColors = staticCompositionLocalOf { LightSemanticColors }
val LocalNagiTypography = staticCompositionLocalOf { NagiTypographyTokens() }
val LocalNagiShapes = staticCompositionLocalOf { NagiShapeTokens() }

object NagiThemeTokens {
    val colors: NagiSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNagiColors.current

    val typography: NagiTypographyTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalNagiTypography.current

    val shapes: NagiShapeTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalNagiShapes.current
}

private val NagiMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val DefaultTypeTokens = NagiTypographyTokens()

private val NagiTypography = Typography(
    headlineMedium = DefaultTypeTokens.largeTitle,
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    titleMedium = DefaultTypeTokens.topicTitle,
    titleSmall = DefaultTypeTokens.cardTitle,
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    bodyMedium = DefaultTypeTokens.bodyText,
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    labelLarge = DefaultTypeTokens.capsuleButton,
    labelMedium = DefaultTypeTokens.meta,
    labelSmall = DefaultTypeTokens.tagBadge,
)

@Composable
fun NagiTheme(theme: String = "system", content: @Composable () -> Unit) {
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val activity = LocalActivity.current as? ComponentActivity
    SideEffect {
        val style = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    val semantic = if (dark) DarkSemanticColors else LightSemanticColors
    val typeTokens = DefaultTypeTokens
    val shapeTokens = NagiShapeTokens()

    val colors = if (dark) {
        darkColorScheme(
            primary = semantic.goldText,
            onPrimary = Color(0xFF18120A),
            primaryContainer = semantic.goldContainer,
            onPrimaryContainer = semantic.goldText,
            secondary = semantic.textSecondary,
            onSecondary = Color(0xFF18191D),
            secondaryContainer = semantic.insetContainer,
            onSecondaryContainer = semantic.textPrimary,
            background = semantic.groupedCanvas,
            onBackground = semantic.textPrimary,
            surface = semantic.cardSurface,
            onSurface = semantic.textPrimary,
            surfaceVariant = semantic.insetContainer,
            onSurfaceVariant = semantic.textSecondary,
            surfaceContainerLowest = semantic.groupedCanvas,
            surfaceContainerLow = semantic.cardSurface,
            surfaceContainer = semantic.insetContainer,
            surfaceContainerHigh = Color(0xFF26282E),
            surfaceContainerHighest = Color(0xFF2E3138),
            outline = semantic.cardBorder,
            outlineVariant = semantic.cardBorder,
            error = semantic.destructive,
            onError = Color(0xFF2E0B0E),
            errorContainer = semantic.destructiveContainer,
            onErrorContainer = semantic.destructive,
            scrim = Color.Black.copy(alpha = 0.56f),
        )
    } else {
        lightColorScheme(
            primary = semantic.goldText,
            onPrimary = Color.White,
            primaryContainer = semantic.goldContainer,
            onPrimaryContainer = semantic.goldText,
            secondary = semantic.textSecondary,
            onSecondary = Color.White,
            secondaryContainer = semantic.insetContainer,
            onSecondaryContainer = semantic.textPrimary,
            background = semantic.groupedCanvas,
            onBackground = semantic.textPrimary,
            surface = semantic.cardSurface,
            onSurface = semantic.textPrimary,
            surfaceVariant = semantic.insetContainer,
            onSurfaceVariant = semantic.textSecondary,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = semantic.groupedCanvas,
            surfaceContainer = semantic.insetContainer,
            surfaceContainerHigh = Color(0xFFE8EAED),
            surfaceContainerHighest = Color(0xFFDDE0E5),
            outline = semantic.cardBorder,
            outlineVariant = semantic.cardBorder,
            error = semantic.destructive,
            onError = Color.White,
            errorContainer = semantic.destructiveContainer,
            onErrorContainer = Color(0xFF991B1B),
            scrim = Color.Black.copy(alpha = 0.36f),
        )
    }

    CompositionLocalProvider(
        LocalNagiColors provides semantic,
        LocalNagiTypography provides typeTokens,
        LocalNagiShapes provides shapeTokens,
    ) {
        MaterialTheme(
            colorScheme = colors,
            shapes = NagiMaterialShapes,
            typography = NagiTypography,
            content = content,
        )
    }
}
