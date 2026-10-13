package dev.j3pf.rp.protocol

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Descifrado AES‑256‑GCM del payload FCM (PROTOCOLO §8.1): `n` = nonce de 12 B (base64),
 * `c` = cifrado + tag de 16 B (base64), AAD = "rp1", clave = `pushKey` (32 B) del dispositivo.
 */
object PushCrypto {
    private val AAD = "rp1".toByteArray(Charsets.US_ASCII)
    private const val TAG_BITS = 128

    /** Devuelve el JSON en claro o `null` si la clave, el nonce o la autenticación no son válidos. */
    fun decrypt(pushKeyB64: String, nonceB64: String, cipherB64: String): String? = runCatching {
        val key = Base64.getDecoder().decode(pushKeyB64)
        require(key.size == 32)
        val nonce = Base64.getDecoder().decode(nonceB64)
        require(nonce.size == 12)
        val data = Base64.getDecoder().decode(cipherB64)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(AAD)
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrNull()

    /** Cifrado (usado por los tests y por la notificación de prueba local). */
    fun encrypt(pushKeyB64: String, nonce: ByteArray, plaintext: String): String {
        val key = Base64.getDecoder().decode(pushKeyB64)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(AAD)
        return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
    }
}
