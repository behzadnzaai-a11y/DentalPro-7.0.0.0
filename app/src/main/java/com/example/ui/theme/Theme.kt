package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Exposes the currently-active [NeonPalette] to any composable, for the
 * extra glow/gradient effects that go beyond what MaterialTheme.colorScheme
 * can express (borders, blurred shadows, hero-banner gradients, etc). */
val LocalNeonPalette = compositionLocalOf { ThemeNeonTeal }

private fun buildDarkScheme(p: NeonPalette) = darkColorScheme(
    primary = p.primary,
    secondary = p.secondary,
    tertiary = p.tertiary,
    background = p.background,
    surface = p.surface,
    surfaceVariant = p.surfaceVariant,
    surfaceTint = p.primary,
    primaryContainer = p.primaryContainer,
    onPrimaryContainer = p.onPrimaryContainer,
    secondaryContainer = p.secondary.copy(alpha = 0.22f).compositeOverSurface(p.surface),
    onSecondaryContainer = p.onPrimaryContainer,
    tertiaryContainer = p.tertiary.copy(alpha = 0.20f).compositeOverSurface(p.surface),
    onTertiaryContainer = p.onPrimaryContainer,
    inversePrimary = p.glow,
    inverseSurface = p.onPrimaryContainer,
    inverseOnSurface = p.background,
    onPrimary = p.onPrimary,
    onSecondary = p.onPrimary,
    onTertiary = p.onPrimary,
    onSurface = Color(0xFFF2F2F7),
    onBackground = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFFCBCBD6),
    outline = p.primary.copy(alpha = 0.45f),
    outlineVariant = p.primary.copy(alpha = 0.18f),
    scrim = Color.Black
)

/** Blends a translucent theme color onto a solid surface so container roles
 * stay opaque (Material expects most container colors to be non-transparent). */
private fun Color.compositeOverSurface(surface: Color): Color {
    val a = this.alpha
    return Color(
        red = red * a + surface.red * (1 - a),
        green = green * a + surface.green * (1 - a),
        blue = blue * a + surface.blue * (1 - a),
        alpha = 1f
    )
}

private fun buildLightScheme(p: NeonPalette) = lightColorScheme(
    primary = p.primary,
    secondary = p.secondary,
    tertiary = p.tertiary,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = p.onPrimary
)

/** ۸ پله‌ی لغزنده‌ی سایز فونت کلی برنامه (تنظیمات اختصاصی). پله ۳ = حالت عادی. */
val GlobalFontScaleSteps = List(20) { index -> 0.775f + (index * 0.025f) }

/**
 * @param themeId one of [NeonPalette.id] (e.g. "NEON_PURPLE"). Falls back to
 * the classic amber theme for any unknown/blank value so older saved
 * settings never crash.
 * @param fontScaleIndex 0..7 — لغزنده‌ی سایز فونت کلی برنامه از تنظیمات اختصاصی
 * (صفحات دستورالعمل بالینی از این مقیاس مستثنی هستند و سایز خودشان را دارند).
 */
@Composable
fun MyApplicationTheme(
  themeId: String = ThemeNeonTeal.id,
  darkTheme: Boolean = true, // Default to the futuristic dark aesthetic
  dynamicColor: Boolean = false, // Keep our own neon brand identity
  fontScaleIndex: Int = 3,
  content: @Composable () -> Unit,
) {
  val palette = neonPaletteById(themeId)
  val colorScheme = if (darkTheme) buildDarkScheme(palette) else buildLightScheme(palette)
  val fontScaleMultiplier = GlobalFontScaleSteps.getOrElse(fontScaleIndex.coerceIn(0, 19)) { 1.0f }
  val baseDensity = LocalDensity.current
  val scaledDensity = Density(density = baseDensity.density, fontScale = baseDensity.fontScale * fontScaleMultiplier)

  CompositionLocalProvider(LocalNeonPalette provides palette, LocalDensity provides scaledDensity) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
