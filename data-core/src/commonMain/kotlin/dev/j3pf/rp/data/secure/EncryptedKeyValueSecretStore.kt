package dev.j3pf.rp.data.secure

import dev.j3pf.rp.data.crypto.Aes256Gcm
import dev.j3pf.rp.data.crypto.AesGcmSecureCrypto
import dev.j3pf.rp.data.ports.SecretStore
import dev.j3pf.rp.data.ports.SecureCrypto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

/**
 * Key-value storage abstraction for [EncryptedKeyValueSecretStore] (backed by Keychain on iOS or
 * in-memory/test storage in common tests).
 */
interface SecretKeyValueBacking {
    fun getString(key: String): String?

    fun putString(
        key: String,
        value: String,
    )

    fun remove(key: String)

    fun clear()
}

/**
 * In-memory [SecretKeyValueBacking] implementation for common tests and unsigned fallback.
 */
class InMemorySecretKeyValueBacking : SecretKeyValueBacking {
    private val map = mutableMapOf<String, String>()

    override fun getString(key: String): String? = map[key]

    override fun putString(
        key: String,
        value: String,
    ) {
        map[key] = value
    }

    override fun remove(key: String) {
        map.remove(key)
    }

    override fun clear() {
        map.clear()
    }
}

/**
 * Platform-neutral [SecretStore] implementation that encrypts each [Session] field with
 * field-scoped AEAD associated data (`relayId`, `relayUrl`, `relayName`, `deviceId`,
 * `deviceToken`, `pushKey`) via [SecureCrypto] and persists the ciphertext in [backing]
 * (O1 Move 3, S18).
 *
 * When a 32-byte master key is managed in [backing] (e.g. iOS Keychain `kSecClassGenericPassword`
 * under the S12 `keychain-access-groups` entitlement), [onPushKeyChanged] also mirrors the active
 * `pushKey` to the shared NSE keychain slot so the Notification Service Extension can decrypt
 * incoming `{k, n, c}` APNs envelopes.
 */
@OptIn(ExperimentalEncodingApi::class)
class EncryptedKeyValueSecretStore(
    private val backing: SecretKeyValueBacking,
    private val accessGroup: String? = null,
    private val random: Random = Random.Default,
    private val onPushKeyChanged: (pushKey: String?, accessGroup: String?) -> Unit = { _, _ -> },
) : SecretStore {
    @Volatile
    private var cachedCrypto: SecureCrypto? = null

    private fun getOrCreateCrypto(): SecureCrypto {
        cachedCrypto?.let { return it }
        val existingB64 = backing.getString(K_MASTER_KEY)
        val masterKey =
            existingB64?.let {
                runCatching { Base64.decode(it) }.getOrNull()?.takeIf { bytes -> bytes.size == Aes256Gcm.KEY_BYTES }
            } ?: random.nextBytes(Aes256Gcm.KEY_BYTES).also { generated ->
                backing.putString(K_MASTER_KEY, Base64.encode(generated))
            }
        return AesGcmSecureCrypto(masterKey, random).also { cachedCrypto = it }
    }

    private fun resetCorruptedMasterKey() {
        cachedCrypto = null
        backing.clear()
    }

    private val _session: MutableStateFlow<Session?> = MutableStateFlow(load())
    override val session: StateFlow<Session?> = _session.asStateFlow()

    override fun current(): Session? = _session.value

    override fun save(session: Session) {
        runCatching { writeEncryptedSession(session) }
            .recoverCatching {
                resetCorruptedMasterKey()
                writeEncryptedSession(session)
            }
        backing.putString(K_NSE_PUSH_KEY, session.pushKey)
        onPushKeyChanged(session.pushKey, accessGroup)
        _session.value = session
    }

    private fun writeEncryptedSession(session: Session) {
        val crypto = getOrCreateCrypto()
        backing.putString(K_RELAY_ID, crypto.encrypt(K_RELAY_ID, session.relayId))
        backing.putString(K_URL, crypto.encrypt(K_URL, session.relayUrl))
        backing.putString(K_NAME, crypto.encrypt(K_NAME, session.relayName))
        backing.putString(K_DEVICE_ID, crypto.encrypt(K_DEVICE_ID, session.deviceId))
        backing.putString(K_TOKEN, crypto.encrypt(K_TOKEN, session.deviceToken))
        backing.putString(K_PUSH_KEY, crypto.encrypt(K_PUSH_KEY, session.pushKey))
    }

    override fun clear() {
        val master = backing.getString(K_MASTER_KEY)
        backing.clear()
        if (master != null) {
            backing.putString(K_MASTER_KEY, master)
        }
        onPushKeyChanged(null, accessGroup)
        _session.value = null
    }

    override fun newRelayId(): String {
        val bytes = random.nextBytes(8)
        val sb = StringBuilder(16)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX_DIGITS[v ushr 4])
            sb.append(HEX_DIGITS[v and 0x0F])
        }
        return sb.toString()
    }

    private fun load(): Session? =
        runCatching {
            val crypto = getOrCreateCrypto()
            Session(
                relayId = getDecrypted(crypto, K_RELAY_ID) ?: return null,
                relayUrl = getDecrypted(crypto, K_URL) ?: return null,
                relayName = getDecrypted(crypto, K_NAME) ?: "",
                deviceId = getDecrypted(crypto, K_DEVICE_ID) ?: return null,
                deviceToken = getDecrypted(crypto, K_TOKEN) ?: return null,
                pushKey = getDecrypted(crypto, K_PUSH_KEY) ?: return null,
            )
        }.getOrNull()

    private fun getDecrypted(
        crypto: SecureCrypto,
        key: String,
    ): String? {
        val raw = backing.getString(key) ?: return null
        return crypto.decrypt(key, raw)
    }

    companion object {
        const val K_MASTER_KEY = "rp_master_key"
        const val K_RELAY_ID = "relayId"
        const val K_URL = "relayUrl"
        const val K_NAME = "relayName"
        const val K_DEVICE_ID = "deviceId"
        const val K_TOKEN = "deviceToken"
        const val K_PUSH_KEY = "pushKey"
        const val K_NSE_PUSH_KEY = "rp_nse_push_key"
        private const val HEX_DIGITS = "0123456789abcdef"
    }
}
