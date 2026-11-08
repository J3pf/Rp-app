package dev.j3pf.rp.data.crypto

import dev.j3pf.rp.data.ports.PushEnvelopeCrypto
import dev.j3pf.rp.data.ports.SecureCrypto
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

/**
 * Multiplatform [PushEnvelopeCrypto] implementation backed by [Aes256Gcm].
 *
 * Uses the fixed protocol AAD `"rp1"`, 32-byte Base64 `pushKey`, 12-byte Base64 `nonce`, and
 * Base64 `ciphertext || 16B tag`. Returns `null` on any malformed input or authentication failure
 * without logging key material or ciphertext (S18).
 */
@OptIn(ExperimentalEncodingApi::class)
object CommonPushEnvelopeCrypto : PushEnvelopeCrypto {
    private val AAD = "rp1".encodeToByteArray()

    override fun decrypt(
        key: String,
        nonce: String,
        ciphertext: String,
    ): String? =
        runCatching {
            val keyBytes = Base64.decode(key)
            val nonceBytes = Base64.decode(nonce)
            val cipherBytes = Base64.decode(ciphertext)
            val plainBytes = Aes256Gcm.decrypt(keyBytes, nonceBytes, cipherBytes, AAD) ?: return null
            plainBytes.decodeToString()
        }.getOrNull()

    fun encrypt(
        key: String,
        nonce: ByteArray,
        plaintext: String,
    ): String {
        val keyBytes = Base64.decode(key)
        val cipherBytes = Aes256Gcm.encrypt(keyBytes, nonce, plaintext.encodeToByteArray(), AAD)
        return Base64.encode(cipherBytes)
    }
}

/**
 * Multiplatform [SecureCrypto] implementation backed by [Aes256Gcm] using field-scoped associated
 * data (`field.encodeToByteArray()`) and a 12-byte random nonce prefixed to the ciphertext.
 */
@OptIn(ExperimentalEncodingApi::class)
class AesGcmSecureCrypto(
    private val masterKey: ByteArray,
    private val random: Random = Random.Default,
) : SecureCrypto {
    init {
        require(masterKey.size == Aes256Gcm.KEY_BYTES) { "Master key must be 32 bytes" }
    }

    override fun encrypt(
        field: String,
        value: String,
    ): String {
        val nonce = random.nextBytes(Aes256Gcm.NONCE_BYTES)
        val ct = Aes256Gcm.encrypt(masterKey, nonce, value.encodeToByteArray(), field.encodeToByteArray())
        val combined = ByteArray(nonce.size + ct.size)
        nonce.copyInto(combined, 0)
        ct.copyInto(combined, nonce.size)
        return Base64.encode(combined)
    }

    override fun decrypt(
        field: String,
        value: String,
    ): String? =
        runCatching {
            val combined = Base64.decode(value)
            if (combined.size < Aes256Gcm.NONCE_BYTES + Aes256Gcm.TAG_BYTES) return null
            val nonce = combined.copyOfRange(0, Aes256Gcm.NONCE_BYTES)
            val ct = combined.copyOfRange(Aes256Gcm.NONCE_BYTES, combined.size)
            val plain = Aes256Gcm.decrypt(masterKey, nonce, ct, field.encodeToByteArray()) ?: return null
            plain.decodeToString()
        }.getOrNull()
}
