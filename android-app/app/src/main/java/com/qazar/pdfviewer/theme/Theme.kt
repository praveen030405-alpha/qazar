package com.qazar.pdfviewer.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Strictly Pure Light Theme (No Dark Mode as specified by architecture rules)
private val LightColorScheme = lightColorScheme(
    primary = PrimaryCobalt,
    onPrimary = PaperWhite,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = PrimaryCobaltHover,
    secondary = AccentTeal,
    onSecondary = PaperWhite,
    background = CanvasWorkspaceBg,
    onBackground = TextPrimary,
    surface = PaperWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceBorderSubtle,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceBorder
)

@Composable
fun MeridianTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
