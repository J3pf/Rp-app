package dev.j3pf.rp.data.secure

import com.google.crypto.tink.Aead
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import dev.j3pf.rp.util.BiometricGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TinkDecryptionTest {
    @Before
    fun setup() {
        AeadConfig.register()
    }

    @Test
    fun `existing stored secrets encrypted with previous Tink format still decrypt after upgrade`() {
        val keysetHandle = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM, RegistryConfiguration.get())
        val serializedKeyset =
            TinkJsonProtoKeysetFormat.serializeKeyset(keysetHandle, RegistryConfiguration.get(), InsecureSecretKeyAccess.get())

        val aead = keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)

        val plaintext = "device-token-secret-12345"
        val associatedData = "device_token".toByteArray(Charsets.UTF_8)
        val ciphertext = aead.encrypt(plaintext.toByteArray(Charsets.UTF_8), associatedData)

        // Simular lectura del keyset almacenado y descifrado del secreto persistido
        val parsedHandle =
            TinkJsonProtoKeysetFormat.parseKeyset(serializedKeyset, RegistryConfiguration.get(), InsecureSecretKeyAccess.get())
        val decryptAead = parsedHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        val decrypted = String(decryptAead.decrypt(ciphertext, associatedData), Charsets.UTF_8)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `biometric gate is not fresh until explicit action authentication and respects monotonic validity`() {
        val gate = BiometricGate()
        assertFalse(gate.isFresh(now = 0L))
        assertFalse(gate.isFresh(now = 1_000L))

        gate.recordSuccessForTest(atElapsedRealtimeMs = 10_000L)
        assertTrue(gate.isFresh(now = 10_000L))
        assertTrue(gate.isFresh(now = 10_000L + BiometricGate.VALIDITY_MS - 1L))
        assertFalse(gate.isFresh(now = 10_000L + BiometricGate.VALIDITY_MS))

        gate.invalidate()
        assertFalse(gate.isFresh(now = 10_001L))
    }
}
