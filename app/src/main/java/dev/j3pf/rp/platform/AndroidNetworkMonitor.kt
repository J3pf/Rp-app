package dev.j3pf.rp.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.data.ports.NetworkMonitor
import dev.j3pf.rp.data.ports.NetworkObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Android ConnectivityManager adapter for the shared network-monitor port. */
class AndroidNetworkMonitor private constructor(
    private val callbackSource: NetworkCallbackSource,
) : NetworkMonitor {
    @Inject
    constructor(@ApplicationContext context: Context) : this(AndroidConnectivityCallbackSource(context))

    private val lock = Any()
    private val online = MutableStateFlow(false)
    private var observer: NetworkObserver? = null
    private var registering = false
    private var registered = false
    private var lastValidatedNetwork: Any? = null

    override val isOnline: StateFlow<Boolean> = online

    override fun register(observer: NetworkObserver) {
        synchronized(lock) {
            this.observer = observer
            if (registering || registered) return
            registering = true
        }

        val didRegister = callbackSource.register(events)
        synchronized(lock) {
            registering = false
            registered = didRegister
            if (!didRegister) this.observer = null
        }
    }

    override fun unregister() {
        val shouldUnregister =
            synchronized(lock) {
                val wasRegistered = registered
                registered = false
                registering = false
                observer = null
                lastValidatedNetwork = null
                online.value = false
                wasRegistered
            }
        if (shouldUnregister) callbackSource.unregister()
    }

    private val events =
        object : NetworkCallbackSource.Events {
            override fun onAvailable(network: Any) {
                synchronized(lock) {
                    if (!registered && !registering) return
                    if (lastValidatedNetwork == null) lastValidatedNetwork = network
                }
                notifyAvailability(true)
            }

            override fun onCapabilitiesChanged(
                network: Any,
                validated: Boolean,
            ) {
                if (!validated) return
                val (currentObserver, identityChanged, identityUnchanged) =
                    synchronized(lock) {
                        if (!registered && !registering) return
                        val previousNetwork = lastValidatedNetwork
                        lastValidatedNetwork = network
                        online.value = true
                        Triple(
                            observer,
                            previousNetwork != null && previousNetwork != network,
                            previousNetwork == network,
                        )
                    }
                // Pre-move semantics (ReconnectingSocket.kt @ c4fa975): every validated
                // capability change ended in retryNow(force = changed). A null previous
                // network (seeded lost, other active) is not an identity change, so it
                // still signals ordinary availability and the socket retries non-forced.
                if (identityChanged) {
                    currentObserver?.onNetworkIdentityChange()
                } else {
                    currentObserver?.onAvailability(true)
                }
            }

            override fun onLost(
                network: Any,
                activeNetwork: Any?,
            ) {
                val shouldNotify =
                    synchronized(lock) {
                        if (network != lastValidatedNetwork && activeNetwork != null) {
                            false
                        } else {
                            if (network == lastValidatedNetwork) lastValidatedNetwork = null
                            true
                        }
                    }
                if (shouldNotify) notifyAvailability(false)
            }
        }

    private fun notifyAvailability(available: Boolean) {
        val currentObserver =
            synchronized(lock) {
                if (!registered && !registering) return
                online.value = available
                observer
            }
        currentObserver?.onAvailability(available)
    }

    companion object {
        /** Test seam for deterministic callback characterization without a live Android network. */
        internal fun withCallbackSource(callbackSource: NetworkCallbackSource): AndroidNetworkMonitor =
            AndroidNetworkMonitor(callbackSource)
    }
}

internal interface NetworkCallbackSource {
    fun register(events: Events): Boolean

    fun unregister()

    interface Events {
        fun onAvailable(network: Any)

        fun onCapabilitiesChanged(
            network: Any,
            validated: Boolean,
        )

        fun onLost(
            network: Any,
            activeNetwork: Any?,
        )
    }
}

private class AndroidConnectivityCallbackSource(
    @ApplicationContext context: Context,
) : NetworkCallbackSource {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    override fun register(events: NetworkCallbackSource.Events): Boolean {
        val manager = connectivityManager ?: return false
        val networkCallback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    events.onAvailable(network)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    caps: NetworkCapabilities,
                ) {
                    events.onCapabilitiesChanged(
                        network,
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    )
                }

                override fun onLost(network: Network) {
                    events.onLost(network, manager.activeNetwork)
                }
            }
        return runCatching {
            manager.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                networkCallback,
            )
            callback = networkCallback
            true
        }.getOrDefault(false)
    }

    override fun unregister() {
        val registeredCallback = callback ?: return
        callback = null
        runCatching { connectivityManager?.unregisterNetworkCallback(registeredCallback) }
    }
}
