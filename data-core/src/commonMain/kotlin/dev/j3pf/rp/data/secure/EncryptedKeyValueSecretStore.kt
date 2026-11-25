package dev.j3pf.rp.data.secure

import dev.j3pf.rp.data.crypto.Aes256Gcm
import dev.j3pf.rp.data.crypto.AesGcmSecureCrypto
import dev.j3pf.rp.data.crypto.platformSecureRandom
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
 * Read outcome from [SecretKeyValueBacking] distinguishing absent entries from transient
 * storage errors (such as a locked iOS Keychain returning `errSecInteractionNotAllowed`).
 */
sealed interface SecretReadResult {
    data class Found(
        val value: String,
    ) : SecretReadResult

    data object NotFound : SecretReadResult

    data class TransientError(
        val cause: Throwable? = null,
    ) : SecretReadResult
}

/**
 * Key-value storage abstraction for [EncryptedKeyValueSecretStore] (backed by Keychain on iOS or
 * in-memory/test storage in common tests).
 */
interface SecretKeyValueBacking {
    fun getString(key: String): String?

    fun read(key: String): SecretReadResult =
        try {
            when (val value = getString(key)) {
                null -> SecretReadResult.NotFound
                EncryptedKeyValueSecretStore.TRANSIENT_READ_ERROR_SENTINEL -> SecretReadResult.TransientError()
                else -> SecretReadResult.Found(value)
            }
        } catch (t: Throwable) {
            SecretReadResult.TransientError(t)
        }

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
    @Volatile
    private var map: Map<String, String> = emptyMap()

    override fun getString(key: String): String? = map[key]

    override fun putString(
        key: String,
        value: String,
    ) {
        map = map + (key to value)
    }

    override fun remove(key: String) {
        map = map - key
    }

    override fun clear() {
        map = emptyMap()
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
    private val random: Random = platformSecureRandom(),
    private val onPushKeyChanged: (pushKey: String?, accessGroup: String?) -> Unit = { _, _ -> },
    private val cryptoFactory: (masterKey: ByteArray) -> SecureCrypto = { masterKey ->
        AesGcmSecureCrypto(masterKey, random)
    },
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
        return cryptoFactory(masterKey).also { cachedCrypto = it }
    }

    private fun resetCorruptedMasterKey() {
        cachedCrypto = null
        backing.clear()
    }

    private fun clearEncryptedSessionEntries() {
        SESSION_KEYS.forEach { backing.remove(it) }
    }

    private val _session: MutableStateFlow<Session?> = MutableStateFlow(load())
    override val session: StateFlow<Session?> = _session.asStateFlow()

    override fun current(): Session? = _session.value

    override fun save(session: Session) {
        runCatching { writeEncryptedSession(session) }
            .recoverCatching {
                resetCorruptedMasterKey()
                writeEncryptedSession(session)
            }.getOrThrow()
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

    private fun load(): Session? {
        val rawEntries = mutableMapOf<String, String?>()
        for (key in SESSION_KEYS) {
            when (val res = backing.read(key)) {
                is SecretReadResult.Found -> rawEntries[key] = res.value
                SecretReadResult.NotFound -> rawEntries[key] = null
                is SecretReadResult.TransientError -> return null
            }
        }
        val hasStoredEntries = rawEntries.values.any { it != null }
        if (!hasStoredEntries) return null

        val crypto =
            when (val masterResult = loadExistingCrypto()) {
                is MasterKeyRead.Ready -> masterResult.crypto
                MasterKeyRead.TransientError -> return null
                MasterKeyRead.CorruptedOrMissing -> {
                    runCatching { clearEncryptedSessionEntries() }
                    return null
                }
            }

        return try {
            val relayId = decryptValue(crypto, K_RELAY_ID, rawEntries[K_RELAY_ID])
            val relayUrl = decryptValue(crypto, K_URL, rawEntries[K_URL])
            val relayNameRaw = rawEntries[K_NAME]
            val relayName =
                if (relayNameRaw == null) {
                    ""
                } else {
                    decryptValue(crypto, K_NAME, relayNameRaw) ?: run {
                        runCatching { clearEncryptedSessionEntries() }
                        return null
                    }
                }
            val deviceId = decryptValue(crypto, K_DEVICE_ID, rawEntries[K_DEVICE_ID])
            val deviceToken = decryptValue(crypto, K_TOKEN, rawEntries[K_TOKEN])
            val pushKey = decryptValue(crypto, K_PUSH_KEY, rawEntries[K_PUSH_KEY])
            if (relayId == null || relayUrl == null || deviceId == null || deviceToken == null || pushKey == null) {
                runCatching { clearEncryptedSessionEntries() }
                return null
            }
            Session(
                relayId = relayId,
                relayUrl = relayUrl,
                relayName = relayName,
                deviceId = deviceId,
                deviceToken = deviceToken,
                pushKey = pushKey,
            )
        } catch (_: IllegalArgumentException) {
            runCatching { clearEncryptedSessionEntries() }
            null
        } catch (_: Throwable) {
            // Unexpected crypto corruption on already-read ciphertext; clear corrupted session fields.
            runCatching { clearEncryptedSessionEntries() }
            null
        }
    }

    private sealed interface MasterKeyRead {
        data class Ready(
            val crypto: SecureCrypto,
        ) : MasterKeyRead

        data object TransientError : MasterKeyRead

        data object CorruptedOrMissing : MasterKeyRead
    }

    private fun loadExistingCrypto(): MasterKeyRead {
        cachedCrypto?.let { return MasterKeyRead.Ready(it) }
        return when (val res = backing.read(K_MASTER_KEY)) {
            is SecretReadResult.TransientError -> MasterKeyRead.TransientError
            SecretReadResult.NotFound -> MasterKeyRead.CorruptedOrMissing
            is SecretReadResult.Found -> {
                val masterKey =
                    runCatching { Base64.decode(res.value) }
                        .getOrNull()
                        ?.takeIf { bytes -> bytes.size == Aes256Gcm.KEY_BYTES }
                        ?: return MasterKeyRead.CorruptedOrMissing
                val crypto = cryptoFactory(masterKey).also { cachedCrypto = it }
                MasterKeyRead.Ready(crypto)
            }
        }
    }

    private fun decryptValue(
        crypto: SecureCrypto,
        key: String,
        raw: String?,
    ): String? {
        if (raw == null) return null
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
        const val TRANSIENT_READ_ERROR_SENTINEL = "__RP_TRANSIENT_KEYCHAIN_ERROR__"
        private val SESSION_KEYS =
            listOf(
                K_RELAY_ID,
                K_URL,
                K_NAME,
                K_DEVICE_ID,
                K_TOKEN,
                K_PUSH_KEY,
            )
        private const val HEX_DIGITS = "0123456789abcdef"
    }
}
