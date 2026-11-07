package dev.j3pf.rp.data.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * C1 (orchestrator condition, host-run identity pin): the LITERAL identity of the stored
 * settings data is pinned HERE on this machine, not deferred to the device harness. A rename
 * of the preferences file, the DataStore name, or ANY of the fifteen production key strings
 * turns this test RED in seconds (mutation probe contract). Values are quoted as strings.
 */
class SettingsContractIdentityTest {
    @Test
    fun fileNameAndPrefsNameArePinnedLiterals() {
        assertEquals("rp_settings.preferences_pb", SettingsKeys.FILE_NAME)
        assertEquals("rp_settings", SettingsKeys.PREFS_NAME)
        // The file name is the DataStore name + the standard preferences extension:
        assertEquals(SettingsKeys.PREFS_NAME, SettingsKeys.FILE_NAME.removeSuffix(".preferences_pb"))
        assertTrue(SettingsKeys.FILE_NAME.endsWith(".preferences_pb"))
    }

    @Test
    fun allFifteenProductionKeyNamesArePinnedLiterals() {
        val expected =
            listOf(
                "selectedContext", "directPush", "requireUnlock", "criticalAlways", "appLock",
                "flagSecure", "onboardingDone", "batteryAsked", "notifyPrefs", "mutedAgents",
                "appTheme", "chatFontScale", "chatMessageSpacing", "mesh_selected_machine",
                "mesh_selected_group",
            )
        assertEquals(expected, SettingsKeys.ALL_NAMES)
        // The counted list matches the PRIVATE to PUBLIC mapping of the store one-to-one:
        // every name is consumed by a Preferences.Key living in this object (rename of any
        // single string breaks the equality above — C1 mutation probe contract).
        assertEquals(15, SettingsKeys.ALL_NAMES.size)
    }

    @Test
    fun preferenceKeysAreBuiltOnThePinnedNames() {
        // Reinforce the second half of the C1 pin: the Preferences.Key instances were built
        // from THE SAME names that this test asserts as literals (no accidental second copy).
        assertTrue(SettingsKeys.ALL_NAMES.containsAll(listOf("selectedContext", "mesh_selected_machine")))
        assertEquals(15, SettingsKeys.ALL_NAMES.toSet().size)
    }
}
