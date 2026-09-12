package com.qazar.pdfviewer.theme

import androidx.compose.ui.graphics.Color

// Qazar Architectural Red Palette (Subtle Rose-White Canvas with Crimson Accents)
val CanvasWorkspaceBg = Color(0xFFFAF6F6)    // Subtle red-tinted white canvas (looks crisp white with subtle warmth)
val PaperWhite = Color(0xFFFFFFFF)
val PageShadow = Color(0x140F172A)
val SurfaceBorder = Color(0xFFE2E8F0)
val SurfaceBorderSubtle = Color(0xFFEDF2F7)

// Phase 4: Eye-Comfort Substrates (Linear-Light Adapted)
val PaperSubstrate = Color(0xFFFBF9F5)     // 5500 K warm ivory paper
val PaperCanvasBg = Color(0xFFF3EFEA)      // Warm architectural canvas
val PaperBorder = Color(0xFFE5DFD5)        // Subtle paper border

val SepiaSubstrate = Color(0xFFF4EFEA)     // 4500 K archival book parchment
val SepiaCanvasBg = Color(0xFFEAE3DB)      // Amber reading canvas
val SepiaBorder = Color(0xFFDDD2C4)        // Warm book border

// Phase 4+: Far-Advanced Comfortness Substrates (OLED Midnight & E-Ink Slate)
val NightSubstrate = Color(0xFF0D0D11)     // Pure velvet dark substrate for OLED
val NightCanvasBg = Color(0xFF060608)      // Deep pitch-black reading backdrop
val NightBorder = Color(0x33EF4444)        // Subtle crimson boundary

val EinkSubstrate = Color(0xFFF1EFEA)      // Carta Paperwhite matte substrate
val EinkCanvasBg = Color(0xFFE2DFD7)       // Anti-glare reading environment
val EinkBorder = Color(0xFFCAC5BA)         // Soft stone edge

val PrimaryCobalt = Color(0xFFDC2626)       // Primary Red Accent (Crimson #DC2626)
val PrimaryCobaltHover = Color(0xFFB91C1C)  // Darker Ruby Red
val PrimaryLight = Color(0xFFFEE2E2)        // Soft Rose Red Tint
val PrimaryRed = Color(0xFFDC2626)          // Brand Crimson
val PrimaryRedLight = Color(0xFFFEE2E2)     // Brand Tint

val TextPrimary = Color(0xFF0F172A)
val TextSecondary = Color(0xFF475569)
val TextMuted = Color(0xFF94A3B8)

val SearchHighlightYellow = Color(0x80FEF08A)
val SearchHighlightBorder = Color(0xFFEAB308)
val SuccessGreen = Color(0xFF10B981)
val AccentTeal = Color(0xFF0284C7)

enum class ViewingMode(
    val label: String,
    val description: String,
    val cct: String,
    val blueReduction: String,
    val swatchColor: Color,
    val textColor: Color
) {
    DEFAULT(
        label = "Studio D65",
        description = "Crisp daylight contrast",
        cct = "6500 K",
        blueReduction = "0%",
        swatchColor = Color(0xFFFFFFFF),
        textColor = Color(0xFF0F172A)
    ),
    PAPER(
        label = "Ivory Paper",
        description = "Warm physical book substrate",
        cct = "5500 K",
        blueReduction = "18.5%",
        swatchColor = Color(0xFFFBF9F5),
        textColor = Color(0xFF2C251D)
    ),
    SEPIA(
        label = "Archival Sepia",
        description = "Golden parchment & chocolate ink",
        cct = "4500 K",
        blueReduction = "32.0%",
        swatchColor = Color(0xFFF4EFEA),
        textColor = Color(0xFF3E2718)
    ),
    NIGHT(
        label = "OLED Midnight",
        description = "Pitch-black background & warm pearl text",
        cct = "2700 K",
        blueReduction = "84.2%",
        swatchColor = Color(0xFF0D0D11),
        textColor = Color(0xFFE2E8F0)
    ),
    EINK(
        label = "E-Ink Slate",
        description = "Carta matte paper with zero glare",
        cct = "5000 K",
        blueReduction = "22.4%",
        swatchColor = Color(0xFFF1EFEA),
        textColor = Color(0xFF1E2220)
    )
}
