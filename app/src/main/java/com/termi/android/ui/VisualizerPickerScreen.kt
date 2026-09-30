package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.theme.LocalTerminalTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Kotlin/Compose port of VisualizerPickerView.swift — a full-screen modal
// (reached by tapping the visualizer in PlayerScreen, matches iOS's
// `fullScreenCover`, not the stock AlertDialog this replaces) listing every
// VisualizerStyle as a live-previewing card under BASE/PRO section headers.
// Lifetime Pro gating (VisualizerStyle.isPremium / ThemeVaultManager) is
// intentionally NOT enforced yet — payment is deferred — so every card here
// is freely selectable; no [L] lock badge is shown.
@Composable
fun VisualizerPickerScreen(
    isPlaying: Boolean,
    liveMagnitudes: FloatArray,
    selectedStyle: VisualizerStyle,
    onSelect: (VisualizerStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalTerminalTheme.current

    var idlePhase by remember { mutableStateOf(0.0) }
    DisposableEffect(Unit) {
        var running = true
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                if (!running) return
                idlePhase += 0.12
                handler.postDelayed(this, 50)
            }
        }
        handler.post(tick)
        onDispose { running = false; handler.removeCallbacks(tick) }
    }
    val idleMagnitudes = remember(idlePhase) {
        FloatArray(32) { i ->
            val f = i.toDouble()
            val v = 0.5 + 0.35 * sin(idlePhase + f * 0.4) + 0.15 * sin(idlePhase * 1.7 + f * 0.9)
            max(0.0, min(1.0, v)).toFloat()
        }
    }
    val previewMagnitudes = if (isPlaying) liveMagnitudes else idleMagnitudes

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "┌─[ VISUALIZER ]",
                    color = theme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "[CLOSE]",
                    color = theme.secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.3f)))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    SectionLabel("╠═[ BASE ]")
                }
                items(VisualizerStyle.baseStyles) { style ->
                    VisualizerStyleCard(
                        style = style,
                        magnitudes = previewMagnitudes,
                        isSelected = selectedStyle == style,
                        onClick = { onSelect(style) },
                    )
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    SectionLabel("╠═[ PRO ]")
                }
                items(VisualizerStyle.proStyles) { style ->
                    VisualizerStyleCard(
                        style = style,
                        magnitudes = previewMagnitudes,
                        isSelected = selectedStyle == style,
                        onClick = { onSelect(style) },
                    )
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(64.dp))
                }
            }
        }
        CRTOverlay()
    }
}

@Composable
private fun SectionLabel(text: String) {
    val theme = LocalTerminalTheme.current
    Text(
        text = text,
        color = theme.dim,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun VisualizerStyleCard(
    style: VisualizerStyle,
    magnitudes: FloatArray,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalTerminalTheme.current
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
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = style.displayName,
                color = if (isSelected) theme.accent else theme.foreground,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
            )
            if (isSelected) {
                Text(
                    text = "[X]",
                    color = theme.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .clipToBounds(),
        ) {
            when (style) {
                VisualizerStyle.BARS -> VisualizerBarsView(
                    magnitudes = magnitudes,
                    maxBarHeight = 10,
                    modifier = Modifier.fillMaxSize(),
                )
                VisualizerStyle.SPARKLINE -> SparklineVisualizerView(
                    magnitudes = magnitudes,
                    modifier = Modifier.fillMaxSize(),
                )
                VisualizerStyle.RADIAL -> RadialVisualizerView(
                    magnitudes = magnitudes,
                    modifier = Modifier.fillMaxSize(),
                )
                VisualizerStyle.WATERFALL -> WaterfallVisualizerView(
                    magnitudes = magnitudes,
                    modifier = Modifier.fillMaxSize(),
                )
                VisualizerStyle.MATRIX_WARP -> MatrixWarpVisualizerView(
                    magnitudes = magnitudes,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
