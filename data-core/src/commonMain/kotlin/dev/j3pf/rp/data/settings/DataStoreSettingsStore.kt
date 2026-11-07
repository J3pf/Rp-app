package dev.j3pf.rp.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.data.ports.SettingsStore
import dev.j3pf.rp.protocol.NotifyPrefs
import dev.j3pf.rp.protocol.RpJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import androidx.datastore.core.IOException

/**
 * MOVE 2 (ADR 0003 §2, orchestrator conditions C1-C3): the settings writer is now COMMON.
 * EXACTLY the same file name (`rp_settings.preferences_pb`), the SAME protobuf preferences
 * encoding, THE SAME keys and value mapping and the SAME edit{} update strategy as the
 * production Android `SettingsRepository` had before this move — byte-for-byte (verified
 * bidirectionally against the committed fixture in `DataStoreBidirectionalCompatibilityTest`).
 * NO encryption layer is added (C3). The corrupt-file tolerance keeps the IOException ->
 * emptyPreferences() fallback semantics (ST-06/ST-08).
 */
object SettingsKeys {
    const val FILE_NAME = "rp_settings.preferences_pb"
    const val PREFS_NAME = "rp_settings"

    // The fifteen production key names, verbatim (C1 identity detail: a rename of ANY of
    // these strings turns SettingsContractIdentityTest RED on this host in seconds).
    const val K_SELECTED_CONTEXT = "selectedContext"
    const val K_DIRECT_PUSH = "directPush"
    const val K_REQUIRE_UNLOCK = "requireUnlock"
    const val K_CRITICAL_ALWAYS = "criticalAlways"
    const val K_APP_LOCK = "appLock"
    const val K_FLAG_SECURE = "flagSecure"
    const val K_ONBOARDING_DONE = "onboardingDone"
    const val K_BATTERY_ASKED = "batteryAsked"
    const val K_NOTIFY_PREFS = "notifyPrefs"
    const val K_MUTED_AGENTS = "mutedAgents"
    const val K_APP_THEME = "appTheme"
    const val K_CHAT_FONT_SCALE = "chatFontScale"
    const val K_CHAT_MESSAGE_SPACING = "chatMessageSpacing"
    const val K_MESH_SELECTED_MACHINE = "mesh_selected_machine"
    const val K_MESH_SELECTED_GROUP = "mesh_selected_group"

    val ALL_NAMES =
        listOf(
            K_SELECTED_CONTEXT, K_DIRECT_PUSH, K_REQUIRE_UNLOCK, K_CRITICAL_ALWAYS, K_APP_LOCK,
            K_FLAG_SECURE, K_ONBOARDING_DONE, K_BATTERY_ASKED, K_NOTIFY_PREFS, K_MUTED_AGENTS,
            K_APP_THEME, K_CHAT_FONT_SCALE, K_CHAT_MESSAGE_SPACING, K_MESH_SELECTED_MACHINE,
            K_MESH_SELECTED_GROUP,
        )

    val SELECTED = stringPreferencesKey(K_SELECTED_CONTEXT)
    val DIRECT = booleanPreferencesKey(K_DIRECT_PUSH)
    val REQUIRE_UNLOCK = booleanPreferencesKey(K_REQUIRE_UNLOCK)
    val CRITICAL = booleanPreferencesKey(K_CRITICAL_ALWAYS)
    val APP_LOCK = booleanPreferencesKey(K_APP_LOCK)
    val FLAG_SECURE = booleanPreferencesKey(K_FLAG_SECURE)
    val ONBOARDING = booleanPreferencesKey(K_ONBOARDING_DONE)
    val BATTERY = booleanPreferencesKey(K_BATTERY_ASKED)
    val PREFS = stringPreferencesKey(K_NOTIFY_PREFS)
    val MUTED_AGENTS = stringSetPreferencesKey(K_MUTED_AGENTS)
    val APP_THEME = stringPreferencesKey(K_APP_THEME)
    val CHAT_FONT_SCALE = floatPreferencesKey(K_CHAT_FONT_SCALE)
    val CHAT_MESSAGE_SPACING = stringPreferencesKey(K_CHAT_MESSAGE_SPACING)
    val MESH_SELECTED_MACHINE = stringPreferencesKey(K_MESH_SELECTED_MACHINE)
    val MESH_SELECTED_GROUP = stringPreferencesKey(K_MESH_SELECTED_GROUP)
}

