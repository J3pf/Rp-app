package dev.j3pf.rp.data.repo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.j3pf.rp.protocol.MachinePrefs
import dev.j3pf.rp.protocol.NotifyPrefs
import dev.j3pf.rp.protocol.QuietHours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Reads the checked-in S14 DataStore fixture and pins recovery from an invalid preferences file. */
class DataStoreFixtureTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun onDiskFileFixtureReadsBackExactValues() = runTest {
        val resource = javaClass.classLoader?.getResourceAsStream("rp_settings.preferences_pb")
        assertNotNull("Missing test resource rp_settings.preferences_pb; run the exporter first", resource)
        val file = File(tmp.root, "rp_settings.preferences_pb")
        resource!!.use { input -> file.outputStream().use(input::copyTo) }

        val store =
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file },
            )
        val settings = SettingsRepository(store).settings.first()
        val expectedNotifyPrefs =
            NotifyPrefs(
                categories = mapOf("approvals" to false, "questions" to true),
                machines =
                    mapOf(
                        "machine-alpha" to
                            MachinePrefs(
                                mute = true,
                                categories = mapOf("terminal" to false),
                                quietHours = QuietHours(from = "22:00", to = "07:00", tz = "Europe/Madrid"),
                            ),
                    ),
            )

        assertEquals("dark", settings.appTheme)
        assertEquals(1.2f, settings.chatFontScale)
        assertEquals("compact", settings.chatMessageSpacing)
        assertEquals(setOf("agent-beta"), settings.mutedAgents)
        assertEquals("personal", settings.selectedContext)
        assertFalse(settings.requireUnlockToApprove)
        assertTrue(settings.flagSecure)
        assertTrue(settings.appLock)
        assertTrue(settings.onboardingDone)
        assertEquals(expectedNotifyPrefs, settings.notifyPrefs)

        assertTrue(settings.directPush)
        assertTrue(settings.criticalAlways)
        assertFalse(settings.batteryAsked)
    }

    @Test
    fun corruptPreferencesFileFallsBackToEmptyPreferences() = runTest {
        val file = File(tmp.root, "corrupt.preferences_pb")
        file.writeBytes("not-a-preferences-file".toByteArray(Charsets.UTF_8))
        val store =
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file },
            )

        // SettingsRepository catches DataStore IOExceptions and maps empty preferences to AppSettings defaults.
        assertEquals(AppSettings(), SettingsRepository(store).settings.first())
    }
}
