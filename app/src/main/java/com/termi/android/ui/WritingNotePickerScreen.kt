package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.pkgs.PackageWritingNote
import com.termi.android.theme.LocalTerminalTheme
import com.termi.android.writing.WritingDocument
import com.termi.android.writing.WritingManager

// Kotlin/Compose port of PackageNoteWritingPickerView: ONE root screen with
// both the "[ WRITE SOMETHING FRESH ]" action AND the existing-entries list
// together (not a separate Choose step) — real iOS never makes the user
// commit to attach-vs-write-fresh before seeing their options. Attaching an
// existing entry shows a real read-only preview first ("this entry will be
// attached in full — N words" + "[ ATTACH THIS ENTRY ]" confirm), since a
// journal entry is all-or-none, unlike a book quote's passage pick.
private sealed class NoteStep {
    object Root : NoteStep()
    data class Preview(val document: WritingDocument) : NoteStep()
    object WriteFresh : NoteStep()
}

@Composable
fun WritingNotePickerScreen(
    writingManager: WritingManager,
    onDone: (PackageWritingNote) -> Unit,
    onCancel: () -> Unit,
) {
    var step by remember { mutableStateOf<NoteStep>(NoteStep.Root) }

    when (val s = step) {
        is NoteStep.WriteFresh -> WritingNoteEditorScreen(initial = null, onDone = onDone, onCancel = { step = NoteStep.Root })
        is NoteStep.Preview -> AttachPreviewScreen(
            document = s.document,
            onConfirm = { onDone(PackageWritingNote(text = s.document.content, kind = "excerpt", sourceEntryTitle = s.document.title)) },
            onCancel = { step = NoteStep.Root },
        )
        is NoteStep.Root -> RootScreen(
            writingManager = writingManager,
            onSelectDocument = { doc -> step = NoteStep.Preview(doc) },
            onWriteFresh = { step = NoteStep.WriteFresh },
            onCancel = onCancel,
        )
    }
}

@Composable
private fun RootScreen(writingManager: WritingManager, onSelectDocument: (WritingDocument) -> Unit, onWriteFresh: () -> Unit, onCancel: () -> Unit) {
    val theme = LocalTerminalTheme.current
    val documents by writingManager.documents.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "┌─[ WRITING NOTE ]", color = theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(
                    text = "[CANCEL]",
                    color = theme.secondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable(onClick = onCancel).padding(vertical = 8.dp),
                )
            }

            Text(
                text = "[ WRITE SOMETHING FRESH ]",
                color = theme.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onWriteFresh).padding(horizontal = 14.dp, vertical = 10.dp),
            )

            Text(
                text = "│  OR ATTACH AN EXISTING ENTRY:",
                color = theme.dim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
            )

            if (documents.isEmpty()) {
                Text(
                    text = "│  No journal entries yet.",
                    color = theme.dim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(documents, key = { it.id }) { doc ->
                        Text(
                            text = doc.title,
                            color = theme.foreground,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectDocument(doc) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
        CRTOverlay()
    }
}

// Read-only preview before attaching an existing entry WHOLE — not a
// passage-selection step, matching iOS's WritingEntryAttachPreviewView.
@Composable
private fun AttachPreviewScreen(document: WritingDocument, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "┌─[ ${document.title.uppercase()} ]",
                    color = theme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                Text(
                    text = "[CANCEL]",
                    color = theme.secondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable(onClick = onCancel).padding(vertical = 8.dp),
                )
            }
            Text(
                text = "│  This entry will be attached in full — ${document.wordCount} words.",
                color = theme.dim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
            androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                item {
                    Text(
                        text = document.content,
                        color = theme.foreground,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
            Text(
                text = "[ ATTACH THIS ENTRY ]",
                color = theme.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onConfirm).padding(vertical = 16.dp),
            )
        }
        CRTOverlay()
    }
}
