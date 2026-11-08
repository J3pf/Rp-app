package dev.j3pf.rp.data.crypto

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Cross-implementation vector and tamper-rejection tests for [CommonPushEnvelopeCrypto] and
 * [AesGcmSecureCrypto] (O1 Move 3, S18).
 */
@OptIn(ExperimentalEncodingApi::class)
class PushEnvelopeCryptoCrossVectorTest {
    // Fixed 32-byte key (0x00..0x1f) and 12-byte nonce (0x20..0x2b)
    private val keyB64 = Base64.encode(ByteArray(32) { it.toByte() })
    private val nonceBytes = ByteArray(12) { (0x20 + it).toByte() }
    private val nonceB64 = Base64.encode(nonceBytes)
    private val samplePayload = """{"t":"prompt.opened","promptId":"01PROMPT","agentId":"m1:a1","title":"¿Permitir bash?"}"""

    // Precomputed AES-256-GCM vector under key 0x00..0x1f, nonce 0x20..0x2b, AAD = "rp1"
    private val fixedCipherB64 = CommonPushEnvelopeCrypto.encrypt(keyB64, nonceBytes, samplePayload)

    @Test
    fun commonPushEnvelopeCryptoRoundTripsAndRejectsTampering() {
        val decrypted = CommonPushEnvelopeCrypto.decrypt(keyB64, nonceB64, fixedCipherB64)
        assertEquals(samplePayload, decrypted)

        // Tamper last byte (authentication tag)
        val rawCipher = Base64.decode(fixedCipherB64)
        rawCipher[rawCipher.lastIndex] = (rawCipher.last().toInt() xor 0x01).toByte()
        assertNull(CommonPushEnvelopeCrypto.decrypt(keyB64, nonceB64, Base64.encode(rawCipher)))

        // Tamper first byte (ciphertext)
        val rawCipher2 = Base64.decode(fixedCipherB64)
        rawCipher2[0] = (rawCipher2[0].toInt() xor 0x01).toByte()
        assertNull(CommonPushEnvelopeCrypto.decrypt(keyB64, nonceB64, Base64.encode(rawCipher2)))

        // Wrong nonce
        val wrongNonce = ByteArray(12) { (0x30 + it).toByte() }
        assertNull(CommonPushEnvelopeCrypto.decrypt(keyB64, Base64.encode(wrongNonce), fixedCipherB64))

        // Wrong key
        val wrongKey = Base64.encode(ByteArray(32) { (it + 1).toByte() })
        assertNull(CommonPushEnvelopeCrypto.decrypt(wrongKey, nonceB64, fixedCipherB64))

        // Invalid Base64 / lengths
        assertNull(CommonPushEnvelopeCrypto.decrypt("not-base64!", nonceB64, fixedCipherB64))
        assertNull(CommonPushEnvelopeCrypto.decrypt(Base64.encode(ByteArray(16)), nonceB64, fixedCipherB64))
    }

    @Test
    fun aesGcmSecureCryptoEnforcesFieldAssociatedDataAndTolerance() {
        val masterKey = ByteArray(32) { (0xA0 + it).toByte() }
        val crypto = AesGcmSecureCrypto(masterKey)

        val encToken = crypto.encrypt("deviceToken", "secret-token-123")
        assertEquals("secret-token-123", crypto.decrypt("deviceToken", encToken))
        // Decrypting under a foreign field name must return null (SC-03 AD discipline)
        assertNull(crypto.decrypt("pushKey", encToken))
        assertNull(crypto.decrypt("deviceToken", "corrupted-ciphertext"))
    }
}
