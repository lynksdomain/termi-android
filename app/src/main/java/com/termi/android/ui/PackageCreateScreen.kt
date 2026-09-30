package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.books.BookLibraryManager
import com.termi.android.music.MusicLibraryManager
import com.termi.android.pkgs.PackageBookQuote
import com.termi.android.pkgs.PackageManifest
import com.termi.android.pkgs.PackageWritingNote
import com.termi.android.theme.LocalTerminalTheme
import com.termi.android.writing.WritingManager

// Kotlin/Compose port of PackageCreateView.swift — ported for real visual/
// structural parity, not just approximated: "║  LABEL" field prefixes (not
// ">"), underlined plain-text fields (no filled background box), a
// slot-row model for all three content types (a label + one "[ + ADD ]"/
// "[ <summary> — EDIT ]" button that opens a picker, not inline fields on
// the page itself), unfilled bracket-text mood buttons, and a plain-text
// (no pill background) bottom send button. All three slots now use real
// pickers pulling from the actual library (MixtapePickerScreen,
// BookQuotePickerScreen, WritingNotePickerScreen) — matches iOS, no more
// free-text stand-ins.
private val MOODS = listOf("romantic", "friendly", "cool")

@Composable
fun PackageCreateScreen(
    senderSignature: String,
    musicLibrary: MusicLibraryManager,
    bookLibrary: BookLibraryManager,
    writingManager: WritingManager,
    onCreate: (PackageManifest, PackageBookQuote?, PackageWritingNote?, MixtapeBuildResult?) -> Unit,
    onCancel: () -> Unit,
) {
    val theme = LocalTerminalTheme.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf("friendly") }
    var quote by remember { mutableStateOf<PackageBookQuote?>(null) }
    var note by remember { mutableStateOf<PackageWritingNote?>(null) }
    var mixtapeBuild by remember { mutableStateOf<MixtapeBuildResult?>(null) }
    var showMixtapePicker by remember { mutableStateOf(false) }
    var showQuotePicker by remember { mutableStateOf(false) }
    var showNotePicker by remember { mutableStateOf(false) }

    val canSend = title.isNotBlank() && (mixtapeBuild != null || quote != null || note != null)

    FullScreenCover(visible = showMixtapePicker, onDismissRequest = { showMixtapePicker = false }) {
        MixtapePickerScreen(
            musicLibrary = musicLibrary,
            onDone = { result -> mixtapeBuild = result; showMixtapePicker = false },
            onCancel = { showMixtapePicker = false },
        )
    }
    FullScreenCover(visible = showQuotePicker, onDismissRequest = { showQuotePicker = false }) {
        BookQuotePickerScreen(
            bookLibrary = bookLibrary,
            onDone = { q -> quote = q; showQuotePicker = false },
            onCancel = { showQuotePicker = false },
        )
    }
    FullScreenCover(visible = showNotePicker, onDismissRequest = { showNotePicker = false }) {
        WritingNotePickerScreen(
            writingManager = writingManager,
            onDone = { n -> note = n; showNotePicker = false },
            onCancel = { showNotePicker = false },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "╔═[ NEW PACKAGE ]",
                    color = theme.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "[CANCEL]",
                    color = theme.secondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable(onClick = onCancel).padding(vertical = 8.dp),
                )
            }

            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                item {
                    UnderlinedField(label = "TITLE", value = title, onValueChange = { title = it }, placeholder = "late night drive")
                    Spacer(modifier = Modifier.height(20.dp))

                    UnderlinedField(label = "DESCRIPTION", value = description, onValueChange = { description = it }, placeholder = "optional")
                    Spacer(modifier = Modifier.height(20.dp))

                    FieldLabel("MOOD")
                    Row {
                        MOODS.forEachIndexed { i, m ->
                            MoodButton(label = m.uppercase(), selected = mood == m) { mood = m }
                            if (i != MOODS.lastIndex) Spacer(modifier = Modifier.width(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    SlotRow(
                        label = "MUSIC",
                        filledText = mixtapeBuild?.let { build ->
                            val trackPart = "${build.tracklist.size} TRACK${if (build.tracklist.size == 1) "" else "S"}"
                            val messagePart = if (build.messages.isNotEmpty()) ", ${build.messages.size} PINNED" else ""
                            "$trackPart$messagePart — REBUILD"
                        },
                        onClick = { showMixtapePicker = true },
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    SlotRow(
                        label = "BOOK",
                        filledText = quote?.let { "QUOTE FROM ${it.bookTitle} — EDIT" },
                        onClick = { showQuotePicker = true },
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    SlotRow(
                        label = "WRITING",
                        filledText = if (note != null) "NOTE ADDED — EDIT" else null,
                        onClick = { showNotePicker = true },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Text(
                text = "[ SEND PACKAGE ]",
                color = if (canSend) theme.accent else theme.dim.copy(alpha = 0.4f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = canSend) {
                        val manifest = PackageManifest(
                            title = title.trim(),
                            description = description.trim(),
                            senderSignature = senderSignature,
                            mood = mood,
                            hasMusic = mixtapeBuild != null,
                            hasBook = quote != null,
                            hasWriting = note != null,
                        )
                        onCreate(manifest, quote, note, mixtapeBuild)
                    }
                    .padding(vertical = 16.dp),
            )
        }
        CRTOverlay()
    }
}

@Composable
private fun FieldLabel(text: String) {
    val theme = LocalTerminalTheme.current
    Text(
        text = "║  $text",
        color = theme.dim,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun UnderlinedField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val theme = LocalTerminalTheme.current
    FieldLabel(label)
    Box(modifier = Modifier.fillMaxWidth()) {
        if (value.isEmpty()) {
            Text(text = placeholder, color = theme.dim, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = theme.foreground, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.2f)))
}

@Composable
private fun MoodButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Text(
        text = "[ $label ]",
        color = if (selected) theme.accent else theme.dim,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
    )
}

@Composable
private fun SlotRow(label: String, filledText: String?, onClick: () -> Unit) {
    val theme = LocalTerminalTheme.current
    FieldLabel("$label SLOT")
    Text(
        text = filledText?.let { "[ $it ]" } ?: "[ + ADD ]",
        color = if (filledText != null) theme.accent else theme.foreground,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}
