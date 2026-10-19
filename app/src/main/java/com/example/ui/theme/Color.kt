package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ZEVN Futuristic Brand Palette
val ElectricBlue = Color(0xFF00AEEF)
val ElectricViolet = Color(0xFF6C63FF)
val NeonCyan = Color(0xFF00F0FF)
val NeonGreen = Color(0xFF00FF9D)
val NeonPink = Color(0xFFFF2A85)
val NeonAmber = Color(0xFFFFB800)

// Dark Theme Surfaces
val BackgroundDark = Color(0xFF0A0A0A)
val SurfaceDark = Color(0xFF131318)
val SurfaceVariantDark = Color(0xFF1B1B22)
val SurfaceElevated = Color(0xFF22222D)
val CardBorder = Color(0xFF262635)
val DividerDark = Color(0xFF1E1E28)

// Text Colors
val TextWhite = Color(0xFFFFFFFF)
val TextMuted = Color(0xFFA0A0B0)
val TextSecondary = Color(0xFF75758A)

// Gradients
val ZevnElectricGradient = Brush.horizontalGradient(
    colors = listOf(ElectricBlue, ElectricViolet)
)
val ZevnCyanPinkGradient = Brush.horizontalGradient(
    colors = listOf(NeonCyan, NeonPink)
)
val ZevnDarkCardGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF181822), Color(0xFF101015))
)
