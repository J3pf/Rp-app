package dev.j3pf.rp.data.secure

import dev.j3pf.rp.data.ports.SecretStore
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.Volatile

/**
 * Bridge for the Swift host (`iOSApp.swift`) to register a native `Security.framework`
 * (`kSecClassGenericPassword`) Keychain backing and shared-access-group `pushKey` mirror
 * (O1 Move 3, S12/S18).
 *
 * When running in headless unit tests before the Swift host registers its Keychain hook,
 * [SecretStoreIos] falls back to an volatile in-memory [InMemorySecretKeyValueBacking] so key
 * material and credentials are never written to unencrypted `NSUserDefaults` on disk (S18).
 */
object IosKeychainBridge {
    @Volatile
    var backingFactory: ((accessGroup: String?) -> SecretKeyValueBacking)? = null

    @Volatile
    var onPushKeyChanged: ((pushKey: String?, accessGroup: String?) -> Unit)? = null
}

/**
 * iOS implementation of [SecretStore] (O1 Move 3, ADR 0003 §3).
 *
 * Receives [accessGroup] as a constructor parameter (e.g. `"19410625785.dev.j3pf.rp.iosApp"`)
 * and delegates field-scoped AES-256-GCM encryption to [EncryptedKeyValueSecretStore].
 */
class SecretStoreIos(
    val accessGroup: String? = null,
    backing: SecretKeyValueBacking =
        IosKeychainBridge.backingFactory?.invoke(accessGroup) ?: InMemorySecretKeyValueBacking(),
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
