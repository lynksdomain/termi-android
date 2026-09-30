package com.termi.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.music.AudioPlayerEngine
import com.termi.android.navigation.AppTab
import com.termi.android.settings.AppSettings
import com.termi.android.stats.MusicStatsManager
import com.termi.android.stats.ReadingStatsManager
import com.termi.android.stats.WritingStatsManager
import com.termi.android.theme.BaseThemes
import com.termi.android.theme.LocalTerminalTheme
import com.termi.android.theme.ProThemes
import com.termi.android.theme.TerminalTheme

// Kotlin/Compose port of SettingsView.swift's per-tab section pattern —
// full-screen modal (FullScreenCover, matching iOS's `.fullScreenCover`),
// root menu + inline drill-down navigation (SettingsNav), same shape as the
// Swift source. Intentionally NOT ported: LAST.FM, LIFETIME PRO, TIP JAR
// (account/payment-gated — deferred app-wide, see android_port_rules
// memory), READING GOALS / FONT PACK / TYPEWRITER SOUNDS (no backing engine
// on Android yet). MUSIC STATS now has a real engine (MusicStatsManager) —
// see the MUSIC branch below.
private enum class SettingsNav { ROOT, THEMES, VISUAL_FX, AUDIO, PACKAGE_SIGNATURE, READING_GOALS, ABOUT }

@Composable
fun SettingsScreen(
    activeTab: AppTab,
    appSettings: AppSettings,
    writingStatsManager: WritingStatsManager,
    readingStatsManager: ReadingStatsManager,
    musicStatsManager: MusicStatsManager,
    audioPlayer: AudioPlayerEngine? = null,
    onDismiss: () -> Unit,
) {
    val theme = LocalTerminalTheme.current
    val context = LocalContext.current
    var nav by remember { mutableStateOf(SettingsNav.ROOT) }
    var showWritingStats by remember { mutableStateOf(false) }
    var showReadingStats by remember { mutableStateOf(false) }
    var showMusicStats by remember { mutableStateOf(false) }
    var showVisualizerPicker by remember { mutableStateOf(false) }

    val tabLabel = when (activeTab) {
        AppTab.MUSIC -> "MUSIC"
        AppTab.BOOKS -> "BOOKS"
        AppTab.JOURNAL -> "JOURNAL"
        AppTab.PACKAGES -> "PACKAGES"
    }
    val navTitle = when (nav) {
        SettingsNav.ROOT -> "TERMI $tabLabel SETTINGS"
        SettingsNav.THEMES -> "THEME SELECTION"
        SettingsNav.VISUAL_FX -> "VISUAL FX"
        SettingsNav.AUDIO -> "AUDIO"
        SettingsNav.PACKAGE_SIGNATURE -> "PACKAGE SIGNATURE"
        SettingsNav.READING_GOALS -> "READING GOALS"
        SettingsNav.ABOUT -> "ABOUT"
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "┌─[ $navTitle ]",
                    color = theme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (nav != SettingsNav.ROOT) {
                    Text(
                        text = "[BACK]",
                        color = theme.secondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { nav = SettingsNav.ROOT }.padding(vertical = 8.dp, horizontal = 6.dp),
                    )
                }
                Text(
                    text = "[CLOSE]",
                    color = theme.secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(vertical = 8.dp),
                )
            }

            when (nav) {
                SettingsNav.ROOT -> SettingsRootMenu(
                    activeTab = activeTab,
                    appSettings = appSettings,
                    onNavigate = { nav = it },
                    onOpenVisualizerPicker = { showVisualizerPicker = true },
                    onOpenWritingStats = { showWritingStats = true },
                    onOpenReadingStats = { showReadingStats = true },
                    onOpenMusicStats = { showMusicStats = true },
                    onEmailUs = {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:lynksdomain@gmail.com"))
                        context.startActivity(Intent.createChooser(intent, null))
                    },
                )
                SettingsNav.THEMES -> ThemeGridSection(appSettings)
                SettingsNav.VISUAL_FX -> VisualFxSection(appSettings)
                SettingsNav.AUDIO -> AudioSection(appSettings)
                SettingsNav.PACKAGE_SIGNATURE -> PackageSignatureSection(appSettings)
                SettingsNav.READING_GOALS -> ReadingGoalsSection(appSettings)
                SettingsNav.ABOUT -> AboutSection(theme, context)
            }
        }
        CRTOverlay()
    }

    FullScreenCover(visible = showVisualizerPicker, onDismissRequest = { showVisualizerPicker = false }) {
        val isPlaying by (audioPlayer?.isPlaying?.collectAsState() ?: remember { mutableStateOf(false) })
        val magnitudes = FloatArray(32)
        val visualizerStyleId by appSettings.visualizerStyleId.collectAsState()
        VisualizerPickerScreen(
            isPlaying = isPlaying,
            liveMagnitudes = magnitudes,
            selectedStyle = VisualizerStyle.fromId(visualizerStyleId),
            onSelect = { style ->
                appSettings.setVisualizerStyleId(style.id)
                showVisualizerPicker = false
            },
            onDismiss = { showVisualizerPicker = false },
        )
    }
    FullScreenCover(visible = showWritingStats, onDismissRequest = { showWritingStats = false }) {
        WritingStatsScreen(manager = writingStatsManager, onBack = { showWritingStats = false })
    }
    FullScreenCover(visible = showReadingStats, onDismissRequest = { showReadingStats = false }) {
        ReadingStatsScreen(manager = readingStatsManager, onBack = { showReadingStats = false })
    }
    FullScreenCover(visible = showMusicStats, onDismissRequest = { showMusicStats = false }) {
        MusicStatsScreen(manager = musicStatsManager, onBack = { showMusicStats = false })
    }
}

