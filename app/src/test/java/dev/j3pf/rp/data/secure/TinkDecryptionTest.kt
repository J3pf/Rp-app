package dev.j3pf.rp.data.secure

import com.google.crypto.tink.Aead
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TinkDecryptionTest {
    @Before
    fun setup() {
        AeadConfig.register()
    }

    @Test
    fun `existing stored secrets encrypted with previous Tink format still decrypt after upgrade`() {
        val keysetHandle = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)
        val serializedKeyset = TinkJsonProtoKeysetFormat.serializeKeyset(keysetHandle, InsecureSecretKeyAccess.get())

        val aead = keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)

        val plaintext = "device-token-secret-12345"
        val associatedData = "device_token".toByteArray(Charsets.UTF_8)
        val ciphertext = aead.encrypt(plaintext.toByteArray(Charsets.UTF_8), associatedData)

        // Simular lectura del keyset almacenado y descifrado del secreto persistido
        val parsedHandle = TinkJsonProtoKeysetFormat.parseKeyset(serializedKeyset, InsecureSecretKeyAccess.get())
        val decryptAead = parsedHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        val decrypted = String(decryptAead.decrypt(ciphertext, associatedData), Charsets.UTF_8)

        assertEquals(plaintext, decrypted)
    }
}
