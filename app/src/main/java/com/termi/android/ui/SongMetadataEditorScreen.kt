package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.music.Song
import com.termi.android.theme.LocalTerminalTheme

// Kotlin/Compose port of SongMetadataEditorView.swift — a full custom
// screen (not a system form/dialog), reached from the song "···" action
// sheet's EDIT METADATA option. Local draft state, only commits on [SAVE].
@Composable
fun SongMetadataEditorScreen(song: Song, onSave: (Song) -> Unit, onCancel: () -> Unit) {
    val theme = LocalTerminalTheme.current
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    var albumArtist by remember { mutableStateOf(song.albumArtist) }
    var genre by remember { mutableStateOf(song.genre) }
    var year by remember { mutableStateOf(song.year) }
    var trackNumber by remember { mutableStateOf(song.trackNumber?.toString() ?: "") }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "┌─[ EDIT METADATA ]",
                    color = theme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    maxLines = 1,
                )
                Text(
                    text = "[CANCEL]",
                    color = theme.secondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable(onClick = onCancel)
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                )
                Text(
                    text = "[SAVE]",
                    color = theme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable {
                            onSave(
                                song.copy(
                                    title = title.ifEmpty { song.title },
                                    artist = artist.ifEmpty { song.artist },
                                    album = album.ifEmpty { song.album },
                                    albumArtist = albumArtist.ifEmpty { song.albumArtist },
                                    genre = genre,
                                    year = year,
                                    trackNumber = trackNumber.toIntOrNull(),
                                ),
                            )
                        }
                        .padding(vertical = 8.dp),
                )
            }
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 0.dp)) {
                Box(modifier = Modifier.fillMaxWidth().background(theme.accent.copy(alpha = 0.3f)).padding(top = 0.dp))
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 32.dp),
            ) {
                FieldRow("TITLE      ", title) { title = it }
                FieldRow("ARTIST     ", artist) { artist = it }
                FieldRow("ALBUM      ", album) { album = it }
                FieldRow("ALBUM ARTIST", albumArtist) { albumArtist = it }
                FieldRow("GENRE      ", genre) { genre = it }
                FieldRow("YEAR       ", year) { year = it }
                FieldRow("TRACK #    ", trackNumber, keyboardType = KeyboardType.Number) { trackNumber = it }

                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).background(theme.dim.copy(alpha = 0.3f)))

                InfoRow("FILE", song.file.name)
                InfoRow("DURATION", formatMs(song.durationMs))
            }
        }
        CRTOverlay()
    }
}

@Composable
private fun FieldRow(label: String, value: String, keyboardType: KeyboardType = KeyboardType.Text, onValueChange: (String) -> Unit) {
    val theme = LocalTerminalTheme.current
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "║ $label ›",
                color = theme.dim,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(150.dp),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = theme.foreground, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(theme.cursor),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(theme.selectionBackground.copy(alpha = 0.4f))
                    .padding(vertical = 10.dp, horizontal = 8.dp),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().background(theme.dim.copy(alpha = 0.12f)))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val theme = LocalTerminalTheme.current
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = "║ $label",
            color = theme.dim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(150.dp),
        )
        Text(
            text = value,
            color = theme.dim.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}
