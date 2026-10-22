package dev.j3pf.rp.client.ws

import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.protocol.CloseCodes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.random.Random

/** Reconnect policy with serialized, generation-fenced transport callbacks. */
class ReconnectingSocketPolicy<Request, Socket>(
    private val scope: CoroutineScope,
    private val driver: RelaySocketDriver<Request, Socket>,
    private val requestProvider: () -> Request?,
    private val onSocketOpen: (Socket) -> Unit,
    private val onSocketText: (String) -> Unit,
    private val onSocketClosed: (Int) -> Unit = {},
    private val onStart: () -> Unit = {},
    private val onStop: () -> Unit = {},
    private val lock: RelayLock = RelayLock(),
    private val randomDouble: () -> Double = { Random.nextDouble() },
) {
    private val mutableState = MutableStateFlow<ConnState>(ConnState.Disconnected)
    val state: StateFlow<ConnState> = mutableState.asStateFlow()
    private var socket: Socket? = null
    private var running = false
    private var attempt = 0
    private var generation = 0
    private var job: Job? = null

    val isConnected: Boolean get() = state.value == ConnState.Connected

    fun start() =
        lock.withLock {
            if (running) return@withLock
            running = true
            attempt = 0
            onStart()
            connectLoop(immediate = true)
        }

    fun stop() =
        lock.withLock {
            running = false
            generation++
            job?.cancel()
            job = null
            onStop()
            val ws = socket
            socket = null
            if (ws != null) {
                driver.close(ws, 1000, "bye")
                onSocketClosed(1000)
            }
            mutableState.value = ConnState.Disconnected
        }

    fun retryNow(force: Boolean = false) =
        lock.withLock {
            if (!running) return@withLock
            if (!force && (state.value == ConnState.Connected || state.value == ConnState.Connecting)) return@withLock
            attempt = 0
            if (force) invalidateSocket()
            connectLoop(immediate = true)
        }

    fun send(text: String): Boolean = lock.withLock { socket?.let { driver.send(it, text) } == true }

    /** Native connectivity owns network identity and decides which loss is relevant. */
    fun networkLost() =
        lock.withLock {
            invalidateSocket()
            if (state.value == ConnState.Connected || state.value == ConnState.Connecting) {
                mutableState.value = ConnState.Disconnected
            }
        }

    private fun invalidateSocket() {
        generation++
        val ws = socket
        socket = null
        if (ws != null) {
            driver.cancel(ws)
            onSocketClosed(1006)
        }
    }

    private fun connectLoop(immediate: Boolean) {
        job?.cancel()
        job =
            scope.launch {
                if (!immediate) delay(lock.withLock { backoffMs() })
                if (!lock.withLock { running }) return@launch
                val request = requestProvider()
                lock.withLock {
                    if (!running) return@withLock
                    if (request == null) {
                        mutableState.value = ConnState.Disconnected
                        return@withLock
                    }
                    mutableState.value = ConnState.Connecting
                    val gen = ++generation
                    socket = driver.connect(request, Listener(gen))
                }
            }
    }

    private fun scheduleReconnect() {
        if (!running) return
        attempt++
        connectLoop(immediate = false)
    }

    private fun backoffMs(): Long {
        val base = min(30_000L, 1_000L shl min(attempt - 1, 5).coerceAtLeast(0))
        val jitter = 1.0 + (randomDouble() * 0.4 - 0.2)
        return (base * jitter).toLong()
    }

    private inner class Listener(
        private val gen: Int,
    ) : RelaySocketListener<Socket> {
        override fun onOpen(socket: Socket) =
            lock.withLock {
                if (gen != generation) {
                    driver.close(socket, 1000, "Stale generation")
                    return@withLock
                }
                attempt = 0
                mutableState.value = ConnState.Connected
                onSocketOpen(socket)
            }

        override fun onText(text: String) =
            lock.withLock {
                if (gen == generation) onSocketText(text)
            }

        override fun onClosed(
            code: Int,
            reason: String,
        ) = lock.withLock {
            if (gen == generation) handleEnd(code, reason)
        }

        override fun onFailure(
            httpCode: Int?,
            message: String,
        ) = lock.withLock {
            if (gen != generation) return@withLock
            if (httpCode == 401 || httpCode == 403) {
                handleEnd(CloseCodes.INVALID_TOKEN, "HTTP $httpCode")
            } else {
                handleEnd(1006, message)
            }
        }

        private fun handleEnd(
            code: Int,
            reason: String,
        ) {
            socket = null
            onSocketClosed(code)
            if (CloseCodes.isFatal(code)) {
                running = false
                onStop()
                mutableState.value = ConnState.Fatal(code, reason)
                return
            }
            mutableState.value = ConnState.Disconnected
            if (code == CloseCodes.SERVICE_RESTART) {
                if (running) {
                    attempt = 0
                    connectLoop(immediate = true)
                }
            } else {
                scheduleReconnect()
            }
        }
    }
}
