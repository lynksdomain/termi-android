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
import com.termi.android.books.BookSource
import com.termi.android.theme.LocalTerminalTheme

// Books-side counterpart to MusicSourcesScreen. Unlike Music (which
// references files in place), Books COPIES each EPUB into its own owned
// folder — so removing a source here is genuinely destructive: it deletes
// the real copied files, not just a grouping. The confirm dialog says so
// explicitly, unlike Music's "the real files are not touched" copy.
@Composable
fun BookSourcesScreen(
    sources: List<BookSource>,
    itemCount: (BookSource) -> Int,
    onAddSource: () -> Unit,
    onOpenWifiTransfer: () -> Unit,
    onRemoveSource: (BookSource) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalTerminalTheme.current
    var removeTarget by remember { mutableStateOf<BookSource?>(null) }

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
                    text = "┌─[ BOOK SOURCES ]",
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
                text = "> EVERY FOLDER YOU'VE IMPORTED BOOKS FROM. REMOVING A\n" +
                    "  SOURCE DELETES THE COPIES IT BROUGHT IN.",
                color = theme.dim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            )

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(sources, key = { it.rootPath }) { source ->
                    BookSourceRow(
                        source = source,
                        count = itemCount(source),
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
            message = "This permanently deletes ${itemCount(source)} book(s) copied from \"${source.rootPath}\" from your library. This cannot be undone.",
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
private fun BookSourceRow(source: BookSource, count: Int, onRemove: () -> Unit) {
    val theme = LocalTerminalTheme.current

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
                    text = "${source.rootPath}  ·  $count BOOK(S)",
                    color = theme.dim,
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
