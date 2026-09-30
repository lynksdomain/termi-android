package com.termi.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.music.AudioPlayerEngine
import com.termi.android.theme.LocalTerminalTheme

// Kotlin/Compose port of MiniPlayerBar.swift. Sits between tab content and
// the bottom tab bar, visible on every tab whenever a song is loaded — same
// as iOS's safeAreaInset stack, not scoped to the Music tab. Only PLAY/PAUSE
// and NEXT (no previous/shuffle/repeat — those live in the full PlayerScreen
// modal). The tap-to-open gesture is deliberately scoped to just the
// title/artist region, matching iOS's documented fix for a double-fire bug
// where tapping near the buttons also opened the full player.
//
// Deferred vs. the original: marquee-scroll on long titles (plain truncated
// text instead).
@Composable
fun MiniPlayerBar(player: AudioPlayerEngine, onOpen: () -> Unit) {
    val theme = LocalTerminalTheme.current
    val song = player.currentSong.collectAsState().value ?: return
    val isPlaying by player.isPlaying.collectAsState()
    val positionMs by player.currentPositionMs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Mirrors `theme.background.overlay(theme.selectionBackground.opacity(0.5))` —
            // Compose backgrounds paint in the order applied, so stacking two
            // achieves the same layered blend.
            .background(theme.background)
            .background(theme.selectionBackground.copy(alpha = 0.5f)),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.5f)))
        BlockScrubber(
            durationMs = song.durationMs,
            currentMs = positionMs,
            accent = theme.accent,
            dim = theme.dim,
            onSeek = { ms -> player.seekTo(ms) },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen),
            ) {
                MarqueeText(
                    text = song.title,
                    color = theme.foreground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = song.artist,
                    color = theme.dim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            }
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp)) {
                Text(
                    text = if (isPlaying) "[PAUSE]" else "[PLAY]",
                    color = theme.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { player.togglePlayPause() }.padding(vertical = 8.dp),
                )
                Text(
                    text = "[>>|]",
                    color = theme.foreground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { player.next() }.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun BlockScrubber(
    durationMs: Long,
    currentMs: Long,
    accent: Color,
    dim: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val progress = if (durationMs > 0) (currentMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val blockCount = 28

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .pointerInput(durationMs) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        onSeek((dragFraction * durationMs).toLong())
                        isDragging = false
                    },
                    onDragCancel = { isDragging = false },
                ) { change, _ ->
                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            },
    ) {
        val gap = 2.dp.toPx()
        val blockW = ((size.width - gap * (blockCount - 1)) / blockCount).coerceAtLeast(1f)
        val effectiveProgress = if (isDragging) dragFraction else progress
        val filledCount = (effectiveProgress * blockCount).toInt().coerceIn(0, blockCount)
        for (i in 0 until blockCount) {
            val color = if (i < filledCount) accent else dim.copy(alpha = 0.25f)
            drawRect(
                color = color,
                topLeft = Offset(i * (blockW + gap), 0f),
                size = Size(blockW, size.height),
            )
        }
    }
}
