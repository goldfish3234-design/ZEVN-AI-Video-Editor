package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ZevnDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = BackgroundDark,
    primaryContainer = Color(0xFF003852),
    onPrimaryContainer = NeonCyan,
    secondary = ElectricViolet,
    onSecondary = TextWhite,
    secondaryContainer = Color(0xFF282363),
    onSecondaryContainer = Color(0xFFD6D1FF),
    tertiary = NeonPink,
    onTertiary = TextWhite,
    background = BackgroundDark,
    onBackground = TextWhite,
    surface = SurfaceDark,
    onSurface = TextWhite,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextMuted,
    outline = CardBorder,
    outlineVariant = DividerDark,
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to ZEVN signature dark theme
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                it.statusBarColor = BackgroundDark.toArgb()
                it.navigationBarColor = BackgroundDark.toArgb()
                val controller = WindowCompat.getInsetsController(it, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = ZevnDarkColorScheme,
        typography = Typography,
        content = content
    )
}
