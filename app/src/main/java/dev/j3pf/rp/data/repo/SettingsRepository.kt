package dev.j3pf.rp.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.data.ports.SettingsStore
import dev.j3pf.rp.protocol.NotifyPrefs
import dev.j3pf.rp.protocol.RpJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.rpDataStore by preferencesDataStore(name = "rp_settings")

@Singleton
class SettingsRepository(
    private val store: DataStore<Preferences>,
) : SettingsStore {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.rpDataStore)

    override val settings: Flow<AppSettings> =
        store.data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }.map { p ->
                AppSettings(
                    selectedContext = p[SELECTED] ?: AppSettings.ALL,
                    directPush = p[DIRECT] ?: true,
                    requireUnlockToApprove = p[REQUIRE_UNLOCK] ?: true,
                    criticalAlways = p[CRITICAL] ?: true,
                    appLock = p[APP_LOCK] ?: false,
                    flagSecure = p[FLAG_SECURE] ?: false,
                    onboardingDone = p[ONBOARDING] ?: false,
                    batteryAsked = p[BATTERY] ?: false,
                    notifyPrefs =
                        p[PREFS]?.let { runCatching { RpJson.decodeFromString(NotifyPrefs.serializer(), it) }.getOrNull() }
                            ?: NotifyPrefs(),
                    mutedAgents = p[MUTED_AGENTS] ?: emptySet(),
                    appTheme = p[APP_THEME] ?: "auto",
                    chatFontScale = (p[CHAT_FONT_SCALE] ?: 1.0f).coerceIn(0.80f, 1.45f),
                    chatMessageSpacing = p[CHAT_MESSAGE_SPACING] ?: "normal",
                    selectedMeshMachine = p[MESH_SELECTED_MACHINE],
                    selectedMeshGroup = p[MESH_SELECTED_GROUP],
                )
            }

    suspend fun current(): AppSettings = settings.first()

    override suspend fun setSelectedContext(v: String) = edit { it[SELECTED] = v }

    override suspend fun setDirectPush(v: Boolean) = edit { it[DIRECT] = v }

    override suspend fun setRequireUnlock(v: Boolean) = edit { it[REQUIRE_UNLOCK] = v }

    override suspend fun setCriticalAlways(v: Boolean) = edit { it[CRITICAL] = v }

    override suspend fun setAppLock(v: Boolean) = edit { it[APP_LOCK] = v }

    override suspend fun setFlagSecure(v: Boolean) = edit { it[FLAG_SECURE] = v }

    override suspend fun setOnboardingDone(v: Boolean) = edit { it[ONBOARDING] = v }

    override suspend fun setBatteryAsked(v: Boolean) = edit { it[BATTERY] = v }

    override suspend fun setAppTheme(v: String) = edit { it[APP_THEME] = v }

    override suspend fun setChatFontScale(v: Float) = edit { it[CHAT_FONT_SCALE] = v.coerceIn(0.80f, 1.45f) }

    override suspend fun setChatMessageSpacing(v: String) = edit { it[CHAT_MESSAGE_SPACING] = v }

    override suspend fun setMeshSelectedMachine(machineId: String?) =
        edit {
            if (machineId == null) it.remove(MESH_SELECTED_MACHINE) else it[MESH_SELECTED_MACHINE] = machineId
        }

    override suspend fun setMeshSelectedGroup(groupId: String?) =
        edit {
            if (groupId == null) it.remove(MESH_SELECTED_GROUP) else it[MESH_SELECTED_GROUP] = groupId
        }

    override suspend fun setNotifyPrefs(v: NotifyPrefs) = edit { it[PREFS] = RpJson.encodeToString(NotifyPrefs.serializer(), v) }

    override suspend fun setAgentMuted(
        agentId: String,
        muted: Boolean,
    ) = edit {
        val cur = it[MUTED_AGENTS] ?: emptySet()
        it[MUTED_AGENTS] = if (muted) cur + agentId else cur - agentId
    }

    override suspend fun wipe() = edit { it.clear() }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    private companion object {
        val SELECTED = stringPreferencesKey("selectedContext")
        val DIRECT = booleanPreferencesKey("directPush")
        val REQUIRE_UNLOCK = booleanPreferencesKey("requireUnlock")
        val CRITICAL = booleanPreferencesKey("criticalAlways")
        val APP_LOCK = booleanPreferencesKey("appLock")
        val FLAG_SECURE = booleanPreferencesKey("flagSecure")
        val ONBOARDING = booleanPreferencesKey("onboardingDone")
        val BATTERY = booleanPreferencesKey("batteryAsked")
        val PREFS = stringPreferencesKey("notifyPrefs")
        val MUTED_AGENTS = stringSetPreferencesKey("mutedAgents")
        val APP_THEME = stringPreferencesKey("appTheme")
        val CHAT_FONT_SCALE = floatPreferencesKey("chatFontScale")
        val CHAT_MESSAGE_SPACING = stringPreferencesKey("chatMessageSpacing")
        val MESH_SELECTED_MACHINE = stringPreferencesKey("mesh_selected_machine")
        val MESH_SELECTED_GROUP = stringPreferencesKey("mesh_selected_group")
    }
}
