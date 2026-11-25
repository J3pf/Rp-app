package dev.j3pf.rp.data.secure

import dev.j3pf.rp.data.crypto.Aes256Gcm
import dev.j3pf.rp.data.ports.SecureCrypto
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
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

        // Stored value in backing is packed session_v2 ciphertext, never plaintext, and K_NSE_PUSH_KEY is never written to backing
        val rawV2 = backing.getString(EncryptedKeyValueSecretStore.K_SESSION_V2)!!
        assertNotEquals(sampleSession.deviceToken, rawV2)
        assertTrue(!rawV2.contains(sampleSession.deviceToken))
        assertTrue(!rawV2.contains(sampleSession.pushKey))
        assertNull(backing.getString(EncryptedKeyValueSecretStore.K_TOKEN))
        assertNull(backing.getString(EncryptedKeyValueSecretStore.K_NSE_PUSH_KEY))

        // Re-opening a new store instance over the same backing decrypts the exact session
        val reopened = EncryptedKeyValueSecretStore(backing = backing)
        assertEquals(sampleSession, reopened.current())

        // Corrupting session_v2 ciphertext fails AEAD authentication, returns null, and purges corrupted entry
        backing.putString(EncryptedKeyValueSecretStore.K_SESSION_V2, "###tampered-ciphertext@@@")
        val tampered = EncryptedKeyValueSecretStore(backing = backing)
        assertNull(tampered.current())
        assertNull(backing.getString(EncryptedKeyValueSecretStore.K_SESSION_V2))

        // Saving again recovers cleanly
        tampered.save(sampleSession)
        assertEquals(sampleSession, tampered.current())

        // Clear wipes session and mirrors null pushKey
        tampered.clear()
        assertNull(tampered.current())
        assertNull(tampered.session.value)
        assertNull(backing.getString(EncryptedKeyValueSecretStore.K_SESSION_V2))
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

    @Test
    fun transientBackingReadErrorDoesNotWipePersistedSession() {
        val delegate = InMemorySecretKeyValueBacking()
        var failReadsWithException = false
        var failReadsWithSentinel = false
        var removeCount = 0
        var clearCount = 0
        val backing =
            object : SecretKeyValueBacking {
                override fun getString(key: String): String? {
                    if (failReadsWithException) {
                        throw IllegalStateException("errSecInteractionNotAllowed")
                    }
                    if (failReadsWithSentinel) {
                        return EncryptedKeyValueSecretStore.TRANSIENT_READ_ERROR_SENTINEL
                    }
                    return delegate.getString(key)
                }

                override fun putString(
                    key: String,
                    value: String,
                ) = delegate.putString(key, value)

                override fun remove(key: String) {
                    removeCount++
                    delegate.remove(key)
                }

                override fun clear() {
                    clearCount++
                    delegate.clear()
                }
            }

        val initial = EncryptedKeyValueSecretStore(backing = backing)
        initial.save(sampleSession)
        assertEquals(sampleSession, initial.current())

        // Simulate transient Keychain lock throwing an exception
        failReadsWithException = true
        val lockedExceptionStore = EncryptedKeyValueSecretStore(backing = backing)
        assertNull(lockedExceptionStore.current())
        assertEquals(0, removeCount, "Transient exception must not remove encrypted entries")
        assertEquals(0, clearCount, "Transient exception must not clear backing")

        // Simulate transient Keychain lock returning sentinel
        failReadsWithException = false
        failReadsWithSentinel = true
        val lockedSentinelStore = EncryptedKeyValueSecretStore(backing = backing)
        assertNull(lockedSentinelStore.current())
        assertEquals(0, removeCount, "Transient sentinel must not remove encrypted entries")
        assertEquals(0, clearCount, "Transient sentinel must not clear backing")

        // Once unlocked, stored credentials remain intact and load cleanly
        failReadsWithSentinel = false
        val recovered = EncryptedKeyValueSecretStore(backing = backing)
        assertEquals(sampleSession, recovered.current())
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun customCryptoFactoryDelegatesEncryptDecryptAndRoundTripsWithAes256Gcm() {
        val backing = InMemorySecretKeyValueBacking()
        val encryptedFields = mutableListOf<String>()
        val decryptedFields = mutableListOf<String>()
        val deterministicRandom = Random(42)

        fun recordingNativeLikeFactory(): (ByteArray) -> SecureCrypto =
            { masterKey ->
                require(masterKey.size == Aes256Gcm.KEY_BYTES)
                object : SecureCrypto {
                    override fun encrypt(
                        field: String,
                        value: String,
                    ): String {
                        encryptedFields += field
                        val nonce = deterministicRandom.nextBytes(Aes256Gcm.NONCE_BYTES)
                        val ctWithTag =
                            Aes256Gcm.encrypt(
                                key = masterKey,
                                nonce = nonce,
                                plaintext = value.encodeToByteArray(),
                                aad = field.encodeToByteArray(),
                            )
                        val combined = ByteArray(nonce.size + ctWithTag.size)
                        nonce.copyInto(combined, 0)
                        ctWithTag.copyInto(combined, nonce.size)
                        return Base64.encode(combined)
                    }

                    override fun decrypt(
                        field: String,
                        value: String,
                    ): String? {
                        decryptedFields += field
                        return runCatching {
                            val combined = Base64.decode(value)
                            if (combined.size < Aes256Gcm.NONCE_BYTES + Aes256Gcm.TAG_BYTES) return null
                            val nonce = combined.copyOfRange(0, Aes256Gcm.NONCE_BYTES)
                            val ctWithTag = combined.copyOfRange(Aes256Gcm.NONCE_BYTES, combined.size)
                            val plain =
                                Aes256Gcm.decrypt(
                                    key = masterKey,
                                    nonce = nonce,
                                    ciphertextWithTag = ctWithTag,
                                    aad = field.encodeToByteArray(),
                                ) ?: return null
                            plain.decodeToString()
                        }.getOrNull()
                    }
                }
            }

        // 1. Encrypt with custom AEAD factory (simulating iOS CryptoKit bridge)
        val nativeBackedStore =
            EncryptedKeyValueSecretStore(
                backing = backing,
                cryptoFactory = recordingNativeLikeFactory(),
            )
        nativeBackedStore.save(sampleSession)
        assertEquals(sampleSession, nativeBackedStore.current())
        assertEquals(
            listOf(EncryptedKeyValueSecretStore.K_SESSION_V2),
            encryptedFields,
        )

        // 2. Decrypt with default pure-Kotlin AesGcmSecureCrypto (wire-format compatibility)
        val pureKotlinStore = EncryptedKeyValueSecretStore(backing = backing)
        assertEquals(sampleSession, pureKotlinStore.current())

        // 3. Encrypt with pure-Kotlin store (including empty + Unicode values), decrypt with custom AEAD factory
        val unicodeSession =
            sampleSession.copy(
                relayName = "",
                deviceId = "dev-ios-ñ-🚀",
            )
        pureKotlinStore.save(unicodeSession)
        val reopenedNativeStore =
            EncryptedKeyValueSecretStore(
                backing = backing,
                cryptoFactory = recordingNativeLikeFactory(),
            )
        assertEquals(unicodeSession, reopenedNativeStore.current())
        assertEquals(
            listOf(EncryptedKeyValueSecretStore.K_SESSION_V2),
            decryptedFields,
        )

        // 4. Tampering with session_v2 ciphertext fails authentication through custom AEAD factory
        val rawV2 = backing.getString(EncryptedKeyValueSecretStore.K_SESSION_V2)!!
        val flipped = rawV2.dropLast(2) + if (rawV2.endsWith("AA")) "BB" else "AA"
        backing.putString(EncryptedKeyValueSecretStore.K_SESSION_V2, flipped)
        val tamperedNativeStore =
            EncryptedKeyValueSecretStore(
                backing = backing,
                cryptoFactory = recordingNativeLikeFactory(),
            )
        assertNull(tamperedNativeStore.current())
    }
}
