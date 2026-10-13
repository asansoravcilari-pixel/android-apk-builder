package com.nexus.control

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

data class DiagnosticResult(val title: String, val ok: Boolean, val detail: String)

enum class SocketState { IDLE, CONNECTING, OPEN, CLOSING, CLOSED, ERROR }

object Diagnostics {
    suspend fun dns(host: String): DiagnosticResult = withContext(Dispatchers.IO) {
        if (host.isBlank()) return@withContext DiagnosticResult("DNS", false, "Host boş")
        runCatching {
            val addresses = InetAddress.getAllByName(host).joinToString { it.hostAddress ?: "?" }
            DiagnosticResult("DNS", true, "$host -> $addresses")
        }.getOrElse { DiagnosticResult("DNS", false, it.message ?: it.javaClass.simpleName) }
    }

    suspend fun tcp(host: String, port: Int): DiagnosticResult = withContext(Dispatchers.IO) {
        if (host.isBlank()) return@withContext DiagnosticResult("TCP", false, "Host boş")
        if (port !in 1..65535) return@withContext DiagnosticResult("TCP", false, "Port 1..65535 olmalı")
        val start = System.nanoTime()
        runCatching {
            Socket().use { it.connect(InetSocketAddress(host, port), 4000) }
            val ms = (System.nanoTime() - start) / 1_000_000
            DiagnosticResult("TCP $port", true, "Bağlantı başarılı · ${ms} ms")
        }.getOrElse { DiagnosticResult("TCP $port", false, it.message ?: it.javaClass.simpleName) }
    }

    suspend fun https(url: String): DiagnosticResult = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext DiagnosticResult("HTTPS", false, "Yalnızca https:// kabul edilir")
        val start = System.nanoTime()
        runCatching {
            val conn = URL(url).openConnection() as HttpsURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "NEXUS-Control/0.3")
            val code = conn.responseCode
            val finalUrl = conn.url.toString()
            val type = conn.contentType ?: "?"
            val ms = (System.nanoTime() - start) / 1_000_000
            conn.disconnect()
            DiagnosticResult("HTTPS", code in 200..499, "HTTP $code · ${ms} ms\n$finalUrl\n$type")
        }.getOrElse { DiagnosticResult("HTTPS", false, it.message ?: it.javaClass.simpleName) }
    }

    suspend fun tls(url: String): DiagnosticResult = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext DiagnosticResult("TLS", false, "Yalnızca https:// kabul edilir")
        runCatching {
            val conn = URL(url).openConnection() as HttpsURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.connect()
            val cert = conn.serverCertificates.firstOrNull() as? X509Certificate
                ?: error("X509 sertifikası bulunamadı")
            val sha = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                .joinToString(":") { "%02X".format(it) }
            val detail = buildString {
                appendLine(conn.cipherSuite)
                appendLine("Subject: ${cert.subjectX500Principal.name}")
                appendLine("Issuer: ${cert.issuerX500Principal.name}")
                appendLine("Valid: ${cert.notBefore} -> ${cert.notAfter}")
                append("SHA-256: $sha")
            }
            conn.disconnect()
            DiagnosticResult("TLS", true, detail)
        }.getOrElse { DiagnosticResult("TLS", false, it.message ?: it.javaClass.simpleName) }
    }
}

class SocketProbe {
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var socket: WebSocket? = null
    @Volatile private var state: SocketState = SocketState.IDLE

    fun state(): SocketState = state

    fun connect(url: String, onState: (SocketState) -> Unit, onLog: (String) -> Unit) {
        close()
        if (!url.startsWith("wss://")) {
            state = SocketState.ERROR
            handler.post { onState(state); onLog("ERROR · yalnız wss:// kabul edilir") }
            return
        }
        state = SocketState.CONNECTING
        handler.post { onState(state) }
        val request = Request.Builder().url(url).header("User-Agent", "NEXUS-Control/0.3").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            private fun postState(newState: SocketState) {
                state = newState
                handler.post { onState(newState) }
            }
            private fun log(message: String) { handler.post { onLog(message.take(4000)) } }

            override fun onOpen(webSocket: WebSocket, response: Response) {
                postState(SocketState.OPEN)
                log("OPEN · HTTP ${response.code}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                log("<< text ${text.length} chars · ${text.take(1500)}")
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                log("<< binary ${bytes.size} bytes · ${bytes.hex().take(800)}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                postState(SocketState.CLOSING)
                log("CLOSING · $code · $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                postState(SocketState.CLOSED)
                log("CLOSED · $code · $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                postState(SocketState.ERROR)
                log("ERROR · ${response?.code ?: "-"} · ${t.message ?: t.javaClass.simpleName}")
            }
        })
    }

    fun send(text: String): Boolean = state == SocketState.OPEN && (socket?.send(text) ?: false)

    fun close() {
        if (socket != null) {
            state = SocketState.CLOSING
            socket?.close(1000, "NEXUS Control")
        }
        socket = null
        if (state != SocketState.ERROR) state = SocketState.IDLE
    }
}
