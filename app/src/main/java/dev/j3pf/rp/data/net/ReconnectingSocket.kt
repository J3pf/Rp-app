package dev.j3pf.rp.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dev.j3pf.rp.protocol.CloseCodes
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * WebSocket con reconexión: backoff exponencial 1 → 30 s con jitter ±20 % (PROTOCOLO §1) y reintento inmediato
 * cuando `ConnectivityManager.NetworkCallback` informa de red disponible. Los cierres fatales (4001/4002) detienen
 * los reintentos; 1012 (reinicio del relay) reconecta de inmediato.
 */
class ReconnectingSocket(
    private val context: Context,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    /** Se evalúa en cada intento: `null` = no hay sesión, no conectar. */
    private val requestProvider: () -> Request?,
    private val onSocketOpen: (WebSocket) -> Unit,
    private val onSocketText: (String) -> Unit,
    private val onSocketClosed: (code: Int) -> Unit = {},
) {
    private val _state = MutableStateFlow<ConnState>(ConnState.Disconnected)
    val state: StateFlow<ConnState> = _state.asStateFlow()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var running = false
    private var attempt = 0
    @Volatile private var generation = 0
    private var job: Job? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private val lock = Any()

    val isConnected: Boolean get() = _state.value == ConnState.Connected

    fun start() {
        synchronized(lock) {
            if (running) return
            running = true
            attempt = 0
            registerNetworkCallback()
            connectLoop(immediate = true)
        }
    }

    fun stop() {
        synchronized(lock) {
            running = false
            generation++
            job?.cancel()
            job = null
            unregisterNetworkCallback()
            socket?.close(1000, "bye")
            socket = null
            _state.value = ConnState.Disconnected
        }
    }

    /** Fuerza una reconexión inmediata (p. ej. al volver la red o al abrir la app). */
    fun retryNow() {
        synchronized(lock) {
            if (!running) return
            if (_state.value == ConnState.Connected || _state.value == ConnState.Connecting) return
            attempt = 0
            connectLoop(immediate = true)
        }
    }

    fun send(text: String): Boolean = socket?.send(text) == true

    private fun connectLoop(immediate: Boolean) {
        job?.cancel()
        job = scope.launch {
            if (!immediate) delay(backoffMs())
            if (!running) return@launch
            val req = requestProvider()
            if (req == null) {
                _state.value = ConnState.Disconnected
                return@launch
            }
            _state.value = ConnState.Connecting
            val gen = synchronized(lock) { ++generation }
            socket = client.newWebSocket(req, Listener(gen))
        }
    }

    private fun scheduleReconnect() {
        synchronized(lock) {
            if (!running) return
            attempt++
            connectLoop(immediate = false)
        }
    }

    private fun backoffMs(): Long {
        val base = min(30_000L, 1_000L shl min(attempt - 1, 5).coerceAtLeast(0))
        val jitter = 1.0 + (Random.nextDouble() * 0.4 - 0.2)
        return (base * jitter).toLong()
    }

    private inner class Listener(private val gen: Int) : WebSocketListener() {
        private fun current() = gen == generation

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!current()) return
            attempt = 0
            _state.value = ConnState.Connected
            onSocketOpen(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (current()) onSocketText(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!current()) return
            handleEnd(code, reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!current()) return
            // 401/403 en el handshake equivale a token inválido.
            val http = response?.code
            if (http == 401 || http == 403) handleEnd(CloseCodes.INVALID_TOKEN, "HTTP $http") else handleEnd(1006, t.message ?: "")
        }

        private fun handleEnd(code: Int, reason: String) {
            socket = null
            onSocketClosed(code)
            if (CloseCodes.isFatal(code)) {
                synchronized(lock) { running = false }
                unregisterNetworkCallback()
                _state.value = ConnState.Fatal(code, reason)
                return
            }
            _state.value = ConnState.Disconnected
            if (code == CloseCodes.SERVICE_RESTART) {
                synchronized(lock) { if (running) { attempt = 0; connectLoop(immediate = true) } }
            } else {
                scheduleReconnect()
            }
        }
    }

    private fun registerNetworkCallback() {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = retryNow()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) retryNow()
            }
        }
        runCatching {
            cm.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                cb,
            )
            netCallback = cb
        }
    }

    private fun unregisterNetworkCallback() {
        val cb = netCallback ?: return
        netCallback = null
        runCatching { context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
    }
}
