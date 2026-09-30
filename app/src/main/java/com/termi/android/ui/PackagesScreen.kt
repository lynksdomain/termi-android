package com.termi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.pkgs.PackageArchive
import com.termi.android.pkgs.PackageBridgeManager
import com.termi.android.pkgs.PackageSentReceipt
import com.termi.android.pkgs.PackageStorageManager
import com.termi.android.pkgs.ReceivedPackage
import com.termi.android.settings.AppSettings
import com.termi.android.theme.LocalTerminalTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// PKGS tab root — Inbox/Sent list + Create/Listen actions, backed by the
// real `.termi` container format (PackageArchive), the real mixtape
// audio-fusing engine (MixtapeComposer), and the real mDNS+HTTP bridge
// transport (PackageBridgeManager). Deferred vs iOS's real feature: no
// picking a quote from a real book or a note from a real Journal entry
// (plain text fields stand in for those pickers for now) — see
// PackageCreateScreen's own comment.
@Composable
fun PackagesScreen(
    appSettings: AppSettings,
    musicLibrary: com.termi.android.music.MusicLibraryManager,
    bookLibrary: com.termi.android.books.BookLibraryManager,
    writingManager: com.termi.android.writing.WritingManager,
    audioPlayer: com.termi.android.music.AudioPlayerEngine,
    mixtapePlayer: com.termi.android.pkgs.MixtapePlaybackManager,
    onSettings: () -> Unit,
) {
    val theme = LocalTerminalTheme.current
    val context = LocalContext.current
    val signature by appSettings.packageSignature.collectAsState()
    val displayName = AppSettings.formatPackageSignature(signature).ifBlank { "GUEST" }

    val storageManager = remember { PackageStorageManager(context) }
    val bridgeManager = remember {
        PackageBridgeManager(context) { tempFile, platform -> storageManager.ingestReceivedPackage(tempFile, platform) }
    }
    val received by storageManager.received.collectAsState()
    val sent by storageManager.sent.collectAsState()

    LaunchedEffect(Unit) { storageManager.loadAll() }

    val hasSignature = signature.trim().isNotEmpty()
    var showSignaturePrompt by remember { mutableStateOf(false) }
    var signaturePromptContext by remember { mutableStateOf(SignaturePromptContext.SENDING) }
    var pendingSignatureAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun requireSignature(context: SignaturePromptContext, action: () -> Unit) {
        if (hasSignature) {
            action()
        } else {
            signaturePromptContext = context
            pendingSignatureAction = action
            showSignaturePrompt = true
        }
    }

    var showCreate by remember { mutableStateOf(false) }
    var ritualMode by remember { mutableStateOf<RitualMode?>(null) }
    var selectedReceived by remember { mutableStateOf<ReceivedPackage?>(null) }
    val createScope = androidx.compose.runtime.rememberCoroutineScope()

    FullScreenCover(visible = showSignaturePrompt, onDismissRequest = { showSignaturePrompt = false; pendingSignatureAction = null }) {
        PackageSignaturePromptScreen(
            context = signaturePromptContext,
            onConfirm = { trimmed ->
                appSettings.setPackageSignature(trimmed)
                showSignaturePrompt = false
                val action = pendingSignatureAction
                pendingSignatureAction = null
                action?.invoke()
            },
        )
    }

    FullScreenCover(visible = showCreate, onDismissRequest = { showCreate = false }) {
        PackageCreateScreen(
            senderSignature = displayName,
            musicLibrary = musicLibrary,
            bookLibrary = bookLibrary,
            writingManager = writingManager,
            onCreate = { manifest, quote, note, mixtapeBuild ->
                showCreate = false
                createScope.launch {
                    val file = File(context.cacheDir, "outgoing_${manifest.id}.termi")
                    withContext(Dispatchers.IO) {
                        if (mixtapeBuild == null) {
                            PackageArchive.write(file, manifest, quote = quote, note = note)
                        } else {
                            PackageArchive.write(
                                file,
                                manifest,
                                mixtapeSource = mixtapeBuild.file,
                                tracklist = mixtapeBuild.tracklist,
                                messages = mixtapeBuild.messages,
                                quote = quote,
                                note = note,
                            )
                            mixtapeBuild.file.delete()
                        }
                    }
                    storageManager.recordSent(manifest)
                    ritualMode = RitualMode.Send(file, manifest.title, manifest.mood)
                }
            },
            onCancel = { showCreate = false },
        )
    }

    FullScreenCover(visible = ritualMode != null, onDismissRequest = { ritualMode = null }) {
        ritualMode?.let { mode ->
            PackageRitualScreen(
                bridgeManager = bridgeManager,
                displayName = displayName,
                mode = mode,
                onDone = { ritualMode = null; storageManager.loadAll() },
            )
        }
    }

    FullScreenCover(visible = selectedReceived != null, onDismissRequest = { selectedReceived = null }) {
        selectedReceived?.let { pkg ->
            PackageDetailScreen(
                pkg = pkg,
                mixtapePlayer = mixtapePlayer,
                onPlayMixtape = { audioPlayer.pause() },
                onBack = { selectedReceived = null },
                onDelete = { storageManager.deleteReceived(pkg); selectedReceived = null },
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            // Kotlin/Compose port of PackagesView.swift's headerBar — NOT
            // the shared TerminalStatsHeaderBar (different glyph/title
            // shape: "┌─[ TERMI PACKAGES ]" + a bold [SETTINGS], no
            // [BACK]/[DELETE] slots at all).
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "┌─[ TERMI PACKAGES ]",
                    color = theme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                Text(
                    text = "[SETTINGS]",
                    color = theme.secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable(onClick = onSettings).padding(vertical = 8.dp),
                )
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    ActionRow(label = "[+ CREATE PACKAGE]") { requireSignature(SignaturePromptContext.SENDING) { showCreate = true } }
                    ActionRow(label = "[ LISTEN FOR INCOMING ]") { requireSignature(SignaturePromptContext.LISTENING) { ritualMode = RitualMode.Listen } }
                }

                item { TerminalSectionHeader(title = "RECEIVED") }
                if (received.isEmpty()) {
                    item { EmptyRow("NO PACKAGES RECEIVED YET.") }
                } else {
                    items(received) { pkg -> ReceivedRow(pkg) { selectedReceived = pkg } }
                }

                item { TerminalSectionHeader(title = "SENT") }
                if (sent.isEmpty()) {
                    item { EmptyRow("NOTHING SENT YET.") }
                } else {
                    items(sent) { receipt -> SentRow(receipt) }
                }

                item { Spacer(modifier = Modifier.height(64.dp)) }
            }
        }
        CRTOverlay()
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Text(
        text = label,
        color = theme.accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun EmptyRow(label: String) {
    val theme = LocalTerminalTheme.current
    Text(
        text = "│  $label",
        color = theme.dim,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

@Composable
private fun SlotBadges(hasMusic: Boolean, hasBook: Boolean, hasWriting: Boolean) {
    val theme = LocalTerminalTheme.current
    Row {
        if (hasMusic) SlotBadge("MIX")
        if (hasBook) SlotBadge("QUOTE")
        if (hasWriting) SlotBadge("NOTE")
    }
}

@Composable
private fun SlotBadge(text: String) {
    val theme = LocalTerminalTheme.current
    Text(
        text = "[$text]",
        color = theme.dim,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(end = 6.dp),
    )
}

@Composable
private fun ReceivedRow(pkg: ReceivedPackage, onClick: () -> Unit) {
    val theme = LocalTerminalTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pkg.manifest.title,
                color = theme.foreground,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Row {
                Text(
                    text = "FROM: ${pkg.manifest.senderSignature}",
                    color = theme.dim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = relativeTime(pkg.manifest.createdAt),
                    color = theme.dim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            SlotBadges(hasMusic = pkg.manifest.hasMusic, hasBook = pkg.manifest.hasBook, hasWriting = pkg.manifest.hasWriting)
        }
        pkg.crossedPlatformLabel?.let { platform ->
            CrossedPlatformBadge(platform = platform, compact = true, modifier = Modifier.padding(end = 8.dp))
        }
        Text(
            text = ">",
            color = theme.dim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.15f)))
}

// Kotlin/Compose port of CrossedPlatformBadge.swift — a free, cosmetic
// double-border flag on a package that demonstrably crossed from the
// other platform (iOS, from Android's perspective). Straight, never
// rotated — "a terminal wouldn't stamp things at an angle" — and
// theme.secondary throughout so it stays in-palette rather than an
// invented accent color. Only the OTHER platform is ever worth flagging;
// Android never flags itself.
@Composable
fun CrossedPlatformBadge(platform: String, compact: Boolean = false, modifier: Modifier = Modifier) {
    val theme = LocalTerminalTheme.current
    val outerPad = if (compact) 6.dp else 10.dp
    val outerPadV = if (compact) 1.dp else 3.dp
    val innerInset = if (compact) 2.dp else 3.dp
    Box(
        modifier = modifier
            .border(if (compact) 1.5.dp else 2.dp, theme.secondary)
            .padding(outerPad, outerPadV),
    ) {
        Box(modifier = Modifier.padding(innerInset).border(if (compact) 0.5.dp else 0.75.dp, theme.secondary)) {
            Text(
                text = platform.uppercase(),
                color = theme.secondary,
                fontSize = if (compact) 9.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = if (compact) 4.dp else 6.dp, vertical = if (compact) 1.dp else 2.dp),
            )
        }
    }
}

@Composable
private fun SentRow(receipt: PackageSentReceipt) {
    val theme = LocalTerminalTheme.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = receipt.title,
            color = theme.foreground,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
        Row {
            Text(
                text = "→ ${receipt.recipientSignature ?: "?"}",
                color = theme.dim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = relativeTime(receipt.sentAt),
                color = theme.dim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        SlotBadges(hasMusic = receipt.hasMusic, hasBook = receipt.hasBook, hasWriting = receipt.hasWriting)
    }
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.dim.copy(alpha = 0.15f)))
}

private fun relativeTime(instant: java.time.Instant): String {
    val seconds = java.time.Instant.now().epochSecond - instant.epochSecond
    return when {
        seconds < 60 -> "now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86400 -> "${seconds / 3600}h ago"
        else -> "${seconds / 86400}d ago"
    }
}
