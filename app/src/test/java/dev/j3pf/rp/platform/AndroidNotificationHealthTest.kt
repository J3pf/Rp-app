package dev.j3pf.rp.platform

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.data.ports.NotificationPermissionOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidNotificationHealthTest {
    private lateinit var context: Application
    private lateinit var health: AndroidNotificationHealth
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        health = AndroidNotificationHealth(context)
        notificationManager = context.getSystemService(NotificationManager::class.java)
    }

    @Test
    fun enabledNotificationsAreGranted() = runTest {
        shadowOf(notificationManager).setNotificationsEnabled(true)

        assertTrue(health.isEnabled())
        assertEquals(NotificationPermissionOutcome.GRANTED, health.request())
    }

    @Test
    fun disabledNotificationsOnAndroid13AreDenied() = runTest {
        shadowOf(notificationManager).setNotificationsEnabled(false)

        assertFalse(health.isEnabled())
        assertEquals(NotificationPermissionOutcome.DENIED, health.request())
    }

    // REAL constraint learned from the red run: the merged manifest declares minSdk 29, so the
    // merged manifest cannot even be parsed under a Robolectric sdk < 29. The API < 33 branch of
    // AndroidNotificationHealth.request() is therefore pinned structurally on sdk 30 (the lowest
    // sdk >= minSdk that is also < 33).
    @Test
    @Config(sdk = [30])
    fun disabledNotificationsBelowAndroid13AreUnavailable() = runTest {
        shadowOf(notificationManager).setNotificationsEnabled(false)

        assertFalse(health.isEnabled())
        assertEquals(NotificationPermissionOutcome.UNAVAILABLE, health.request())
    }
}
