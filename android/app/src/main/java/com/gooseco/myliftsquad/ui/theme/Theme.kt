package com.gooseco.myliftsquad.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Blue800,
    onPrimary = OnPrimaryLight,
    primaryContainer = Blue100,
    onPrimaryContainer = Blue900,
    secondary = Blue600,
    onSecondary = Color.White,
    secondaryContainer = Blue50,
    onSecondaryContainer = Blue900,
    background = BackgroundLight,
    onBackground = Color(0xFF1C1B1F),
    surface = SurfaceLight,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE8EAF6),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    error = Color(0xFFB00020),
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue200,
    onPrimary = Blue900,
    primaryContainer = Blue800,
    onPrimaryContainer = Blue100,
    secondary = Blue200,
    onSecondary = Blue900,
    secondaryContainer = Blue700,
    onSecondaryContainer = Blue50,
    background = BackgroundDark,
    onBackground = Color(0xFFE6E1E5),
    surface = SurfaceDark,
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF1E2A3A),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    error = Color(0xFFCF6679),
    onError = Color(0xFF370014),
)

@Composable
fun MyLiftSquadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
