package com.door43.translationstudio.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// --- Brand Colors ---
val PrimaryBlueLight = Color(0xFF0250D3)
val PrimaryBlueDark = Color(0xFF6A91D3)
val PrimaryDarkBlue = Color(0xFF003389)
val PrimaryDarkBlueMuted = Color(0xFF445E89)
val PrimaryLightBlue = Color(0xFFE2F0FF)

// --- Accent (Secondary) Colors ---
val AccentGreenLight = Color(0xFF00A56C)
val AccentLightGreenLight = Color(0xFFC0E9D6)
val AccentGreenDark = Color(0xFF52A588)
val AccentLightGreenDark = Color(0xFF8EA89E)

// --- Tertiary Colors ---
val TertiaryBlueLight = Color(0xFFA5E4FF)
val TertiaryLightBlueLight = Color(0xFFE2F0FF)
val TertiaryBlueDark = Color(0xFF51AFC7)
val TertiaryLightBlueDark = Color(0xFF92B4CE)
val TertiaryTextLight = Color(0xFF272727)

// --- Backgrounds & Surfaces ---
val BackgroundLight = Color(0xFFEFEFEF)
val BackgroundDark = Color(0xFF1C1C1C)
val SurfaceLight = Color(0xFFF2F2F2)
val SurfaceDark = Color(0xFF272727)

// --- Text Colors ---
val TextPrimaryDark = Color(0xFF1C1C1C)
val TextPrimaryLight = Color(0xFFD2D2D2)
val TextSecondaryDark = Color(0xFF888888)
val TextSecondaryLight = Color(0xFFA4A4A4)
val TextReverseLight = Color(0xFFFFFFFF)
val TextReverseDark = Color(0xFF1C1C1C)

// --- Borders & Extras ---
val BorderLight = Color(0xFFDDDDDD)
val BorderDark = Color(0xFF333333)
val ErrorLight = Color(0xFFFF0000)
val ErrorDark = Color(0xFFFF8080)
val WarningLight = Color(0xFFFF9800)
val WarningDark = Color(0xFFFFCB80)

private val LightColors = lightColorScheme(
    primary = PrimaryBlueLight,
    onPrimary = TextReverseLight,
    primaryContainer = PrimaryLightBlue,
    onPrimaryContainer = PrimaryDarkBlue,

    secondary = AccentGreenLight,
    onSecondaryContainer = AccentLightGreenLight,
    onSecondary = TextReverseLight,

    tertiary = TertiaryBlueLight,
    tertiaryContainer = TertiaryLightBlueLight,
    onTertiary = TertiaryTextLight,

    background = BackgroundLight,
    onBackground = TextPrimaryDark,

    surface = SurfaceLight,
    onSurface = TextPrimaryDark,
    surfaceVariant = BorderLight,
    onSurfaceVariant = TextSecondaryDark,

    error = ErrorLight,
    errorContainer = WarningLight,
    onError = TextReverseLight,
    onErrorContainer = SurfaceLight
)

private val DarkColors = darkColorScheme(
    primary = PrimaryBlueDark,
    onPrimary = TextReverseDark,
    primaryContainer = PrimaryDarkBlueMuted,
    onPrimaryContainer = PrimaryLightBlue,

    secondary = AccentGreenDark,
    onSecondaryContainer = AccentLightGreenDark,
    onSecondary = TextPrimaryLight,

    tertiary = TertiaryBlueDark,
    tertiaryContainer = TertiaryLightBlueDark,
    onTertiary = TertiaryTextLight,

    background = BackgroundDark,
    onBackground = TextPrimaryLight,

    surface = SurfaceDark,
    onSurface = TextPrimaryLight,
    surfaceVariant = BorderDark,
    onSurfaceVariant = TextSecondaryLight,

    error = ErrorDark,
    errorContainer = WarningDark,
    onError = TextReverseDark,
    onErrorContainer = SurfaceDark
)

val AppTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),

    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),

    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = Shapes(),
        content = content
    )
}