package com.termi.android.pkgs

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import fi.iki.elonen.NanoHTTPD
import java.io.DataInputStream
import java.io.File
import com.termi.android.filesystem.ImportLimits
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

// The Android Packages transport — mDNS/NSD for discovery (a real open
// standard, Bonjour on iOS / NsdManager here) + plain HTTP request/response
// for the invite handshake and file transfer, reusing the exact embedded-
// server pattern WiFiTransferServer already proves works.
//
// This is now the ONLY Android transport, not just the cross-platform
// fallback it started as — Nearby Connections (the original "same carrier"
// path) turned out to be genuinely unreliable on real target hardware:
// custom-ROM retro handhelds frequently ship a Play Services build that's
// missing the Nearby Connections module entirely (confirmed live: "NEARBY.
// CONNECTIONS_API is not available on this device"). iOS keeps a real
// split (MultipeerConnectivity for iOS-to-iOS, this same bridge for
// reaching Android) since Apple's tightly controlled hardware doesn't have
// Android's fragmentation problem. `peerPlatform` is what lets the ritual
// UI still show a distinct "same carrier" vs "foreign carrier" flavor even
// though there's only one transport now — decided live from what's
// actually discovered, not from a pre-made sender choice.
// `onPackageReceived` is called with a transient temp file holding the
// just-arrived `.termi` bytes, plus the sender's platform (from the same
// invite payload `_peerPlatform` was set from — real cross-platform
// receipts need this recorded as cosmetic receipt-time metadata, matching
// iOS's own CrossedPlatformBadge/PackageReceiptMeta, since it's never part
// of the package contents itself). The caller (PackageStorageManager) is
// responsible for extracting it into permanent storage and deleting the
// temp copy; this manager is transport-only and doesn't know about
// storage. Returns the manifest title to show on "PACKAGE RECEIVED", or
// null if ingestion failed.
class PackageBridgeManager(
    private val context: Context,
    private val onPackageReceived: (File, String?) -> String?,
) {

    private val _state = MutableStateFlow<PackageRitualState>(PackageRitualState.Idle)
    val state: StateFlow<PackageRitualState> = _state.asStateFlow()

    private val _peerPlatform = MutableStateFlow<String?>(null)
    val peerPlatform: StateFlow<String?> = _peerPlatform.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var server: InviteServer? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveInFlight = false

    // mDNS discovery/advertising depends on multicast packets, and on a
    // real, non-trivial number of Android devices the WiFi driver silently
    // drops multicast traffic unless something explicitly asks it not to
    // (NsdManager does NOT reliably do this for you) — caught live:
    // advertise + discover both ran without error, but neither side ever
    // found the other. Holding this for the whole listen/send lifetime is
    // the standard fix.
    private var multicastLock: WifiManager.MulticastLock? = null

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        val lock = wifiManager.createMulticastLock("termi-pkgx-mdns")
        lock.setReferenceCounted(false)
        lock.acquire()
        multicastLock = lock
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
    }

    private var resolvedHost: String? = null
    private var resolvedPort: Int = 0
    private var uploadToken: String? = null

    // ── Receiver ──────────────────────────────────────────────────

    fun startListening(signature: String) {
        acquireMulticastLock()
        _state.value = PackageRitualState.Listening
        val srv = InviteServer { title, sender, mood, platform ->
            _peerPlatform.value = platform
            _state.value = PackageRitualState.IncomingInvite(sender)
        }
        try {
            srv.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        } catch (e: Exception) {
            _state.value = PackageRitualState.Failed(e.message ?: "server start failed")
            return
        }
        server = srv
        registerService(srv.listeningPort, signature)
    }

    private fun registerService(port: Int, signature: String) {
        val info = NsdServiceInfo().apply {
            serviceName = "termi-${signature.ifBlank { "guest" }}-${UUID.randomUUID().toString().take(4)}"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("v", "1")
            setAttribute("s", signature)
            setAttribute("p", "android")
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                _state.value = PackageRitualState.Failed("advertise failed ($errorCode)")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun acceptIncoming() {
        _state.value = PackageRitualState.LinkEstablished("peer")
        server?.resolveInvite(true)
    }

    fun declineIncoming() {
        server?.resolveInvite(false)
        _state.value = PackageRitualState.Idle
    }

    // ── Sender ────────────────────────────────────────────────────

    fun startSending(signature: String, packageTitle: String, mood: String) {
        acquireMulticastLock()
        _state.value = PackageRitualState.Dialing
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceType != SERVICE_TYPE || resolveInFlight) return
                resolveInFlight = true
                nsdManager.stopServiceDiscovery(this)
                resolveAndInvite(info, signature, packageTitle, mood)
            }
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _state.value = PackageRitualState.Failed("discovery failed ($errorCode)")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discoveryListener = listener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun resolveAndInvite(info: NsdServiceInfo, mySignature: String, title: String, mood: String) {
        nsdManager.resolveService(
            info,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    _state.value = PackageRitualState.Failed("resolve failed ($errorCode)")
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val peerSignature = serviceInfo.attributes["s"]?.toString(Charsets.UTF_8) ?: "?"
                    _peerPlatform.value = serviceInfo.attributes["p"]?.toString(Charsets.UTF_8)
                    resolvedHost = serviceInfo.host.hostAddress
                    resolvedPort = serviceInfo.port
                    _state.value = PackageRitualState.CarrierDetected(peerSignature)
                    sendInvite(mySignature, title, mood)
                }
            },
        )
    }

    private fun sendInvite(mySignature: String, title: String, mood: String) {
        val host = resolvedHost ?: return
        _state.value = PackageRitualState.AwaitingAcceptance(host)
        scope.launch {
            try {
                val body = JSONObject().apply {
                    put("title", title)
                    put("senderSignature", mySignature)
                    put("mood", mood)
                    put("platform", "android")
                }.toString().toByteArray()
                val conn = (URL("http://$host:$resolvedPort/invite").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 10_000
                    // Long enough for a human to actually decide on the
                    // other end — mirrors MultipeerConnectivity's own
                    // 30s invite timeout on iOS, with headroom.
                    readTimeout = 45_000
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(body) }
                val code = conn.responseCode
                if (code == 200) {
                    val responseJson = JSONObject(conn.inputStream.bufferedReader().readText())
                    uploadToken = responseJson.getString("token")
                    _state.value = PackageRitualState.LinkEstablished(host)
                } else {
                    _state.value = PackageRitualState.Cancelled
                }
                conn.disconnect()
            } catch (e: Exception) {
                _state.value = PackageRitualState.Failed(e.message ?: "invite failed")
            }
        }
    }

    // PUTs the real `.termi` container's bytes to the accepted upload
    // token. Runs on the sender's own IO scope, not the invite's blocking
    // server thread — that thread already returned once accept resolved.
    fun sendPackage(file: File) {
        val host = resolvedHost ?: return
        val token = uploadToken ?: return
        scope.launch {
            try {
                val conn = (URL("http://$host:$resolvedPort/upload/$token").openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout = 30_000
                    setFixedLengthStreamingMode(file.length().toInt())
                }
                conn.outputStream.use { output -> file.inputStream().use { it.copyTo(output) } }
                val code = conn.responseCode
                _state.value = if (code == 200) PackageRitualState.CompletedSend else PackageRitualState.Failed("upload failed ($code)")
                conn.disconnect()
            } catch (e: Exception) {
                _state.value = PackageRitualState.Failed(e.message ?: "upload failed")
            }
        }
    }

    fun reset() {
        server?.stop()
        server = null
        registrationListener?.let { runCatching { nsdManager.unregisterService(it) } }
        discoveryListener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
        registrationListener = null
        discoveryListener = null
        resolveInFlight = false
        resolvedHost = null
        resolvedPort = 0
        uploadToken = null
        releaseMulticastLock()
        _peerPlatform.value = null
        _state.value = PackageRitualState.Idle
    }

    // ── Embedded server (receiver side) ──────────────────────────
    // Reuses the WiFiTransferServer/NanoHTTPD pattern rather than a new
    // socket protocol. `/invite` deliberately BLOCKS the request's own
    // worker thread until the human decides — NanoHTTPD serves each
    // connection on its own thread by default, so this is safe and gives
    // the sender a real "AwaitingAcceptance" wait for free, with a timeout
    // fallback if nobody ever answers.
    private inner class InviteServer(
        private val onInvite: (title: String, sender: String, mood: String, platform: String) -> Unit,
    ) : NanoHTTPD(0) {

        private val decisionQueue = ArrayBlockingQueue<Boolean>(1)

        override fun serve(session: IHTTPSession): Response {
            return when {
                session.method == Method.POST && session.uri == "/invite" -> handleInvite(session)
                session.method == Method.PUT && session.uri.startsWith("/upload/") -> handleUpload(session)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
            }
        }

        private fun handleInvite(session: IHTTPSession): Response {
            return try {
                val bodyMap = HashMap<String, String>()
                session.parseBody(bodyMap) // raw JSON body lands under "postData" for unrecognized content types
                val json = JSONObject(bodyMap["postData"] ?: "{}")
                onInvite(
                    json.optString("title"),
                    json.optString("senderSignature"),
                    json.optString("mood"),
                    json.optString("platform"),
                )
                val accepted = decisionQueue.poll(45, TimeUnit.SECONDS) ?: false
                if (accepted) {
                    val token = UUID.randomUUID().toString()
                    this@PackageBridgeManager.uploadToken = token
                    newFixedLengthResponse(Response.Status.OK, "application/json", "{\"token\":\"$token\"}")
                } else {
                    newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "declined")
                }
            } catch (e: Exception) {
                newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "bad request")
            }
        }

        private fun handleUpload(session: IHTTPSession): Response {
            val token = session.uri.removePrefix("/upload/")
            if (token != this@PackageBridgeManager.uploadToken) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "bad token")
            }
            return try {
                val contentLength = session.headers["content-length"]?.toLongOrNull()
                if (contentLength == null || contentLength !in 1..ImportLimits.MAX_TRANSFER_BYTES) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid package size (maximum 512 MiB)")
                }
                val tempFile = File.createTempFile("incoming_", ".termi", context.cacheDir)
                val title = try {
                    tempFile.outputStream().buffered().use { output ->
                        val input = DataInputStream(session.inputStream)
                        val buffer = ByteArray(32 * 1024)
                        var remaining = contentLength
                        while (remaining > 0) {
                            val size = minOf(remaining, buffer.size.toLong()).toInt()
                            input.readFully(buffer, 0, size)
                            output.write(buffer, 0, size)
                            remaining -= size
                        }
                    }
                    this@PackageBridgeManager.uploadToken = null
                    onPackageReceived(tempFile, _peerPlatform.value)
                } finally { tempFile.delete() }
                _state.value = if (title != null) {
                    PackageRitualState.CompletedReceive(title)
                } else {
                    PackageRitualState.Failed("received file was not a valid package")
                }
                newFixedLengthResponse(if (title != null) Response.Status.OK else Response.Status.BAD_REQUEST, "text/plain", if (title != null) "ok" else "invalid package")
            } catch (e: Exception) {
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.message ?: "failed")
            }
        }

        fun resolveInvite(accepted: Boolean) {
            decisionQueue.offer(accepted)
        }
    }

    companion object {
        // Deliberately a DIFFERENT Bonjour/mDNS service name from whatever
        // backs MultipeerConnectivity's own advertising, so the two
        // mechanisms never show up tangled in each other's discovery
        // results on the network.
        private const val SERVICE_TYPE = "_termi-pkgx._tcp."
    }
}
