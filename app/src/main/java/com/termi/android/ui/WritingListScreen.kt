package com.termi.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.theme.LocalTerminalTheme
import com.termi.android.writing.WritingDocument
import com.termi.android.writing.WritingGroup
import com.termi.android.writing.WritingManager
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class WritingDocMenuState { OPTIONS, CONFIRM_DELETE, RENAME }
private enum class WritingGroupMenuState { OPTIONS, CONFIRM_DELETE }

// Kotlin/Compose port of the list portion of WritingView.swift — full
// terminal-dialog parity pass: real morphing "···" action panels
// (options -> confirm/rename, matching Swift's spring(0.28, 0.82)
// morph-in-place) for both entries and groups, a real NAME/DESC/SEARCH+
// checkbox-multiselect group editor, a real add-to-group screen, and a
// full-hero empty state — replacing the earlier pass's stock Material3
// AlertDialogs (rule 3 debt flagged in the Books/Journal parity audit).
// Reuses the SAME ActionOverlayScaffold/ActionOptionsPanel/
// ActionConfirmPanel components BooksScreen.kt already established, since
// real iOS independently builds the identical panel chrome for both tabs.
@Composable
fun WritingListScreen(
    manager: WritingManager,
    onOpenDocument: (WritingDocument) -> Unit,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
) {
    val theme = LocalTerminalTheme.current
    val documents by manager.documents.collectAsState()
    val groups by manager.groups.collectAsState()

    // Picks up anything WiFi Transfer just wrote into the writing folder —
    // without this, a mid-session upload wouldn't show until the app
    // restarted (same gap as Music/Books — see android_port_status memory).
    val wifiUploadLog by com.termi.android.network.WiFiTransferServer.uploadLog.collectAsState()
    androidx.compose.runtime.LaunchedEffect(wifiUploadLog) { manager.refreshForNewFiles() }

    var activeGroup by remember { mutableStateOf<WritingGroup?>(null) }
    var showNewDocDialog by remember { mutableStateOf(false) }
    var newDocTitle by remember { mutableStateOf("") }
    var showFileBrowser by remember { mutableStateOf(false) }
    val importProgress = rememberImportProgressController()
    val importScope = androidx.compose.runtime.rememberCoroutineScope()
    var showImportMethod by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    val sources by manager.sources.collectAsState()

    var showCreateGroup by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<WritingGroup?>(null) }
    var showAddToGroup by remember { mutableStateOf(false) }
    var addToGroupDocument by remember { mutableStateOf<WritingDocument?>(null) }

    var actionDocument by remember { mutableStateOf<WritingDocument?>(null) }
    var docMenuState by remember { mutableStateOf(WritingDocMenuState.OPTIONS) }
    var renameText by remember { mutableStateOf("") }
    var actionGroup by remember { mutableStateOf<WritingGroup?>(null) }
    var groupMenuState by remember { mutableStateOf(WritingGroupMenuState.OPTIONS) }

    fun closeDocMenu() {
        actionDocument = null
        docMenuState = WritingDocMenuState.OPTIONS
    }
    fun closeGroupMenu() {
        actionGroup = null
        groupMenuState = WritingGroupMenuState.OPTIONS
    }

    val displayedDocuments = activeGroup?.let { manager.documentsIn(it) } ?: manager.ungroupedDocuments

    FullScreenCover(visible = showSources, onDismissRequest = { showSources = false }) {
        WritingSourcesScreen(
            sources = sources,
            itemCount = { manager.itemCount(it) },
            onAddSource = {
                showSources = false
                showFileBrowser = true
            },
            onOpenWifiTransfer = {
                showSources = false
                showImportMethod = true
            },
            onRemoveSource = { manager.removeSource(it) },
            onDismiss = { showSources = false },
        )
    }

    FullScreenCover(visible = showImportMethod, onDismissRequest = { showImportMethod = false }) {
        ImportMethodScreen(
            target = ImportTarget.JOURNAL,
            onBrowseFiles = {
                showImportMethod = false
                showFileBrowser = true
            },
            onDismiss = { showImportMethod = false },
            openWifiPanel = true,
        )
    }

    FullScreenCover(visible = showFileBrowser, onDismissRequest = { showFileBrowser = false }) {
        if (com.termi.android.filesystem.StoragePermission.isGranted()) {
            FileBrowserScreen(
                startDir = android.os.Environment.getExternalStorageDirectory(),
                extensionFilter = setOf("txt", "md"),
                onFileSelected = { file ->
                    showFileBrowser = false
                    val root = file.parentFile ?: file
                    manager.registerSource(root)
                    importScope.launch { importProgress.run(listOf(file)) { manager.importDocuments(listOf(it), root.absolutePath) } }
                },
                onFolderSelected = { rootDir, files ->
                    showFileBrowser = false
                    manager.registerSource(rootDir)
                    importScope.launch { importProgress.run(files) { manager.importDocuments(listOf(it), rootDir.absolutePath) } }
                },
                onCancel = { showFileBrowser = false },
                scanProgress = importProgress,
            )
        } else {
            val context = androidx.compose.ui.platform.LocalContext.current
            StoragePermissionScreen(
                onRequestPermission = {
                    context.startActivity(com.termi.android.filesystem.StoragePermission.requestIntent(context))
                },
                onBack = { showFileBrowser = false },
            )
        }
    }

    FullScreenCover(visible = showCreateGroup, onDismissRequest = { showCreateGroup = false; editingGroup = null }) {
        WritingGroupEditorScreen(
            group = editingGroup,
            allDocuments = documents,
            onSave = { name, description, filenames ->
                val existing = editingGroup
                if (existing != null) manager.updateGroup(existing, name, description, filenames) else manager.createGroup(name, description, filenames)
                showCreateGroup = false
                editingGroup = null
            },
            onCancel = { showCreateGroup = false; editingGroup = null },
        )
    }

    FullScreenCover(visible = showAddToGroup, onDismissRequest = { showAddToGroup = false; addToGroupDocument = null }) {
        val doc = addToGroupDocument
        if (doc != null) {
            WritingAddToGroupScreen(
                document = doc,
                groups = groups,
                onToggle = { group ->
                    val filenames = group.documentFilenames.toMutableList()
                    val filename = doc.file.name
                    if (filename in filenames) filenames.remove(filename) else filenames.add(filename)
                    manager.updateGroup(group, group.name, group.groupDescription, filenames)
                },
                onCancel = { showAddToGroup = false; addToGroupDocument = null },
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            TerminalStatsHeaderBar(
                title = activeGroup?.name ?: "TERMI JOURNAL",
                showDelete = false,
                onDelete = {},
                onBack = if (activeGroup != null) {
                    { activeGroup = null }
                } else {
                    onBack
                },
                onSettings = if (activeGroup == null) onSettings else null,
            )

            if (documents.isEmpty()) {
                EmptyJournalBlock(onNewEntry = { newDocTitle = ""; showNewDocDialog = true })
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    val currentGroup = activeGroup
                    if (currentGroup == null) {
                        item {
                            PinnedActionRow(icon = "+", label = "CREATE NEW ENTRY") { newDocTitle = ""; showNewDocDialog = true }
                        }
                        item {
                            MenuActionRow("+ IMPORT FILE") { showSources = true }
                        }
                        item {
                            PinnedActionRow(icon = ">", label = "CREATE GROUP") { editingGroup = null; showCreateGroup = true }
                        }
                        if (groups.isNotEmpty()) {
                            item { TerminalSectionHeader(title = "GROUPS") }
                            items(groups) { group ->
                                GroupRow(
                                    group = group,
                                    count = manager.documentsIn(group).size,
                                    onClick = { activeGroup = group },
                                    onMore = { actionGroup = group },
                                )
                            }
                        }
                        item { TerminalSectionHeader(title = "ENTRIES") }
                    } else {
                        // Matches iOS's real groupDocsList: no pinned
                        // create-entry/import-file actions while drilled
                        // into a group — just the group info card (with
                        // its own [EDIT] entry point, previously only
                        // reachable via the root list's "···") and its
                        // entries.
                        item {
                            GroupInfoCard(
                                group = currentGroup,
                                count = displayedDocuments.size,
                                onEdit = { editingGroup = currentGroup; showCreateGroup = true },
                            )
                        }
                        item { TerminalSectionHeader(title = "ENTRIES") }
                    }
                    if (displayedDocuments.isEmpty()) {
                        item {
                            Text(
                                text = "│  NO ENTRIES IN THIS GROUP.",
                                color = theme.dim,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    } else {
                        items(displayedDocuments) { doc ->
                            DocumentRow(
                                document = doc,
                                onOpen = { onOpenDocument(doc) },
                                onMore = { actionDocument = doc },
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(64.dp)) }
                }
            }
        }
        ImportProgressOverlay(importProgress)
    }

    if (showNewDocDialog) {
        TerminalTextInputOverlay(
            title = "╔═[ NEW DOCUMENT ]",
            value = newDocTitle,
            onValueChange = { newDocTitle = it },
            placeholder = "untitled",
            confirmLabel = "[ CREATE ]",
            onConfirm = {
                val title = newDocTitle.trim()
                val doc = manager.createDocument(title.ifEmpty { "untitled" })
                newDocTitle = ""
                showNewDocDialog = false
                onOpenDocument(doc)
            },
            onDismiss = { showNewDocDialog = false; newDocTitle = "" },
        )
    }

    actionDocument?.let { doc ->
        ActionOverlayScaffold(onDismiss = { closeDocMenu() }) {
            AnimatedContent(
                targetState = docMenuState,
                transitionSpec = { (scaleIn(initialScale = 0.92f) + fadeIn(tween(220))) togetherWith (scaleOut(targetScale = 0.92f) + fadeOut(tween(160))) },
                label = "docMenu",
            ) { s ->
                when (s) {
                    WritingDocMenuState.OPTIONS -> ActionOptionsPanel(
                        rows = listOf(
                            ActionRow("x", "DELETE ENTRY", true) { docMenuState = WritingDocMenuState.CONFIRM_DELETE },
                            ActionRow("+", "ADD TO GROUP", false) { addToGroupDocument = doc; showAddToGroup = true; closeDocMenu() },
                            ActionRow("*", "RENAME", false) { renameText = doc.title; docMenuState = WritingDocMenuState.RENAME },
                        ),
                        onCancel = { closeDocMenu() },
                    )
                    WritingDocMenuState.CONFIRM_DELETE -> ActionConfirmPanel(
                        title = "╔═[ DELETE ENTRY ]",
                        message = "\"${doc.title}\" — are you sure? This cannot be undone.",
                        confirmLabel = "[ YES, DELETE ]",
                        onCancel = { docMenuState = WritingDocMenuState.OPTIONS },
                        onConfirm = { manager.deleteDocument(doc); closeDocMenu() },
                    )
                    WritingDocMenuState.RENAME -> RenamePanel(
                        value = renameText,
                        onValueChange = { renameText = it },
                        onCancel = { docMenuState = WritingDocMenuState.OPTIONS },
                        onSave = {
                            val title = renameText.trim()
                            if (title.isNotEmpty()) manager.renameDocument(doc, title)
                            closeDocMenu()
                        },
                    )
                }
            }
        }
    }

    actionGroup?.let { group ->
        ActionOverlayScaffold(onDismiss = { closeGroupMenu() }) {
            AnimatedContent(
                targetState = groupMenuState,
                transitionSpec = { (scaleIn(initialScale = 0.92f) + fadeIn(tween(220))) togetherWith (scaleOut(targetScale = 0.92f) + fadeOut(tween(160))) },
                label = "groupMenu",
            ) { s ->
                when (s) {
                    WritingGroupMenuState.OPTIONS -> ActionOptionsPanel(
                        rows = listOf(
                            ActionRow("*", "EDIT GROUP", false) { editingGroup = group; showCreateGroup = true; closeGroupMenu() },
                            ActionRow("x", "DELETE GROUP", true) { groupMenuState = WritingGroupMenuState.CONFIRM_DELETE },
                        ),
                        onCancel = { closeGroupMenu() },
                    )
                    WritingGroupMenuState.CONFIRM_DELETE -> ActionConfirmPanel(
                        title = "╔═[ DELETE GROUP ]",
                        message = "\"${group.name}\" — entries in this group will NOT be deleted, only removed from the group.",
                        confirmLabel = "[ YES, DELETE ]",
                        onCancel = { groupMenuState = WritingGroupMenuState.OPTIONS },
                        onConfirm = {
                            manager.deleteGroup(group)
                            if (activeGroup?.id == group.id) activeGroup = null
                            closeGroupMenu()
                        },
                    )
                }
            }
        }
    }
}

// Compact rename panel matching WritingView.swift's docRenamePanel shape —
// reuses ActionOptionsPanel's 280dp scaffold host but its own bordered-
// field content, since that's what real iOS renders here too (not a
// third shared component, just this one screen's rename state).
@Composable
private fun RenamePanel(value: String, onValueChange: (String) -> Unit, onCancel: () -> Unit, onSave: () -> Unit) {
    val theme = LocalTerminalTheme.current
    val canSave = value.isNotBlank()
    Column {
        Text(
            text = "╔═[ RENAME ENTRY ]",
            color = theme.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 14.dp).padding(top = 10.dp, bottom = 8.dp),
        )
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.25f)))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(top = 14.dp, bottom = 4.dp)
                .background(theme.selectionBackground.copy(alpha = 0.3f))
                .padding(vertical = 10.dp, horizontal = 12.dp),
        ) {
            if (value.isEmpty()) {
                Text(text = "Entry name", color = theme.dim, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = theme.foreground, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(theme.cursor),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "[ CANCEL ]",
                color = theme.dim,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.background(theme.dim.copy(alpha = 0.12f)).clickable(onClick = onCancel).padding(vertical = 10.dp, horizontal = 16.dp),
            )
            Text(
                text = "[ SAVE ]",
                color = if (canSave) Color.Black else theme.dim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(if (canSave) theme.accent else theme.dim.copy(alpha = 0.12f))
                    .clickable(enabled = canSave, onClick = onSave)
                    .padding(vertical = 10.dp, horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun EmptyJournalBlock(onNewEntry: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text(text = "> NO ENTRIES YET.", color = theme.dim, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(
                text = "START YOUR FIRST JOURNAL ENTRY.",
                color = theme.dim.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                text = "[ NEW ENTRY ]",
                color = Color.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(theme.accent)
                    .clickable(onClick = onNewEntry)
                    .padding(vertical = 18.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(text = "SAVED AS: TXT", color = theme.dim.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun PinnedActionRow(icon: String, label: String, onClick: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "[$icon]",
            color = theme.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            text = label,
            color = theme.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun MenuActionRow(label: String, onClick: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Text(
        text = label,
        color = theme.accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

// Matches BooksScreen.kt's GroupBooksList in-list card exactly — real iOS
// independently builds the identical "[>] NAME ... [EDIT]" +
// description + count card for both Books and Writing group drill-ins.
@Composable
private fun GroupInfoCard(group: WritingGroup, count: Int, onEdit: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.selectionBackground.copy(alpha = 0.3f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "[>] ${group.name}",
                color = theme.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "[EDIT]",
                color = theme.secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable(onClick = onEdit),
            )
        }
        if (group.groupDescription.isNotBlank()) {
            Text(text = group.groupDescription, color = theme.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 2)
        }
        Text(text = if (count == 1) "1 entry" else "$count entries", color = theme.dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun GroupRow(group: WritingGroup, count: Int, onClick: () -> Unit, onMore: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.name,
                color = theme.foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(
                text = if (count == 1) "1 entry" else "$count entries",
                color = theme.dim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = "···",
            color = theme.dim,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable(onClick = onMore).padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun DocumentRow(document: WritingDocument, onOpen: () -> Unit, onMore: () -> Unit) {
    val theme = LocalTerminalTheme.current
    val dateFmt = remember {
        DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US).withZone(ZoneId.systemDefault())
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "> ${document.title}",
                color = theme.foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(
                text = "${dateFmt.format(document.lastModified)} · ${document.wordCount} WORDS",
                color = theme.dim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = "···",
            color = theme.dim,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clickable(onClick = onMore)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
