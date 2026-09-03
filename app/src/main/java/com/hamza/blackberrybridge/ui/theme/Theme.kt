package com.hamza.blackberrybridge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val ElegantDarkColorScheme = darkColorScheme(
    primary = AccentGreen,
    onPrimary = TextWhite,
    secondary = IconBlue,
    tertiary = IconOrange,
    background = BgMain,
    surface = BgCard,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = BgInner,
    onSurfaceVariant = TextMuted,
    error = AccentRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme
    dynamicColor: Boolean = false, // Disable dynamic color to enforce our elegant dark theme
    content: @Composable () -> Unit,
) {
    val colorScheme = ElegantDarkColorScheme

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
