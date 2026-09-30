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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.music.MusicSource
import com.termi.android.theme.LocalTerminalTheme
import java.io.File

// The Android-specific reframe of ImportMethodScreen's "[+ADD]" flow: this
// app's library is a flat list of REFERENCED external paths, not one
// sandboxed folder the way iOS's is — so the mental model that actually
// matches what's happening is "which folders feed my library", not "pick
// one way to add a file". Shows every user-picked folder MusicSource (the
// WiFi Transfer inbox is deliberately NOT listed here — it's a one-time
// transfer mechanism, not a folder the user manages; its songs still fold
// into the library via MusicLibraryManager, they're just not a row here),
// its live song count, and a way to remove a whole source at once —
// dropping every song under it from the library.
@Composable
fun MusicSourcesScreen(
    sources: List<MusicSource>,
    songCount: (MusicSource) -> Int,
    onAddSource: () -> Unit,
    onOpenWifiTransfer: () -> Unit,
    onRemoveSource: (MusicSource) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalTerminalTheme.current
    var removeTarget by remember { mutableStateOf<MusicSource?>(null) }
    val folderSources = sources.filterNot { it.isInbox }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "┌─[ MUSIC SOURCES ]",
                    color = theme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
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
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.3f)))

            Text(
                text = "> EVERY FOLDER FEEDING THIS LIBRARY. REMOVING A SOURCE\n" +
                    "  DROPS EVERYTHING IMPORTED FROM IT.",
                color = theme.dim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            )

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(folderSources, key = { it.rootPath }) { source ->
                    SourceRow(
                        source = source,
                        count = songCount(source),
                        onRemove = { removeTarget = source },
                    )
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
                item {
                    Text(
                        text = "[ + ADD SOURCE ]",
                        color = androidx.compose.ui.graphics.Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(theme.accent)
                            .clickable(onClick = onAddSource)
                            .padding(vertical = 12.dp),
                    )
                }
                item { Spacer(modifier = Modifier.height(10.dp)) }
                item {
                    Text(
                        text = "[ WIFI TRANSFER ]",
                        color = theme.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(theme.accent.copy(alpha = 0.08f))
                            .clickable(onClick = onOpenWifiTransfer)
                            .padding(vertical = 12.dp),
                    )
                }
                item { Spacer(modifier = Modifier.height(64.dp)) }
            }
        }
        CRTOverlay()
    }

    removeTarget?.let { source ->
        TerminalConfirmDialog(
            title = "Remove \"${source.label}\"?",
            message = "This removes ${songCount(source)} song(s) from your library. The real files on \"${source.rootPath}\" are not touched.",
            confirmLabel = "[REMOVE]",
            onConfirm = {
                onRemoveSource(source)
                removeTarget = null
            },
            onDismiss = { removeTarget = null },
        )
    }
}

@Composable
private fun SourceRow(source: MusicSource, count: Int, onRemove: () -> Unit) {
    val theme = LocalTerminalTheme.current
    val exists = File(source.rootPath).exists()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "[FOLDER]",
                color = theme.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.label,
                    color = theme.foreground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                Text(
                    text = if (exists) "${source.rootPath}  ·  $count SONG(S)" else "⚠ NOT FOUND  ·  $count SONG(S) STILL TRACKED",
                    color = if (exists) theme.dim else theme.secondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            }
        }
        Row(modifier = Modifier.padding(top = 6.dp)) {
            Text(
                text = "[REMOVE]",
                color = theme.secondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable(onClick = onRemove).padding(top = 4.dp, bottom = 4.dp),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(0.5.dp).background(theme.dim.copy(alpha = 0.15f)))
    }
}
