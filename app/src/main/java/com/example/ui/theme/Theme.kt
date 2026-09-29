package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NexaPrimary,
    onPrimary = Color.White,
    primaryContainer = NexaDarkSurfaceVariant,
    onPrimaryContainer = NexaDarkTextPrimary,
    secondary = NexaSecondary,
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = NexaTertiary,
    onTertiary = Color.White,
    background = NexaDarkBackground,
    onBackground = NexaDarkTextPrimary,
    surface = NexaDarkSurface,
    onSurface = NexaDarkTextPrimary,
    surfaceVariant = NexaDarkSurfaceVariant,
    onSurfaceVariant = NexaDarkTextSecondary,
    outline = NexaDarkBorder,
    error = NexaError
)

private val LightColorScheme = lightColorScheme(
    primary = NexaPrimaryVariant,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF312E81),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = Color(0xFF9333EA),
    onTertiary = Color.White,
    background = NexaLightBackground,
    onBackground = NexaLightTextPrimary,
    surface = NexaLightSurface,
    onSurface = NexaLightTextPrimary,
    surfaceVariant = NexaLightSurfaceVariant,
    onSurfaceVariant = NexaLightTextSecondary,
    outline = NexaLightBorder,
    error = NexaError
)

@Composable
fun NexaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
