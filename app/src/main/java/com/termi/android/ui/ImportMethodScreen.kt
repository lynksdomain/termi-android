package com.termi.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termi.android.network.WiFiTransferServer
import com.termi.android.theme.LocalTerminalTheme
import com.termi.android.theme.TerminalTheme
import kotlinx.coroutines.delay

// Which library the picker was opened from — mirrors ImportTarget.swift.
// Only the extensions/noun differ per target; the sheet UI is shared.
enum class ImportTarget(val noun: String, val extensions: Set<String>) {
    MUSIC("MUSIC", setOf("mp3", "m4a", "mp4", "aac", "wav", "aiff", "flac", "ogg")),
    BOOKS("BOOK", setOf("epub")),
    JOURNAL("TEXT FILE", setOf("txt", "md")),
}

// Kotlin/Compose port of ImportMethodSheet.swift — shown when the user taps
// [+ADD]/[+ IMPORT ...] in a library screen. Three methods: our own file
// browser (replaces iOS's DocumentPicker sheet), a USB/MTP help block
// (Android has no Finder-over-USB integration, so this is reworded — see
// android_port_rules memory), and a real WiFi Transfer panel backed by
// WiFiTransferServer (mirrors the Swift NWListener-based server).
@Composable
fun ImportMethodScreen(
    target: ImportTarget,
    onBrowseFiles: () -> Unit,
    onDismiss: () -> Unit,
    openWifiPanel: Boolean = false,
) {
    val theme = LocalTerminalTheme.current
    val context = LocalContext.current
    var showUsbHelp by remember { mutableStateOf(false) }
    var showWifiPanel by remember { mutableStateOf(openWifiPanel) }

    val wifiRunning by WiFiTransferServer.isRunning.collectAsState()
    val wifiUrl by WiFiTransferServer.serverUrl.collectAsState()
    val wifiPin by WiFiTransferServer.pin.collectAsState()
    val uploadLog by WiFiTransferServer.uploadLog.collectAsState()

    LaunchedEffect(Unit) {
        if (WiFiTransferServer.isRunning.value) showWifiPanel = true
    }

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
            ) {
                Text(
                    text = "┌─[ IMPORT ${target.noun} ]",
                    color = theme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "[CANCEL]",
                    color = theme.secondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(vertical = 8.dp),
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(theme.accent.copy(alpha = 0.3f)))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            ) {
                item {
                    Text(
                        text = "> SELECT IMPORT METHOD:",
                        color = theme.dim,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 24.dp, bottom = 16.dp),
                    )
                }
                item {
                    MethodButton(
                        label = "[ 1 ]  BROWSE FILES  —  DEVICE STORAGE",
                        theme = theme,
                        onClick = onBrowseFiles,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    MethodButton(
                        label = "[ 2 ]  USB CABLE  —  CONNECT TO COMPUTER",
                        theme = theme,
                        onClick = {
                            showUsbHelp = !showUsbHelp
                            if (showUsbHelp) showWifiPanel = false
                        },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (showUsbHelp) {
                    item {
                        UsbHelpBlock(theme = theme, onDone = onDismiss)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                item {
                    WifiMethodButton(
                        theme = theme,
                        isRunning = wifiRunning,
                        onClick = {
                            showWifiPanel = !showWifiPanel
                            if (showWifiPanel) showUsbHelp = false
                        },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (showWifiPanel) {
                    item {
                        WifiPanel(
                            theme = theme,
                            isRunning = wifiRunning,
                            serverUrl = wifiUrl,
                            pin = wifiPin,
                            uploadLog = uploadLog.take(3),
                            onToggleServer = {
                                if (wifiRunning) {
                                    WiFiTransferServer.stop()
                                } else {
                                    startWifiServer(context, theme)
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                item { Spacer(modifier = Modifier.height(64.dp)) }
            }
        }
        CRTOverlay()
    }
}

private fun startWifiServer(context: Context, theme: TerminalTheme) {
    WiFiTransferServer.start(
        context = context,
        accentHex = theme.accent.toHexString(),
        accentRgb = theme.accent.toRgbTriplet(),
        dimHex = theme.dim.toHexString(),
        subTextHex = theme.dim.toHexString(),
        backgroundHex = theme.background.toHexString(),
    )
}

private fun Color.toHexString(): String {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X", r, g, b)
}

private fun Color.toRgbTriplet(): String {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    return "$r,$g,$b"
}

@Composable
private fun MethodButton(label: String, theme: TerminalTheme, onClick: () -> Unit) {
    Text(
        text = label,
        color = theme.accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(theme.accent.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    )
}

@Composable
private fun WifiMethodButton(theme: TerminalTheme, isRunning: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(theme.accent.copy(alpha = if (isRunning) 0.12f else 0.07f))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = "[ 3 ]  WIFI TRANSFER  —  SAME NETWORK",
            color = theme.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        if (isRunning) {
            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(theme.accent),
            )
        }
    }
}

@Composable
private fun UsbHelpBlock(theme: TerminalTheme, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Divider(theme)
        listOf(
            "1. CONNECT YOUR ANDROID DEVICE TO A COMPUTER.",
            "2. ALLOW \"FILE TRANSFER (MTP)\" IF PROMPTED.",
            "3. OPEN THE DEVICE IN YOUR COMPUTER'S FILE",
            "   BROWSER (EXPLORER / FINDER WITH ANDROID",
            "   FILE TRANSFER).",
            "4. NAVIGATE TO THE APP'S STORAGE FOLDER AND",
            "   DROP FILES IN.",
            "5. REOPEN THE APP TO SEE NEW FILES.",
        ).forEach { line ->
            Text(
                text = line,
                color = theme.foreground.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Divider(theme)
        Text(
            text = "[ DONE ]",
            color = Color.Black,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(theme.accent)
                .clickable(onClick = onDone)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun Divider(theme: TerminalTheme) {
    Text(
        text = "──────────────────────────────────",
        color = theme.dim.copy(alpha = 0.4f),
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun WifiPanel(
    theme: TerminalTheme,
    isRunning: Boolean,
    serverUrl: String,
    pin: String,
    uploadLog: List<WiFiTransferServer.UploadEntry>,
    onToggleServer: () -> Unit,
) {
    val context = LocalContext.current
    var urlCopied by remember { mutableStateOf(false) }
    LaunchedEffect(urlCopied) {
        if (urlCopied) {
            delay(1500)
            urlCopied = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.selectionBackground.copy(alpha = 0.12f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isRunning) theme.accent else theme.dim),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isRunning) "SERVER ONLINE" else "SERVER OFFLINE",
                color = if (isRunning) theme.accent else theme.dim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }

        if (isRunning && serverUrl.isNotEmpty()) {
            Text(
                text = serverUrl,
                color = if (urlCopied) theme.secondary else theme.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Termi Transfer URL", serverUrl))
                    urlCopied = true
                },
            )
            Text(
                text = if (urlCopied) "> COPIED!" else "> TAP URL TO COPY · OPEN IN A BROWSER",
                color = if (urlCopied) theme.secondary else theme.dim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
            Row {
                Text(
                    text = "PIN: ",
                    color = theme.dim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = pin,
                    color = theme.accent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp,
                )
            }
            Text(
                text = "> ENTER PIN IN BROWSER TO UNLOCK TRANSFER",
                color = theme.dim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        Text(
            text = if (isRunning) "[ STOP SERVER ]" else "[ START SERVER ]",
            color = Color.Black,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(if (isRunning) theme.dim else theme.accent)
                .clickable(onClick = onToggleServer)
                .padding(vertical = 12.dp),
        )

        if (uploadLog.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "> RECENT TRANSFERS:",
                    color = theme.dim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                uploadLog.forEach { entry ->
                    Row {
                        Text(
                            text = if (entry.success) "[ OK ]" else "[ERR]",
                            color = if (entry.success) theme.accent else theme.dim,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = entry.filename,
                            color = theme.foreground,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
