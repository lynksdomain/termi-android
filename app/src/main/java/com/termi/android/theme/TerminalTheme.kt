package com.termi.android.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class TerminalTheme(
    val id: String,
    val displayName: String,
    val background: Color,
    val foreground: Color,
    val accent: Color,
    val secondary: Color,
    val dim: Color,
    val cursor: Color,
    val selectionBackground: Color,
    val barColors: List<Color>,
    val glowIntensity: Float,
    val isPremium: Boolean,
)

// Full port of Theme.swift's built-in catalog. `isPremium` is kept for
// parity/display (BASE vs PRO section split in the theme picker) but is
// NOT enforced as a lock anywhere — Lifetime Pro gating is intentionally
// deferred app-wide (see android_port_rules memory), so every theme here
// is freely selectable regardless of this flag.

val AmberTerminal = TerminalTheme(
    id = "amber_terminal",
    displayName = "Amber Terminal",
    background = Color(0xFF0D0800),
    foreground = Color(0xFFFFB000),
    accent = Color(0xFFFFB000),
    secondary = Color(0xFFFF8C00),
    dim = Color(0xFF7A5400),
    cursor = Color(0xFFFFB000),
    selectionBackground = Color(0xFF3D2B00),
    barColors = listOf(Color(0xFFFFB000), Color(0xFFFF8C00), Color(0xFFFFC200)),
    glowIntensity = 0.70f,
    isPremium = false,
)

val AppleII = TerminalTheme(
    id = "apple_ii",
    displayName = "Apple ][",
    background = Color(0xFF0A0F0A),
    foreground = Color(0xFF33FF57),
    accent = Color(0xFF33FF57),
    secondary = Color(0xFF57FF33),
    dim = Color(0xFF1A6B2A),
    cursor = Color(0xFF33FF57),
    selectionBackground = Color(0xFF0A2E10),
    barColors = listOf(Color(0xFF33FF57), Color(0xFF57FF33), Color(0xFF00FF88)),
    glowIntensity = 0.80f,
    isPremium = false,
)

val VirtualBoy = TerminalTheme(
    id = "virtual_boy",
    displayName = "Virtual Boy",
    background = Color(0xFF0D0000),
    foreground = Color(0xFFFF1111),
    accent = Color(0xFFFF3333),
    secondary = Color(0xFFCC0000),
    dim = Color(0xFF8C2020),
    cursor = Color(0xFFFF4444),
    selectionBackground = Color(0xFF2A0000),
    barColors = listOf(Color(0xFFFF1111), Color(0xFFCC0000), Color(0xFFFF4444)),
    glowIntensity = 0.85f,
    isPremium = false,
)

val WhitePhosphor = TerminalTheme(
    id = "white_phosphor",
    displayName = "White Phosphor",
    background = Color(0xFF0C0C0C),
    foreground = Color(0xFFE8E8D0),
    accent = Color(0xFFFFFFFF),
    secondary = Color(0xFFC8C8B0),
    dim = Color(0xFF6E6E60),
    cursor = Color(0xFFFFFFFF),
    selectionBackground = Color(0xFF2A2A25),
    barColors = listOf(Color(0xFFFFFFFF), Color(0xFFE8E8D0), Color(0xFFC8C8B0)),
    glowIntensity = 0.45f,
    isPremium = false,
)

val RosePine = TerminalTheme(
    id = "rose_pine",
    displayName = "Rosé Pine",
    background = Color(0xFF191724),
    foreground = Color(0xFFE0DEF4),
    accent = Color(0xFFEBBCBA),
    secondary = Color(0xFFEB6F92),
    dim = Color(0xFF6E6A86),
    cursor = Color(0xFFEBBCBA),
    selectionBackground = Color(0xFF26233A),
    barColors = listOf(Color(0xFFEBBCBA), Color(0xFFEB6F92), Color(0xFFC4A7E7)),
    glowIntensity = 0.20f,
    isPremium = false,
)

val Dracula = TerminalTheme(
    id = "dracula",
    displayName = "Dracula",
    background = Color(0xFF282A36),
    foreground = Color(0xFFF8F8F2),
    accent = Color(0xFFBD93F9),
    secondary = Color(0xFFFF79C6),
    dim = Color(0xFF6272A4),
    cursor = Color(0xFFBD93F9),
    selectionBackground = Color(0xFF44475A),
    barColors = listOf(Color(0xFFBD93F9), Color(0xFFFF79C6), Color(0xFF8BE9FD)),
    glowIntensity = 0.25f,
    isPremium = true,
)

