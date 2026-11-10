package com.music.bitchord.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.music.bitchord.R

// Apple Music's signature red. Kept for Replay rank badges that want that
// specific red regardless of the Material You seed.
val AccentRed = Color(0xFFFA2D48)

/**
 * Static Material You fallback when wallpaper colors are unavailable
 * (Android 11 and below, or the setting turned off). Terracotta seed — the
 * same tonal family as Material 3's music-app examples.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF8F4C33),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF77574C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF2C160D),
    tertiary = Color(0xFF6B5D2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF4E2A7),
    onTertiaryContainer = Color(0xFF221B00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231A16),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231A16),
    surfaceVariant = Color(0xFFF5DED6),
    onSurfaceVariant = Color(0xFF53433E),
    outline = Color(0xFF85736D),
    outlineVariant = Color(0xFFD8C2BB),
    inverseSurface = Color(0xFF392E2B),
    inverseOnSurface = Color(0xFFFFEDE7),
    inversePrimary = Color(0xFFFFB59A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EC),
    surfaceContainer = Color(0xFFFCEAE4),
    surfaceContainerHigh = Color(0xFFF6E4DE),
    surfaceContainerHighest = Color(0xFFF0DFD8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB59A),
    onPrimary = Color(0xFF55200A),
    primaryContainer = Color(0xFF72351E),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFE7BDB0),
    onSecondary = Color(0xFF442A20),
    secondaryContainer = Color(0xFF5D4035),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFD7C68D),
    onTertiary = Color(0xFF3A2F05),
    tertiaryContainer = Color(0xFF524619),
    onTertiaryContainer = Color(0xFFF4E2A7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A110E),
    onBackground = Color(0xFFF0DFD8),
    surface = Color(0xFF1A110E),
    onSurface = Color(0xFFF0DFD8),
    surfaceVariant = Color(0xFF53433E),
    onSurfaceVariant = Color(0xFFD8C2BB),
    outline = Color(0xFFA08D86),
    outlineVariant = Color(0xFF53433E),
    inverseSurface = Color(0xFFF0DFD8),
    inverseOnSurface = Color(0xFF392E2B),
    inversePrimary = Color(0xFF8F4C33),
    surfaceContainerLowest = Color(0xFF140C0A),
    surfaceContainerLow = Color(0xFF231A16),
    surfaceContainer = Color(0xFF271E1A),
    surfaceContainerHigh = Color(0xFF322824),
    surfaceContainerHighest = Color(0xFF3D332F),
)

private val BitChordShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Material 3 type, with [Typography.displayLarge] kept at headline size so
 * existing screen titles ("Listen Now", "Library") do not jump to 57sp.
 */
private val BitChordTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(
            fontSize = 32.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
        ),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Normal),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Normal),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Medium),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Medium),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.Medium),
    )
}

/** Bundled SF Pro. The Material You theme uses the platform typeface instead. */
val SFProDisplay = FontFamily(
    Font(R.font.sf_pro_display_regular, FontWeight.W400),
    Font(R.font.sf_pro_display_medium, FontWeight.W500),
    Font(R.font.sf_pro_display_semibold, FontWeight.W600),
    Font(R.font.sf_pro_display_bold, FontWeight.W700),
    Font(R.font.sf_pro_display_heavy, FontWeight.W800),
)

@Composable
fun BitChordTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = BitChordTypography,
        shapes = BitChordShapes,
        content = content,
    )
}

/**
 * Draws the status and navigation bar glyphs dark or light.
 *
 * `enableEdgeToEdge()` decides this from the *system* dark-mode setting, which
 * is the wrong input the moment the in-app theme disagrees with it: Light theme
 * on a phone in dark mode left white icons on a white bar, invisible. The bars
 * have to follow the theme the app is actually painting — with one exception,
 * the player, which is dark artwork regardless and so always wants light
 * glyphs. Hence a parameter rather than reading the theme here.
 */
@Composable
fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.context as? Activity)?.window ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = dark
            isAppearanceLightNavigationBars = dark
        }
    }
}
