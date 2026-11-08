package dev.j3pf.rp.data.secure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract and security tests for [EncryptedKeyValueSecretStore] (O1 Move 3: SC-01..SC-05).
 */
class EncryptedKeyValueSecretStoreTest {
    private val sampleSession =
        Session(
            relayId = "0123456789abcdef",
            relayUrl = "https://relay.example.com",
            relayName = "Studio Mac",
            deviceId = "dev-ios-1",
            deviceToken = "secret-device-token",
            pushKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        )

    @Test
    fun savePersistsEncryptedFieldsReloadsAcrossInstancesAndClearsCleanly() {
        val backing = InMemorySecretKeyValueBacking()
        var mirroredPushKey: String? = null
        var mirroredGroup: String? = null
        val store =
            EncryptedKeyValueSecretStore(
                backing = backing,
                accessGroup = "19410625785.dev.j3pf.rp.iosApp",
                onPushKeyChanged = { pk, group ->
                    mirroredPushKey = pk
                    mirroredGroup = group
                },
            )

        assertNull(store.current())
        assertNull(store.session.value)

        store.save(sampleSession)
        assertEquals(sampleSession, store.current())
        assertEquals(sampleSession, store.session.value)
        assertEquals(sampleSession.pushKey, mirroredPushKey)
        assertEquals("19410625785.dev.j3pf.rp.iosApp", mirroredGroup)

        // Stored values in backing are ciphertext, never plaintext, and K_NSE_PUSH_KEY is never written to backing
        val rawToken = backing.getString(EncryptedKeyValueSecretStore.K_TOKEN)!!
        assertNotEquals(sampleSession.deviceToken, rawToken)
        assertNull(backing.getString(EncryptedKeyValueSecretStore.K_NSE_PUSH_KEY))

        // Re-opening a new store instance over the same backing decrypts the exact session
        val reopened = EncryptedKeyValueSecretStore(backing = backing)
        assertEquals(sampleSession, reopened.current())

        // Swapping two fields' ciphertexts fails AEAD field-associated-data check, returns null, and purges corrupted entries
        val rawUrl = backing.getString(EncryptedKeyValueSecretStore.K_URL)!!
        backing.putString(EncryptedKeyValueSecretStore.K_TOKEN, rawUrl)
        val tampered = EncryptedKeyValueSecretStore(backing = backing)
        assertNull(tampered.current())
        assertNull(backing.getString(EncryptedKeyValueSecretStore.K_TOKEN))

        // Saving again recovers cleanly
        tampered.save(sampleSession)
        assertEquals(sampleSession, tampered.current())

        // Clear wipes session and mirrors null pushKey
        tampered.clear()
        assertNull(tampered.current())
        assertNull(tampered.session.value)
    }

    @Test
    fun savePropagatesExceptionWhenBackingFailsOnBothInitialAndRetryWrites() {
        val failingBacking =
            object : SecretKeyValueBacking {
                override fun getString(key: String): String? = null

                override fun putString(
                    key: String,
                    value: String,
                ): Nothing = throw IllegalStateException("Keychain write failed")

                override fun remove(key: String) = Unit

                override fun clear() = Unit
            }
        val store = EncryptedKeyValueSecretStore(backing = failingBacking)
        assertFailsWith<IllegalStateException> {
            store.save(sampleSession)
        }
        assertNull(store.current())
    }

    @Test
    fun newRelayIdProduces16LowercaseHexCharsAndDiffersAcrossCalls() {
        val store = EncryptedKeyValueSecretStore(InMemorySecretKeyValueBacking())
        val id1 = store.newRelayId()
        val id2 = store.newRelayId()
        assertEquals(16, id1.length)
        assertTrue(id1.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(id1, id2)
    }
}