@Composable
private fun SettingsRootMenu(
    activeTab: AppTab,
    appSettings: AppSettings,
    onNavigate: (SettingsNav) -> Unit,
    onOpenVisualizerPicker: () -> Unit,
    onOpenWritingStats: () -> Unit,
    onOpenReadingStats: () -> Unit,
    onOpenMusicStats: () -> Unit,
    onEmailUs: () -> Unit,
) {
    val packageSignature by appSettings.packageSignature.collectAsState()
    val dailyReadingGoal by appSettings.dailyReadingGoal.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
    ) {
        when (activeTab) {
            AppTab.MUSIC -> {
                item { SectionHeader("MUSIC") }
                item {
                    SettingsRow(
                        title = "AUDIO",
                        subtitle = "playback information",
                        onClick = { onNavigate(SettingsNav.AUDIO) },
                    )
                }
                item { SectionHeader("STATS") }
                item { SettingsRow(title = "MUSIC STATS", onClick = onOpenMusicStats) }
            }
            AppTab.BOOKS -> {
                item { SectionHeader("READING") }
                item {
                    SettingsRow(
                        title = "READING GOALS",
                        subtitle = if (dailyReadingGoal > 0) "goal: $dailyReadingGoal pages/day" else "no goal set",
                        onClick = { onNavigate(SettingsNav.READING_GOALS) },
                    )
                }
                item { SectionHeader("STATS") }
                item { SettingsRow(title = "READING STATS", onClick = onOpenReadingStats) }
            }
            AppTab.JOURNAL -> {
                item { SectionHeader("STATS") }
                item { SettingsRow(title = "WRITING STATS", onClick = onOpenWritingStats) }
            }
            AppTab.PACKAGES -> {}
        }

        item { SectionHeader("DISPLAY") }
        item { SettingsRow(title = "THEME SELECTION", onClick = { onNavigate(SettingsNav.THEMES) }) }
        item { SettingsRow(title = "VISUALIZER", onClick = onOpenVisualizerPicker) }
        item { SettingsRow(title = "VISUAL FX", onClick = { onNavigate(SettingsNav.VISUAL_FX) }) }

        item { SectionHeader("SUPPORT") }
        item {
            SettingsRow(
                title = "PACKAGE SIGNATURE",
                subtitle = if (packageSignature.isBlank()) "not set" else AppSettings.formatPackageSignature(packageSignature),
                onClick = { onNavigate(SettingsNav.PACKAGE_SIGNATURE) },
            )
        }
        item { SettingsRow(title = "ABOUT", onClick = { onNavigate(SettingsNav.ABOUT) }) }
        item { SettingsRow(title = "EMAIL US", subtitle = "lynksdomain@gmail.com", onClick = onEmailUs) }
        item { Spacer(modifier = Modifier.height(64.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val theme = LocalTerminalTheme.current
    Text(
        text = "╠═[ $title ]",
        color = theme.secondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun SettingsRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = theme.foreground,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = theme.dim,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Text(text = ">", color = theme.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.2f)))
    }
}

@Composable
private fun ToggleRow(label: String, isOn: Boolean, onToggle: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "║ > $label",
            color = theme.foreground,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = if (isOn) "[ ON ]" else "[ OFF]",
            color = if (isOn) theme.accent else theme.dim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clip(RoundedCornerShape(2.dp))
                .background(if (isOn) theme.accent.copy(alpha = 0.08f) else Color.Transparent)
                .clickable(onClick = onToggle)
                .padding(vertical = 6.dp, horizontal = 4.dp),
        )
    }
}

