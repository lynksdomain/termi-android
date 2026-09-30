package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.music.AudioPlayerEngine
import com.termi.android.music.RepeatMode
import com.termi.android.music.SleepTimerManager
import com.termi.android.music.VisualizerEngine
import com.termi.android.theme.LocalTerminalTheme
import kotlinx.coroutines.launch

// Kotlin/Compose port of PlayerView.swift — the full now-playing screen,
// presented as a full-screen modal over everything (including the tab bar),
// reached only via MiniPlayerBar — matching `fullScreenCover`, never a nav
// push. Structure follows the Swift source closely: header, big tappable
// visualizer, title/artist/album, block scrubber with drag-to-seek, terminal
// transport row ([|<<] [PLAY/PAUSE] [>>|]), and a mode row (shuffle/repeat/
// sleep timer). Tapping the visualizer opens VisualizerPickerScreen (full
// BASE/PRO grid, all 5 styles). Gain normalization remains deferred.
@Composable
fun PlayerScreen(player: AudioPlayerEngine, appSettings: com.termi.android.settings.AppSettings, onDismiss: () -> Unit) {
    val theme = LocalTerminalTheme.current
    val context = LocalContext.current
    val currentSong by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val positionMs by player.currentPositionMs.collectAsState()
    val isShuffled by player.isShuffled.collectAsState()
    val repeatMode by player.repeatModeState.collectAsState()
    val sleepTimer = player.sleepTimer
    val sleepRemaining by sleepTimer.remainingSeconds.collectAsState()

    // "[LOCK]" keeps the screen awake while this player is open — Android
    // times out to the lock screen mid-song/visualizer otherwise, with no
    // way for the user to override it (unlike iOS, where isIdleTimerDisabled
    // is a one-line toggle). FLAG_KEEP_SCREEN_ON is scoped to this window for
    // as long as the flag is set; always cleared on dispose so leaving the
    // player never leaves the whole app unable to time out.
    var keepScreenAwake by remember { mutableStateOf(false) }
    val activity = context as? android.app.Activity
    DisposableEffect(keepScreenAwake) {
        if (keepScreenAwake) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    var visualizerEngine by remember { mutableStateOf<VisualizerEngine?>(null) }
    DisposableEffect(currentSong != null) {
        visualizerEngine?.stop()
        val engine = if (currentSong != null) {
            VisualizerEngine(player.fftTap.rawMagnitudes).also { it.start() }
        } else {
            null
        }
        visualizerEngine = engine
        onDispose { engine?.stop() }
    }
    val magnitudes by (visualizerEngine?.magnitudes?.collectAsState() ?: remember { mutableStateOf(FloatArray(32)) })

    val visualizerStyleId by appSettings.visualizerStyleId.collectAsState()
    val visualizerStyle = VisualizerStyle.fromId(visualizerStyleId)
    var showVisualizerPicker by remember { mutableStateOf(false) }

    // Drag-down-to-dismiss, matching PlayerView.swift's DragGesture on the
    // whole screen: follows the finger while dragging down, dismisses past
    // an 80dp threshold, springs back to 0 otherwise.
    val dragOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val dismissThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 80.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            // A modal that's only visually on top doesn't block touches in
            // its blank areas by default in Compose — taps in regions with
            // no clickable/pointerInput of their own fall through to
            // whatever's underneath. This consumes everything so nothing
            // leaks to the screen behind the modal.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .offset { androidx.compose.ui.unit.IntOffset(0, dragOffset.value.toInt()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val next = (dragOffset.value + dragAmount.y).coerceAtLeast(0f)
                        scope.launch { dragOffset.snapTo(next) }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (dragOffset.value > dismissThresholdPx) {
                                onDismiss()
                            } else {
                                dragOffset.animateTo(0f)
                            }
                        }
                    },
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "╔═[ NOW PLAYING ]",
                    color = theme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    text = if (keepScreenAwake) "[UNLOCK]" else "[LOCK]",
                    color = if (keepScreenAwake) theme.accent else theme.dim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable(onClick = { keepScreenAwake = !keepScreenAwake })
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                )
                Text(
                    text = "[CLOSE]",
                    color = theme.dim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }

            if (currentSong == null) {
                Text(
                    text = "║  NOTHING PLAYING",
                    color = theme.dim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 12.dp),
                )
            } else {
                val song = currentSong!!
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        // SwiftUI's Canvas clips to its own bounds by default;
                        // Compose's Canvas does NOT — without this, styles
                        // like MatrixWarp (whose glyph radius is based on
                        // max(width, height), not min) paint outside this
                        // box and over the rest of the screen on a
                        // non-square canvas.
                        .clipToBounds()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showVisualizerPicker = true },
                ) {
                    when (visualizerStyle) {
                        VisualizerStyle.BARS -> VisualizerBarsView(
                            magnitudes = magnitudes,
                            maxBarHeight = 22,
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

                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    MarqueeText(
                        text = song.title,
                        color = theme.accent,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MarqueeText(
                        text = song.artist,
                        color = theme.secondary,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MarqueeText(
                        text = song.album,
                        color = theme.dim,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Scrubber(
                    currentMs = positionMs,
                    durationMs = song.durationMs,
                    accent = theme.accent,
                    dim = theme.dim,
                    onSeek = { ms -> player.seekTo(ms) },
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    TerminalCmd("|<<", theme.foreground, theme) { player.previous() }
                    Text("  ")
                    TerminalCmd(if (isPlaying) "PAUSE" else "PLAY", theme.accent, theme, accent = true) {
                        player.togglePlayPause()
                    }
                    Text("  ")
                    TerminalCmd(">>|", theme.foreground, theme) { player.next() }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Text(
                        text = if (isShuffled) "[SHUF:ON ]" else "[SHUF:OFF]",
                        color = if (isShuffled) theme.accent else theme.dim,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { player.toggleShuffle() }.padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                    Text(
                        text = when (repeatMode) {
                            RepeatMode.OFF -> "[RPT:OFF]"
                            RepeatMode.ALL -> "[RPT:ALL]"
                            RepeatMode.ONE -> "[RPT: 1 ]"
                        },
                        color = if (repeatMode == RepeatMode.OFF) theme.dim else theme.secondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { player.cycleRepeatMode() }.padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                    Text(
                        text = if (sleepTimer.isActive) "[ZZZ ${sleepTimer.remainingLabel}]" else "[ZZZ OFF]",
                        color = if (sleepTimer.isActive) theme.accent else theme.dim,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { sleepTimer.cycle() }.padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                }
            }
        }
        CRTOverlay()
    }

    // Port of VisualizerPickerView.swift — full-screen modal (not a stock
    // dialog) with BASE/PRO sections and live-previewing cards. Lifetime Pro
    // gating is intentionally NOT enforced (payment deferred) — see
    // VisualizerPickerScreen.kt's header comment.
    FullScreenCover(
        visible = showVisualizerPicker,
        onDismissRequest = { showVisualizerPicker = false },
    ) {
        VisualizerPickerScreen(
            isPlaying = isPlaying,
            liveMagnitudes = magnitudes,
            selectedStyle = visualizerStyle,
            onSelect = { style ->
                appSettings.setVisualizerStyleId(style.id)
                showVisualizerPicker = false
            },
            onDismiss = { showVisualizerPicker = false },
        )
    }

}

@Composable
private fun TerminalCmd(label: String, color: Color, theme: com.termi.android.theme.TerminalTheme, accent: Boolean = false, onClick: () -> Unit) {
    Text(
        text = "[$label]",
        color = color,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(if (accent) theme.accent.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

@Composable
private fun Scrubber(
    currentMs: Long,
    durationMs: Long,
    accent: Color,
    dim: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val progress = if (durationMs > 0) (currentMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val displayMs = if (isDragging) (dragFraction * durationMs).toLong() else currentMs

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = formatMs(displayMs),
            color = dim,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(44.dp),
        )
        val blockCount = 36
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .padding(horizontal = 6.dp)
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
        Text(
            text = formatMs(durationMs),
            color = dim,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(44.dp),
        )
    }
}
