package dev.j3pf.rp.protocol

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PushCryptoTest {
    // Vector generado de forma independiente (Python `cryptography`, AESGCM, aad="rp1"):
    // key = bytes 0..31, nonce = bytes 0..11.
    private val key = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
    private val nonce = "AAECAwQFBgcICQoL"
    private val cipher = "PCCmaaqIsm/EJbWxk7lJT6/05lOVFSs1XEXfp3BTc5B82Wuo1oFvA560fS/kQVMNyQ=="
    private val plain = """{"promptId":"P1","agentId":"m:s"}"""

    @Test
    fun `descifra el vector compartido`() {
        assertEquals(plain, PushCrypto.decrypt(key, nonce, cipher))
    }

    @Test
    fun `cifrar y descifrar son inversos`() {
        val n = ByteArray(12) { it.toByte() }
        val c = PushCrypto.encrypt(key, n, "hola ñandú ✓")
        assertEquals("hola ñandú ✓", PushCrypto.decrypt(key, Base64.getEncoder().encodeToString(n), c))
    }

    @Test
    fun `clave incorrecta o dato manipulado falla en silencio`() {
        val other = Base64.getEncoder().encodeToString(ByteArray(32) { 9 })
        assertNull(PushCrypto.decrypt(other, nonce, cipher))
        val bytes = Base64.getDecoder().decode(cipher).also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertNull(PushCrypto.decrypt(key, nonce, Base64.getEncoder().encodeToString(bytes)))
        assertNull(PushCrypto.decrypt(key, "corto", cipher))
        assertNull(PushCrypto.decrypt("xx", nonce, cipher))
    }
}