@Composable
private fun AudioSection(appSettings: AppSettings) {
    val theme = LocalTerminalTheme.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            text = "║  Volume normalization is planned for a future release.",
            color = theme.dim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// Kotlin/Compose port of SettingsView.swift's readingGoalsSection — a
// fixed page-count picker (0 = no goal), not a free-text field, matching
// iOS exactly.
private val READING_GOAL_OPTIONS = listOf(0, 5, 10, 15, 20, 30, 40, 50)

@Composable
private fun ReadingGoalsSection(appSettings: AppSettings) {
    val theme = LocalTerminalTheme.current
    val dailyReadingGoal by appSettings.dailyReadingGoal.collectAsState()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            text = "║  DAILY READING GOAL",
            color = theme.secondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = "║  Set a daily target in pages. Progress",
            color = theme.dim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = "║  shown in the reader toolbar.",
            color = theme.dim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        READING_GOAL_OPTIONS.forEach { pages ->
            val isSelected = dailyReadingGoal == pages
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { appSettings.setDailyReadingGoal(pages) }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (pages == 0) "║  [ NO GOAL ]" else "║  [ $pages PAGES / DAY ]",
                        color = if (isSelected) theme.accent else theme.foreground,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        Text(text = "[X]", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.2f)))
            }
        }
    }
}

@Composable
private fun VisualFxSection(appSettings: AppSettings) {
    val showScanlines by appSettings.showScanlines.collectAsState()
    val showVignette by appSettings.showCrtVignette.collectAsState()
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        ToggleRow(label = "SCANLINES", isOn = showScanlines, onToggle = { appSettings.setShowScanlines(!showScanlines) })
        ToggleRow(label = "CRT VIGNETTE", isOn = showVignette, onToggle = { appSettings.setShowCrtVignette(!showVignette) })
    }
}

@Composable
private fun ThemeGridSection(appSettings: AppSettings) {
    val themeId by appSettings.themeId.collectAsState()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(2) }) { SectionHeader("BASE") }
        items(BaseThemes) { t ->
            ThemeCard(theme = t, isSelected = themeId == t.id, onClick = { appSettings.setThemeId(t.id) })
        }
        item(span = { GridItemSpan(2) }) { SectionHeader("PRO") }
        items(ProThemes) { t ->
            ThemeCard(theme = t, isSelected = themeId == t.id, onClick = { appSettings.setThemeId(t.id) })
        }
        item(span = { GridItemSpan(2) }) { Spacer(modifier = Modifier.height(64.dp)) }
    }
}

@Composable
private fun ThemeCard(theme: TerminalTheme, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(theme.background)
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) theme.accent else theme.dim,
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Swatch(theme.background)
            Swatch(theme.foreground)
            Swatch(theme.accent)
            Swatch(theme.secondary)
            Spacer(modifier = Modifier.weight(1f))
            if (isSelected) {
                Text(text = "[X]", color = theme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
        Text(
            text = theme.displayName,
            color = if (isSelected) theme.accent else theme.foreground,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = "A>_ Hello World",
            color = theme.foreground,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(
        modifier = Modifier
            .height(10.dp)
            .width(10.dp)
            .clip(RoundedCornerShape(50))
            .background(color)
            .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50)),
    )
}

@Composable
private fun PackageSignatureSection(appSettings: AppSettings) {
    val theme = LocalTerminalTheme.current
    val stored by appSettings.packageSignature.collectAsState()
    var draft by remember(stored) { mutableStateOf(stored) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            text = "║  SIGN YOUR PACKAGES AS",
            color = theme.secondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = "║  Shown to whoever you send or receive",
            color = theme.dim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = "║  a Termi Package with.",
            color = theme.dim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Text(text = "║ ", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = TextStyle(color = theme.foreground, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(theme.accent),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text(text = "e.g. jdoe", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                    inner()
                },
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.2f)).padding(bottom = 12.dp))
        if (draft.trim().isNotEmpty()) {
            Text(
                text = "║  PREVIEW: ${AppSettings.formatPackageSignature(draft)}",
                color = theme.accent,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
            )
        }
        Text(
            text = "║  [ CONFIRM ]",
            color = theme.accent,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { appSettings.setPackageSignature(draft.trim()) }
                .padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun AboutSection(theme: com.termi.android.theme.TerminalTheme, context: android.content.Context) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(text = "║  TERMI v1.0", color = theme.foreground, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Text(text = "║  Retro Terminal Music Player", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Text(text = "║  Built by Lynk's Domain", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.2f)).padding(vertical = 8.dp))
        Text(
            text = "> PRIVACY POLICY",
            color = theme.accent,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lynksdomain.github.io/termi/privacy"))
                    context.startActivity(intent)
                }
                .padding(top = 8.dp),
        )
    }
}
