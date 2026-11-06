package dev.j3pf.rp.platform

import android.app.Application
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidBatteryExemptionTest {
    private lateinit var context: Application
    private lateinit var exemption: AndroidBatteryExemption
    private lateinit var powerManager: PowerManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        exemption = AndroidBatteryExemption(context)
        powerManager = context.getSystemService(PowerManager::class.java)
    }

    @Test
    fun reportsPowerManagerStateAndIsSupported() = runTest {
        assertTrue(exemption.isSupported)
        shadowOf(powerManager).setIgnoringBatteryOptimizations(context.packageName, true)
        assertTrue(exemption.isIgnoring())
        shadowOf(powerManager).setIgnoringBatteryOptimizations(context.packageName, false)
        assertFalse(exemption.isIgnoring())
    }
}
