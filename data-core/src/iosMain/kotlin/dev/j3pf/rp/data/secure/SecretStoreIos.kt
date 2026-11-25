package dev.j3pf.rp.data.secure

import dev.j3pf.rp.data.crypto.AesGcmSecureCrypto
import dev.j3pf.rp.data.ports.SecretStore
import dev.j3pf.rp.data.ports.SecureCrypto
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.Volatile

/**
 * Bridge for the Swift host (`iOSApp.swift`) to register a native `Security.framework`
 * (`kSecClassGenericPassword`) Keychain backing and shared-access-group `pushKey` mirror
 * (O1 Move 3, S12/S18).
 *
 * When running in headless unit tests before the Swift host registers its Keychain hook,
 * [SecretStoreIos] falls back to a volatile in-memory [InMemorySecretKeyValueBacking] so key
 * material and credentials are never written to unencrypted `NSUserDefaults` on disk (S18).
 */
object IosKeychainBridge {
    @Volatile
    var backingFactory: ((accessGroup: String?) -> SecretKeyValueBacking)? = null

    @Volatile
    var onPushKeyChanged: ((pushKey: String?, accessGroup: String?) -> Unit)? = null
}

/**
 * Bridge for the Swift host (`iOSApp.swift`) to register an Apple `CryptoKit` (`AES.GCM`)
 * [SecureCrypto] implementation for [SecretStoreIos].
 *
 * Registered ciphers must preserve the exact wire format of [AesGcmSecureCrypto]:
 * Base64-encoded `nonce (12 bytes) || ciphertext || tag (16 bytes)` with AAD = UTF-8 bytes of the
 * field name. When unregistered (e.g. headless unit tests), [SecretStoreIos] falls back to
 * pure-Kotlin [AesGcmSecureCrypto].
 */
object IosNativeAeadBridge {
    @Volatile
    var cryptoFactory: ((masterKey: ByteArray) -> SecureCrypto)? = null
}

/**
 * iOS implementation of [SecretStore] (O1 Move 3, ADR 0003 §3).
 *
 * Receives [accessGroup] as a constructor parameter (e.g. resolved from `RPKeychainAccessGroup`
 * in Info.plist) and delegates field-scoped AES-256-GCM encryption to
 * [EncryptedKeyValueSecretStore] using [IosNativeAeadBridge] when registered or pure-Kotlin
 * [AesGcmSecureCrypto] as fallback. Backing implementations should signal transient Keychain errors
 * (such as `errSecInteractionNotAllowed` while locked) by returning
 * [SecretReadResult.TransientError], returning
 * [EncryptedKeyValueSecretStore.TRANSIENT_READ_ERROR_SENTINEL], or throwing so persisted sessions
 * are never wiped on transient read failures.
 */
class SecretStoreIos(
    val accessGroup: String? = null,
    backing: SecretKeyValueBacking =
        IosKeychainBridge.backingFactory?.invoke(accessGroup) ?: InMemorySecretKeyValueBacking(),
    cryptoFactory: (masterKey: ByteArray) -> SecureCrypto = { masterKey ->
        IosNativeAeadBridge.cryptoFactory?.invoke(masterKey) ?: AesGcmSecureCrypto(masterKey)
    },
) : SecretStore {
    private val delegate =
        EncryptedKeyValueSecretStore(
            backing = backing,
            accessGroup = accessGroup,
            onPushKeyChanged = { pushKey, group ->
                IosKeychainBridge.onPushKeyChanged?.invoke(pushKey, group)
            },
            cryptoFactory = cryptoFactory,
        )

    override val session: StateFlow<Session?> get() = delegate.session

    override fun current(): Session? = delegate.current()

    override fun save(session: Session) = delegate.save(session)

    override fun clear() = delegate.clear()

    override fun newRelayId(): String = delegate.newRelayId()
}
