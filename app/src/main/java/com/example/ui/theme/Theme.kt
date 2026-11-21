package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val JarvisDarkColorScheme = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = JarvisObsidian,
    primaryContainer = JarvisPanelElevated,
    onPrimaryContainer = JarvisCyan,
    secondary = JarvisCobalt,
    onSecondary = JarvisTextPrimary,
    secondaryContainer = JarvisPanel,
    onSecondaryContainer = JarvisTextPrimary,
    tertiary = JarvisTeal,
    onTertiary = JarvisObsidian,
    background = JarvisObsidian,
    onBackground = JarvisTextPrimary,
    surface = JarvisDeepSpace,
    onSurface = JarvisTextPrimary,
    surfaceVariant = JarvisPanel,
    onSurfaceVariant = JarvisTextSecondary,
    outline = JarvisBorderGlow,
    outlineVariant = JarvisTextMuted,
    error = JarvisError,
    onError = JarvisObsidian
)

private val JarvisShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JarvisTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisDarkColorScheme,
        typography = JarvisTypography,
        shapes = JarvisShapes,
        content = content
    )
}
