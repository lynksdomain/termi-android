package com.termi.android.network

import android.content.Context
import android.net.wifi.WifiManager
import com.termi.android.books.BookFolderManager
import com.termi.android.music.MusicFolderManager
import com.termi.android.writing.WritingFolderManager
import fi.iki.elonen.NanoHTTPD
import java.io.File
import com.termi.android.filesystem.ImportLimits
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Kotlin port of WiFiTransferServer.swift — a minimal local HTTP server for
// dropping files into Termi from any browser on the same network. iOS binds
// NWListener directly; here NanoHTTPD (a small embeddable HTTP server, no
// transitive deps) plays the same role since Android has no equivalent
// framework-level socket-listener API. Singleton object mirrors the Swift
// `static let shared` — one server, started/stopped from ImportMethodScreen.
object WiFiTransferServer {

    data class UploadEntry(
        val filename: String,
        val success: Boolean,
        val message: String,
        val timestamp: Long,
    )

    private const val PORT = 8080

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _pin = MutableStateFlow("")
    val pin: StateFlow<String> = _pin.asStateFlow()

    private val _uploadLog = MutableStateFlow<List<UploadEntry>>(emptyList())
    val uploadLog: StateFlow<List<UploadEntry>> = _uploadLog.asStateFlow()

    private var server: Server? = null

    // The served page's colors, set from the app's current theme by whoever
    // calls start() (ImportMethodScreen) — mirrors the Swift page*Hex fields.
    private var pageAccentHex = "#39FF14"
    private var pageAccentRgb = "57,255,20"
    private var pageDimHex = "#1A6B2A"
    private var pageSubTextHex = "#2A8010"
    private var pageBackgroundHex = "#020D02"

    fun start(
        context: Context,
        accentHex: String? = null,
        accentRgb: String? = null,
        dimHex: String? = null,
        subTextHex: String? = null,
        backgroundHex: String? = null,
    ) {
        if (_isRunning.value) return
        accentHex?.let { pageAccentHex = it }
        accentRgb?.let { pageAccentRgb = it }
        dimHex?.let { pageDimHex = it }
        subTextHex?.let { pageSubTextHex = it }
        backgroundHex?.let { pageBackgroundHex = it }

        val generatedPin = String.format(Locale.US, "%04d", (0..9999).random())
        _pin.value = generatedPin

        val appContext = context.applicationContext
        val instance = Server(appContext, generatedPin)
        try {
            instance.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        } catch (e: Exception) {
            return
        }
        server = instance
        _isRunning.value = true
        val ip = localIpAddress(appContext) ?: "?"
        _serverUrl.value = "http://$ip:$PORT"
    }

    fun stop() {
        server?.stop()
        server = null
        _isRunning.value = false
        _serverUrl.value = ""
        _pin.value = ""
    }

    private fun record(filename: String, success: Boolean, message: String) {
        val entry = UploadEntry(filename, success, message, System.currentTimeMillis())
        _uploadLog.value = (listOf(entry) + _uploadLog.value).take(100)
    }

