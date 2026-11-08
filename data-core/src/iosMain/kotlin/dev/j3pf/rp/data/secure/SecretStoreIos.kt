package dev.j3pf.rp.data.secure

import dev.j3pf.rp.data.ports.SecretStore
import kotlinx.coroutines.flow.StateFlow
import platform.Foundation.NSUserDefaults
import kotlin.concurrent.Volatile

/**
 * Bridge for the Swift host (`iOSApp.swift`) to register a native `Security.framework`
 * (`kSecClassGenericPassword`) Keychain backing and shared-access-group `pushKey` mirror
 * (O1 Move 3, S12/S18).
 *
 * When running in an unsigned iOS Simulator build (`CODE_SIGNING_ALLOWED=NO` in `.github/workflows/ios.yml`),
 * or before the Swift host registers its Keychain hook, [SecretStoreIos] falls back seamlessly to
 * an app-scoped backing while keeping every [Session] field AES-256-GCM encrypted under a
 * randomly generated 32-byte master key with field-scoped associated data.
 */
object IosKeychainBridge {
    @Volatile
    var backingFactory: ((accessGroup: String?) -> SecretKeyValueBacking)? = null

    @Volatile
    var onPushKeyChanged: ((pushKey: String?, accessGroup: String?) -> Unit)? = null
}

private class NsUserDefaultsSecretBacking : SecretKeyValueBacking {
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
    private val knownKeys =
        listOf(
            EncryptedKeyValueSecretStore.K_MASTER_KEY,
            EncryptedKeyValueSecretStore.K_RELAY_ID,
            EncryptedKeyValueSecretStore.K_URL,
            EncryptedKeyValueSecretStore.K_NAME,
            EncryptedKeyValueSecretStore.K_DEVICE_ID,
            EncryptedKeyValueSecretStore.K_TOKEN,
            EncryptedKeyValueSecretStore.K_PUSH_KEY,
            EncryptedKeyValueSecretStore.K_NSE_PUSH_KEY,
        )

    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(
        key: String,
        value: String,
    ) {
        defaults.setObject(value, forKey = key)
    }

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }

    override fun clear() {
        knownKeys.forEach { defaults.removeObjectForKey(it) }
    }
}

/**
 * iOS implementation of [SecretStore] (O1 Move 3, ADR 0003 §3).
 *
 * Receives [accessGroup] as a constructor parameter (e.g. `"$(AppIdentifierPrefix)dev.j3pf.rp.iosApp"`)
 * and delegates field-scoped AES-256-GCM encryption to [EncryptedKeyValueSecretStore].
 */
class SecretStoreIos(
    val accessGroup: String? = null,
    backing: SecretKeyValueBacking =
        IosKeychainBridge.backingFactory?.invoke(accessGroup) ?: NsUserDefaultsSecretBacking(),
) : SecretStore {
    private val delegate =
        EncryptedKeyValueSecretStore(
            backing = backing,
            accessGroup = accessGroup,
            onPushKeyChanged = { pushKey, group ->
                IosKeychainBridge.onPushKeyChanged?.invoke(pushKey, group)
            },
        )

    override val session: StateFlow<Session?> get() = delegate.session

    override fun current(): Session? = delegate.current()

    override fun save(session: Session) = delegate.save(session)

    override fun clear() = delegate.clear()

    override fun newRelayId(): String = delegate.newRelayId()
}
