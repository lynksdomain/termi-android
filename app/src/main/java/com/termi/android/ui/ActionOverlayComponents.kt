package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.theme.LocalTerminalTheme

// Shared terminal-styled "morphing action panel" system — a single 280dp
// panel over a tap-to-dismiss scrim whose CONTENT morphs between states in
// place (options <-> confirm), matching how BookLibraryView.swift and
// WritingView.swift both independently implement the exact same panel
// shape for their "···" menus (options/rename/confirm-delete). Originally
// built only for Books; extracted here so Writing's real terminal dialog
// migration (replacing stock Material AlertDialogs) can reuse it exactly
// rather than re-implementing it a second time — matches the real iOS
// behavior of both screens using the identical panel chrome.

@Composable
fun ActionOverlayScaffold(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val theme = LocalTerminalTheme.current
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClick = onDismiss),
        )
        Box(modifier = Modifier.align(Alignment.Center)) {
            Box(
                modifier = Modifier
                    .width(280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.background)
                    .background(theme.selectionBackground.copy(alpha = 0.5f))
                    .clickable(enabled = false) {},
            ) {
                content()
            }
        }
    }
}

data class ActionRow(val icon: String, val label: String, val destructive: Boolean, val onClick: () -> Unit)

@Composable
fun ActionOptionsPanel(rows: List<ActionRow>, onCancel: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Column {
        Text(
            text = "[SUB-MENU]",
            color = theme.dim,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 14.dp).padding(top = 10.dp, bottom = 6.dp),
        )
        rows.forEach { row ->
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(0.5.dp).background(theme.accent.copy(alpha = 0.2f)))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.selectionBackground.copy(alpha = 0.3f))
                    .clickable(onClick = row.onClick)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.icon,
                    color = if (row.destructive) theme.secondary else theme.accent,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(20.dp),
                )
                Text(
                    text = row.label,
                    color = if (row.destructive) theme.secondary else theme.foreground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(0.5.dp).background(theme.accent.copy(alpha = 0.3f)))
        Text(
            text = "[CANCEL]",
            color = theme.dim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCancel)
                .padding(vertical = 11.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun ActionConfirmPanel(title: String, message: String, confirmLabel: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Column {
        Text(
            text = title,
            color = theme.secondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 14.dp).padding(top = 10.dp, bottom = 8.dp),
        )
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.25f)))
        Text(
            text = message,
            color = theme.dim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 14.dp).padding(top = 12.dp, bottom = 14.dp),
        )
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.25f)))
        Row(modifier = Modifier.fillMaxWidth().height(44.dp)) {
            Box(
                modifier = Modifier.weight(1f).fillMaxSize().clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "[ CANCEL ]", color = theme.dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            Box(modifier = Modifier.width(0.5.dp).fillMaxSize().background(theme.accent.copy(alpha = 0.25f)))
            Box(
                modifier = Modifier.weight(1f).fillMaxSize().clickable(onClick = onConfirm),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = confirmLabel, color = theme.secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// A compact, centered, bordered text-input overlay over a tap-to-dismiss
// scrim — matches WritingView.swift's terminalNewDocOverlay/docRenamePanel
// shape (a "╔═[ TITLE ]" header + one bordered field + CANCEL/pill-CONFIRM
// buttons), distinct from ActionOverlayScaffold's rounded 280dp panel since
// iOS itself renders these as two different shapes, not the same component
// reused.
@Composable
fun TerminalTextInputOverlay(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalTerminalTheme.current
    val canConfirm = value.isNotBlank()
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onDismiss),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .background(theme.background)
                .clickable(enabled = false) {}
                .padding(24.dp),
        ) {
            Column {
                Text(
                    text = title,
                    color = theme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.selectionBackground.copy(alpha = 0.3f))
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                ) {
                    if (value.isEmpty()) {
                        Text(text = placeholder, color = theme.dim, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle = TextStyle(color = theme.foreground, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(theme.cursor),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "[ CANCEL ]",
                        color = theme.dim,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(theme.dim.copy(alpha = 0.12f))
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 10.dp, horizontal = 16.dp),
                    )
                    Text(
                        text = confirmLabel,
                        color = if (canConfirm) Color.Black else theme.dim,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(if (canConfirm) theme.accent else theme.dim.copy(alpha = 0.12f))
                            .clickable(enabled = canConfirm, onClick = onConfirm)
                            .padding(vertical = 10.dp, horizontal = 16.dp),
                    )
                }
            }
        }
    }
}
