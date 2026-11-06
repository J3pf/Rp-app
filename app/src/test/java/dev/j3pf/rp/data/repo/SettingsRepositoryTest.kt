package dev.j3pf.rp.data.repo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.j3pf.rp.protocol.NotifyPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Characterization (T1/Gate A) of the settings store on DataStore, production code UNCHANGED.
 * Pins ST-01..ST-06 of docs/parity/o1-data.md (defaults, font-scale clamp, mute toggle, mesh
 * selection, malformed notifyPrefs JSON fallback, wipe, boolean/string round trips).
 */
class SettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        val file = File(tmp.root, "test-${System.nanoTime()}.preferences_pb")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store =
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file },
            )
        repo = SettingsRepository(store)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun freshInstallYieldsDocumentedDefaults() = runTest {
        val s = repo.current()
        assertEquals(AppSettings.ALL, s.selectedContext)
        assertTrue(s.directPush)
        assertTrue(s.requireUnlockToApprove)
        assertTrue(s.criticalAlways)
        assertFalse(s.appLock)
        assertFalse(s.flagSecure)
        assertFalse(s.onboardingDone)
        assertFalse(s.batteryAsked)
        assertEquals(NotifyPrefs(), s.notifyPrefs)
        assertEquals(emptySet<String>(), s.mutedAgents)
        assertEquals("auto", s.appTheme)
        assertEquals(1.0f, s.chatFontScale)
        assertEquals("normal", s.chatMessageSpacing)
        assertNull(s.selectedMeshMachine)
        assertNull(s.selectedMeshGroup)
    }

    @Test
    fun fontScaleIsClampedTo80To145OnLoadAndOnSet() = runTest {
        repo.setChatFontScale(2.0f)
        assertEquals(1.45f, repo.current().chatFontScale)
        repo.setChatFontScale(0.1f)
        assertEquals(0.80f, repo.current().chatFontScale)
        repo.setChatFontScale(1.1f)
        assertEquals(1.1f, repo.current().chatFontScale)
    }

    @Test
    fun agentMutingTogglesWithoutTouchingOtherSettings() = runTest {
        repo.setAgentMuted("a1", true)
        assertEquals(setOf("a1"), repo.current().mutedAgents)
        assertEquals("auto", repo.current().appTheme) // unrelated keys intact
        repo.setAgentMuted("a2", true)
        assertEquals(setOf("a1", "a2"), repo.current().mutedAgents)
        repo.setAgentMuted("a1", false)
        assertEquals(setOf("a2"), repo.current().mutedAgents)
    }

    @Test
    fun meshSelectionsRemoveKeyWhenSetToNull() = runTest {
        repo.setMeshSelectedMachine("m1")
        repo.setMeshSelectedGroup("g1")
        assertEquals("m1", repo.current().selectedMeshMachine)
        assertEquals("g1", repo.current().selectedMeshGroup)
        repo.setMeshSelectedMachine(null)
        assertNull(repo.current().selectedMeshMachine)
        assertEquals("g1", repo.current().selectedMeshGroup)
        repo.setMeshSelectedGroup(null)
        assertNull(repo.current().selectedMeshGroup)
    }

    @Test
    fun malformedNotifyPrefsJsonFallsBackToDefaultsInsteadOfCrashing() = runTest {
        // Write garbage directly into backing storage (as if produced by an older or corrupt build).
        val backing = File(tmp.root, "corrupt-${System.nanoTime()}.preferences_pb")
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store2 =
            PreferenceDataStoreFactory.create(
                scope = scope2,
                produceFile = { backing },
            )
        store2.edit { it[stringPreferencesKey("notifyPrefs")] = "this-is{not-json" }
        val corruptRepo = SettingsRepository(store2)
        assertEquals(NotifyPrefs(), corruptRepo.current().notifyPrefs)
        // All other settings still map with defaults despite the polluted key.
        assertEquals("auto", corruptRepo.current().appTheme)
        scope2.cancel()
    }

    @Test
    fun wipeClearsEveryKey() = runTest {
        repo.setAppTheme("dark")
        repo.setAgentMuted("a1", true)
        repo.setOnboardingDone(true)
        repo.wipe()
        assertEquals("auto", repo.current().appTheme)
        assertEquals(emptySet<String>(), repo.current().mutedAgents)
        assertFalse(repo.current().onboardingDone)
    }

    @Test
    fun booleanAndStringSettersRoundTrip() = runTest {
        repo.setSelectedContext("trabajo")
        repo.setDirectPush(false)
        repo.setRequireUnlock(false)
        repo.setCriticalAlways(false)
        repo.setAppLock(true)
        repo.setFlagSecure(true)
        repo.setOnboardingDone(true)
        repo.setBatteryAsked(true)
        repo.setChatMessageSpacing("compact")
        val s = repo.current()
        assertEquals("trabajo", s.selectedContext)
        assertFalse(s.directPush)
        assertFalse(s.requireUnlockToApprove)
        assertFalse(s.criticalAlways)
        assertTrue(s.appLock)
        assertTrue(s.flagSecure)
        assertTrue(s.onboardingDone)
        assertTrue(s.batteryAsked)
        assertEquals("compact", s.chatMessageSpacing)
    }
}