val SolarizedDark = TerminalTheme(
    id = "solarized_dark",
    displayName = "Solarized Dark",
    background = Color(0xFF002B36),
    foreground = Color(0xFF839496),
    accent = Color(0xFF268BD2),
    secondary = Color(0xFF2AA198),
    dim = Color(0xFF586E75),
    cursor = Color(0xFF839496),
    selectionBackground = Color(0xFF073642),
    barColors = listOf(Color(0xFF268BD2), Color(0xFF2AA198), Color(0xFF859900)),
    glowIntensity = 0.20f,
    isPremium = true,
)

val SolarizedLight = TerminalTheme(
    id = "solarized_light",
    displayName = "Solarized Light",
    background = Color(0xFFFDF6E3),
    foreground = Color(0xFF3D5460),
    accent = Color(0xFF1A6EBF),
    secondary = Color(0xFF1A6B68),
    dim = Color(0xFF5A6E75),
    cursor = Color(0xFF3D5460),
    selectionBackground = Color(0xFFEEE8D5),
    barColors = listOf(Color(0xFF268BD2), Color(0xFF2AA198), Color(0xFF859900)),
    glowIntensity = 0.0f,
    isPremium = true,
)

val IbmBlue = TerminalTheme(
    id = "ibm_blue",
    displayName = "IBM Blue",
    background = Color(0xFF000080),
    foreground = Color(0xFFFFFFFF),
    accent = Color(0xFF00FFFF),
    secondary = Color(0xFFFFFF00),
    dim = Color(0xFF8080FF),
    cursor = Color(0xFFFFFFFF),
    selectionBackground = Color(0xFF0000C0),
    barColors = listOf(Color(0xFF00FFFF), Color(0xFFFFFF00), Color(0xFF00FF00)),
    glowIntensity = 0.30f,
    isPremium = true,
)

val Commodore64 = TerminalTheme(
    id = "commodore64",
    displayName = "Commodore 64",
    background = Color(0xFF3F3F6B),
    foreground = Color(0xFFA0A0FF),
    accent = Color(0xFFC0C0FF),
    secondary = Color(0xFF7B7BFF),
    dim = Color(0xFF8080B0),
    cursor = Color(0xFFA0A0FF),
    selectionBackground = Color(0xFF4A4A7A),
    barColors = listOf(Color(0xFFC0C0FF), Color(0xFFA0A0FF), Color(0xFF7B7BFF)),
    glowIntensity = 0.40f,
    isPremium = true,
)

val BbcMicro = TerminalTheme(
    id = "bbc_micro",
    displayName = "BBC Micro",
    background = Color(0xFF000000),
    foreground = Color(0xFF00EEEE),
    accent = Color(0xFF00FFFF),
    secondary = Color(0xFFFFFF00),
    dim = Color(0xFF006666),
    cursor = Color(0xFF00FFFF),
    selectionBackground = Color(0xFF003333),
    barColors = listOf(Color(0xFF00FFFF), Color(0xFFFFFF00), Color(0xFF00FF00)),
    glowIntensity = 0.65f,
    isPremium = true,
)

val ZxSpectrum = TerminalTheme(
    id = "zx_spectrum",
    displayName = "ZX Spectrum",
    background = Color(0xFF000000),
    foreground = Color(0xFFCDCDCD),
    accent = Color(0xFF4444FF),
    secondary = Color(0xFFCD0000),
    dim = Color(0xFF555555),
    cursor = Color(0xFFCDCDCD),
    selectionBackground = Color(0xFF1A1A1A),
    barColors = listOf(Color(0xFF0000CD), Color(0xFFCD0000), Color(0xFF00CD00)),
    glowIntensity = 0.10f,
    isPremium = true,
)

val MsDos = TerminalTheme(
    id = "ms_dos",
    displayName = "MS-DOS",
    background = Color(0xFF000000),
    foreground = Color(0xFFAAAAAA),
    accent = Color(0xFFFFFFFF),
    secondary = Color(0xFFFFFF55),
    dim = Color(0xFF555555),
    cursor = Color(0xFFAAAAAA),
    selectionBackground = Color(0xFF0000AA),
    barColors = listOf(Color(0xFFFFFFFF), Color(0xFFAAAAAA), Color(0xFFFFFF55)),
    glowIntensity = 0.15f,
    isPremium = true,
)

val PaperWhite = TerminalTheme(
    id = "paper_white",
    displayName = "Paper White",
    background = Color(0xFFF5F0E8),
    foreground = Color(0xFF1A1A1A),
    accent = Color(0xFF2C2C2C),
    secondary = Color(0xFF8B7355),
    dim = Color(0xFF8A7E6E),
    cursor = Color(0xFF1A1A1A),
    selectionBackground = Color(0xFFE0D8CC),
    barColors = listOf(Color(0xFF1A1A1A), Color(0xFF8B7355), Color(0xFF5C4033)),
    glowIntensity = 0.0f,
    isPremium = true,
)

