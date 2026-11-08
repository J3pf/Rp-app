package dev.j3pf.rp.data.crypto

import dev.j3pf.rp.protocol.PushCrypto
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies byte-for-byte cross-implementation parity between the JVM/Android `javax.crypto`
 * [PushCrypto] helper in `:protocol` and the pure-Kotlin [CommonPushEnvelopeCrypto] in
 * `:data-core` (O1 Move 3, ADR 0003 §3).
 */
class PushEnvelopeCryptoJvmParityTest {
    @Test
    fun jvmPushCryptoAndCommonPushEnvelopeCryptoProduceIdenticalCiphertextAndPlaintext() {
        val keyBytes = ByteArray(32) { (it * 7 + 3).toByte() }
        val keyB64 = Base64.getEncoder().encodeToString(keyBytes)
        val nonce = ByteArray(12) { (it * 13 + 5).toByte() }
        val nonceB64 = Base64.getEncoder().encodeToString(nonce)
        val payload = """{"t":"prompt.opened","promptId":"01CrossVector","title":"¿Autorizar comando?"}"""

        val jvmCipherB64 = PushCrypto.encrypt(keyB64, nonce, payload)
        val commonCipherB64 = CommonPushEnvelopeCrypto.encrypt(keyB64, nonce, payload)

        // Exact wire ciphertext + tag byte equality
        assertEquals(jvmCipherB64, commonCipherB64)

        // Cross-decrypt in both directions
        assertEquals(payload, CommonPushEnvelopeCrypto.decrypt(keyB64, nonceB64, jvmCipherB64))
        assertEquals(payload, PushCrypto.decrypt(keyB64, nonceB64, commonCipherB64))
    }
}
