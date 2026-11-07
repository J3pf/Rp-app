package dev.j3pf.rp.data.ports

/**
 * Decrypts the versioned push envelope body used by FCM and APNs.
 *
 * The key, nonce, and ciphertext are Base64-encoded wire values. The v1 envelope's AES-GCM
 * associated data is fixed by the protocol (`rp1`). Malformed input or failed authentication
 * returns null rather than throwing.
 */
interface PushEnvelopeCrypto {
    fun decrypt(
        key: String,
        nonce: String,
        ciphertext: String,
    ): String?
}
