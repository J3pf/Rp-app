package dev.j3pf.rp.data.repo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.j3pf.rp.data.settings.DataStoreSettingsStore
import dev.j3pf.rp.data.settings.SettingsKeys
import dev.j3pf.rp.protocol.MachinePrefs
import dev.j3pf.rp.protocol.NotifyPrefs
import dev.j3pf.rp.protocol.QuietHours
import dev.j3pf.rp.protocol.RpJson
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

/**
 * C2 (orchestrator condition): BIDIRECTIONAL format proof against the committed S14 fixture
 * `rp_settings.preferences_pb` (the committed copy under data-core/src/commonTest/resources
 * is a byte-identical copy; the ORIGINAL app fixture stays untouched and DataStoreFixtureTest
 * keeps proving the old direction on its own):
 *
 * (a) OLD bytes -> NEW common store: the committed fixture, produced by the CURRENT
 *     :app production writer, must be read by the NEW common store into the EXACT same
 *     AppSettings values, verified FIELD-BY-FIELD (not "non-default").
 * (b) NEW store writes -> OLD layout reader: a file written by the common store must be
 *     read by an independent reader that mirrors the OLD production key names as literal
 *     strings (NOT via SettingsKeys), proving the NEW bytes remain readable by the old
 *     file layout with identical values (protobuf encoding unchanged, C3).
 */
class DataStoreBidirectionalCompatibilityTest {
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

    private fun copyFixtureTo(target: File) {
        // The APP fixture is read-only here (S15): copied into the tmp folder, never edited.
        copyFixtureFrom("rp_settings.preferences_pb", target)
    }

    private fun copyFixtureFrom(name: String, target: File) {
        val stream = javaClass.classLoader.getResourceAsStream(name)
        assertNotNull("Missing fixture $name", stream)
        stream!!.use { input -> target.outputStream().use(input::copyTo) }
    }

    @Test
    fun fixtureOldBytesAreReadFieldByFieldByTheNewCommonStore() = runTest {
        val file = File(tmp.root, "fixture-copy.preferences_pb")
        copyFixtureTo(file)

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

        val settings = DataStoreSettingsStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })).settings.first()
        // FIELD-BY-FIELD, not "non-default" (same list as DataStoreFixtureTest pins for the
        // OLD reader, so both directions must agree on the SAME fixture bytes):
        assertEquals("personal", settings.selectedContext)
        assertTrue(settings.directPush)
        assertFalse(settings.requireUnlockToApprove)
        assertTrue(settings.criticalAlways)
        assertTrue(settings.appLock)
        assertTrue(settings.flagSecure)
        assertTrue(settings.onboardingDone)
        assertFalse(settings.batteryAsked)
        assertEquals(expectedNotifyPrefs, settings.notifyPrefs)
        assertEquals(setOf("agent-beta"), settings.mutedAgents)
        assertEquals("dark", settings.appTheme)
        assertEquals(1.2f, settings.chatFontScale)
        assertEquals("compact", settings.chatMessageSpacing)
        assertEquals(null, settings.selectedMeshMachine)
        assertEquals(null, settings.selectedMeshGroup)
    }

    @Test
    fun newStoreWritesAreReadableByAnIndependentOldLayoutReader() = runTest {
        val file = File(tmp.root, "new-writes.preferences_pb")
        // A single file must never host two ACTIVE DataStores (FileStorage constraint): the
        // WRITING store lives in its own scope that is CANCELLED (releasing the connection)
        // before the independent old-layout reader opens the same file.
        val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val newStore =
            DataStoreSettingsStore(
                PreferenceDataStoreFactory.create(scope = writeScope, produceFile = { file }),
            )
        newStore.setSelectedContext("family")
        newStore.setDirectPush(false)
        newStore.setRequireUnlock(false)
        newStore.setCriticalAlways(false)
        newStore.setAppLock(true)
        newStore.setFlagSecure(true)
        newStore.setOnboardingDone(true)
        newStore.setBatteryAsked(true)
        newStore.setAppTheme("sexy")
        newStore.setChatFontScale(1.2f)
        newStore.setChatMessageSpacing("compact")
        newStore.setMeshSelectedMachine("machine-alpha")
        newStore.setMeshSelectedGroup(null)
        newStore.setAgentMuted("agent-beta", true)
        newStore.setNotifyPrefs(
            NotifyPrefs(
                categories = mapOf("approvals" to true, "questions" to false),
                machines = emptyMap(),
            ),
        )

        writeScope.cancel() // release the writing connection; file stays on disk

        // INDEPENDENT old-layout reader: the SAME DataStore factory with the OLD production
        // key names written as LITERAL STRINGS here (no SettingsKeys, no new abstractions
        // beyond the test itself) and the SAME AppSettings field mapping the old
        // SettingsRepository used. Reading the bytes written by the NEW store must produce
        // identical values — this is the real "the upgrade does not corrupt what the user
        // already has in the other direction" proof (C2-b).
        val oldReader =
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        val p = oldReader.data.first()
        val expectedNotifyPrefs =
            NotifyPrefs(categories = mapOf("approvals" to true, "questions" to false), machines = emptyMap())

        assertEquals("family", p[stringPreferencesKey("selectedContext")])
        assertEquals(false, p[booleanPreferencesKey("directPush")])
        assertEquals(false, p[booleanPreferencesKey("requireUnlock")])
        assertEquals(false, p[booleanPreferencesKey("criticalAlways")])
        assertTrue(p[booleanPreferencesKey("appLock")]!!)
        assertTrue(p[booleanPreferencesKey("flagSecure")]!!)
        assertTrue(p[booleanPreferencesKey("onboardingDone")]!!)
        assertTrue(p[booleanPreferencesKey("batteryAsked")]!!)
        assertEquals(expectedNotifyPrefs, p[stringPreferencesKey("notifyPrefs")]!!.let { runCatching { RpJson.decodeFromString(NotifyPrefs.serializer(), it) }.getOrNull() })
        assertEquals(setOf("agent-beta"), p[stringSetPreferencesKey("mutedAgents")])
        assertEquals("sexy", p[stringPreferencesKey("appTheme")])
        assertEquals(1.2f, p[floatPreferencesKey("chatFontScale")])
        assertEquals("compact", p[stringPreferencesKey("chatMessageSpacing")])
        assertEquals("machine-alpha", p[stringPreferencesKey("mesh_selected_machine")])
        assertFalse(p.contains(stringPreferencesKey("mesh_selected_group")))
        // And the file is the SAME PROTOCOL FORMAT (magic/marker check via size sanity only:
        // protobuf-preferences content is opaque, so this is the C3 no-format-change witness).
        assertTrue(file.length() > 0)
        assertEquals(
            "New store keys must be byte-identical old-layout keys",
            listOf("selectedContext", "directPush", "requireUnlock", "criticalAlways", "appLock", "flagSecure", "onboardingDone", "batteryAsked", "notifyPrefs", "mutedAgents", "appTheme", "chatFontScale", "chatMessageSpacing", "mesh_selected_machine", "mesh_selected_group"),
            SettingsKeys.ALL_NAMES,
        )
    }
}
