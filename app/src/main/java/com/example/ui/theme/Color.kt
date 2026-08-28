package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// SEMANTIC / FUNCTIONAL COLORS (theme-independent — do NOT tie
// these to a selected theme: they carry meaning across the app:
// medical warnings, cheque status, gender avatars, tooth-chart
// anatomy, etc. Screens reference these constants directly.)
// ============================================================
val DebtColor = Color(0xFFFF453A)
val ClearColor = Color(0xFF30D158)

// ============================================================
// NEON THEME ENGINE
// Each palette drives the full Material3 color scheme AND a set
// of extra "glow" values used for neon borders/shadows/gradients
// on hero banners, selected cards, and accents.
// ============================================================

/**
 * A complete, self-contained visual theme. Every value here is
 * hand-tuned so the theme reads as one coherent neon family
 * rather than a single recolored accent on top of old chrome.
 */
data class NeonPalette(
    val id: String,
    val labelFa: String,
    val labelEn: String,
    // Core brand hues
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    // Surfaces
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    // Foreground on brand colors
    val onPrimary: Color,
    // Neon-specific extras
    val glow: Color,           // bright glow color for shadows/borders
    val gradientStart: Color,  // hero banner / accent gradient
    val gradientEnd: Color,
    val swatchDots: List<Color> // preview dots shown in the theme picker
)

val ThemeNeonTeal = NeonPalette(
    id = "NEON_TEAL",
    labelFa = "سبز-آبی (پیش‌فرض)",
    labelEn = "Teal (Default)",
    primary = Color(0xFF00B4D8),
    secondary = Color(0xFF48CAE4),
    tertiary = Color(0xFF0077B6),
    background = Color(0xFF0A1619),
    surface = Color(0xFF102227),
    surfaceVariant = Color(0xFF183239),
    primaryContainer = Color(0xFF005662),
    onPrimaryContainer = Color(0xFFD4F7FC),
    onPrimary = Color.Black,
    glow = Color(0xFF00B4D8),
    gradientStart = Color(0xFF005662),
    gradientEnd = Color(0xFF0A1619),
    swatchDots = listOf(Color(0xFF0077B6), Color(0xFF00B4D8), Color(0xFF48CAE4))
)

val ThemeAmberClassic = NeonPalette(
    id = "AMBER_CLASSIC",
    labelFa = "کهربایی کلاسیک",
    labelEn = "Amber Classic",
    primary = Color(0xFFFF9F0A),
    secondary = Color(0xFFFFB74D),
    tertiary = Color(0xFFFF7043),
    background = Color(0xFF0C0C0F),
    surface = Color(0xFF16161E),
    surfaceVariant = Color(0xFF22222E),
    primaryContainer = Color(0xFF3A2A0F),
    onPrimaryContainer = Color(0xFFFFE0B2),
    onPrimary = Color.Black,
    glow = Color(0xFFFFD60A),
    gradientStart = Color(0xFF3A2A0F),
    gradientEnd = Color(0xFF1A1408),
    swatchDots = listOf(Color(0xFFFF9F0A), Color(0xFFFFD60A), Color(0xFFFF7043))
)

val ThemeNeonPurple = NeonPalette(
    id = "NEON_PURPLE",
    labelFa = "بنفش مدرن",
    labelEn = "Modern Purple",
    primary = Color(0xFFB026FF),
    secondary = Color(0xFF8B5CF6),
    tertiary = Color(0xFFE0AAFF),
    background = Color(0xFF0A0612),
    surface = Color(0xFF160B24),
    surfaceVariant = Color(0xFF221232),
    primaryContainer = Color(0xFF2E1250),
    onPrimaryContainer = Color(0xFFEBD6FF),
    onPrimary = Color.White,
    glow = Color(0xFFD65DFF),
    gradientStart = Color(0xFF3A0F6E),
    gradientEnd = Color(0xFF130826),
    swatchDots = listOf(Color(0xFFB026FF), Color(0xFFD65DFF), Color(0xFF8B5CF6))
)

val ThemeNeonGold = NeonPalette(
    id = "NEON_GOLD",
    labelFa = "طلایی سلطنتی",
    labelEn = "Royal Gold",
    primary = Color(0xFFFFD700),
    secondary = Color(0xFFFFC400),
    tertiary = Color(0xFFFFF176),
    background = Color(0xFF0F0C04),
    surface = Color(0xFF1E1808),
    surfaceVariant = Color(0xFF2B2210),
    primaryContainer = Color(0xFF4A3B08),
    onPrimaryContainer = Color(0xFFFFF3C4),
    onPrimary = Color.Black,
    glow = Color(0xFFFFEA00),
    gradientStart = Color(0xFF4A3B08),
    gradientEnd = Color(0xFF1A1504),
    swatchDots = listOf(Color(0xFFFFD700), Color(0xFFFFEA00), Color(0xFFFFC400))
)

