package dev.j3pf.rp.platform

import dev.j3pf.rp.data.ports.NetworkMonitor
import dev.j3pf.rp.data.ports.NetworkObserver
import dev.j3pf.rp.di.PortsModule
import kotlinx.coroutines.test.runTest
import org.junit.Test
import javax.inject.Singleton
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidNetworkMonitorTest {
    @Test
    fun callbackAvailabilityAndValidatedIdentityChangesAreSeparated() =
        runTest {
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

            // Same validated network while already online short-circuits RSSI/bandwidth updates.
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

    @Test
    fun multipleObserversShareSingleCallbackRegistrationWithFanOutAndRefCountedUnregister() =
        runTest {
            val callbackSource = RecordingNetworkCallbackSource()
            val monitor: NetworkMonitor = AndroidNetworkMonitor.withCallbackSource(callbackSource)
            val avail1 = mutableListOf<Boolean>()
            val avail2 = mutableListOf<Boolean>()
            var id1 = 0
            var id2 = 0

            monitor.register(
                object : NetworkObserver {
                    override fun onAvailability(available: Boolean) {
                        avail1 += available
                    }

                    override fun onNetworkIdentityChange() {
                        id1 += 1
                    }
                },
            )
            monitor.register(
                object : NetworkObserver {
                    override fun onAvailability(available: Boolean) {
                        avail2 += available
                    }

                    override fun onNetworkIdentityChange() {
                        id2 += 1
                    }
                },
            )

            assertEquals(1, callbackSource.registerCalls)

            val wifi = NetworkIdentity("wifi")
            val cell = NetworkIdentity("cell")
            callbackSource.available(wifi)
            callbackSource.capabilitiesChanged(wifi, validated = true)
            callbackSource.capabilitiesChanged(cell, validated = true)

            assertEquals(listOf(true), avail1)
            assertEquals(listOf(true), avail2)
            assertEquals(1, id1)
            assertEquals(1, id2)

            // First unregister keeps OS callback active for remaining observer.
            monitor.unregister()
            assertEquals(0, callbackSource.unregisterCalls)

            // Final unregister releases OS callback once.
            monitor.unregister()
            assertEquals(1, callbackSource.unregisterCalls)
        }

    @Test
    fun portsModuleBindsNetworkMonitorAsSingleton() {
        val bindMethod =
            PortsModule::class.java.declaredMethods.single { it.name == "bindNetworkMonitor" }
        assertTrue(
            bindMethod.isAnnotationPresent(Singleton::class.java),
            "PortsModule.bindNetworkMonitor must be @Singleton so foreground and direct sockets share one callback",
        )
    }

    private data class NetworkIdentity(
        val value: String,
    )

    private class RecordingNetworkCallbackSource : NetworkCallbackSource {
        private lateinit var events: NetworkCallbackSource.Events
        var registerCalls = 0
            private set
        var unregisterCalls = 0
            private set

        override fun register(events: NetworkCallbackSource.Events): Boolean {
            registerCalls += 1
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
