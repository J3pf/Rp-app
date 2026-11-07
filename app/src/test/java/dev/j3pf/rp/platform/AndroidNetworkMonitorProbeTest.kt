package dev.j3pf.rp.platform

import dev.j3pf.rp.data.ports.NetworkObserver
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidNetworkMonitorProbeTest {
    @Test
    fun independentMonitorInstances_doNotShareCallbacks() = runTest {
        val firstSource = RecordingNetworkCallbackSource()
        val secondSource = RecordingNetworkCallbackSource()
        val firstMonitor = AndroidNetworkMonitor.withCallbackSource(firstSource)
        val secondMonitor = AndroidNetworkMonitor.withCallbackSource(secondSource)
        val firstObserver = Obs()
        val secondObserver = Obs()
        firstMonitor.register(firstObserver)
        secondMonitor.register(secondObserver)

        firstSource.available(NetworkIdentity("A"))
        firstSource.lost(NetworkIdentity("A"), activeNetwork = null)

        assertEquals(listOf(true, false), firstObserver.availability)
        assertEquals(emptyList(), firstObserver.identityChanges)
        assertEquals(emptyList(), secondObserver.availability)
        assertEquals(emptyList(), secondObserver.identityChanges)
        assertFalse(firstMonitor.isOnline.value)
        assertFalse(secondMonitor.isOnline.value)
    }

    @Test
    fun validatedAfterUnvalidatedAvailable_pre_move_retried() = runTest {
        val source = RecordingNetworkCallbackSource()
        val monitor = AndroidNetworkMonitor.withCallbackSource(source)
        val observer = Obs()
        monitor.register(observer)
        val network = NetworkIdentity("A")

        source.available(network)
        source.capabilitiesChanged(network, validated = true)

        assertEquals(listOf(true, true), observer.availability)
        assertEquals(emptyList(), observer.identityChanges)
    }

    @Test
    fun availableA_thenValidatedB_pre_move_forced() = runTest {
        val source = RecordingNetworkCallbackSource()
        val monitor = AndroidNetworkMonitor.withCallbackSource(source)
        val observer = Obs()
        monitor.register(observer)

        source.available(NetworkIdentity("A"))
        source.capabilitiesChanged(NetworkIdentity("B"), validated = true)

        assertEquals(listOf(true), observer.availability)
        assertEquals(listOf(Unit), observer.identityChanges)
    }

    // DI-level singletonFreeBind is checked by reading PortsModule.kt: bindNetworkMonitor must
    // have no @Singleton scope, while bindAppLifecycleMonitor keeps @Singleton. The injected
    // AndroidNetworkMonitor constructor is also unscoped; reflection/this unit test cannot verify
    // Hilt's generated graph without turning the probe into an integration test.

    private data class NetworkIdentity(val value: String)

    private class Obs : NetworkObserver {
        val availability = mutableListOf<Boolean>()
        val identityChanges = mutableListOf<Unit>()

        override fun onAvailability(available: Boolean) {
            availability += available
        }

        override fun onNetworkIdentityChange() {
            identityChanges += Unit
        }
    }

    private class RecordingNetworkCallbackSource : NetworkCallbackSource {
        private lateinit var events: NetworkCallbackSource.Events

        override fun register(events: NetworkCallbackSource.Events): Boolean {
            this.events = events
            return true
        }

        override fun unregister() = Unit

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
