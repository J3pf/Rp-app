package dev.j3pf.rp.platform

import dev.j3pf.rp.data.ports.NetworkMonitor
import dev.j3pf.rp.data.ports.NetworkObserver
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidNetworkMonitorTest {
    @Test
    fun callbackAvailabilityAndValidatedIdentityChangesAreSeparated() = runTest {
        val callbackSource = RecordingNetworkCallbackSource()
        val monitor: NetworkMonitor = AndroidNetworkMonitor.withCallbackSource(callbackSource)
        val availability = mutableListOf<Boolean>()
        var identityChanges = 0
        monitor.register(
            object : NetworkObserver {
                override fun onAvailability(available: Boolean) {
                    availability += available
                }

                override fun onNetworkIdentityChange() {
                    identityChanges += 1
                }
            },
        )

        assertFalse(monitor.isOnline.value)
        val first = NetworkIdentity("first")
        val second = NetworkIdentity("second")
        callbackSource.available(first)
        assertTrue(monitor.isOnline.value)
        assertEquals(listOf(true), availability)
        assertEquals(0, identityChanges)

        callbackSource.capabilitiesChanged(first, validated = false)
        callbackSource.capabilitiesChanged(first, validated = true)
        callbackSource.capabilitiesChanged(first, validated = true)
        assertEquals(listOf(true), availability)
        assertEquals(0, identityChanges)

        callbackSource.available(second)
        callbackSource.capabilitiesChanged(second, validated = false)
        callbackSource.capabilitiesChanged(second, validated = true)
        callbackSource.capabilitiesChanged(second, validated = true)
        assertTrue(monitor.isOnline.value)
        assertEquals(listOf(true, true), availability)
        assertEquals(1, identityChanges)

        callbackSource.lost(first, activeNetwork = second)
        assertTrue(monitor.isOnline.value)
        assertEquals(listOf(true, true), availability)
        assertEquals(1, identityChanges)

        callbackSource.lost(second, activeNetwork = null)
        assertFalse(monitor.isOnline.value)
        assertEquals(listOf(true, true, false), availability)
        assertEquals(1, identityChanges)

        monitor.unregister()
        assertEquals(1, callbackSource.unregisterCalls)
    }

    private data class NetworkIdentity(val value: String)

    private class RecordingNetworkCallbackSource : NetworkCallbackSource {
        private lateinit var events: NetworkCallbackSource.Events
        var unregisterCalls = 0
            private set

        override fun register(events: NetworkCallbackSource.Events): Boolean {
            this.events = events
            return true
        }

        override fun unregister() {
            unregisterCalls += 1
        }

        fun available(network: Any) = events.onAvailable(network)

        fun capabilitiesChanged(
            network: Any,
            validated: Boolean,
        ) = events.onCapabilitiesChanged(network, validated)

        fun lost(
            network: Any,
            activeNetwork: Any?,
        ) = events.onLost(network, activeNetwork)
    }
}
