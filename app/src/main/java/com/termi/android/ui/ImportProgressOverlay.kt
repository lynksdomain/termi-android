package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.termi.android.theme.LocalTerminalTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class ImportPhase { IDLE, SCANNING, IMPORTING }

// Drives the real-feedback overlay the user asked for in place of a static
// "SCANNING…" label: "it should be an overlay like uploading all the files,
// a progress bar if possible... rather than being stuck on scanning and not
// sure what happens". One controller instance is shared between
// FileBrowserScreen (which sets SCANNING while it walks the folder tree) and
// the calling screen's own per-file import loop (which sets IMPORTING with a
// real X/Y count) so the overlay reads as one continuous operation instead
// of two disconnected states.
class ImportProgressController {
    private var phase by mutableStateOf(ImportPhase.IDLE)
    var current by mutableStateOf(0)
        private set
    var total by mutableStateOf(0)
        private set

    val active: Boolean get() = phase != ImportPhase.IDLE
    val label: String
        get() = when (phase) {
            ImportPhase.SCANNING -> "SCANNING FOLDER..."
            ImportPhase.IMPORTING -> "IMPORTING FILES..."
            ImportPhase.IDLE -> ""
        }

    fun beginScanning() {
        phase = ImportPhase.SCANNING
        current = 0
        total = 0
    }

    // Runs the per-file import work off the main thread, updating a real
    // progress count as each file completes. `importOne` runs on
    // Dispatchers.IO so heavy work (e.g. MediaMetadataRetriever extraction)
    // never blocks the UI thread the way the old synchronous calls did.
    suspend fun run(files: List<File>, importOne: (File) -> Unit) {
        if (files.isEmpty()) {
            phase = ImportPhase.IDLE
            return
        }
        phase = ImportPhase.IMPORTING
        total = files.size
        current = 0
        withContext(Dispatchers.IO) {
            for (file in files) {
                importOne(file)
                current += 1
            }
        }
        phase = ImportPhase.IDLE
    }
}

@Composable
fun rememberImportProgressController(): ImportProgressController = remember { ImportProgressController() }

@Composable
fun ImportProgressOverlay(controller: ImportProgressController) {
    if (!controller.active) return
    val theme = LocalTerminalTheme.current
    val barWidth = 24
    val determinate = controller.total > 0
    val fraction = if (determinate) (controller.current.toFloat() / controller.total).coerceIn(0f, 1f) else 0f
    val filled = (fraction * barWidth).toInt()
    val bar = "[" + "#".repeat(filled) + "-".repeat(barWidth - filled) + "]"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = controller.label,
                color = theme.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            if (determinate) {
                Text(
                    text = bar,
                    color = theme.accent,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = "${controller.current} / ${controller.total}",
                    color = theme.dim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
