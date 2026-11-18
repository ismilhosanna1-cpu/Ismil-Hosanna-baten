package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val YesminDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = MidnightElevated,
    onPrimaryContainer = TextPrimary,
    secondary = NeonViolet,
    onSecondary = TextPrimary,
    secondaryContainer = ColorTokens.SecondaryContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = NeonCyanGlow,
    onTertiary = ObsidianVoid,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = MidnightCard,
    onSurfaceVariant = TextSecondary,
    outline = MidnightBorder,
    outlineVariant = MidnightBorder,
    error = RosePulse,
    onError = TextPrimary
)

private object ColorTokens {
    val SecondaryContainer = androidx.compose.ui.graphics.Color(0xFF2B1D4E)
}

val YesminShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun YesminTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = YesminDarkColorScheme,
        typography = YesminTypography,
        shapes = YesminShapes,
        content = content
    )
}
