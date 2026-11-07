package dev.j3pf.rp.data.net

import dev.j3pf.rp.client.ws.ReconnectingSocketPolicy
import dev.j3pf.rp.client.ws.RelayLock
import dev.j3pf.rp.data.ports.NetworkMonitor
import dev.j3pf.rp.data.ports.NetworkObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket

/** Native socket facade; connectivity events and reconnect timing live behind shared ports/policy. */
class ReconnectingSocket(
    private val networkMonitor: NetworkMonitor,
    client: OkHttpClient,
    scope: CoroutineScope,
    /** Se evalúa en cada intento: `null` = no hay sesión, no conectar. */
    requestProvider: () -> Request?,
    onSocketOpen: (WebSocket) -> Unit,
    onSocketText: (String) -> Unit,
    onSocketClosed: (code: Int) -> Unit = {},
) {
    private val lock = RelayLock()
    private val networkObserver: NetworkObserver
    private val policy: ReconnectingSocketPolicy<Request, WebSocket>

    init {
        networkObserver =
            object : NetworkObserver {
                override fun onAvailability(available: Boolean) {
                    lock.withLock {
                        if (available) {
                            policy.retryNow(force = false)
                        } else {
                            policy.networkLost()
                        }
                    }
                }

                override fun onNetworkIdentityChange() {
                    lock.withLock { policy.retryNow(force = true) }
                }
            }
        policy =
            ReconnectingSocketPolicy(
                scope = scope,
                driver = OkHttpSocketDriver(client),
                requestProvider = requestProvider,
                onSocketOpen = onSocketOpen,
                onSocketText = onSocketText,
                onSocketClosed = onSocketClosed,
                onStart = { networkMonitor.register(networkObserver) },
                onStop = networkMonitor::unregister,
                lock = lock,
            )
    }

    val state: StateFlow<ConnState> get() = policy.state
    val isConnected: Boolean get() = policy.isConnected

    fun start() = policy.start()

    fun stop() = policy.stop()

    /** Fuerza una reconexión inmediata (p. ej. al volver la red o al abrir la app). */
    fun retryNow(force: Boolean = false) = policy.retryNow(force)

    fun send(text: String): Boolean = policy.send(text)
}
