package dev.j3pf.rp.data.ports

import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.protocol.NotifyPrefs
import kotlinx.coroutines.flow.Flow

/**
 * Settings/theme port (S14 upgrade surface: the SAME `rp_settings` preferences file and key set
 * must survive the migration; exact keys pinned as ST-01 in docs/parity/o1-data.md).
 *
 * Android impl: DataStore `preferencesDataStore(name = "rp_settings")`. iOS/T3c:
 * `datastore-preferences-core` KMP over the same preferences file format.
 */
interface SettingsStore {
    val settings: Flow<AppSettings>

    suspend fun setSelectedContext(v: String)

    suspend fun setDirectPush(v: Boolean)

    suspend fun setRequireUnlock(v: Boolean)

    suspend fun setCriticalAlways(v: Boolean)

    suspend fun setAppLock(v: Boolean)

    suspend fun setFlagSecure(v: Boolean)

    suspend fun setOnboardingDone(v: Boolean)

    suspend fun setBatteryAsked(v: Boolean)

    suspend fun setNotifyPrefs(v: NotifyPrefs)

    suspend fun setAgentMuted(
        agentId: String,
        muted: Boolean,
    )

    suspend fun setAppTheme(v: String)

    suspend fun setChatFontScale(v: Float)

    suspend fun setChatMessageSpacing(v: String)

    suspend fun setMeshSelectedMachine(machineId: String?)

    suspend fun setMeshSelectedGroup(groupId: String?)

    /** Clears EVERY key (logout path, SE-05). */
    suspend fun wipe()
}
