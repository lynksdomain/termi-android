package com.termi.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.termi.android.theme.LocalTerminalTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.isActive

// Kotlin/Compose port of AdvancedVisualizers.swift. iOS pro-gates these
// behind Lifetime Pro — that paywall isn't ported (deferred per explicit
// instruction), so all three styles are freely selectable here.

// MARK: - Radial (bars arranged in a ring)

@Composable
fun RadialVisualizerView(magnitudes: FloatArray, modifier: Modifier = Modifier) {
    val theme = LocalTerminalTheme.current
    Canvas(modifier = modifier) {
        val count = magnitudes.size
        if (count == 0) return@Canvas

        val center = Offset(size.width / 2f, size.height / 2f)
        val innerRadius = min(size.width, size.height) * 0.22f
        val maxBarLen = min(size.width, size.height) * 0.24f
        val barColors = theme.barColors
        val step = (2 * Math.PI / count).toFloat()

        for (i in 0 until count) {
            val angle = step * i - (Math.PI / 2).toFloat()
            val mag = magnitudes[i]
            val len = innerRadius + mag * maxBarLen

            val p0 = Offset(center.x + cos(angle) * innerRadius, center.y + sin(angle) * innerRadius)
            val p1 = Offset(center.x + cos(angle) * len, center.y + sin(angle) * len)

            val color = when {
                mag > 0.75f && barColors.size > 1 -> barColors[1]
                mag > 0.4f && barColors.isNotEmpty() -> barColors[0]
                else -> theme.accent.copy(alpha = 0.6f)
            }
            drawLine(color = color, start = p0, end = p1, strokeWidth = 2.dp.toPx())
        }

        drawCircle(color = theme.accent, radius = 2.dp.toPx(), center = center)
    }
}

// MARK: - Waterfall (receding ridge silhouette)

@Composable
fun WaterfallVisualizerView(magnitudes: FloatArray, modifier: Modifier = Modifier) {
    val theme = LocalTerminalTheme.current
    val maxRows = 20
    var history by remember { mutableStateOf(listOf(magnitudes)) }

    LaunchedEffect(magnitudes) {
        history = (listOf(magnitudes) + history).take(maxRows)
    }

    Canvas(modifier = modifier) {
        val count = magnitudes.size
        if (count == 0 || history.isEmpty()) return@Canvas

        val rowStep = size.height / maxRows * 0.6f
        val maxPeak = size.height * 0.55f
        val colW = size.width / count

        for (r in history.indices.reversed()) {
            val frame = history[r]
            if (frame.size != count) continue
            val baseY = size.height - r * rowStep
            if (baseY <= -maxPeak) continue
            val isNewest = r == 0
            val ageFade = 1.0 - r.toDouble() / maxRows

            fun peakPoint(i: Int): Offset {
                val x = i * colW + colW / 2
                val y = baseY - frame[i] * maxPeak
                return Offset(x, y)
            }

            val ridge = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, baseY)
                for (i in 0 until count) {
                    val p = peakPoint(i)
                    lineTo(p.x, p.y)
                }
                lineTo(size.width, baseY)
            }
            val fill = androidx.compose.ui.graphics.Path().apply {
                addPath(ridge)
                lineTo(size.width, baseY + rowStep)
                lineTo(0f, baseY + rowStep)
                close()
            }

            val fillColor = if (isNewest) theme.accent else theme.foreground
            val fillAlpha = if (isNewest) 0.85f else (ageFade * 0.45).coerceAtLeast(0.05).toFloat()
            drawPath(path = fill, color = fillColor.copy(alpha = fillAlpha))

            if (isNewest) {
                drawPath(path = ridge, color = theme.accent, style = Stroke(width = 1.5.dp.toPx()))
            }
        }
    }
}

// MARK: - Matrix Warp (glyphs flying outward from a center point)

private data class WarpGlyph(
    val id: Long,
    var angle: Float,
    var radius: Float,
    val speed: Float,
    val char: Char,
)

private val WARP_CHARSET = "01アイウエオカキクケコ日月火水木金土#\$%&".toList()

