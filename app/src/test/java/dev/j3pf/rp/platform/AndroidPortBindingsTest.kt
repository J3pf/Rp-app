package dev.j3pf.rp.platform

import dagger.Binds
import dev.j3pf.rp.data.ports.BackgroundScheduler
import dev.j3pf.rp.data.ports.BatteryExemption
import dev.j3pf.rp.data.ports.DedupStore
import dev.j3pf.rp.data.ports.NotificationHealth
import dev.j3pf.rp.data.ports.OpenPromptGuard
import dev.j3pf.rp.data.ports.PlatformInfo
import dev.j3pf.rp.data.ports.SettingsStore
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
    @Test
    fun eachPortImplementationMatchesItsBindingAndModuleHasSevenBinds() {
        assertImplements(WorkManagerBackgroundScheduler::class.java, BackgroundScheduler::class.java)
        assertImplements(DaoDedupStore::class.java, DedupStore::class.java)
        assertImplements(DaoOpenPromptGuard::class.java, OpenPromptGuard::class.java)
        assertImplements(SettingsRepository::class.java, SettingsStore::class.java)
        assertImplements(AndroidNotificationHealth::class.java, NotificationHealth::class.java)
        assertImplements(AndroidBatteryExemption::class.java, BatteryExemption::class.java)
        assertImplements(AndroidPlatformInfo::class.java, PlatformInfo::class.java)

        val bindings = PortsModule::class.java.declaredMethods.count { method ->
            method.isAnnotationPresent(Binds::class.java)
        }
        assertEquals(7, bindings)
    }

    private fun assertImplements(implementation: Class<*>, port: Class<*>) {
        assertTrue(port in implementation.interfaces, "${implementation.simpleName} must implement ${port.simpleName}")
    }
}
