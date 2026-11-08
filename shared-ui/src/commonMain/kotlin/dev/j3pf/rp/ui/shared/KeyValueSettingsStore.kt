package dev.j3pf.rp.ui.shared

import dev.j3pf.rp.data.ports.SettingsStore
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.data.secure.InMemorySecretKeyValueBacking
import dev.j3pf.rp.data.secure.SecretKeyValueBacking
import dev.j3pf.rp.data.settings.SettingsKeys
import dev.j3pf.rp.protocol.NotifyPrefs
import dev.j3pf.rp.protocol.RpJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Multiplatform [SettingsStore] backed by [SecretKeyValueBacking] (in-memory for tests/JVM or
 * `NSUserDefaults` on iOS) using the canonical 15 [SettingsKeys] names.
 */
class KeyValueSettingsStore(
    private val backing: SecretKeyValueBacking = InMemorySecretKeyValueBacking(),
    private val onMutedAgentsChanged: (Set<String>) -> Unit = {},
) : SettingsStore {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    override val settings: StateFlow<AppSettings> = state

    override suspend fun setSelectedContext(v: String) {
        update(SettingsKeys.K_SELECTED_CONTEXT, v) { copy(selectedContext = v) }
    }

    override suspend fun setDirectPush(v: Boolean) {
        update(SettingsKeys.K_DIRECT_PUSH, v.toString()) { copy(directPush = v) }
    }

    override suspend fun setRequireUnlock(v: Boolean) {
        update(SettingsKeys.K_REQUIRE_UNLOCK, v.toString()) { copy(requireUnlockToApprove = v) }
    }

    override suspend fun setCriticalAlways(v: Boolean) {
        update(SettingsKeys.K_CRITICAL_ALWAYS, v.toString()) { copy(criticalAlways = v) }
    }

    override suspend fun setAppLock(v: Boolean) {
        update(SettingsKeys.K_APP_LOCK, v.toString()) { copy(appLock = v) }
    }

    override suspend fun setFlagSecure(v: Boolean) {
        update(SettingsKeys.K_FLAG_SECURE, v.toString()) { copy(flagSecure = v) }
    }

    override suspend fun setOnboardingDone(v: Boolean) {
        update(SettingsKeys.K_ONBOARDING_DONE, v.toString()) { copy(onboardingDone = v) }
    }

    override suspend fun setBatteryAsked(v: Boolean) {
        update(SettingsKeys.K_BATTERY_ASKED, v.toString()) { copy(batteryAsked = v) }
    }

    override suspend fun setNotifyPrefs(v: NotifyPrefs) {
        update(
            SettingsKeys.K_NOTIFY_PREFS,
            RpJson.encodeToString(NotifyPrefs.serializer(), v),
        ) { copy(notifyPrefs = v) }
    }

    override suspend fun setAgentMuted(
        agentId: String,
        muted: Boolean,
    ) {
        val next = if (muted) _state.value.mutedAgents + agentId else _state.value.mutedAgents - agentId
        backing.putString(SettingsKeys.K_MUTED_AGENTS, next.joinToString(","))
        _state.value = _state.value.copy(mutedAgents = next)
        onMutedAgentsChanged(next)
    }

    override suspend fun setAppTheme(v: String) {
        update(SettingsKeys.K_APP_THEME, v) { copy(appTheme = v) }
    }

    override suspend fun setChatFontScale(v: Float) {
        val clamped = v.coerceIn(0.80f, 1.45f)
        update(SettingsKeys.K_CHAT_FONT_SCALE, clamped.toString()) { copy(chatFontScale = clamped) }
    }

    override suspend fun setChatMessageSpacing(v: String) {
        update(SettingsKeys.K_CHAT_MESSAGE_SPACING, v) { copy(chatMessageSpacing = v) }
    }

    override suspend fun setMeshSelectedMachine(machineId: String?) {
        if (machineId == null) {
            backing.remove(SettingsKeys.K_MESH_SELECTED_MACHINE)
        } else {
            backing.putString(SettingsKeys.K_MESH_SELECTED_MACHINE, machineId)
        }
        _state.value = _state.value.copy(selectedMeshMachine = machineId)
    }

    override suspend fun setMeshSelectedGroup(groupId: String?) {
        if (groupId == null) {
            backing.remove(SettingsKeys.K_MESH_SELECTED_GROUP)
        } else {
            backing.putString(SettingsKeys.K_MESH_SELECTED_GROUP, groupId)
        }
        _state.value = _state.value.copy(selectedMeshGroup = groupId)
    }

    override suspend fun wipe() {
        SettingsKeys.ALL_NAMES.forEach { backing.remove(it) }
        _state.value = AppSettings()
        onMutedAgentsChanged(emptySet())
    }

    private fun update(
        key: String,
        rawValue: String,
        transform: AppSettings.() -> AppSettings,
    ) {
        backing.putString(key, rawValue)
        _state.value = _state.value.transform()
    }

    private fun load(): AppSettings {
        val notifyPrefs =
            backing.getString(SettingsKeys.K_NOTIFY_PREFS)?.let {
                runCatching { RpJson.decodeFromString(NotifyPrefs.serializer(), it) }.getOrNull()
            } ?: NotifyPrefs()
        val mutedRaw = backing.getString(SettingsKeys.K_MUTED_AGENTS).orEmpty()
        val mutedSet = if (mutedRaw.isBlank()) emptySet() else mutedRaw.split(",").filter { it.isNotBlank() }.toSet()
        val scale = backing.getString(SettingsKeys.K_CHAT_FONT_SCALE)?.toFloatOrNull() ?: 1.0f
        return AppSettings(
            selectedContext = backing.getString(SettingsKeys.K_SELECTED_CONTEXT) ?: AppSettings.ALL,
            directPush = backing.getString(SettingsKeys.K_DIRECT_PUSH)?.toBooleanStrictOrNull() ?: true,
            requireUnlockToApprove = backing.getString(SettingsKeys.K_REQUIRE_UNLOCK)?.toBooleanStrictOrNull() ?: true,
            criticalAlways = backing.getString(SettingsKeys.K_CRITICAL_ALWAYS)?.toBooleanStrictOrNull() ?: true,
            appLock = backing.getString(SettingsKeys.K_APP_LOCK)?.toBooleanStrictOrNull() ?: false,
            flagSecure = backing.getString(SettingsKeys.K_FLAG_SECURE)?.toBooleanStrictOrNull() ?: false,
            onboardingDone = backing.getString(SettingsKeys.K_ONBOARDING_DONE)?.toBooleanStrictOrNull() ?: false,
            batteryAsked = backing.getString(SettingsKeys.K_BATTERY_ASKED)?.toBooleanStrictOrNull() ?: false,
            notifyPrefs = notifyPrefs,
            mutedAgents = mutedSet,
            appTheme = backing.getString(SettingsKeys.K_APP_THEME) ?: "auto",
            chatFontScale = scale.coerceIn(0.80f, 1.45f),
            chatMessageSpacing = backing.getString(SettingsKeys.K_CHAT_MESSAGE_SPACING) ?: "normal",
            selectedMeshMachine = backing.getString(SettingsKeys.K_MESH_SELECTED_MACHINE),
            selectedMeshGroup = backing.getString(SettingsKeys.K_MESH_SELECTED_GROUP),
        )
    }
}
