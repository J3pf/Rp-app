package dev.j3pf.rp.platform

import org.junit.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PushEnvelopeCryptoVectorTest {
    private val crypto = AndroidPushEnvelopeCrypto()

    // Python cryptography AESGCM vector: AAD "rp1", key bytes 0..31, nonce bytes 0..11.
    private val key = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
    private val nonce = "AAECAwQFBgcICQoL"
    private val ciphertext = "PCCmaaqIsm/EJbWxk7lJT6/05lOVFSs1XEXfp3BTc5B82Wuo1oFvA560fS/kQVMNyQ=="
    private val plaintext = """{"promptId":"P1","agentId":"m:s"}"""

    @Test
    fun `decrypts the independently generated AES GCM push vector`() {
        assertEquals(plaintext, crypto.decrypt(key, nonce, ciphertext))
    }

    @Test
    fun `tampering with the final ciphertext byte fails authentication`() {
        val tampered = Base64.getDecoder().decode(ciphertext).also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        }
        val tamperedCiphertext = Base64.getEncoder().encodeToString(tampered)

        assertNull(crypto.decrypt(key, nonce, tamperedCiphertext))
    }

    @Test
    fun `wrong length nonce returns null`() {
        // PushCrypto.decrypt returns null when the decoded nonce is not exactly 12 bytes.
        assertNull(crypto.decrypt(key, Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)), ciphertext))
    }

    @Test
    fun `encrypt and decrypt roundtrip with a per message nonce`() {
        val nonceBytes = ByteArray(12) { (it + 1).toByte() }
        val nonceB64 = Base64.getEncoder().encodeToString(nonceBytes)
        val encrypted = crypto.encrypt(key, nonceBytes, plaintext)

        assertEquals(plaintext, crypto.decrypt(key, nonceB64, encrypted))
    }
}