@Composable
fun MatrixWarpVisualizerView(magnitudes: FloatArray, modifier: Modifier = Modifier) {
    val theme = LocalTerminalTheme.current
    var glyphs by remember { mutableStateOf(listOf<WarpGlyph>()) }
    var ringRadii by remember { mutableStateOf(listOf(0f, 0.25f, 0.5f, 0.75f)) }
    val lastSpawn = remember { mutableMapOf<Int, Long>() }
    var nextGlyphId by remember { mutableStateOf(0L) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    fun avgMagnitude(): Float = if (magnitudes.isEmpty()) 0f else magnitudes.average().toFloat()

    // Continuous frame-driven animation, independent of magnitude update
    // rate — mirrors iOS's TimelineView(.animation) + onChange(of: context.date).
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                val nowMs = frameTimeNanos / 1_000_000L
                val width = canvasSize.width
                val height = canvasSize.height
                if (width <= 0f || height <= 0f) return@withFrameNanos
                val maxRadius = maxOf(width, height) * 0.75f
                val energy = (avgMagnitude() * 1.6f).coerceAtMost(1f)

                glyphs = glyphs.mapNotNull { g ->
                    val newRadius = g.radius + g.speed * (1 + g.radius / maxRadius)
                    if (newRadius < maxRadius) g.copy(radius = newRadius) else null
                }

                val ringBaseSpeed = 0.0035f + energy * 0.006f
                ringRadii = ringRadii.map { depth ->
                    val next = depth + ringBaseSpeed * (1 + depth)
                    if (next > 1f) next - 1f else next
                }

                val count = magnitudes.size
                if (count > 0) {
                    val angleStep = (2 * Math.PI / count).toFloat()
                    val spawned = mutableListOf<WarpGlyph>()
                    for (i in 0 until count) {
                        val mag = magnitudes[i]
                        if (mag <= 0.15f) continue
                        val last = lastSpawn[i] ?: 0L
                        val intervalMs = (400 - mag * 350).coerceAtLeast(40f)
                        if (nowMs - last < intervalMs) continue
                        lastSpawn[i] = nowMs
                        val angle = angleStep * i - (Math.PI / 2).toFloat()
                        spawned.add(
                            WarpGlyph(
                                id = nextGlyphId++,
                                angle = angle,
                                radius = 6f,
                                speed = 1.5f + mag * 5f,
                                char = WARP_CHARSET.random(),
                            ),
                        )
                    }
                    if (spawned.isNotEmpty()) glyphs = glyphs + spawned
                    if (glyphs.size > 260) glyphs = glyphs.takeLast(260)
                }
            }
        }
    }

    Canvas(modifier = modifier) {
        canvasSize = size
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = maxOf(size.width, size.height) * 0.75f
        val energy = (avgMagnitude() * 1.6f).coerceAtMost(1f)

        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(
                    theme.accent.copy(alpha = 0.04f + 0.08f * energy),
                    theme.accent.copy(alpha = 0f),
                ),
                center = center,
                radius = maxRadius,
            ),
            radius = maxRadius,
            center = center,
        )

        val bandWidth = maxRadius * 0.22f
        for (depth in ringRadii) {
            val fadeIn = if (depth < 0.12f) depth / 0.12f else 1f
            val fadeOut = if (depth > 0.8f) ((1 - depth) / 0.2f).coerceAtLeast(0f) else 1f
            val opacity = fadeIn * fadeOut * (0.18f + 0.4f * energy)
            if (opacity <= 0.01f) continue
            val r = depth * maxRadius
            drawCircle(
                color = theme.accent.copy(alpha = opacity),
                radius = r,
                center = center,
                style = Stroke(width = bandWidth),
            )
        }

        drawIntoCanvas { canvas ->
            for (glyph in glyphs) {
                val depth = (glyph.radius / maxRadius).coerceAtMost(1f)
                val scale = 0.3f + depth * 1.4f
                val fontSize = maxOf(8f, 12f * scale)
                val fadeIn = if (depth < 0.15f) depth / 0.15f else 1f
                val x = center.x + cos(glyph.angle) * glyph.radius
                val y = center.y + sin(glyph.angle) * glyph.radius

                canvas.nativeCanvas.drawText(
                    glyph.char.toString(),
                    x,
                    y,
                    android.graphics.Paint().apply {
                        isAntiAlias = true
                        textSize = fontSize
                        color = android.graphics.Color.argb(
                            (fadeIn * 255).toInt().coerceIn(0, 255),
                            (theme.accent.red * 255).toInt(),
                            (theme.accent.green * 255).toInt(),
                            (theme.accent.blue * 255).toInt(),
                        )
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.MONOSPACE
                        setFakeBoldText(true)
                    },
                )
            }
        }

        drawCircle(color = theme.background, radius = 3.dp.toPx(), center = center)
    }
}
