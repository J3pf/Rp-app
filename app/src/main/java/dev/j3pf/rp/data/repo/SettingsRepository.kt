package dev.j3pf.rp.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.data.ports.SettingsStore
import dev.j3pf.rp.data.settings.DataStoreSettingsStore
import dev.j3pf.rp.data.settings.SettingsKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// MOVE 2 thin delegate (ADR 0003 §2): the preference name comes from the SINGLE source of
// truth (SettingsKeys.PREFS_NAME) so the C1 identity pin owns renames; the file layout and
// protobuf encoding are unchanged on Android. "rp_settings" lives inside the common object.
private val Context.rpDataStore by preferencesDataStore(name = SettingsKeys.PREFS_NAME)

/**
 * SAME public API as before Move 2 (S5/S15: zero behavioral delta for the 20+ consumers);
 * the body now delegates to the common `DataStoreSettingsStore` in `:data-core` (all 15
 * set* members, wipe(), and the settings Flow come from the `by delegate` forwarding).
 * The `dev.j3pf.rp.data.repo.AppSettings` type lives in `:relay-client` commonMain.
 */
@Singleton
class SettingsRepository private constructor(
    private val delegate: SettingsStore,
) : SettingsStore by delegate {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(DataStoreSettingsStore(context.rpDataStore))

    constructor(store: DataStore<Preferences>) : this(DataStoreSettingsStore(store))

    override val settings: Flow<AppSettings> = delegate.settings

    suspend fun current(): AppSettings = settings.first()
}
