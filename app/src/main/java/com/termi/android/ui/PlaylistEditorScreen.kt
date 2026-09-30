package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.music.Playlist
import com.termi.android.music.Song
import com.termi.android.theme.LocalTerminalTheme
import java.util.UUID

// Kotlin/Compose port of PlaylistEditorView.swift — the ONE place playlist
// song membership is actually assigned (via a search + checkbox list), not
// from the song's own action menu. `playlist == null` means create mode.
@Composable
fun PlaylistEditorScreen(
    playlist: Playlist?,
    allSongs: List<Song>,
    onSave: (title: String, description: String, songs: List<Song>) -> Unit,
    onCancel: () -> Unit,
) {
    val theme = LocalTerminalTheme.current
    val isCreateMode = playlist == null
    var title by remember { mutableStateOf(playlist?.title ?: "") }
    var description by remember { mutableStateOf(playlist?.playlistDescription ?: "") }
    var searchQuery by remember { mutableStateOf("") }
    val initialSelected = remember {
        val paths = playlist?.songPaths?.toSet() ?: emptySet()
        allSongs.filter { it.file.absolutePath in paths }.map { it.id }.toMutableSet()
    }
    var selectedIds by remember { mutableStateOf(initialSelected.toSet()) }

    val filteredSongs = remember(searchQuery, allSongs) {
        if (searchQuery.isBlank()) {
            allSongs
        } else {
            val q = searchQuery.lowercase()
            allSongs.filter {
                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
            }
        }
    }
    val canSave = title.isNotBlank()

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "┌─[ ${if (isCreateMode) "NEW PLAYLIST" else "EDIT PLAYLIST"} ]",
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
                    modifier = Modifier.clickable(onClick = onCancel).padding(vertical = 8.dp, horizontal = 6.dp),
                )
                Text(
                    text = if (isCreateMode) "[CREATE]" else "[SAVE]",
                    color = if (canSave) theme.accent else theme.dim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable(enabled = canSave) {
                            val selectedSongs = allSongs.filter { it.id in selectedIds }
                            onSave(title.trim(), description.trim(), selectedSongs)
                        }
                        .padding(vertical = 8.dp),
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.3f)))

            LabeledTextField(prefix = "│ TITLE>", placeholder = "Playlist name", value = title, onValueChange = { title = it })
            LabeledTextField(prefix = "│ DESC >", placeholder = "Optional description", value = description, onValueChange = { description = it })

            Text(
                text = "╠══════════════════════════════════════════════╣",
                color = theme.dim.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 4.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.selectionBackground.copy(alpha = 0.25f))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "│ SEARCH>", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = TextStyle(color = theme.foreground, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(theme.cursor),
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
                if (searchQuery.isNotEmpty()) {
                    Text(
                        text = "[X]",
                        color = theme.dim,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { searchQuery = "" },
                    )
                }
                Text(text = "│", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.selectionBackground.copy(alpha = 0.15f))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "│ ${selectedIds.size} SELECTED",
                            color = if (selectedIds.isEmpty()) theme.dim else theme.accent,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        if (selectedIds.isNotEmpty()) {
                            Text(
                                text = "[CLEAR ALL]",
                                color = theme.dim,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.clickable { selectedIds = emptySet() }.padding(end = 8.dp),
                            )
                        }
                        Text(text = "│", color = theme.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.3f)))
                }
                items(filteredSongs) { song ->
                    SongPickerRow(
                        song = song,
                        selected = song.id in selectedIds,
                        onToggle = {
                            selectedIds = if (song.id in selectedIds) selectedIds - song.id else selectedIds + song.id
                        },
                    )
                }
                item { Spacer(modifier = Modifier.height(64.dp)) }
            }
        }
        CRTOverlay()
    }
}

@Composable
private fun LabeledTextField(prefix: String, placeholder: String, value: String, onValueChange: (String) -> Unit) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = prefix, color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Box(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
            if (value.isEmpty()) {
                Text(text = placeholder, color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = theme.foreground, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(theme.cursor),
                singleLine = true,
            )
        }
        Text(text = "│", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun SongPickerRow(song: Song, selected: Boolean, onToggle: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (selected) theme.selectionBackground.copy(alpha = 0.45f) else androidx.compose.ui.graphics.Color.Transparent)
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (selected) "[X]" else "[ ]",
                color = if (selected) theme.accent else theme.dim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(32.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = if (selected) theme.accent else theme.foreground,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                Text(
                    text = song.artist,
                    color = theme.dim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.15f)))
    }
}