val ThemeNeonCopper = NeonPalette(
    id = "NEON_COPPER",
    labelFa = "مسی متالیک",
    labelEn = "Metallic Copper",
    primary = Color(0xFFFF6B35),
    secondary = Color(0xFFE8873A),
    tertiary = Color(0xFFFFA773),
    background = Color(0xFF130904),
    surface = Color(0xFF231208),
    surfaceVariant = Color(0xFF32190D),
    primaryContainer = Color(0xFF522210),
    onPrimaryContainer = Color(0xFFFFDCC4),
    onPrimary = Color.Black,
    glow = Color(0xFFFF8C42),
    gradientStart = Color(0xFF5C2408),
    gradientEnd = Color(0xFF1D0F05),
    swatchDots = listOf(Color(0xFFFF6B35), Color(0xFFFF8C42), Color(0xFFE8873A))
)

val ThemeNeonBlue = NeonPalette(
    id = "NEON_BLUE",
    labelFa = "آبی عمیق",
    labelEn = "Deep Blue",
    primary = Color(0xFF00B8FF),
    secondary = Color(0xFF2979FF),
    tertiary = Color(0xFF18FFFF),
    background = Color(0xFF03070F),
    surface = Color(0xFF08131F),
    surfaceVariant = Color(0xFF0E1E30),
    primaryContainer = Color(0xFF0A2C4A),
    onPrimaryContainer = Color(0xFFC9EFFF),
    onPrimary = Color.Black,
    glow = Color(0xFF00E5FF),
    gradientStart = Color(0xFF0A3A5C),
    gradientEnd = Color(0xFF061420),
    swatchDots = listOf(Color(0xFF00B8FF), Color(0xFF18FFFF), Color(0xFF2979FF))
)

val ThemeNeonSkyBlue = NeonPalette(
    id = "NEON_SKY_BLUE",
    labelFa = "آبی آسمانی",
    labelEn = "Sky Blue",
    primary = Color(0xFF40C4FF),
    secondary = Color(0xFF80D8FF),
    tertiary = Color(0xFF00B0FF),
    background = Color(0xFF0A1929),
    surface = Color(0xFF0F263D),
    surfaceVariant = Color(0xFF173859),
    primaryContainer = Color(0xFF0C3D66),
    onPrimaryContainer = Color(0xFFD6EFFF),
    onPrimary = Color.Black,
    glow = Color(0xFF40C4FF),
    gradientStart = Color(0xFF0C3D66),
    gradientEnd = Color(0xFF06111C),
    swatchDots = listOf(Color(0xFF00B0FF), Color(0xFF40C4FF), Color(0xFF80D8FF))
)

val ThemeNeonCarbon = NeonPalette(
    id = "NEON_CARBON",
    labelFa = "کاربنی عمیق",
    labelEn = "Deep Carbon",
    primary = Color(0xFF536DFE),
    secondary = Color(0xFF8C9EFF),
    tertiary = Color(0xFF3D5AFE),
    background = Color(0xFF080B12),
    surface = Color(0xFF10141D),
    surfaceVariant = Color(0xFF1A2130),
    primaryContainer = Color(0xFF182959),
    onPrimaryContainer = Color(0xFFD9E2FF),
    onPrimary = Color.Black,
    glow = Color(0xFF536DFE),
    gradientStart = Color(0xFF182959),
    gradientEnd = Color(0xFF05080E),
    swatchDots = listOf(Color(0xFF3D5AFE), Color(0xFF536DFE), Color(0xFF8C9EFF))
)

val ThemeClinicalClean = NeonPalette(
    id = "CLINICAL_CLEAN",
    labelFa = "کلینیکال روشن و مدرن",
    labelEn = "Clinical Clean Light",
    primary = Color(0xFF0284C7),
    secondary = Color(0xFF0EA5E9),
    tertiary = Color(0xFF0369A1),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F5F9),
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    onPrimary = Color.White,
    glow = Color(0xFF38BDF8),
    gradientStart = Color(0xFF0284C7),
    gradientEnd = Color(0xFF0369A1),
    swatchDots = listOf(Color(0xFF0284C7), Color(0xFF0EA5E9), Color(0xFF38BDF8))
)

val ThemeClinicalDarkUnit = NeonPalette(
    id = "CLINICAL_DARK_UNIT",
    labelFa = "تم ارگونومیک بالینی یونیت (Dark Unit)",
    labelEn = "Clinical Dark Unit Ergonomic",
    primary = Color(0xFF38BDF8),
    secondary = Color(0xFF818CF8),
    tertiary = Color(0xFF34D399),
    background = Color(0xFF090D16),
    surface = Color(0xFF111827),
    surfaceVariant = Color(0xFF1F2937),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFE0F2FE),
    onPrimary = Color(0xFF090D16),
    glow = Color(0xFF38BDF8),
    gradientStart = Color(0xFF0369A1),
    gradientEnd = Color(0xFF090D16),
    swatchDots = listOf(Color(0xFF38BDF8), Color(0xFF818CF8), Color(0xFF34D399))
)

val AllNeonPalettes = listOf(
    ThemeClinicalClean,
    ThemeClinicalDarkUnit,
    ThemeNeonGold,
    ThemeNeonCopper,
    ThemeNeonBlue,
    ThemeNeonSkyBlue,
    ThemeNeonCarbon
)

fun neonPaletteById(id: String?): NeonPalette =
    AllNeonPalettes.firstOrNull { it.id == id } ?: ThemeNeonTeal

// Light-mode fallback (app is designed dark-first; kept for completeness)
val LightBackground = Color(0xFFF8F9FC)
val LightSurface = Color(0xFFFFFFFF)
