package com.example.reports

import androidx.compose.ui.graphics.toArgb
import com.example.ui.theme.NeonPalette
import com.example.ui.theme.ThemeAmberClassic
import com.example.ui.theme.GlobalFontScaleSteps

/**
 * Unified theme snapshot for all document exports (PDFs and WhatsApp card PNGs).
 * Captures active NeonPalette colors and font scaling so exported documents
 * dynamically mirror the selected app theme.
 */
data class ExportThemeSpec(
    val primaryArgb: Int,
    val secondaryArgb: Int,
    val tertiaryArgb: Int,
    val backgroundArgb: Int,
    val surfaceArgb: Int,
    val surfaceVariantArgb: Int,
    val primaryContainerArgb: Int,
    val onPrimaryContainerArgb: Int,
    val onPrimaryArgb: Int,
    val glowArgb: Int,
    val gradientStartArgb: Int,
    val gradientEndArgb: Int,
    val fontScaleMultiplier: Float = 1.0f
) {
    companion object {
        fun fromPalette(palette: NeonPalette, fontScaleIndex: Int = 3): ExportThemeSpec {
            val scale = GlobalFontScaleSteps.getOrElse(fontScaleIndex.coerceIn(0, 19)) { 1.0f }
            return ExportThemeSpec(
                primaryArgb = palette.primary.toArgb(),
                secondaryArgb = palette.secondary.toArgb(),
                tertiaryArgb = palette.tertiary.toArgb(),
                backgroundArgb = palette.background.toArgb(),
                surfaceArgb = palette.surface.toArgb(),
                surfaceVariantArgb = palette.surfaceVariant.toArgb(),
                primaryContainerArgb = palette.primaryContainer.toArgb(),
                onPrimaryContainerArgb = palette.onPrimaryContainer.toArgb(),
                onPrimaryArgb = palette.onPrimary.toArgb(),
                glowArgb = palette.glow.toArgb(),
                gradientStartArgb = palette.gradientStart.toArgb(),
                gradientEndArgb = palette.gradientEnd.toArgb(),
                fontScaleMultiplier = scale
            )
        }

        val Default: ExportThemeSpec by lazy { fromPalette(ThemeAmberClassic, 3) }
    }
}
