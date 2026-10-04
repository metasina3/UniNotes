package com.sina.uninotes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val UniColorScheme = darkColorScheme(
    primary = UniPrimary,
    onPrimary = UniText,
    secondary = UniAccent,
    onSecondary = UniBackground,
    background = UniBackground,
    onBackground = UniText,
    surface = UniSurface,
    onSurface = UniText,
    surfaceVariant = UniSurface,
    onSurfaceVariant = UniTextSecondary,
    error = UniDanger,
    onError = UniText,
)

@Composable
fun UniNotesTheme(content: @Composable () -> Unit) {
    // Keep the application shell LTR even on Persian devices.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        MaterialTheme(
            colorScheme = UniColorScheme,
            typography = UniTypography,
            content = content,
        )
    }
}
