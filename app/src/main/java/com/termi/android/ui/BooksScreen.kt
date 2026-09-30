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
import androidx.compose.runtime.LaunchedEffect
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
import com.termi.android.books.Book
import com.termi.android.books.BookGroup
import com.termi.android.books.BookLibraryManager
import com.termi.android.theme.LocalTerminalTheme
import kotlinx.coroutines.launch

private sealed class BookNav {
    object Root : BookNav()
    data class GroupBooks(val group: BookGroup) : BookNav()
}

private enum class BookMenuState { OPTIONS, CONFIRM_RESTART, CONFIRM_REMOVE }
private enum class GroupMenuState { OPTIONS, CONFIRM_DELETE }

// Kotlin/Compose port of BookLibraryView.swift — full parity pass: header
// [+ADD]/[SETTINGS] pills, search, groups (create/edit/delete, add-to-group,
// drill-down), per-book/group "···" morphing action panels (options ->
// confirm, matching the Swift spring(0.28, 0.82) morph-in-place), loading
// and real empty states. Replaces the earlier flat-list-only pass, and
// drops the stock Material3 AlertDialogs it had been using (rule 3).
@Composable
fun BooksScreen(
    library: BookLibraryManager,
    onOpenBook: (Book) -> Unit = {},
    onSettings: (() -> Unit)? = null,
) {
    val theme = LocalTerminalTheme.current
    val books by library.books.collectAsState()
    val groups by library.groups.collectAsState()
    val hasLoadedOnce by library.hasLoadedOnce.collectAsState()
    var query by remember { mutableStateOf("") }
    var nav by remember { mutableStateOf<BookNav>(BookNav.Root) }

    var showFileBrowser by remember { mutableStateOf(false) }
    val importProgress = rememberImportProgressController()
    val importScope = androidx.compose.runtime.rememberCoroutineScope()
    var showImportMethod by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    val sources by library.sources.collectAsState()
    var showCreateGroup by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<BookGroup?>(null) }
    var showAddToGroup by remember { mutableStateOf(false) }
    var addToGroupBook by remember { mutableStateOf<Book?>(null) }

    var actionBook by remember { mutableStateOf<Book?>(null) }
    var bookMenuState by remember { mutableStateOf(BookMenuState.OPTIONS) }
    var actionGroup by remember { mutableStateOf<BookGroup?>(null) }
    var groupMenuState by remember { mutableStateOf(GroupMenuState.OPTIONS) }

    LaunchedEffect(Unit) { if (!library.hasLoadedOnce.value) library.loadLibrary() }
    // Picks up anything WiFi Transfer just wrote into the books folder —
    // without this, a mid-session upload wouldn't show until the app
    // restarted (caught live: "i uploaded a book... it didn't show until i
    // closed it and reopened the app").
    val wifiUploadLog by com.termi.android.network.WiFiTransferServer.uploadLog.collectAsState()
    LaunchedEffect(wifiUploadLog) { library.refreshForNewFiles() }
    LaunchedEffect(query) { library.setSearchQuery(query) }

    fun closeBookMenu() {
        actionBook = null
        bookMenuState = BookMenuState.OPTIONS
    }
    fun closeGroupMenu() {
        actionGroup = null
        groupMenuState = GroupMenuState.OPTIONS
    }

    FullScreenCover(visible = showSources, onDismissRequest = { showSources = false }) {
        BookSourcesScreen(
            sources = sources,
            itemCount = { library.itemCount(it) },
            onAddSource = {
                showSources = false
                showFileBrowser = true
            },
            onOpenWifiTransfer = {
                showSources = false
                showImportMethod = true
            },
            onRemoveSource = { library.removeSource(it) },
            onDismiss = { showSources = false },
        )
    }

    FullScreenCover(visible = showImportMethod, onDismissRequest = { showImportMethod = false }) {
        ImportMethodScreen(
            target = ImportTarget.BOOKS,
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
                extensionFilter = setOf("epub"),
                onFileSelected = { file ->
                    showFileBrowser = false
                    val root = file.parentFile ?: file
                    library.registerSource(root)
                    importScope.launch { importProgress.run(listOf(file)) { library.importFromFile(it, root.absolutePath) } }
                },
                onFolderSelected = { rootDir, files ->
                    showFileBrowser = false
                    library.registerSource(rootDir)
                    importScope.launch { importProgress.run(files) { library.importFromFile(it, rootDir.absolutePath) } }
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
        BookGroupEditorScreen(
            group = editingGroup,
            allBooks = books,
            onSave = { name, description, filenames ->
                val existing = editingGroup
                if (existing != null) {
                    library.updateGroup(existing, name, description, filenames)
                } else {
                    library.createGroup(name, description, filenames)
                }
                showCreateGroup = false
                editingGroup = null
            },
            onCancel = { showCreateGroup = false; editingGroup = null },
        )
    }

    FullScreenCover(visible = showAddToGroup, onDismissRequest = { showAddToGroup = false; addToGroupBook = null }) {
        val book = addToGroupBook
        if (book != null) {
            AddToGroupScreen(
                book = book,
                groups = groups,
                onToggle = { group -> library.toggleGroupMembership(book, group) },
                onCancel = { showAddToGroup = false; addToGroupBook = null },
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            val currentGroup = (nav as? BookNav.GroupBooks)?.group
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (currentGroup == null) "┌─[ TERMI BOOKS ]" else "┌─[ ${currentGroup.name} ]",
                    color = theme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (currentGroup == null) {
                    HeaderPillButton("[+ADD]") { showSources = true }
                }
                if (onSettings != null) {
                    HeaderPillButton("[SETTINGS]", onClick = onSettings)
                }
            }
            if (books.isNotEmpty() && currentGroup == null) {
                SearchBar(query = query, onQueryChange = { query = it })
            }

            when {
                !hasLoadedOnce -> LoadingBlock()
                books.isEmpty() && groups.isEmpty() -> EmptyLibraryBlock(onAdd = { showSources = true })
                currentGroup != null -> GroupBooksList(
                    group = currentGroup,
                    books = library.booksIn(currentGroup),
                    onBack = { nav = BookNav.Root },
                    onEditGroup = {
                        editingGroup = currentGroup
                        showCreateGroup = true
                    },
                    onOpenBook = onOpenBook,
                    onBookMenu = { actionBook = it },
                )
                else -> RootBooksList(
                    query = query,
                    filteredBooks = library.filteredBooks,
                    groups = groups,
                    ungroupedBooks = library.ungroupedBooks,
                    onCreateGroup = { editingGroup = null; showCreateGroup = true },
                    onOpenGroup = { nav = BookNav.GroupBooks(it) },
                    onGroupMenu = { actionGroup = it },
                    onOpenBook = onOpenBook,
                    onBookMenu = { actionBook = it },
                )
            }
        }
        CRTOverlay()
        ImportProgressOverlay(importProgress)
    }

    actionBook?.let { book ->
        BookActionOverlay(
            state = bookMenuState,
            book = book,
            onOpenBook = { onOpenBook(book); closeBookMenu() },
            onAddToGroup = { addToGroupBook = book; showAddToGroup = true; closeBookMenu() },
            onRequestRestart = { bookMenuState = BookMenuState.CONFIRM_RESTART },
            onRequestRemove = { bookMenuState = BookMenuState.CONFIRM_REMOVE },
            onConfirmRestart = { library.resetProgress(book); closeBookMenu() },
            onConfirmRemove = { library.removeBook(book); closeBookMenu() },
            onBackToOptions = { bookMenuState = BookMenuState.OPTIONS },
            onDismiss = { closeBookMenu() },
        )
    }

    actionGroup?.let { group ->
        GroupActionOverlay(
            state = groupMenuState,
            group = group,
            onEdit = {
                editingGroup = group
                showCreateGroup = true
                closeGroupMenu()
            },
            onRequestDelete = { groupMenuState = GroupMenuState.CONFIRM_DELETE },
            onConfirmDelete = {
                library.deleteGroup(group)
                if ((nav as? BookNav.GroupBooks)?.group?.id == group.id) nav = BookNav.Root
                closeGroupMenu()
            },
            onBackToOptions = { groupMenuState = GroupMenuState.OPTIONS },
            onDismiss = { closeGroupMenu() },
        )
    }
}

@Composable
private fun LoadingBlock() {
    val theme = LocalTerminalTheme.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "loading books…", color = theme.dim, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun EmptyLibraryBlock(onAdd: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text(text = "> NO BOOKS FOUND.", color = theme.dim, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(
                text = "IMPORT AN EPUB FILE TO GET STARTED.",
                color = theme.dim.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "[ ADD BOOK ]",
                color = Color.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(theme.accent)
                    .clickable(onClick = onAdd)
                    .padding(vertical = 18.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(text = "SUPPORTED: EPUB", color = theme.dim.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun RootBooksList(
    query: String,
    filteredBooks: List<Book>,
    groups: List<BookGroup>,
    ungroupedBooks: List<Book>,
    onCreateGroup: () -> Unit,
    onOpenGroup: (BookGroup) -> Unit,
    onGroupMenu: (BookGroup) -> Unit,
    onOpenBook: (Book) -> Unit,
    onBookMenu: (Book) -> Unit,
) {
    val theme = LocalTerminalTheme.current
    LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
        if (query.isNotBlank()) {
            items(filteredBooks) { book -> BookRow(book, onClick = { onOpenBook(book) }, onMore = { onBookMenu(book) }) }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onCreateGroup).padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "[>]", color = theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.width(24.dp))
                    Text(text = "CREATE GROUP", color = theme.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.3f)))
            }
            if (groups.isNotEmpty()) {
                item { TerminalSectionHeader(title = "GROUPS") }
                items(groups) { group ->
                    GroupRow(group, onClick = { onOpenGroup(group) }, onMore = { onGroupMenu(group) })
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.2f)))
                }
            }
            if (ungroupedBooks.isNotEmpty()) {
                item { TerminalSectionHeader(title = "BOOKS") }
                items(ungroupedBooks) { book ->
                    BookRow(book, onClick = { onOpenBook(book) }, onMore = { onBookMenu(book) })
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.2f)))
                }
            }
        }
        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}

