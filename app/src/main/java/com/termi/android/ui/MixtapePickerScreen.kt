package com.termi.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.termi.android.music.MusicLibraryManager
import com.termi.android.music.Song
import com.termi.android.pkgs.MixtapeComposer
import com.termi.android.pkgs.PackageMixtapeMessage
import com.termi.android.pkgs.PackageTrackInfo
import com.termi.android.theme.LocalTerminalTheme
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The result MixtapePickerScreen hands back — already-fused audio, since
// building now happens INSIDE this screen (matching real iOS: the whole
// build-then-pin flow lives in one self-contained MixtapeBuilderView that
// returns a finished MixtapeBuildResult, not a list of raw songs for the
// caller to fuse later).
data class MixtapeBuildResult(val file: File, val tracklist: List<PackageTrackInfo>, val messages: List<PackageMixtapeMessage>)

private const val MAX_SONGS = 10
private const val MAX_MESSAGES = 10
private const val MESSAGE_CHAR_CAP = 120
private const val MIN_MESSAGE_SPACING_SECONDS = 8.0

// Kotlin/Compose port of MixtapeBuilderView.swift — a real audit-and-match
// pass, not the earlier approximation: real iOS is ONE screen with an
// ORDERED tracklist (reorder via ▲▼, not a plain checklist), a real search
// bar over the song list (title/artist/album), tapping a song row toggles
// add/remove directly (no separate "next" step), a real 20-segment build-
// progress bar shown inline while MixtapeComposer actually runs, and — the
// biggest structural difference from the earlier Android pass — message
// pinning against REAL AUDIO PLAYBACK of the just-built mixtape (a live
// scrubber + play/pause + pin-at-current-position), not manually typed
// mm:ss.
@Composable
fun MixtapePickerScreen(
    musicLibrary: MusicLibraryManager,
    onDone: (MixtapeBuildResult) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val allSongs by musicLibrary.songs.collectAsState()
    var selectedSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isBuilding by remember { mutableStateOf(false) }
    var buildProgress by remember { mutableFloatStateOf(0f) }
    var buildError by remember { mutableStateOf<String?>(null) }
    var builtResult by remember { mutableStateOf<MixtapeBuildResult?>(null) }
    val scope = rememberCoroutineScope()

    fun build() {
        if (selectedSongs.isEmpty() || isBuilding) return
        isBuilding = true
        buildError = null
        val songs = selectedSongs
        scope.launch {
            try {
                val outputFile = File(context.cacheDir, "mixtape_build_${UUID.randomUUID()}.m4a")
                val composed = withContext(Dispatchers.IO) {
                    MixtapeComposer.compose(songs.map { it.file }, outputFile) { p -> buildProgress = p.toFloat() }
                }
                val tracklist = songs.mapIndexed { i, song ->
                    PackageTrackInfo(title = song.title, artist = song.artist, startOffsetSeconds = composed.trackOffsetsSeconds.getOrElse(i) { 0.0 })
                }
                isBuilding = false
                builtResult = MixtapeBuildResult(composed.outputFile, tracklist, emptyList())
            } catch (e: Exception) {
                isBuilding = false
                buildError = e.message ?: "unknown error"
            }
        }
    }

    val result = builtResult
    when {
        result != null -> MessagesStep(
            result = result,
            onDone = onDone,
            onBack = { builtResult = null },
        )
        isBuilding -> BuildProgressView(progress = buildProgress, trackCount = selectedSongs.size)
        else -> SongPickerStep(
            allSongs = allSongs,
            selectedSongs = selectedSongs,
            buildError = buildError,
            onToggle = { song ->
                selectedSongs = if (selectedSongs.any { it.id == song.id }) {
                    selectedSongs.filterNot { it.id == song.id }
                } else if (selectedSongs.size < MAX_SONGS) {
                    selectedSongs + song
                } else {
                    selectedSongs
                }
            },
            onMoveUp = { index -> if (index > 0) selectedSongs = selectedSongs.toMutableList().apply { add(index - 1, removeAt(index)) } },
            onMoveDown = { index -> if (index < selectedSongs.size - 1) selectedSongs = selectedSongs.toMutableList().apply { add(index + 1, removeAt(index)) } },
            onRemove = { song -> selectedSongs = selectedSongs.filterNot { it.id == song.id } },
            onBuild = { build() },
            onCancel = onCancel,
        )
    }
}

