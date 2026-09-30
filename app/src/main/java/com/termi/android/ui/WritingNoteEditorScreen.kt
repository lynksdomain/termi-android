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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.pkgs.PackageWritingNote
import com.termi.android.theme.LocalTerminalTheme

// Simplified stand-in for iOS's PackageNoteWritingPickerView (attach a
// whole Journal entry, or compose fresh in a plain autosave-free field).
// Plain "compose fresh" text entry until a real Journal-entry picker
// exists on Android — matches iOS's "fresh" kind exactly (kind: "excerpt"
// is the other iOS option, not yet reachable here since there's no
// Journal-entry picker to excerpt from).
@Composable
fun WritingNoteEditorScreen(initial: PackageWritingNote?, onDone: (PackageWritingNote) -> Unit, onCancel: () -> Unit) {
    val theme = LocalTerminalTheme.current
    var text by remember { mutableStateOf(initial?.text ?: "") }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "╔═[ WRITING NOTE ]", color = theme.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(
                    text = "[CANCEL]",
                    color = theme.secondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable(onClick = onCancel).padding(vertical = 8.dp),
                )
            }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                EditorField(label = "NOTE", value = text, onValueChange = { text = it }, placeholder = "write something fresh...", lines = 6)
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "[ SAVE ]",
                    color = if (text.isNotBlank()) theme.accent else theme.dim.copy(alpha = 0.4f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = text.isNotBlank()) {
                            onDone(PackageWritingNote(text = text.trim(), kind = "fresh"))
                        }
                        .padding(vertical = 12.dp),
                )
            }
        }
        CRTOverlay()
    }
}
