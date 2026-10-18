package com.tejeet.carddrop.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Brand (matches the logo): teal primary, navy secondary.
val BrandTeal = Color(0xFF03D3C0)
val BrandTealDark = Color(0xFF019E90)
val BrandNavy = Color(0xFF404B69)
val BrandNavyDark = Color(0xFF2F3852)
/** Text/icons placed on BrandTeal - white on this light teal isn't readable enough. */
val OnBrandTeal = Color(0xFF0B2B33)

/** A status colour: soft [container] for backgrounds, readable [content] on top, and a strong [accent] for strips and dots. */
@Immutable
data class Tone(val container: Color, val content: Color, val accent: Color)

/** App-specific colours Material's scheme has no slot for (card states, lead priority, header). */
@Immutable
data class ExtendedColors(
    val brand: Tone,
    val navy: Tone,
    val review: Tone,
    val pending: Tone,
    val sent: Tone,
    val failed: Tone,
    val ai: Tone,
    val neutral: Tone,
    val hot: Tone,
    val warm: Tone,
    val cold: Tone,
    /** Top app bars (white content on top). */
    val topBar: Color,
    val headerGradient: List<Color>,
    val avatarPalette: List<Color>
)

private val AvatarPalette = listOf(
    BrandTealDark, BrandNavy, Color(0xFF0EA5E9), Color(0xFFF97316),
    Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFF5C6B8A), Color(0xFFE11D48)
)

val LightExtendedColors = ExtendedColors(
    brand = Tone(Color(0xFFCCF7F2), Color(0xFF00574F), BrandTeal),
    navy = Tone(Color(0xFFE4E8F2), BrandNavyDark, BrandNavy),
    review = Tone(Color(0xFFFEF3C7), Color(0xFF92400E), Color(0xFFF59E0B)),
    pending = Tone(Color(0xFFDBEAFE), Color(0xFF1E40AF), Color(0xFF3B82F6)),
    sent = Tone(Color(0xFFD1FAE5), Color(0xFF065F46), Color(0xFF10B981)),
    failed = Tone(Color(0xFFFEE2E2), Color(0xFF991B1B), Color(0xFFEF4444)),
    ai = Tone(Color(0xFFE4E8F2), BrandNavyDark, BrandNavy),
    neutral = Tone(Color(0xFFEEF1F6), Color(0xFF475569), Color(0xFF94A3B8)),
    hot = Tone(Color(0xFFFFE4E6), Color(0xFFBE123C), Color(0xFFF43F5E)),
    warm = Tone(Color(0xFFFFEDD5), Color(0xFFC2410C), Color(0xFFF97316)),
    cold = Tone(Color(0xFFE0F2FE), Color(0xFF0369A1), Color(0xFF0EA5E9)),
    topBar = BrandNavy,
    // Navy into a deeper shade of the brand teal, so white text stays readable across the whole header.
    headerGradient = listOf(BrandNavy, BrandTealDark),
    avatarPalette = AvatarPalette
)

val DarkExtendedColors = ExtendedColors(
    brand = Tone(Color(0xFF003D37), Color(0xFF7FF0E4), BrandTeal),
    navy = Tone(Color(0xFF262D42), Color(0xFFC3CAE0), Color(0xFF8C97B8)),
    review = Tone(Color(0xFF422006), Color(0xFFFCD34D), Color(0xFFF59E0B)),
    pending = Tone(Color(0xFF172554), Color(0xFF93C5FD), Color(0xFF3B82F6)),
    sent = Tone(Color(0xFF052E2B), Color(0xFF6EE7B7), Color(0xFF10B981)),
    failed = Tone(Color(0xFF450A0A), Color(0xFFFCA5A5), Color(0xFFEF4444)),
    ai = Tone(Color(0xFF262D42), Color(0xFFC3CAE0), Color(0xFF8C97B8)),
    neutral = Tone(Color(0xFF232838), Color(0xFFCBD5E1), Color(0xFF64748B)),
    hot = Tone(Color(0xFF4C0519), Color(0xFFFDA4AF), Color(0xFFF43F5E)),
    warm = Tone(Color(0xFF431407), Color(0xFFFDBA74), Color(0xFFF97316)),
    cold = Tone(Color(0xFF082F49), Color(0xFF7DD3FC), Color(0xFF0EA5E9)),
    topBar = BrandNavyDark,
    headerGradient = listOf(BrandNavyDark, Color(0xFF01786E)),
    avatarPalette = AvatarPalette
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