@Composable
private fun SongPickerStep(
    allSongs: List<Song>,
    selectedSongs: List<Song>,
    buildError: String?,
    onToggle: (Song) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Song) -> Unit,
    onBuild: () -> Unit,
    onCancel: () -> Unit,
) {
    val theme = LocalTerminalTheme.current
    var searchQuery by remember { mutableStateOf("") }
    val filteredSongs = remember(searchQuery, allSongs) {
        if (searchQuery.isBlank()) {
            allSongs
        } else {
            val q = searchQuery.lowercase()
            allSongs.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q) }
        }
    }
    val canBuild = selectedSongs.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "┌─[ MIXTAPE ]", color = theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Row {
                    Text(
                        text = "[CANCEL]",
                        color = theme.secondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable(onClick = onCancel).padding(top = 8.dp, bottom = 8.dp, end = 12.dp),
                    )
                    Text(
                        text = "[ BUILD MIXTAPE ]",
                        color = if (canBuild) theme.accent else theme.dim,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable(enabled = canBuild, onClick = onBuild).padding(vertical = 8.dp),
                    )
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.3f)))

            if (buildError != null) {
                Text(
                    text = "║  BUILD FAILED: $buildError",
                    color = theme.dim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }

            // Ordered tracklist section — reorder/remove, matches iOS's
            // selectedSection exactly (order matters: it becomes the real
            // track sequence in the fused mixtape).
            Column(modifier = Modifier.fillMaxWidth().background(theme.selectionBackground.copy(alpha = 0.12f))) {
                Text(
                    text = "│ TRACKLIST (${selectedSongs.size}/$MAX_SONGS)",
                    color = if (selectedSongs.isEmpty()) theme.dim else theme.accent,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
                )
                if (selectedSongs.isEmpty()) {
                    Text(
                        text = "│  No tracks yet — tap songs below to add them.",
                        color = theme.dim,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                    )
                } else {
                    selectedSongs.forEachIndexed { index, song ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${index + 1}.",
                                color = theme.dim,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(24.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(text = song.title, color = theme.foreground, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                                Text(text = song.artist, color = theme.dim, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                            }
                            Text(
                                text = "▲",
                                color = if (index == 0) theme.dim.copy(alpha = 0.3f) else theme.dim,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.clickable(enabled = index != 0) { onMoveUp(index) }.padding(6.dp),
                            )
                            Text(
                                text = "▼",
                                color = if (index == selectedSongs.lastIndex) theme.dim.copy(alpha = 0.3f) else theme.dim,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.clickable(enabled = index != selectedSongs.lastIndex) { onMoveDown(index) }.padding(6.dp),
                            )
                            Text(
                                text = "[X]",
                                color = theme.dim,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.clickable { onRemove(song) }.padding(6.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.2f)))

            Row(
                modifier = Modifier.fillMaxWidth().background(theme.selectionBackground.copy(alpha = 0.25f)).padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "│ SEARCH> ", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(text = "Find a song...", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = theme.foreground, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(theme.cursor),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    Text(
                        text = "[X]",
                        color = theme.dim,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { searchQuery = "" }.padding(horizontal = 6.dp),
                    )
                }
                Text(text = "│", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }

            if (filteredSongs.isEmpty()) {
                Text(
                    text = "║  NO SONGS IN YOUR LIBRARY YET",
                    color = theme.dim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(filteredSongs, key = { it.id }) { song ->
                        val isSelected = selectedSongs.any { it.id == song.id }
                        val atCap = selectedSongs.size >= MAX_SONGS
                        SongPickRow(song = song, isSelected = isSelected, enabled = isSelected || !atCap, onToggle = { onToggle(song) })
                    }
                }
            }
        }
        CRTOverlay()
    }
}

@Composable
private fun SongPickRow(song: Song, isSelected: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .background(if (isSelected) theme.selectionBackground.copy(alpha = 0.45f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = if (isSelected) "[X]" else "[ ]",
            color = if (isSelected) theme.accent else theme.dim.copy(alpha = if (enabled) 1f else 0.3f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = song.title,
                color = if (isSelected) theme.accent else theme.foreground,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(text = song.artist, color = theme.dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.15f)))
}

@Composable
private fun BuildProgressView(progress: Float, trackCount: Int) {
    val theme = LocalTerminalTheme.current
    Box(modifier = Modifier.fillMaxSize().background(theme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "BUILDING MIXTAPE...", color = theme.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(16.dp))
            val width = 20
            val filled = (progress * width).toInt().coerceIn(0, width)
            Text(
                text = "[${"█".repeat(filled)}${"░".repeat(width - filled)}] ${(progress * 100).toInt()}%",
                color = theme.accent,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "$trackCount TRACKS", color = theme.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

// The message-pinning step, tied to REAL playback of the just-built
// mixtape — matches iOS's messagesScreen exactly: a live scrubber + play/
// pause against a real AVAudioPlayer-equivalent (ExoPlayer here), pinning
// happens AT the current playback position, not a typed timestamp.
@Composable
private fun MessagesStep(result: MixtapeBuildResult, onDone: (MixtapeBuildResult) -> Unit, onBack: () -> Unit) {
    val theme = LocalTerminalTheme.current
    val context = LocalContext.current
    var messages by remember { mutableStateOf(result.messages) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentTimeSec by remember { mutableFloatStateOf(0f) }
    var durationSec by remember { mutableFloatStateOf(0.01f) }
    var isComposingMessage by remember { mutableStateOf(false) }
    var draftMessageText by remember { mutableStateOf("") }

    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) {
        player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(result.file)))
        player.prepare()
        onDispose { player.release() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            if (player.duration > 0) durationSec = (player.duration / 1000.0).toFloat()
            currentTimeSec = (player.currentPosition / 1000.0).toFloat()
            val stillPlaying = player.isPlaying
            if (isPlaying != stillPlaying) isPlaying = stillPlaying
        }
    }

    BackHandler(enabled = true) { onBack() }

    val canAddMore = messages.size < MAX_MESSAGES
    val canPinHere = messages.none { abs(it.timestampSeconds - currentTimeSec) < MIN_MESSAGE_SPACING_SECONDS }

    fun pinMessage() {
        val trimmed = draftMessageText.trim()
        if (trimmed.isEmpty() || !canAddMore || !canPinHere) return
        messages = (messages + PackageMixtapeMessage(timestampSeconds = currentTimeSec.toDouble(), text = trimmed)).sortedBy { it.timestampSeconds }
        isComposingMessage = false
        draftMessageText = ""
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "┌─[ ADD MESSAGES ]", color = theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(
                    text = "[ DONE ]",
                    color = theme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { onDone(result.copy(messages = messages)) }.padding(vertical = 8.dp),
                )
            }
            Text(
                text = "│  Optional — pin short notes to moments in your mixtape. Up to $MAX_MESSAGES, $MESSAGE_CHAR_CAP characters each.",
                color = theme.dim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            )

            PreviewScrubber(
                currentTimeSec = currentTimeSec,
                durationSec = durationSec,
                messages = messages,
                onSeek = { fraction -> player.seekTo((fraction * durationSec * 1000).toLong()) },
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
            )

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isPlaying) "[ ‖ PAUSE ]" else "[ ▶ PLAY ]",
                    color = theme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable {
                        if (player.isPlaying) player.pause() else player.play()
                    }.padding(vertical = 8.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "${timeLabelSec(currentTimeSec)} / ${timeLabelSec(durationSec)}", color = theme.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "(${messages.size}/$MAX_MESSAGES)", color = theme.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }

            if (isComposingMessage) {
                ComposeMessageRow(
                    value = draftMessageText,
                    onValueChange = { if (it.length <= MESSAGE_CHAR_CAP) draftMessageText = it },
                    onCancel = { isComposingMessage = false },
                    onPin = { pinMessage() },
                )
            } else {
                Text(
                    text = if (canPinHere) "[ + ADD MESSAGE HERE ]" else "[ TOO CLOSE TO ANOTHER MESSAGE ]",
                    color = if (canAddMore && canPinHere) theme.accent else theme.dim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canAddMore && canPinHere) { isComposingMessage = true; draftMessageText = "" }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }

            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.3f)))

            if (messages.isEmpty()) {
                Text(
                    text = "│  No messages pinned yet.",
                    color = theme.dim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(messages, key = { it.id }) { message ->
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = timeLabelSec(message.timestampSeconds.toFloat()),
                                color = theme.dim,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(44.dp),
                            )
                            Text(
                                text = message.text,
                                color = theme.foreground,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "[X]",
                                color = theme.dim,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.clickable { messages = messages.filterNot { it.id == message.id } }.padding(6.dp),
                            )
                        }
                    }
                }
            }
        }
        CRTOverlay()
    }
}

