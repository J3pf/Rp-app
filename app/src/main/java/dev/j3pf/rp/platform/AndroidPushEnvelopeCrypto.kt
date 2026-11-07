package dev.j3pf.rp.platform

import dev.j3pf.rp.data.ports.PushEnvelopeCrypto
import dev.j3pf.rp.protocol.PushCrypto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android impl of [PushEnvelopeCrypto] over the protocol JVM AES-256-GCM helper.
 * The helper preserves its cached `@Volatile` key spec and fixed `"rp1"` AAD; this adapter
 * never logs plaintext credentials (S18).
 */
@Singleton
class AndroidPushEnvelopeCrypto
    @Inject
    constructor() : PushEnvelopeCrypto {
        override fun decrypt(
            key: String,
            nonce: String,
            ciphertext: String,
        ): String? = PushCrypto.decrypt(key, nonce, ciphertext)

        /** Encrypts a message with its per-message nonce. */
        fun encrypt(
            key: String,
            nonce: ByteArray,
            plaintext: String,
        ): String = PushCrypto.encrypt(key, nonce, plaintext)
    }
