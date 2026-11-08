package dev.j3pf.rp.platform

import dagger.Binds
import dev.j3pf.rp.data.ports.AgentsGate
import dev.j3pf.rp.data.ports.BackgroundScheduler
import dev.j3pf.rp.data.ports.BatteryExemption
import dev.j3pf.rp.data.ports.DedupStore
import dev.j3pf.rp.data.ports.HostGateway
import dev.j3pf.rp.data.ports.NotificationHealth
import dev.j3pf.rp.data.ports.OpenPromptGuard
import dev.j3pf.rp.data.ports.PlatformInfo
import dev.j3pf.rp.data.ports.SettingsStore
import dev.j3pf.rp.data.ports.VoiceInputHost
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.HostRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.di.PortsModule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidPortBindingsTest {
    // Binding-count contract (Move 4): the ORIGINAL seven binds all still exist by PORT NAME,
    // and the module cannot have fewer binds than the original. O1 Move 1 + O3 M2's
    // VoiceInputHost raise the base to 8; O2's T4b Move 1 legitimately adds bindSecretStore +
    // bindNetworkMonitor + bindAppLifecycleMonitor (total 11) + O2's T4b Move 2
    // bindPushEnvelopeCrypto (total 12) + P3's HostGateway (total 13): the union list and the >= 13 floor are pinned below
    // (the original invariant — every port has its bind, none ever removed — is what this
    // test pins; the floor always matches the list it guards).
    @Test
    fun everyPortStaysBoundAndNoneRemoved() {
        assertImplements(WorkManagerBackgroundScheduler::class.java, BackgroundScheduler::class.java)
        assertImplements(DaoDedupStore::class.java, DedupStore::class.java)
        assertImplements(DaoOpenPromptGuard::class.java, OpenPromptGuard::class.java)
        assertImplements(SettingsRepository::class.java, SettingsStore::class.java)
        assertImplements(AndroidNotificationHealth::class.java, NotificationHealth::class.java)
        assertImplements(AndroidBatteryExemption::class.java, BatteryExemption::class.java)
        assertImplements(AndroidPlatformInfo::class.java, PlatformInfo::class.java)
        assertImplements(AndroidVoiceInputHost::class.java, VoiceInputHost::class.java)
        assertImplements(HostRepository::class.java, HostGateway::class.java)
        assertImplements(AgentsRepository::class.java, AgentsGate::class.java)

        val bindingPorts = PortsModule::class.java.declaredMethods
            .filter { method -> method.isAnnotationPresent(Binds::class.java) }
            .map { it.returnType.simpleName }
            .toSet()
        // Original Move-4 seven + O3 M2's VoiceInputHost; O2's T4b Move 1 adds
        // bindSecretStore + bindNetworkMonitor + bindAppLifecycleMonitor, and O2's T4b
        // Move 2 adds bindPushEnvelopeCrypto, P3 adds bindHostGateway, and P2 adds bindAgentsGate.
        val requiredPorts = setOf(
            "BackgroundScheduler", "DedupStore", "OpenPromptGuard", "SettingsStore",
            "NotificationHealth", "BatteryExemption", "PlatformInfo", "VoiceInputHost",
            "SecretStore", "NetworkMonitor", "AppLifecycleMonitor",
            "PushEnvelopeCrypto", "HostGateway", "AgentsGate",
        )
        assertTrue(
            bindingPorts.containsAll(requiredPorts),
            "missing binds: ${requiredPorts - bindingPorts} (bound: $bindingPorts)",
        )
        assertTrue(bindingPorts.size >= 14, "bind count dropped below the 14 required ports: $bindingPorts")
    }

    private fun assertImplements(implementation: Class<*>, port: Class<*>) {
        assertTrue(port in implementation.interfaces, "${implementation.simpleName} must implement ${port.simpleName}")
    }
}