@Composable
private fun ComposeMessageRow(value: String, onValueChange: (String) -> Unit, onCancel: () -> Unit, onPin: () -> Unit) {
    val theme = LocalTerminalTheme.current
    val canPin = value.trim().isNotEmpty()
    Column(modifier = Modifier.fillMaxWidth().background(theme.selectionBackground.copy(alpha = 0.2f)).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "│ ", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(text = "this part makes me think of you...", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(color = theme.foreground, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(theme.cursor),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "${value.length}/$MESSAGE_CHAR_CAP", color = theme.dim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "[CANCEL]",
                color = theme.secondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable(onClick = onCancel).padding(end = 12.dp),
            )
            Text(
                text = "[PIN]",
                color = if (canPin) theme.accent else theme.dim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable(enabled = canPin, onClick = onPin),
            )
        }
    }
}

@Composable
private fun PreviewScrubber(
    currentTimeSec: Float,
    durationSec: Float,
    messages: List<PackageMixtapeMessage>,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTerminalTheme.current
    val progress = (currentTimeSec / durationSec.coerceAtLeast(0.01f)).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .pointerInputSeek { fraction -> onSeek(fraction) },
    ) {
        val blockCount = 28
        val gap = 2f
        val blockW = ((size.width - gap * (blockCount - 1)) / blockCount).coerceAtLeast(1f)
        val filledCount = (progress * blockCount).toInt()
        for (i in 0 until blockCount) {
            val x = i * (blockW + gap)
            drawRoundRect(
                color = if (i < filledCount) theme.accent else theme.dim.copy(alpha = 0.25f),
                topLeft = Offset(x, 0f),
                size = androidx.compose.ui.geometry.Size(blockW, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f, 1f),
            )
        }
        messages.forEach { message ->
            val x = (message.timestampSeconds / durationSec.coerceAtLeast(0.01f)).toFloat() * size.width
            drawRect(color = theme.secondary, topLeft = Offset(x - 1f, 0f), size = androidx.compose.ui.geometry.Size(2f, size.height))
        }
    }
}

private fun Modifier.pointerInputSeek(onFraction: (Float) -> Unit): Modifier = this.pointerInput(Unit) {
    detectDragGestures { change, _ ->
        val fraction = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
        onFraction(fraction)
    }
}

private fun timeLabelSec(seconds: Float): String {
    if (!seconds.isFinite() || seconds < 0) return "0:00"
    val total = seconds.toInt()
    return "%d:%02d".format(total / 60, total % 60)
}
