package dev.j3pf.rp.platform

import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PushEnvelopeCryptoContractProbeTest {
    private val crypto = AndroidPushEnvelopeCrypto()
    private val key = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
    private val nonce = "AAECAwQFBgcICQoL"
    private val ciphertext = "PCCmaaqIsm/EJbWxk7lJT6/05lOVFSs1XEXfp3BTc5B82Wuo1oFvA560fS/kQVMNyQ=="
    private val plaintext = """{"promptId":"P1","agentId":"m:s"}"""
    private val b64 = Base64.getEncoder()

    @Test fun `same length but different nonce fails authentication`() {
        assertNull(crypto.decrypt(key, b64.encodeToString(ByteArray(12) { 7 }), ciphertext))
    }

    @Test fun `wrong key and malformed key return null`() {
        assertNull(crypto.decrypt(b64.encodeToString(ByteArray(32) { 9 }), nonce, ciphertext))
        assertNull(crypto.decrypt("xx", nonce, ciphertext))
        assertNull(crypto.decrypt("", nonce, ciphertext))
        assertNull(crypto.decrypt(b64.encodeToString(ByteArray(16)), nonce, ciphertext))
        assertNull(crypto.decrypt("!!!not-base64!!!", nonce, ciphertext))
    }

    @Test fun `malformed or truncated ciphertext returns null and never throws`() {
        assertNull(crypto.decrypt(key, nonce, ""))
        assertNull(crypto.decrypt(key, nonce, "@@@@"))
        assertNull(crypto.decrypt(key, nonce, b64.encodeToString(ByteArray(15))))
        assertNull(crypto.decrypt(key, "", ciphertext))
        assertNull(crypto.decrypt(key, "@@@@", ciphertext))
    }

    @Test fun `tampering the first byte and the tag both fail`() {
        val raw = Base64.getDecoder().decode(ciphertext)
        val first = raw.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertNull(crypto.decrypt(key, nonce, b64.encodeToString(first)))
        assertNull(crypto.decrypt(key, nonce, b64.encodeToString(raw.copyOf(raw.size - 1))))
    }

    @Test fun `a bad key does not poison the cached key spec`() {
        assertEquals(plaintext, crypto.decrypt(key, nonce, ciphertext))
        assertNull(crypto.decrypt(b64.encodeToString(ByteArray(32) { 9 }), nonce, ciphertext))
        assertEquals(plaintext, crypto.decrypt(key, nonce, ciphertext))
    }

    @Test fun `AAD is rigid rp1 only - field-name AAD does not authenticate`() {
        val k = Base64.getDecoder().decode(key)
        val n = Base64.getDecoder().decode(nonce)
        for (aad in listOf("pushKey", "rp2", "RP1", "")) {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(128, n))
            c.updateAAD(aad.toByteArray())
            val ct = b64.encodeToString(c.doFinal(plaintext.toByteArray()))
            assertNull(crypto.decrypt(key, nonce, ct), "AAD '$aad' must not authenticate")
        }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(128, n))
        c.updateAAD("rp1".toByteArray())
        assertEquals(plaintext, crypto.decrypt(key, nonce, b64.encodeToString(c.doFinal(plaintext.toByteArray()))))
    }
}
