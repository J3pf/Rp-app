package dev.j3pf.rp.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.data.ports.NetworkMonitor
import dev.j3pf.rp.data.ports.NetworkObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject

/** Android ConnectivityManager adapter for the shared network-monitor port. */
class AndroidNetworkMonitor private constructor(
    private val callbackSource: NetworkCallbackSource,
) : NetworkMonitor {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(AndroidConnectivityCallbackSource(context))

    private val lock = Any()
    private val online = MutableStateFlow(false)
    private val observers = CopyOnWriteArraySet<NetworkObserver>()
    private var activeRegistrations = 0
    private var registering = false
    private var registered = false
    private var lastValidatedNetwork: Any? = null

    override val isOnline: StateFlow<Boolean> = online

    override fun register(observer: NetworkObserver) {
        val shouldRegister =
            synchronized(lock) {
                observers.add(observer)
                activeRegistrations += 1
                if (registering || registered) {
                    false
                } else {
                    registering = true
                    true
                }
            }
        if (!shouldRegister) return

        val didRegister = callbackSource.register(events)
        synchronized(lock) {
            registering = false
            registered = didRegister
            if (!didRegister) {
                observers.clear()
                activeRegistrations = 0
            }
        }
    }

    override fun unregister() {
        val shouldUnregister =
            synchronized(lock) {
                if (activeRegistrations > 1) {
                    activeRegistrations -= 1
                    false
                } else {
                    val wasRegistered = registered
                    activeRegistrations = 0
                    registered = false
                    registering = false
                    observers.clear()
                    lastValidatedNetwork = null
                    online.value = false
                    wasRegistered
                }
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
                val identityChanged =
                    synchronized(lock) {
                        if (!registered && !registering) return
                        val previousNetwork = lastValidatedNetwork
                        if (previousNetwork == network && online.value) return
                        lastValidatedNetwork = network
                        online.value = true
                        previousNetwork != null && previousNetwork != network
                    }
                if (identityChanged) {
                    observers.forEach { it.onNetworkIdentityChange() }
                } else {
                    observers.forEach { it.onAvailability(true) }
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
        synchronized(lock) {
            if (!registered && !registering) return
            online.value = available
        }
        observers.forEach { it.onAvailability(available) }
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
            manager.registerDefaultNetworkCallback(networkCallback)
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