    // Prefers wlan0 (WiFi), matching the Swift implementation's en0
    // preference, falling back to any other active non-loopback IPv4.
    private fun localIpAddress(context: Context): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiIp = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (wifiIp != 0) {
                return String.format(
                    Locale.US, "%d.%d.%d.%d",
                    wifiIp and 0xff, (wifiIp shr 8) and 0xff, (wifiIp shr 16) and 0xff, (wifiIp shr 24) and 0xff,
                )
            }
        } catch (e: Exception) {
            // fall through to interface enumeration
        }
        var fallback: String? = null
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (iface in interfaces) {
                val addresses = Collections.list(iface.inetAddresses)
                for (addr in addresses) {
                    if (addr.isLoopbackAddress || addr.hostAddress?.contains(":") == true) continue
                    val host = addr.hostAddress ?: continue
                    if (iface.name.startsWith("wlan")) return host
                    if (fallback == null) fallback = host
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return fallback
    }

    private class Server(private val context: Context, private val expectedPin: String) : NanoHTTPD(PORT) {

        override fun serve(session: IHTTPSession): Response {
            return when {
                session.method == Method.GET && (session.uri == "/" || session.uri == "/index.html") ->
                    newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", indexHtml())
                session.method == Method.POST && session.uri == "/upload" -> handleUpload(session)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
            }
        }

        private fun handleUpload(session: IHTTPSession): Response {
            val providedPin = session.headers["x-termi-pin"]
            if (providedPin != expectedPin) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "forbidden")
            }

            val contentLength = session.headers["content-length"]?.toLongOrNull()
            if (contentLength == null || contentLength !in 1..ImportLimits.MAX_TRANSFER_BYTES) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Upload must be at most 512 MiB with a Content-Length header")
            }
            val files = HashMap<String, String>()
            try {
                session.parseBody(files)
            } catch (e: Exception) {
                return jsonResponse(false, null, "bad request")
            }

            val tempPath = files["file"] ?: return jsonResponse(false, null, "no file")
            val originalName = session.parameters["file"]?.firstOrNull()
                ?: File(tempPath).name

            try {
                ImportLimits.destination(context.filesDir, originalName)
            } catch (e: IllegalArgumentException) {
                return jsonResponse(false, null, "invalid filename")
            }
            if (File(tempPath).length() > ImportLimits.MAX_TRANSFER_BYTES) return jsonResponse(false, null, "file too large")

            val ext = originalName.substringAfterLast('.', "").lowercase(Locale.US)
            val isMusic = ext in setOf("mp3", "m4a", "mp4", "aac", "wav", "aiff", "flac", "ogg")
            val isBook = ext == "epub"
            val isJournal = ext in setOf("txt", "md")

            if (!isMusic && !isBook && !isJournal) {
                WiFiTransferServer.record(originalName, false, "UNSUPPORTED FORMAT: .$ext")
                return jsonResponse(false, null, "unsupported format")
            }

            val folder = when {
                isMusic -> MusicFolderManager.musicFolder(context)
                isBook -> BookFolderManager.booksFolder(context)
                else -> WritingFolderManager.writingFolder(context)
            }

            val existing = ImportLimits.destination(folder, originalName)
            if (existing.exists()) {
                WiFiTransferServer.record(originalName, false, "ALREADY EXISTS — SKIPPED")
                return jsonResponse(false, null, "already exists")
            }

            val dest = uniqueDestination(folder, originalName)
            return try {
                File(tempPath).copyTo(dest, overwrite = false)
                val kb = dest.length() / 1024
                WiFiTransferServer.record(dest.name, true, "RECEIVED OK — $kb KB")
                jsonResponse(true, dest.name, null)
            } catch (e: Exception) {
                WiFiTransferServer.record(originalName, false, e.message ?: "write failed")
                jsonResponse(false, null, e.message ?: "write failed")
            }
        }

        private fun uniqueDestination(folder: File, filename: String): File {
            val base = filename.substringBeforeLast('.', filename)
            val ext = filename.substringAfterLast('.', "")
            var dest = ImportLimits.destination(folder, filename)
            var counter = 2
            while (dest.exists()) {
                val name = if (ext.isEmpty()) "${base}_$counter" else "${base}_$counter.$ext"
                dest = ImportLimits.destination(folder, name)
                counter += 1
            }
            return dest
        }

        private fun jsonResponse(ok: Boolean, filename: String?, error: String?): Response {
            val body = buildString {
                append("{\"ok\":").append(ok)
                if (filename != null) append(",\"filename\":\"").append(filename).append('"')
                if (error != null) append(",\"error\":\"").append(error).append('"')
                append("}")
            }
            return newFixedLengthResponse(Response.Status.OK, "application/json", body)
        }

        private fun indexHtml(): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>TERMI TRANSFER</title>
          <style>
            *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
            body {
              background: $pageBackgroundHex;
              color: $pageAccentHex;
              font-family: 'Courier New', Courier, monospace;
              padding: 24px;
              min-height: 100vh;
              font-size: 14px;
            }
            .frame { border: 1px solid $pageDimHex; padding: 16px 20px; margin-bottom: 20px; }
            .title { font-size: 18px; font-weight: bold; letter-spacing: 2px; }
            .sub { color: $pageSubTextHex; font-size: 12px; margin-top: 6px; }
            #pinScreen { max-width: 360px; margin: 60px auto 0; text-align: center; }
            .pin-label { font-size: 12px; color: $pageSubTextHex; margin-bottom: 16px; letter-spacing: 1px; }
            .pin-input {
              background: transparent; border: 1px solid $pageDimHex; color: $pageAccentHex;
              font-family: inherit; font-size: 28px; font-weight: bold; letter-spacing: 12px;
              text-align: center; padding: 14px 20px; width: 100%; outline: none; margin-bottom: 12px;
            }
            .pin-input:focus { border-color: $pageAccentHex; }
            .pin-btn {
              width: 100%; padding: 12px 0; font-size: 13px; font-weight: bold; font-family: inherit;
              cursor: pointer; border: 1px solid $pageDimHex; background: transparent; color: $pageAccentHex;
              letter-spacing: 1px; transition: border-color .15s, background .15s;
            }
            .pin-btn:hover { border-color: $pageAccentHex; background: rgba($pageAccentRgb,.04); }
            .pin-err { color: #FF4444; font-size: 12px; margin-top: 10px; min-height: 16px; }
            #uploadScreen { display: none; }
            .drop {
              border: 2px dashed $pageDimHex; padding: 40px 20px; text-align: center; cursor: pointer;
              transition: border-color .15s, background .15s; margin-bottom: 8px;
            }
            .drop:hover, .drop.over { border-color: $pageAccentHex; background: rgba($pageAccentRgb,.04); }
            .drop-label { font-size: 16px; font-weight: bold; pointer-events: none; }
            .drop-hint { color: $pageSubTextHex; font-size: 12px; margin-top: 8px; pointer-events: none; }
            .pick-row { display: flex; gap: 10px; margin-bottom: 8px; }
            .pick-btn {
              flex: 1; padding: 10px 0; text-align: center; font-size: 12px; font-weight: bold; font-family: inherit;
              cursor: pointer; border: 1px solid $pageDimHex; background: transparent; color: $pageAccentHex;
              letter-spacing: 1px; transition: border-color .15s, background .15s;
            }
            .pick-btn:hover { border-color: $pageAccentHex; background: rgba($pageAccentRgb,.04); }
            .formats { color: $pageDimHex; font-size: 11px; margin-bottom: 20px; }
            .progress { height: 3px; background: $pageBackgroundHex; margin-bottom: 20px; display: none; }
            .progress-fill { height: 100%; background: $pageAccentHex; width: 0%; transition: width .2s; }
            .log-header { color: $pageSubTextHex; font-size: 11px; margin-bottom: 8px; }
            .log { max-height: 260px; overflow-y: auto; }
            .entry { padding: 4px 0; border-bottom: 1px solid $pageBackgroundHex; font-size: 12px; line-height: 1.5; }
            .ok { color: $pageAccentHex; }
            .err { color: #FF4444; }
            .inf { color: $pageSubTextHex; }
          </style>
        </head>
        <body>
          <div class="frame">
            <div class="title">${'╔'}${'═'}[ TERMI TRANSFER ]</div>
            <div class="sub">&gt; SECURE LOCAL FILE TRANSFER</div>
          </div>
          <div id="pinScreen">
            <div class="pin-label">&gt; ENTER PIN FROM TERMI APP TO CONNECT</div>
            <input class="pin-input" id="pinInput" type="tel" maxlength="4" placeholder="····" autocomplete="off" inputmode="numeric">
            <button class="pin-btn" id="pinBtn">[ CONNECT ]</button>
            <div class="pin-err" id="pinErr"></div>
          </div>
          <div id="uploadScreen">
            <div class="drop" id="drop">
              <div class="drop-label">[ DROP FILES HERE ]</div>
              <div class="drop-hint">or use the buttons below</div>
            </div>
            <div class="pick-row">
              <button class="pick-btn" onclick="document.getElementById('filePicker').click()">[ BROWSE FILES ]</button>
            </div>
            <input type="file" id="filePicker" style="display:none" multiple accept=".mp3,.m4a,.mp4,.aac,.wav,.aiff,.flac,.ogg,.epub,.txt,.md">
            <div class="formats">&gt; SUPPORTED: MP3 &middot; M4A &middot; MP4 &middot; AAC &middot; WAV &middot; AIFF &middot; FLAC &middot; OGG &middot; EPUB &middot; TXT &middot; MD</div>
            <div class="progress" id="bar"><div class="progress-fill" id="fill"></div></div>
            <div class="log-header">&gt; TRANSFER LOG:</div>
            <div class="log" id="log"><div class="entry inf">WAITING FOR FILES...</div></div>
          </div>
          <script>
            let sessionPin = '';
            const pinInput = document.getElementById('pinInput');
            const pinBtn = document.getElementById('pinBtn');
            const pinErr = document.getElementById('pinErr');

            function tryConnect() {
              const entered = pinInput.value.trim();
              if (entered.length !== 4) { pinErr.textContent = '> PIN IS 4 DIGITS'; return; }
              sessionPin = entered;
              const xhr = new XMLHttpRequest();
              xhr.open('POST', '/upload');
              xhr.setRequestHeader('X-Termi-Pin', sessionPin);
              xhr.onload = () => {
                if (xhr.status === 403) {
                  sessionPin = '';
                  pinErr.textContent = '> INCORRECT PIN — TRY AGAIN';
                } else {
                  document.getElementById('pinScreen').style.display = 'none';
                  document.getElementById('uploadScreen').style.display = 'block';
                }
              };
              xhr.onerror = () => { pinErr.textContent = '> CONNECTION ERROR'; };
              xhr.send(new FormData());
            }
            pinBtn.addEventListener('click', tryConnect);
            pinInput.addEventListener('keydown', e => { if (e.key === 'Enter') tryConnect(); });

            const drop = document.getElementById('drop');
            const filePicker = document.getElementById('filePicker');
            const bar = document.getElementById('bar');
            const fill = document.getElementById('fill');
            const log = document.getElementById('log');
            let firstEntry = true;

            // Walks one FileSystemEntry (file or directory) recursively so a
            // dropped folder-of-folders (Artist/Album/*.mp3) is fully
            // resolved to individual File objects — dataTransfer.files alone
            // only ever contains loose top-level files, never a nested
            // folder's contents, so a plain drop of a folder used to
            // silently upload nothing from inside it.
            function walkEntry(entry) {
              return new Promise((resolve, reject) => {
                if (entry.isFile) {
                  entry.file(resolve, reject);
                } else if (entry.isDirectory) {
                  const reader = entry.createReader();
                  const all = [];
                  const readBatch = () => {
                    reader.readEntries(async entries => {
                      if (!entries.length) {
                        try {
                          const nested = await Promise.all(all.map(walkEntry));
                          resolve(nested.flat());
                        } catch (err) { reject(err); }
                        return;
                      }
                      all.push(...entries);
                      readBatch();
                    }, reject);
                  };
                  readBatch();
                } else {
                  resolve([]);
                }
              });
            }

            async function filesFromDataTransfer(dataTransfer) {
              const items = dataTransfer.items;
              if (items && items.length && items[0].webkitGetAsEntry) {
                const entries = Array.from(items)
                  .map(item => item.webkitGetAsEntry())
                  .filter(Boolean);
                const resolved = await Promise.all(entries.map(walkEntry));
                return resolved.flat();
              }
              return Array.from(dataTransfer.files || []);
            }

            drop.addEventListener('dragover', e => { e.preventDefault(); drop.classList.add('over'); });
            drop.addEventListener('dragleave', () => drop.classList.remove('over'));
            drop.addEventListener('drop', async e => {
              e.preventDefault(); drop.classList.remove('over');
              const files = await filesFromDataTransfer(e.dataTransfer);
              if (files.length) upload(files);
            });
            filePicker.addEventListener('change', () => {
              const files = Array.from(filePicker.files);
              filePicker.value = '';
              if (files.length) upload(files);
            });

            function addLog(msg, cls) {
              if (firstEntry) { log.innerHTML = ''; firstEntry = false; }
              const d = document.createElement('div');
              d.className = 'entry ' + cls;
              d.textContent = msg;
              log.prepend(d);
            }

            async function upload(files) {
              bar.style.display = 'block';
              for (let i = 0; i < files.length; i++) {
                const f = files[i];
                fill.style.width = (i / files.length * 100).toFixed(0) + '%';
                addLog('> UPLOADING: ' + f.name + ' (' + (f.size/1024).toFixed(0) + ' KB)', 'inf');
                await new Promise(res => {
                  const fd = new FormData();
                  fd.append('file', f, f.name);
                  const xhr = new XMLHttpRequest();
                  xhr.upload.onprogress = e => {
                    if (e.lengthComputable) fill.style.width = ((i + e.loaded/e.total) / files.length * 100).toFixed(0) + '%';
                  };
                  xhr.onload = () => {
                    if (xhr.status === 403) { addLog('[ ERR ] ' + f.name + ': session expired — reload page', 'err'); }
                    else {
                      let r = {};
                      try { r = JSON.parse(xhr.responseText); } catch(e) {}
                      if (r.ok) addLog('[ OK ] ' + (r.filename || f.name), 'ok');
                      else addLog('[ ERR ] ' + f.name + ': ' + (r.error || 'unknown'), 'err');
                    }
                    res();
                  };
                  xhr.onerror = () => { addLog('[ ERR ] ' + f.name + ': network error', 'err'); res(); };
                  xhr.open('POST', '/upload');
                  xhr.setRequestHeader('X-Termi-Pin', sessionPin);
                  xhr.send(fd);
                });
              }
              fill.style.width = '100%';
              setTimeout(() => { bar.style.display = 'none'; fill.style.width = '0%'; }, 800);
            }
          </script>
        </body>
        </html>
        """.trimIndent()
    }
}
