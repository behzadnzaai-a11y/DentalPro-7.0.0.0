package com.example.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Clean Material 3 style helper without exaggerated neon glow/blur.
 */
fun Modifier.neonGlow(
    color: Color = Color.Transparent,
    shape: Shape = RoundedCornerShape(12.dp),
    elevation: Dp = 0.dp,
): Modifier = this

/** A clean, crisp border in the theme's primary/accent color. */
fun Modifier.neonBorder(
    color: Color,
    shape: Shape = RoundedCornerShape(12.dp),
    width: Dp = 1.dp,
): Modifier = this.border(width = width, color = color.copy(alpha = 0.6f), shape = shape)

/** Clean gradient for top cards and banners. */
@Composable
fun NeonPalette.heroBrush(): Brush = Brush.linearGradient(
    colors = listOf(surfaceVariant, surface, background)
)

/** Subtle clean gradient for accent headers. */
@Composable
fun NeonPalette.glowRadialBrush(): Brush = Brush.radialGradient(
    colors = listOf(primary.copy(alpha = 0.12f), Color.Transparent)
)

