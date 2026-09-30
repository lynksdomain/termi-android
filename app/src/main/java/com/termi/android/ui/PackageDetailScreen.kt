package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.pkgs.MixtapePlaybackManager
import com.termi.android.pkgs.PackageMixtapeMessage
import com.termi.android.pkgs.PackageTrackInfo
import com.termi.android.pkgs.ReceivedPackage
import com.termi.android.theme.LocalTerminalTheme

// Kotlin/Compose port of the read side of PackageDetailView.swift — quote
// card, note card, and now the mixtape section: a compact "[ ▶ PLAY
// MIXTAPE ]" starter card, or (once this package's mixtape is the active
// one on the shared MixtapePlaybackManager) the full player — scrubber,
// transport, message reveal, per-track rows. No export/share anywhere,
// matching iOS's deliberate "you give a CD, you don't keep the CD" stance.
@Composable
fun PackageDetailScreen(
    pkg: ReceivedPackage,
    mixtapePlayer: MixtapePlaybackManager,
    onPlayMixtape: () -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    val theme = LocalTerminalTheme.current
    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "┌─[ PACKAGE ]",
                    color = theme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Row {
                    Text(
                        text = "[REMOVE]",
                        color = theme.secondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable(onClick = onDelete).padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
                    )
                    Text(
                        text = "[BACK]",
                        color = theme.dim,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable(onClick = onBack).padding(vertical = 4.dp),
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pkg.manifest.title,
                            color = theme.foreground,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = "FROM: ${pkg.manifest.senderSignature}  ·  ${pkg.manifest.mood.uppercase()}",
                            color = theme.dim,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    pkg.crossedPlatformLabel?.let { platform -> CrossedPlatformBadge(platform = platform) }
                }
                Box(modifier = Modifier.height(20.dp))

                if (pkg.mixtapeFile != null) {
                    MixtapeSection(pkg = pkg, mixtapeFile = pkg.mixtapeFile, mixtapePlayer = mixtapePlayer, onPlayMixtape = onPlayMixtape)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                pkg.quote?.let { quote ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.selectionBackground.copy(alpha = 0.12f))
                            .padding(14.dp),
                    ) {
                        Text(
                            text = "╠═[ QUOTE ]",
                            color = theme.secondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Text(
                            text = "“${quote.text}”",
                            color = theme.foreground,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = "— ${quote.bookTitle}, ${quote.author}",
                            color = theme.dim,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                pkg.note?.let { note ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.selectionBackground.copy(alpha = 0.12f))
                            .padding(14.dp),
                    ) {
                        Text(
                            text = "╠═[ ${if (note.kind == "fresh") "NOTE" else "EXCERPT"} ]",
                            color = theme.secondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Text(
                            text = note.text,
                            color = theme.foreground,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        note.sourceEntryTitle?.let { source ->
                            Text(
                                text = "— from \"$source\"",
                                color = theme.dim,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }

                if (pkg.quote == null && pkg.note == null && pkg.mixtapeFile == null) {
                    Text(
                        text = "║  THIS PACKAGE HAS NO READABLE CONTENT YET",
                        color = theme.dim,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        CRTOverlay()
    }
}

@Composable
private fun MixtapeSection(
    pkg: ReceivedPackage,
    mixtapeFile: java.io.File,
    mixtapePlayer: MixtapePlaybackManager,
    onPlayMixtape: () -> Unit,
) {
    val theme = LocalTerminalTheme.current
    val activePackageId by mixtapePlayer.packageId.collectAsState()
    val isThisMixtapeActive = activePackageId == pkg.manifest.id

    if (isThisMixtapeActive) {
        BigMixtapePlayer(pkg = pkg, mixtapePlayer = mixtapePlayer)
    } else {
        Column(modifier = Modifier.fillMaxWidth().background(theme.selectionBackground.copy(alpha = 0.12f)).padding(16.dp)) {
            Text(
                text = "╠═[ MIXTAPE ]",
                color = theme.secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Text(
                text = "[ ▶ PLAY MIXTAPE ]",
                color = theme.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clickable {
                        onPlayMixtape()
                        mixtapePlayer.load(
                            id = pkg.manifest.id,
                            file = mixtapeFile,
                            tracklist = pkg.tracklist,
                            messages = pkg.messages,
                            title = pkg.manifest.title,
                            pauseRealMusic = {},
                        )
                    }
                    .padding(vertical = 8.dp),
            )
            pkg.tracklist.forEachIndexed { index, track ->
                TrackRow(index = index, track = track, tracklist = pkg.tracklist, messages = pkg.messages, isActive = false, currentTrackIndex = null, onClick = {})
            }
        }
    }
}

@Composable
private fun BigMixtapePlayer(pkg: ReceivedPackage, mixtapePlayer: MixtapePlaybackManager) {
    val theme = LocalTerminalTheme.current
    val tracklist by mixtapePlayer.tracklist.collectAsState()
    val messages by mixtapePlayer.messages.collectAsState()
    val currentTrackIndex by mixtapePlayer.currentTrackIndex.collectAsState()
    val isPlaying by mixtapePlayer.isPlaying.collectAsState()
    val currentTime by mixtapePlayer.currentTime.collectAsState()
    val duration by mixtapePlayer.duration.collectAsState()
    val activeMessage by mixtapePlayer.activeMessage.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.selectionBackground.copy(alpha = 0.16f))
            .padding(18.dp),
    ) {
        Text(
            text = "╠═[ MIXTAPE — PLAYING ]",
            color = theme.secondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 14.dp),
        )

        currentTrackIndex?.let { index ->
            if (index < tracklist.size) {
                Text(
                    text = tracklist[index].title,
                    color = theme.accent,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                Text(
                    text = tracklist[index].artist,
                    color = theme.dim,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }

        // Reserved slot, always present — a terminal-style waiting prompt,
        // not a card that pops in/out, matching iOS's "don't love the
        // popping in" fix in PackagesMixtapePlayerPaneView.
        val hasMessage = activeMessage != null
        Text(
            text = if (hasMessage) "[MESSAGE]: ${activeMessage!!.text}" else "[MESSAGE]:",
            color = if (hasMessage) theme.accent else theme.dim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.accent.copy(alpha = if (hasMessage) 0.08f else 0.03f))
                .padding(12.dp)
                .padding(bottom = 4.dp),
        )

        Spacer(modifier = Modifier.height(10.dp))

        Scrubber(currentTime = currentTime, duration = duration, onSeek = { mixtapePlayer.seek(it) })
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(text = timeLabel(currentTime), color = theme.dim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = timeLabel(duration), color = theme.dim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "[|<<]",
                color = theme.foreground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { mixtapePlayer.previousTrack() },
            )
            Spacer(modifier = Modifier.width(28.dp))
            Text(
                text = if (isPlaying) "[PAUSE]" else "[PLAY]",
                color = theme.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { mixtapePlayer.togglePlayPause() },
            )
            Spacer(modifier = Modifier.width(28.dp))
            Text(
                text = "[>>|]",
                color = theme.foreground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable { mixtapePlayer.nextTrack() },
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            tracklist.forEachIndexed { index, track ->
                TrackRow(
                    index = index,
                    track = track,
                    tracklist = tracklist,
                    messages = messages,
                    isActive = true,
                    currentTrackIndex = currentTrackIndex,
                    onClick = { mixtapePlayer.seek(track.startOffsetSeconds) },
                )
            }
        }

        Text(
            text = "[ STOP ]",
            color = theme.dim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable { mixtapePlayer.stop() }.padding(top = 12.dp),
        )
    }
}

@Composable
private fun Scrubber(currentTime: Double, duration: Double, onSeek: (Double) -> Unit) {
    val theme = LocalTerminalTheme.current
    var widthPx by remember { mutableStateOf(1) }
    val progress = if (duration > 0) (currentTime / duration).coerceIn(0.0, 1.0) else 0.0
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(theme.dim.copy(alpha = 0.25f))
            .onSizeChanged { widthPx = it.width }
            .pointerInput(duration) {
                detectDragGestures { change, _ ->
                    val fraction = (change.position.x / widthPx.coerceAtLeast(1)).coerceIn(0f, 1f)
                    onSeek(fraction * duration)
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.toFloat())
                .background(theme.accent),
        )
    }
}

@Composable
private fun TrackRow(
    index: Int,
    track: PackageTrackInfo,
    tracklist: List<PackageTrackInfo>,
    messages: List<PackageMixtapeMessage>,
    isActive: Boolean,
    currentTrackIndex: Int?,
    onClick: () -> Unit,
) {
    val theme = LocalTerminalTheme.current
    val start = track.startOffsetSeconds
    val end = if (index + 1 < tracklist.size) tracklist[index + 1].startOffsetSeconds else Double.POSITIVE_INFINITY
    val msgCount = messages.count { it.timestampSeconds >= start && it.timestampSeconds < end }
    val isCurrentTrack = isActive && currentTrackIndex == index

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isActive, onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Text(text = "${index + 1}.", color = theme.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = track.title,
            color = if (isCurrentTrack) theme.accent else theme.foreground,
            fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "— ${track.artist}", color = theme.dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        if (msgCount > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "✉ $msgCount", color = theme.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

private fun timeLabel(seconds: Double): String {
    if (!seconds.isFinite() || seconds < 0) return "0:00"
    val total = seconds.toInt()
    return "%d:%02d".format(total / 60, total % 60)
}