val Nord = TerminalTheme(
    id = "nord",
    displayName = "Nord",
    background = Color(0xFF2E3440),
    foreground = Color(0xFFD8DEE9),
    accent = Color(0xFF88C0D0),
    secondary = Color(0xFFBF616A),
    dim = Color(0xFF4C566A),
    cursor = Color(0xFF88C0D0),
    selectionBackground = Color(0xFF3B4252),
    barColors = listOf(Color(0xFF88C0D0), Color(0xFFBF616A), Color(0xFFA3BE8C)),
    glowIntensity = 0.20f,
    isPremium = true,
)

val GruvboxDark = TerminalTheme(
    id = "gruvbox_dark",
    displayName = "Gruvbox Dark",
    background = Color(0xFF282828),
    foreground = Color(0xFFEBDBB2),
    accent = Color(0xFFB8BB26),
    secondary = Color(0xFFFB4934),
    dim = Color(0xFF928374),
    cursor = Color(0xFFB8BB26),
    selectionBackground = Color(0xFF3C3836),
    barColors = listOf(Color(0xFFB8BB26), Color(0xFFFB4934), Color(0xFFFABD2F)),
    glowIntensity = 0.20f,
    isPremium = true,
)

val CatppuccinMocha = TerminalTheme(
    id = "catppuccin_mocha",
    displayName = "Catppuccin Mocha",
    background = Color(0xFF1E1E2E),
    foreground = Color(0xFFCDD6F4),
    accent = Color(0xFFCBA6F7),
    secondary = Color(0xFFF38BA8),
    dim = Color(0xFF7F849C),
    cursor = Color(0xFFCBA6F7),
    selectionBackground = Color(0xFF313244),
    barColors = listOf(Color(0xFFCBA6F7), Color(0xFFF38BA8), Color(0xFFA6E3A1)),
    glowIntensity = 0.20f,
    isPremium = true,
)

val TokyoNight = TerminalTheme(
    id = "tokyo_night",
    displayName = "Tokyo Night",
    background = Color(0xFF1A1B26),
    foreground = Color(0xFFC0CAF5),
    accent = Color(0xFF7AA2F7),
    secondary = Color(0xFFF7768E),
    dim = Color(0xFF565F89),
    cursor = Color(0xFF7AA2F7),
    selectionBackground = Color(0xFF292E42),
    barColors = listOf(Color(0xFF7AA2F7), Color(0xFFF7768E), Color(0xFF9ECE6A)),
    glowIntensity = 0.20f,
    isPremium = true,
)

val Monokai = TerminalTheme(
    id = "monokai",
    displayName = "Monokai",
    background = Color(0xFF272822),
    foreground = Color(0xFFF8F8F2),
    accent = Color(0xFFA6E22E),
    secondary = Color(0xFFF92672),
    dim = Color(0xFF75715E),
    cursor = Color(0xFFA6E22E),
    selectionBackground = Color(0xFF3E3D32),
    barColors = listOf(Color(0xFFA6E22E), Color(0xFFF92672), Color(0xFFE6DB74)),
    glowIntensity = 0.20f,
    isPremium = true,
)

val OneDark = TerminalTheme(
    id = "one_dark",
    displayName = "One Dark",
    background = Color(0xFF282C34),
    foreground = Color(0xFFABB2BF),
    accent = Color(0xFF61AFEF),
    secondary = Color(0xFFE06C75),
    dim = Color(0xFF5C6370),
    cursor = Color(0xFF61AFEF),
    selectionBackground = Color(0xFF2C313A),
    barColors = listOf(Color(0xFF61AFEF), Color(0xFFE06C75), Color(0xFFE5C07B)),
    glowIntensity = 0.20f,
    isPremium = true,
)

// Free, always-unlocked themes — shown under the [BASE] section in the picker.
val BaseThemes: List<TerminalTheme> = listOf(AmberTerminal, AppleII, VirtualBoy, WhitePhosphor, RosePine)

// Theme Vault-gated in iOS — shown under the [PRO] section here too, but
// selectable without restriction (gating deferred).
val ProThemes: List<TerminalTheme> = listOf(
    Commodore64, Dracula, BbcMicro, ZxSpectrum, MsDos, IbmBlue, SolarizedDark,
    SolarizedLight, PaperWhite, Nord, GruvboxDark, CatppuccinMocha, TokyoNight,
    Monokai, OneDark,
)

val AllThemes: List<TerminalTheme> = BaseThemes + ProThemes

fun themeForId(id: String): TerminalTheme = AllThemes.firstOrNull { it.id == id } ?: AppleII

val LocalTerminalTheme = staticCompositionLocalOf { AppleII }
