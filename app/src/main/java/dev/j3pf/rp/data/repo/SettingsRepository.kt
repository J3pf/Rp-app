package dev.j3pf.rp.data.repo

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.protocol.NotifyPrefs
import dev.j3pf.rp.protocol.RpJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.rpDataStore by preferencesDataStore(name = "rp_settings")

/** Categorías de notificación (coinciden con los canales por PC). */
object NotifyCategory {
    const val APPROVALS = "approvals"
    const val QUESTIONS = "questions"
    const val TERMINAL = "terminal"
    const val FINISHED = "finished"
    const val ERRORS = "errors"
    val all = listOf(APPROVALS, QUESTIONS, TERMINAL, FINISHED, ERRORS)
}

data class AppSettings(
    /** `"all"` o el `context` de la PC seleccionada en el selector superior. */
    val selectedContext: String = ALL,
    val directPush: Boolean = true,
    val requireUnlockToApprove: Boolean = false,
    val criticalAlways: Boolean = true,
    val appLock: Boolean = false,
    val flagSecure: Boolean = false,
    val onboardingDone: Boolean = false,
    val batteryAsked: Boolean = false,
    val notifyPrefs: NotifyPrefs = NotifyPrefs(),
    /** Agentes silenciados (preferencia local por agente). */
    val mutedAgents: Set<String> = emptySet(),
    val appTheme: String = "auto",
) {
    companion object {
        const val ALL = "all"
    }
}

@Singleton
class SettingsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val store get() = context.rpDataStore

        val settings: Flow<AppSettings> =
            store.data.map { p ->
                AppSettings(
                    selectedContext = p[SELECTED] ?: AppSettings.ALL,
                    directPush = p[DIRECT] ?: true,
                    requireUnlockToApprove = p[REQUIRE_UNLOCK] ?: false,
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
                )
            }

        suspend fun current(): AppSettings = settings.first()

        suspend fun setSelectedContext(v: String) = edit { it[SELECTED] = v }

        suspend fun setDirectPush(v: Boolean) = edit { it[DIRECT] = v }

        suspend fun setRequireUnlock(v: Boolean) = edit { it[REQUIRE_UNLOCK] = v }

        suspend fun setCriticalAlways(v: Boolean) = edit { it[CRITICAL] = v }

        suspend fun setAppLock(v: Boolean) = edit { it[APP_LOCK] = v }

        suspend fun setFlagSecure(v: Boolean) = edit { it[FLAG_SECURE] = v }

        suspend fun setOnboardingDone(v: Boolean) = edit { it[ONBOARDING] = v }

        suspend fun setBatteryAsked(v: Boolean) = edit { it[BATTERY] = v }

        suspend fun setAppTheme(v: String) = edit { it[APP_THEME] = v }

        suspend fun setNotifyPrefs(v: NotifyPrefs) = edit { it[PREFS] = RpJson.encodeToString(NotifyPrefs.serializer(), v) }

        suspend fun setAgentMuted(
            agentId: String,
            muted: Boolean,
        ) = edit {
            val cur = it[MUTED_AGENTS] ?: emptySet()
            it[MUTED_AGENTS] = if (muted) cur + agentId else cur - agentId
        }

        suspend fun wipe() = edit { it.clear() }

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
        }
    }
