package com.termi.android.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.termi.android.books.BookLibraryManager
import com.termi.android.data.TermiDatabase
import com.termi.android.music.AudioPlayerEngine
import com.termi.android.music.MusicLibraryManager
import com.termi.android.music.PlaylistManager
import com.termi.android.stats.MusicStatsManager
import com.termi.android.stats.ReadingStatsManager
import com.termi.android.stats.WritingStatsManager
import com.termi.android.ui.BookReaderScreen
import com.termi.android.ui.BooksScreen
import com.termi.android.ui.BottomTabBar
import com.termi.android.ui.MiniPlayerBar
import com.termi.android.ui.MusicLibraryScreen
import com.termi.android.ui.PackagesScreen
import com.termi.android.ui.PlayerScreen
import com.termi.android.ui.CRTOverlay
import com.termi.android.ui.SettingsScreen
import com.termi.android.ui.WritingEditorScreen
import com.termi.android.ui.WritingListScreen
import com.termi.android.theme.LocalTerminalTheme
import com.termi.android.settings.AppSettings
import com.termi.android.writing.WritingManager

object Routes {
    const val MUSIC = "music"
    const val BOOKS = "books"
    const val WRITING = "writing"
    const val PACKAGES = "packages"
    const val WRITING_EDITOR = "writing_editor/{filename}"

    fun writingEditor(filename: String) = "writing_editor/${Uri.encode(filename)}"
}

// The four AppTab root routes get the persistent bottom tab bar; everything
// else (editor, in-screen file browser) is a drill-in that hides it, same as
// iOS's tab bar disappearing behind pushed content. Settings is NOT a nav
// route — like Player/VisualizerPicker, it's a fullScreenCover-style modal
// (see FullScreenCover.kt) toggled by plain state, matching iOS's
// `.fullScreenCover(isPresented:)` presentation.
private val TAB_ROUTES = setOf(Routes.MUSIC, Routes.BOOKS, Routes.WRITING, Routes.PACKAGES)

