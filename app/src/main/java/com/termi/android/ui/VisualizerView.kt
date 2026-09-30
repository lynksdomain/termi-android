package com.termi.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.termi.android.theme.LocalTerminalTheme

// Compose port of VisualizerView.swift (the bar-grid EQ — the simplest of
// the four iOS visualizer styles, and the only one ported so far). Draws
// with a single Canvas pass like the SwiftUI original's `.drawingGroup()`
// Canvas — Compose Canvas is already GPU-composited, no equivalent flag needed.
@Composable
fun VisualizerBarsView(magnitudes: FloatArray, maxBarHeight: Int = 12, modifier: Modifier = Modifier) {
    val theme = LocalTerminalTheme.current
    Canvas(modifier = modifier) {
        val count = magnitudes.size
        if (count == 0) return@Canvas

        val gapPx = 2.dp.toPx()
        val totalGap = (count - 1) * gapPx
        val barW = ((size.width - totalGap) / count).coerceAtLeast(1f)
        val cellH = size.height / maxBarHeight

        val barColors = theme.barColors

        for (i in 0 until count) {
            val filled = (magnitudes[i] * maxBarHeight).toInt().coerceIn(0, maxBarHeight)
            val x = i * (barW + gapPx)

            for (row in 0 until maxBarHeight) {
                val y = size.height - (row + 1) * cellH
                val color: Color = if (row < filled) {
                    val ratio = if (filled > 1) row.toFloat() / (filled - 1) else 0f
                    when {
                        ratio > 0.75f && barColors.size > 1 -> barColors[1]
                        ratio > 0.4f && barColors.isNotEmpty() -> barColors[0]
                        else -> theme.accent.copy(alpha = 0.7f)
                    }
                } else {
                    theme.dim.copy(alpha = 0.15f)
                }
                drawRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(barW, (cellH - 1f).coerceAtLeast(1f)),
                )
            }
        }
    }
}

// Compose port of SparklineVisualizerView.swift — the second-simplest style:
// mirrored bars flush against a center line, no inter-column gap.
@Composable
fun SparklineVisualizerView(magnitudes: FloatArray, modifier: Modifier = Modifier) {
    val theme = LocalTerminalTheme.current
    Canvas(modifier = modifier) {
        val count = magnitudes.size
        if (count == 0) return@Canvas

        val w = size.width / count
        val centerY = size.height / 2
        val maxHalf = centerY * 0.95f

        for (i in 0 until count) {
            val mag = magnitudes[i]
            val half = (mag * maxHalf).coerceAtLeast(2f)
            val x = i * w
            val color = if (mag > 0.7f) theme.accent else theme.foreground.copy(alpha = 0.85f)
            drawRect(
                color = color,
                topLeft = Offset(x, centerY - half),
                size = Size(w, half * 2),
            )
        }
    }
}

// Mirrors VisualizerStyle.swift: only Bars ships free (BASE), the rest are
// Lifetime Pro-gated (PRO) in the original — gating itself is deferred here.
enum class VisualizerStyle {
    BARS, SPARKLINE, RADIAL, WATERFALL, MATRIX_WARP;

    val displayName: String
        get() = when (this) {
            BARS -> "Bars"
            SPARKLINE -> "Sparkline"
            RADIAL -> "Radial"
            WATERFALL -> "Waterfall"
            MATRIX_WARP -> "Matrix Warp"
        }

    val isPremium: Boolean get() = this != BARS

    val id: String
        get() = when (this) {
            BARS -> "bars"
            SPARKLINE -> "sparkline"
            RADIAL -> "radial"
            WATERFALL -> "waterfall"
            MATRIX_WARP -> "matrixRain"
        }

    companion object {
        fun fromId(id: String): VisualizerStyle = entries.firstOrNull { it.id == id } ?: BARS

        val baseStyles: List<VisualizerStyle> = listOf(BARS)
        val proStyles: List<VisualizerStyle> = listOf(SPARKLINE, RADIAL, WATERFALL, MATRIX_WARP)
    }
}
