package dev.j3pf.rp.data.ports

import kotlinx.coroutines.flow.StateFlow

/** Push/foreground coordination contract negotiated with O2 (T4b Move 1, 2026-11-06). */
fun interface NetworkObserver {
    fun onAvailability(available: Boolean)
}

/**
 * Platform network availability, negotiated with O2: Android impl wraps the existing
 * ConnectivityManager callback adapter (ReconnectingSocket.kt:75-107 re-wired by O2; the
 * ReconnectingSocketPolicy DECISION MODE stays untouched), iOS impl = NWPathMonitor.
 */
interface NetworkMonitor {
    fun register(observer: NetworkObserver)

    fun unregister()

    val isOnline: StateFlow<Boolean>
}

/**
 * Foreground/background state, negotiated with O2: Android impl wraps ProcessLifecycleOwner
 * (RelayConnection.kt:86-104 re-wired by O2), iOS impl = scene/UIApplicationDelegate.
 */
interface AppLifecycleMonitor {
    val isForeground: StateFlow<Boolean>

    fun observe(
        onForeground: () -> Unit,
        onBackground: () -> Unit,
    )
}