@Composable
fun TermiNavHost(
    db: TermiDatabase,
    appSettings: AppSettings,
    playback: com.termi.android.music.PlaybackService,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    val writingManager = remember { WritingManager(context) }
    LaunchedEffect(Unit) { writingManager.loadDocuments() }
    val writingStatsManager = remember { WritingStatsManager(db.writingEventDao()) }
    val readingStatsManager = remember { ReadingStatsManager(db.readingEventDao()) }
    val musicStatsManager = playback.musicStats
    val musicLibrary = remember { MusicLibraryManager(context) }
    val audioPlayer = playback.music
    val playlistManager = remember { PlaylistManager(db.playlistDao()) }
    val bookLibrary = remember { BookLibraryManager(context) }
    val mixtapePlayer = playback.mixtape

    val backStackEntry: NavBackStackEntry? by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showTabBar = currentRoute in TAB_ROUTES

    // Mirrors iOS's `showPlayer` + `.fullScreenCover` — the full now-playing
    // screen is a modal over EVERYTHING (including the tab bar), toggled by
    // a plain boolean rather than being a nav destination, since it's
    // reachable identically from every tab via the mini bar.
    var showPlayerModal by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var settingsTab by remember { mutableStateOf(AppTab.MUSIC) }
    // Mirrors iOS's `.fullScreenCover(item: $selectedBook)` — the reader is
    // a modal over everything, not a nav destination, same reasoning as the
    // player above.
    var showReader by remember { mutableStateOf(false) }
    var readerBook by remember { mutableStateOf<com.termi.android.books.Book?>(null) }
    var stickyReaderBook by remember { mutableStateOf<com.termi.android.books.Book?>(null) }
    LaunchedEffect(readerBook) { if (readerBook != null) stickyReaderBook = readerBook }
    val theme = LocalTerminalTheme.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                NavHost(navController = navController, startDestination = Routes.MUSIC) {
                    composable(Routes.MUSIC) {
                        MusicLibraryScreen(
                            library = musicLibrary,
                            player = audioPlayer,
                            playlistManager = playlistManager,
                            onSettings = { settingsTab = AppTab.MUSIC; showSettings = true },
                        )
                    }
                    composable(Routes.BOOKS) {
                        BooksScreen(
                            library = bookLibrary,
                            onOpenBook = { book -> readerBook = book; showReader = true },
                            onSettings = { settingsTab = AppTab.BOOKS; showSettings = true },
                        )
                    }
                    composable(Routes.WRITING) {
                        WritingListScreen(
                            manager = writingManager,
                            onOpenDocument = { doc -> navController.navigate(Routes.writingEditor(doc.file.name)) },
                            onSettings = { settingsTab = AppTab.JOURNAL; showSettings = true },
                        )
                    }
                    composable(Routes.PACKAGES) {
                        PackagesScreen(
                            appSettings = appSettings,
                            musicLibrary = musicLibrary,
                            bookLibrary = bookLibrary,
                            writingManager = writingManager,
                            audioPlayer = audioPlayer,
                            mixtapePlayer = mixtapePlayer,
                            onSettings = { settingsTab = AppTab.PACKAGES; showSettings = true },
                        )
                    }
                    composable(
                        route = Routes.WRITING_EDITOR,
                        arguments = listOf(navArgument("filename") { type = NavType.StringType }),
                    ) { backStackEntry ->
                        val filename = backStackEntry.arguments?.getString("filename").orEmpty()
                        val documents by writingManager.documents.collectAsState()
                        val document = documents.firstOrNull { it.file.name == filename }
                        if (document != null) {
                            WritingEditorScreen(
                                document = document,
                                writingManager = writingManager,
                                statsManager = writingStatsManager,
                                audioPlayer = audioPlayer,
                                onClose = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
            // Mirrors iOS's safeAreaInset bottom stack: mini bar (when a song
            // is loaded) sits directly above the tab bar, on every tab.
            val hasSong by audioPlayer.currentSong.collectAsState()
            if (hasSong != null) {
                MiniPlayerBar(player = audioPlayer, onOpen = { showPlayerModal = true })
            }
            if (showTabBar) {
                BottomTabBar(
                    activeTab = AppTab.entries.first { it.route == currentRoute },
                    onSelect = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
        // One overlay for the WHOLE stack (tab content + mini bar + tab bar),
        // matching iOS's iPhoneLayout — a single CRTOverlay wrapping
        // contentStack() (which itself includes the safeAreaInset bottom
        // stack), not one per screen. The player modal below gets its own
        // separate overlay, same as iOS applies one per fullScreenCover.
        CRTOverlay()
        com.termi.android.ui.FullScreenCover(
            visible = showPlayerModal,
            onDismissRequest = { showPlayerModal = false },
        ) {
            PlayerScreen(player = audioPlayer, appSettings = appSettings, onDismiss = { showPlayerModal = false })
        }
        com.termi.android.ui.FullScreenCover(
            visible = showSettings,
            onDismissRequest = { showSettings = false },
        ) {
            SettingsScreen(
                activeTab = settingsTab,
                appSettings = appSettings,
                writingStatsManager = writingStatsManager,
                readingStatsManager = readingStatsManager,
                musicStatsManager = musicStatsManager,
                audioPlayer = audioPlayer,
                onDismiss = { showSettings = false },
            )
        }
        com.termi.android.ui.FullScreenCover(
            visible = showReader,
            onDismissRequest = { showReader = false; readerBook = null },
        ) {
            val book = stickyReaderBook
            if (book != null) {
                BookReaderScreen(
                    book = book,
                    library = bookLibrary,
                    readingStats = readingStatsManager,
                    audioPlayer = audioPlayer,
                    appSettings = appSettings,
                    onClose = { showReader = false; readerBook = null },
                )
            }
        }
        // Renders every FullScreenCover() registered anywhere in the tree —
        // must be the LAST child here so modals paint on top of everything
        // above, including the global CRTOverlay.
        com.termi.android.ui.FullScreenOverlayHost()
    }
}
