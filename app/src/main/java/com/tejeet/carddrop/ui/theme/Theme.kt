package com.tejeet.carddrop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = BrandTeal,
    onPrimary = OnBrandTeal,
    primaryContainer = Color(0xFFCCF7F2),
    onPrimaryContainer = Color(0xFF00413B),
    secondary = BrandNavy,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E8F2),
    onSecondaryContainer = BrandNavyDark,
    tertiary = BrandTealDark,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCF7F2),
    onTertiaryContainer = Color(0xFF00413B),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEEF1F7),
    onSurfaceVariant = Color(0xFF5B6475),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBFD),
    surfaceContainer = Color(0xFFF3F5FA),
    surfaceContainerHigh = Color(0xFFEDF0F7),
    surfaceContainerHighest = Color(0xFFE6EAF3),
    outline = Color(0xFFCBD2E0),
    outlineVariant = Color(0xFFE2E7F0),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColors = darkColorScheme(
    primary = BrandTeal,
    onPrimary = OnBrandTeal,
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFFB3F5EE),
    secondary = Color(0xFFAEB8D6),
    onSecondary = Color(0xFF1C2336),
    secondaryContainer = BrandNavy,
    onSecondaryContainer = Color(0xFFE4E8F2),
    tertiary = Color(0xFF7FF0E4),
    onTertiary = Color(0xFF00332E),
    tertiaryContainer = Color(0xFF00504A),
    onTertiaryContainer = Color(0xFFB3F5EE),
    background = Color(0xFF0F1220),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF171A2B),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF232741),
    onSurfaceVariant = Color(0xFFB6BDD1),
    surfaceContainerLowest = Color(0xFF0B0D18),
    surfaceContainerLow = Color(0xFF151827),
    surfaceContainer = Color(0xFF1B1E30),
    surfaceContainerHigh = Color(0xFF232739),
    surfaceContainerHighest = Color(0xFF2B2F44),
    outline = Color(0xFF3A405C),
    outlineVariant = Color(0xFF2A2F47),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Access to [ExtendedColors] alongside MaterialTheme, e.g. `AppTheme.colors.sent`. */
object AppTheme {
    val colors: ExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}

@Composable
fun CardDropTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
