package com.termi.android.ui

import android.content.Context
import android.os.storage.StorageManager
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.theme.LocalTerminalTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class StorageVolumeEntry(val label: String, val root: File)

// Enumerates real storage volumes (internal + any removable SD card/USB
// media) via StorageManager — File("/storage/emulated/0") alone only ever
// reaches internal storage; a removable SD card lives at a sibling path
// like /storage/3062-3939 that's otherwise undiscoverable by walking "up"
// from internal storage, since real file managers treat each volume as its
// own root rather than one contiguous tree.
private fun listStorageVolumes(context: Context): List<StorageVolumeEntry> {
    return try {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return emptyList()
        storageManager.storageVolumes.mapNotNull { volume ->
            val dir = volume.directory ?: return@mapNotNull null
            val label = if (volume.isPrimary) "INTERNAL STORAGE" else (volume.getDescription(context) ?: "SD CARD")
            StorageVolumeEntry(label, dir)
        }
    } catch (e: Exception) {
        emptyList()
    }
}

// Terminal-styled in-app file browser, backed by MANAGE_EXTERNAL_STORAGE —
// replaces the system SAF picker for imports so the whole flow stays in the
// app's own aesthetic instead of dropping into stock Android chrome. Shared
// by Music and Journal import.
//
// `null` currentDir means "showing the storage-volume picker" (Internal
// Storage / SD Card / etc, each its own root) rather than a real directory
// — matches how every real Android file manager treats storage, instead of
// pretending it's all one tree you can walk "up" through indefinitely.
//
// `onFolderSelected`, when provided, adds a "[USE THIS FOLDER]" action that
// bulk-imports every matching file in the current directory (non-recursive,
// same flat-scan behavior as the single-file import) — for grabbing a whole
// album or a folder of notes in one action instead of one file at a time.
@Composable
fun FileBrowserScreen(
    startDir: File,
    extensionFilter: Set<String>? = null,
    onFileSelected: (File) -> Unit,
    onFolderSelected: ((File, List<File>) -> Unit)? = null,
    onCancel: () -> Unit,
    scanProgress: ImportProgressController? = null,
) {
    val theme = LocalTerminalTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val volumes = remember { listStorageVolumes(context) }
    var currentDir by remember { mutableStateOf<File?>(startDir) }

    // Which volume (if any) currentDir lives inside — the longest matching
    // root wins, so a nested volume path can't shadow a more specific one.
    val activeVolumeRoot = remember(currentDir, volumes) {
        val dir = currentDir
        if (dir == null) null else volumes.filter { dir.path == it.root.path || dir.path.startsWith(it.root.path + "/") }
            .maxByOrNull { it.root.path.length }?.root
    }

    // Listing runs off the main thread — some real-world folders (storage
    // roots, Android/data, DCIM) can hold tens of thousands of entries, and
    // doing this synchronously during composition was blocking the UI
    // thread hard enough to look like a hang (caught live: "took forever
    // to open" / "hung again" navigating and switching storage).
    var entries by remember { mutableStateOf<List<File>>(emptyList()) }
    var isListing by remember { mutableStateOf(false) }
    LaunchedEffect(currentDir) {
        val dir = currentDir
        if (dir == null) {
            entries = emptyList()
            return@LaunchedEffect
        }
        isListing = true
        entries = withContext(Dispatchers.IO) {
            // Some folders (Android/data subfolders, other apps' private
            // dirs) can throw SecurityException even with broad storage
            // access on newer Android versions — treat as empty rather
            // than crashing the whole browser.
            try {
                (dir.listFiles() ?: emptyArray())
                    .filter { !it.name.startsWith(".") }
                    .filter { it.isDirectory || extensionFilter == null || it.extension.lowercase() in extensionFilter }
                    .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            } catch (e: Exception) {
                emptyList()
            }
        }
        isListing = false
    }

    // "[USE THIS FOLDER]" means "everything matching, recursively, under
    // here" — same as pointing a real file manager/music app at a Music
    // folder full of Artist/Album subfolders and having it pick up every
    // track inside. That walk only runs when the button is actually
    // tapped (not eagerly per folder navigated into, which is what caused
    // the hang above), and off the main thread.
    var isScanningFolder by remember { mutableStateOf(false) }
    fun useThisFolder(dir: File) {
        scope.launch {
            isScanningFolder = true
            scanProgress?.beginScanning()
            val files = withContext(Dispatchers.IO) { scanRecursively(dir, extensionFilter) }
            isScanningFolder = false
            onFolderSelected?.invoke(dir, files)
        }
    }

    // "Up" stops at the current volume's own root — going further surfaces
    // the storage picker instead of wandering into unrelated system
    // folders like /storage/emulated. At the storage picker itself, [BACK]
    // cancels the whole flow.
    fun goBack() {
        val dir = currentDir
        if (dir == null) {
            onCancel()
            return
        }
        val root = activeVolumeRoot
        if (root != null && dir.path == root.path) {
            if (volumes.size > 1) currentDir = null else onCancel()
            return
        }
        val parent = dir.parentFile
        if (parent != null) currentDir = parent else currentDir = null
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            TerminalStatsHeaderBar(
                title = if (currentDir == null) "SELECT STORAGE" else "SELECT FILE",
                showDelete = false,
                onDelete = {},
                onBack = { goBack() },
            )
            val dir = currentDir
            if (dir != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(theme.selectionBackground.copy(alpha = 0.15f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = dir.path,
                        color = theme.dim,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                    if (volumes.size > 1) {
                        Text(
                            text = "[STORAGE]",
                            color = theme.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.clickable { currentDir = null }.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
                if (onFolderSelected != null) {
                    Text(
                        text = if (isScanningFolder) "SCANNING…" else "[USE THIS FOLDER]",
                        color = if (isScanningFolder) theme.dim else theme.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isScanningFolder) { useThisFolder(dir) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }

                val canGoUp = activeVolumeRoot?.let { dir.path != it.path } ?: (dir.parentFile != null)

                if (isListing) {
                    Text(
                        text = "║  LOADING…",
                        color = theme.dim,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                } else if (entries.isEmpty() && !canGoUp) {
                    Text(
                        text = "║  EMPTY",
                        color = theme.dim,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        if (canGoUp) {
                            item { ParentDirRow { goBack() } }
                        }
                        if (entries.isEmpty()) {
                            item {
                                Text(
                                    text = "║  EMPTY",
                                    color = theme.dim,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                )
                            }
                        }
                        items(entries) { entry ->
                            FileEntryRow(entry) {
                                if (entry.isDirectory) currentDir = entry else onFileSelected(entry)
                            }
                        }
                        item { Spacer(modifier = Modifier.height(64.dp)) }
                    }
                }
            } else {
                // Storage-volume picker.
                if (volumes.isEmpty()) {
                    Text(
                        text = "║  NO STORAGE VOLUMES FOUND",
                        color = theme.dim,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(volumes) { volume ->
                            StorageVolumeRow(volume) { currentDir = volume.root }
                        }
                        item { Spacer(modifier = Modifier.height(64.dp)) }
                    }
                }
            }
        }
        CRTOverlay()
    }
}

@Composable
private fun StorageVolumeRow(volume: StorageVolumeEntry, onClick: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "[SD]",
            color = theme.accent,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column {
            Text(
                text = volume.label,
                color = theme.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = volume.root.path,
                color = theme.dim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
    }
}

// A standard file-manager "go up a directory" row — pinned above the
// listing whenever we're not already at the current volume's own root, so
// there's an obvious inline way to go up besides the header's [BACK].
@Composable
private fun ParentDirRow(onClick: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "[DIR]",
            color = theme.accent,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(
            text = ".. (PARENT DIRECTORY)",
            color = theme.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun FileEntryRow(file: File, onClick: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (file.isDirectory) "[DIR]" else "[   ]",
            color = if (file.isDirectory) theme.accent else theme.dim,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(
            text = file.name,
            color = if (file.isDirectory) theme.accent else theme.foreground,
            fontSize = 13.sp,
            fontWeight = if (file.isDirectory) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (!file.isDirectory) {
            Text(
                text = formatSize(file.length()),
                color = theme.dim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

// Walks the whole subtree under `dir`, collecting every file matching
// `extensionFilter` (or every file, if null) regardless of how deeply
// nested it is.
private fun scanRecursively(dir: File, extensionFilter: Set<String>?): List<File> {
    val results = mutableListOf<File>()
    fun walk(current: File) {
        val children = try {
            current.listFiles() ?: return
        } catch (e: Exception) {
            return
        }
        for (child in children) {
            if (child.name.startsWith(".")) continue
            if (child.isDirectory) {
                walk(child)
            } else if (extensionFilter == null || child.extension.lowercase() in extensionFilter) {
                results.add(child)
            }
        }
    }
    walk(dir)
    return results
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0fKB".format(kb)
    return "%.1fMB".format(kb / 1024.0)
}
