package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = BeaconCyan,
    onPrimary = BeaconNavyDark,
    primaryContainer = BeaconNavyCard,
    onPrimaryContainer = BeaconCyanGlow,
    secondary = BeaconEmerald,
    onSecondary = BeaconNavyDark,
    tertiary = BeaconAmber,
    error = BeaconRed,
    onError = BeaconTextLight,
    background = BeaconNavyDark,
    onBackground = BeaconTextLight,
    surface = BeaconNavy,
    onSurface = BeaconTextLight,
    surfaceVariant = BeaconNavyCard,
    onSurfaceVariant = BeaconTextMuted
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