@Composable
private fun GroupBooksList(
    group: BookGroup,
    books: List<Book>,
    onBack: () -> Unit,
    onEditGroup: () -> Unit,
    onOpenBook: (Book) -> Unit,
    onBookMenu: (Book) -> Unit,
) {
    val theme = LocalTerminalTheme.current
    LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onBack).padding(horizontal = 2.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "<", color = theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "[BACK]", color = theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.3f)))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.selectionBackground.copy(alpha = 0.3f))
                    .padding(horizontal = 2.dp, vertical = 10.dp),
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
                        modifier = Modifier.clickable(onClick = onEditGroup),
                    )
                }
                if (group.groupDescription.isNotBlank()) {
                    Text(text = group.groupDescription, color = theme.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 2)
                }
                Text(text = "${books.size} book(s)", color = theme.dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.3f)))
        }
        if (books.isEmpty()) {
            item {
                Text(
                    text = "[ EMPTY GROUP ]",
                    color = theme.dim,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            items(books) { book ->
                BookRow(book, onClick = { onOpenBook(book) }, onMore = { onBookMenu(book) })
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.2f)))
            }
        }
        item { Spacer(modifier = Modifier.height(120.dp)) }
    }
}

@Composable
private fun GroupRow(group: BookGroup, onClick: () -> Unit, onMore: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = group.name, color = theme.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
            val count = group.bookFilenames.size
            Text(text = "$count book${if (count == 1) "" else "s"}", color = theme.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Text(
            text = "···",
            color = theme.dim,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable(onClick = onMore).padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun BookRow(book: Book, onClick: () -> Unit, onMore: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = book.title, color = theme.foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
            Text(text = book.author, color = theme.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
        Text(
            text = book.formattedProgress,
            color = if (book.progressFraction > 0) theme.secondary else theme.dim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(38.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
        Text(
            text = "···",
            color = theme.dim,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable(onClick = onMore).padding(horizontal = 6.dp),
        )
    }
}

// MARK: - Morphing action panels (options <-> confirm), matching
// BookLibraryView.swift's bookActionOverlay/groupActionOverlay: a single
// 280dp panel whose CONTENT morphs between states in place, over a
// tap-to-dismiss scrim.

@Composable
private fun BookActionOverlay(
    state: BookMenuState,
    book: Book,
    onOpenBook: () -> Unit,
    onAddToGroup: () -> Unit,
    onRequestRestart: () -> Unit,
    onRequestRemove: () -> Unit,
    onConfirmRestart: () -> Unit,
    onConfirmRemove: () -> Unit,
    onBackToOptions: () -> Unit,
    onDismiss: () -> Unit,
) {
    ActionOverlayScaffold(onDismiss = onDismiss) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { (scaleIn(initialScale = 0.92f) + fadeIn(tween(220))) togetherWith (scaleOut(targetScale = 0.92f) + fadeOut(tween(160))) },
            label = "bookMenu",
        ) { s ->
            when (s) {
                BookMenuState.OPTIONS -> ActionOptionsPanel(
                    rows = listOf(
                        ActionRow(">", "OPEN BOOK", false, onOpenBook),
                        ActionRow("+", "ADD TO GROUP", false, onAddToGroup),
                        ActionRow("~", "RESTART FROM START", false, onRequestRestart),
                        ActionRow("x", "REMOVE FROM LIBRARY", true, onRequestRemove),
                    ),
                    onCancel = onDismiss,
                )
                BookMenuState.CONFIRM_RESTART -> ActionConfirmPanel(
                    title = "╔═[ RESTART BOOK ]",
                    message = "\"${book.title}\" — this resets all reading progress to 0%. This cannot be undone.",
                    confirmLabel = "[ YES, RESTART ]",
                    onCancel = onBackToOptions,
                    onConfirm = onConfirmRestart,
                )
                BookMenuState.CONFIRM_REMOVE -> ActionConfirmPanel(
                    title = "╔═[ REMOVE BOOK ]",
                    message = "\"${book.title}\" — this permanently removes the book from your library. This cannot be undone.",
                    confirmLabel = "[ YES, REMOVE ]",
                    onCancel = onBackToOptions,
                    onConfirm = onConfirmRemove,
                )
            }
        }
    }
}

@Composable
private fun GroupActionOverlay(
    state: GroupMenuState,
    group: BookGroup,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onBackToOptions: () -> Unit,
    onDismiss: () -> Unit,
) {
    ActionOverlayScaffold(onDismiss = onDismiss) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { (scaleIn(initialScale = 0.92f) + fadeIn(tween(220))) togetherWith (scaleOut(targetScale = 0.92f) + fadeOut(tween(160))) },
            label = "groupMenu",
        ) { s ->
            when (s) {
                GroupMenuState.OPTIONS -> ActionOptionsPanel(
                    rows = listOf(
                        ActionRow("*", "EDIT GROUP", false, onEdit),
                        ActionRow("x", "DELETE GROUP", true, onRequestDelete),
                    ),
                    onCancel = onDismiss,
                )
                GroupMenuState.CONFIRM_DELETE -> ActionConfirmPanel(
                    title = "╔═[ DELETE GROUP ]",
                    message = "> \"${group.name}\"\nBOOKS IN THIS GROUP WILL NOT BE DELETED, ONLY REMOVED FROM THE GROUP.",
                    confirmLabel = "[ YES, DELETE ]",
                    onCancel = onBackToOptions,
                    onConfirm = onConfirmDelete,
                )
            }
        }
    }
}
