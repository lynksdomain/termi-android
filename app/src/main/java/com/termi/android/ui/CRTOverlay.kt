package com.termi.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import com.termi.android.theme.LocalTerminalTheme

// The user's live VISUAL FX toggle state (Settings > VISUAL FX), provided
// once at the app root (MainActivity) from AppSettings and read here as a
// default so every `CRTOverlay()` call site — present and future — gets the
// correct on/off state for free, without needing to thread AppSettings
// through as an explicit parameter every time a new screen is added. See
// android_port_rules memory rule 7d/8: every new full-screen presentation
// surface needs its own `CRTOverlay()` call; this default is what makes
// that a one-line, zero-plumbing addition.
data class CrtSettings(val showScanlines: Boolean = true, val showVignette: Boolean = true)

val LocalCrtSettings = staticCompositionLocalOf { CrtSettings() }

// Port of CRTOverlay/ScanlinesView from CRTViews.swift. Drawn on top of every
// screen, non-interactive (touches pass through), so it's laid over content
// the same way `.overlay(...).allowsHitTesting(false)` did in SwiftUI.
// All parameters default to the current theme/VISUAL FX settings — a bare
// `CRTOverlay()` is correct in the overwhelming majority of call sites.
@Composable
fun CRTOverlay(
    showScanlines: Boolean = LocalCrtSettings.current.showScanlines,
    showVignette: Boolean = LocalCrtSettings.current.showVignette,
    glowColor: Color = LocalTerminalTheme.current.accent,
    glowIntensity: Float = LocalTerminalTheme.current.glowIntensity,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (showScanlines) {
            val lineHeight = 4.dp.toPx()
            val lineThickness = 1.5.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.18f),
                    topLeft = Offset(0f, y),
                    size = Size(size.width, lineThickness),
                )
                y += lineHeight
            }
        }

        if (glowIntensity > 0f) {
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    color = glowColor.copy(alpha = 0.03f * glowIntensity)
                    blendMode = BlendMode.Screen
                }
                canvas.drawRect(0f, 0f, size.width, size.height, paint)
            }
        }

        if (showVignette) {
            // Mirrors RadialGradient(startRadius: 0.25 * w, endRadius: 0.85 * w) —
            // transparent out to 25% of the radius, then fading to black by 85%.
            val center = Offset(size.width / 2f, size.height / 2f)
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.25f / 0.85f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.30f),
                    ),
                    center = center,
                    radius = size.width * 0.85f,
                    tileMode = TileMode.Clamp,
                ),
            )
        }

        // Subtle left/right edge darkening — mirrors the horizontal
        // LinearGradient(leading→trailing: dark, clear, dark) in the original.
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.06f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.06f),
                ),
            ),
        )
    }
}
