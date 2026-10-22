package dev.j3pf.rp.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dev.j3pf.rp.client.ws.ReconnectingSocketPolicy
import dev.j3pf.rp.client.ws.RelayLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket

/** Native socket/connectivity facade; reconnect timing and fencing live in shared policy. */
class ReconnectingSocket(
    private val context: Context,
    client: OkHttpClient,
    scope: CoroutineScope,
    /** Se evalúa en cada intento: `null` = no hay sesión, no conectar. */
    requestProvider: () -> Request?,
    onSocketOpen: (WebSocket) -> Unit,
    onSocketText: (String) -> Unit,
    onSocketClosed: (code: Int) -> Unit = {},
) {
    private val lock = RelayLock()
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var lastValidatedNetwork: Network? = null
    private val policy =
        ReconnectingSocketPolicy(
            scope = scope,
            driver = OkHttpSocketDriver(client),
            requestProvider = requestProvider,
            onSocketOpen = onSocketOpen,
            onSocketText = onSocketText,
            onSocketClosed = onSocketClosed,
            onStart = ::registerNetworkCallback,
            onStop = ::unregisterNetworkCallback,
            lock = lock,
        )

    val state: StateFlow<ConnState> get() = policy.state
    val isConnected: Boolean get() = policy.isConnected

    fun start() = policy.start()

    fun stop() =
        lock.withLock {
            policy.stop()
            lastValidatedNetwork = null
        }

    /** Fuerza una reconexión inmediata (p. ej. al volver la red o al abrir la app). */
    fun retryNow(force: Boolean = false) = policy.retryNow(force)

    fun send(text: String): Boolean = policy.send(text)

    private fun registerNetworkCallback() {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val cb =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    lock.withLock {
                        if (lastValidatedNetwork == null) lastValidatedNetwork = network
                    }
                    retryNow(force = false)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    caps: NetworkCapabilities,
                ) {
                    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return
                    val changed =
                        lock.withLock {
                            val prev = lastValidatedNetwork
                            lastValidatedNetwork = network
                            prev != null && prev != network
                        }
                    retryNow(force = changed)
                }

                override fun onLost(network: Network) {
                    lock.withLock {
                        if (network != lastValidatedNetwork && cm.activeNetwork != null) return@withLock
                        if (network == lastValidatedNetwork) lastValidatedNetwork = null
                        policy.networkLost()
                    }
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
