package com.jnz.chess.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6B4E2F),
    onPrimary = Color.White,
    secondary = Color(0xFF8B7355),
    background = Color(0xFFF5F0E8),
    onBackground = Color(0xFF2C2C2C),
    surface = Color(0xFFF5F0E8),
    onSurface = Color(0xFF2C2C2C),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB8956A),
    onPrimary = Color.Black,
    secondary = Color(0xFF8B7355),
    background = Color(0xFF1A1A1A),
    onBackground = Color(0xFFE8E0D0),
    surface = Color(0xFF1A1A1A),
    onSurface = Color(0xFFE8E0D0),
)

@Composable
fun ChessDemoTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