class DataStoreSettingsStore(
    private val store: DataStore<Preferences>,
) : SettingsStore {
    override val settings: Flow<AppSettings> =
        store.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }.map { p ->
                AppSettings(
                    selectedContext = p[SettingsKeys.SELECTED] ?: AppSettings.ALL,
                    directPush = p[SettingsKeys.DIRECT] ?: true,
                    requireUnlockToApprove = p[SettingsKeys.REQUIRE_UNLOCK] ?: true,
                    criticalAlways = p[SettingsKeys.CRITICAL] ?: true,
                    appLock = p[SettingsKeys.APP_LOCK] ?: false,
                    flagSecure = p[SettingsKeys.FLAG_SECURE] ?: false,
                    onboardingDone = p[SettingsKeys.ONBOARDING] ?: false,
                    batteryAsked = p[SettingsKeys.BATTERY] ?: false,
                    notifyPrefs =
                        p[SettingsKeys.PREFS]?.let {
                            runCatching { RpJson.decodeFromString(NotifyPrefs.serializer(), it) }.getOrNull()
                        } ?: NotifyPrefs(),
                    mutedAgents = p[SettingsKeys.MUTED_AGENTS] ?: emptySet(),
                    appTheme = p[SettingsKeys.APP_THEME] ?: "auto",
                    chatFontScale = (p[SettingsKeys.CHAT_FONT_SCALE] ?: 1.0f).coerceIn(0.80f, 1.45f),
                    chatMessageSpacing = p[SettingsKeys.CHAT_MESSAGE_SPACING] ?: "normal",
                    selectedMeshMachine = p[SettingsKeys.MESH_SELECTED_MACHINE],
                    selectedMeshGroup = p[SettingsKeys.MESH_SELECTED_GROUP],
                )
            }

    override suspend fun setSelectedContext(v: String) = edit { it[SettingsKeys.SELECTED] = v }

    override suspend fun setDirectPush(v: Boolean) = edit { it[SettingsKeys.DIRECT] = v }

    override suspend fun setRequireUnlock(v: Boolean) = edit { it[SettingsKeys.REQUIRE_UNLOCK] = v }

    override suspend fun setCriticalAlways(v: Boolean) = edit { it[SettingsKeys.CRITICAL] = v }

    override suspend fun setAppLock(v: Boolean) = edit { it[SettingsKeys.APP_LOCK] = v }

    override suspend fun setFlagSecure(v: Boolean) = edit { it[SettingsKeys.FLAG_SECURE] = v }

    override suspend fun setOnboardingDone(v: Boolean) = edit { it[SettingsKeys.ONBOARDING] = v }

    override suspend fun setBatteryAsked(v: Boolean) = edit { it[SettingsKeys.BATTERY] = v }

    override suspend fun setAppTheme(v: String) = edit { it[SettingsKeys.APP_THEME] = v }

    override suspend fun setChatFontScale(v: Float) = edit { it[SettingsKeys.CHAT_FONT_SCALE] = v.coerceIn(0.80f, 1.45f) }

    override suspend fun setChatMessageSpacing(v: String) = edit { it[SettingsKeys.CHAT_MESSAGE_SPACING] = v }

    override suspend fun setMeshSelectedMachine(machineId: String?) =
        edit {
            if (machineId == null) it.remove(SettingsKeys.MESH_SELECTED_MACHINE) else it[SettingsKeys.MESH_SELECTED_MACHINE] = machineId
        }

    override suspend fun setMeshSelectedGroup(groupId: String?) =
        edit {
            if (groupId == null) it.remove(SettingsKeys.MESH_SELECTED_GROUP) else it[SettingsKeys.MESH_SELECTED_GROUP] = groupId
        }

    override suspend fun setNotifyPrefs(v: NotifyPrefs) =
        edit { it[SettingsKeys.PREFS] = RpJson.encodeToString(NotifyPrefs.serializer(), v) }

    override suspend fun setAgentMuted(
        agentId: String,
        muted: Boolean,
    ) = edit {
        val cur = it[SettingsKeys.MUTED_AGENTS] ?: emptySet()
        it[SettingsKeys.MUTED_AGENTS] = if (muted) cur + agentId else cur - agentId
    }

    override suspend fun wipe() = edit { it.clear() }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }
}
