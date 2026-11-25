package dev.j3pf.rp.data.secure

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import com.google.crypto.tink.Aead
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.data.ports.SecretStore
import dev.j3pf.rp.protocol.RpJson
import dev.j3pf.rp.protocol.obj
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Almacén cifrado respaldado por Android Keystore (Tink AES‑256‑GCM con clave maestra en Keystore).
 * `deviceToken` y `pushKey` sólo existen en disco cifrados y están excluidos de las copias de seguridad.
 * Move-1 port seam (contract: data-core CredentialsPorts.kt:14-27); the concrete SharedPreferences/Tink implementation stays as-is.
 */
@Singleton
open class SecretStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SecretStore {
        private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        private val aeadLock = Any()

        @Volatile
        private var cachedAead: Aead? = null

        protected open fun buildAead(): Aead {
            AeadConfig.register()
            return AndroidKeysetManager
                .Builder()
                .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
                .withKeyTemplate(
                    com.google.crypto.tink.KeyTemplates
                        .get("AES256_GCM"),
                ).withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
                .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        }

        private fun resetCorruptedKeyset() {
            cachedAead = null
            runCatching {
                KeyStore.getInstance("AndroidKeyStore").apply {
                    load(null)
                    deleteEntry(MASTER_KEY_ALIAS)
                }
            }
            runCatching {
                context.getSharedPreferences(KEYSET_PREFS, Context.MODE_PRIVATE).edit { clear() }
                prefs.edit { clear() }
            }
        }

        private fun getOrCreateAead(): Aead {
            cachedAead?.let { return it }
            synchronized(aeadLock) {
                cachedAead?.let { return it }
                val created =
                    try {
                        buildAead()
                    } catch (t: Throwable) {
                        if (isPermanentKeysetCorruption(t)) {
                            Log.w(TAG, "Permanent keyset corruption detected; resetting keyset", t)
                            resetCorruptedKeyset()
                            buildAead()
                        } else {
                            throw t
                        }
                    }
                cachedAead = created
                return created
            }
        }

        private val rng = SecureRandom()

        private val _session: MutableStateFlow<Session?> = MutableStateFlow(load())
        override val session: StateFlow<Session?> = _session.asStateFlow()
        protected val sessionState: MutableStateFlow<Session?> get() = _session

        /** Lectura síncrona (receivers y servicios). */
        override fun current(): Session? = _session.value

        override fun save(session: Session) {
            try {
                writeEncryptedSession(session)
            } catch (t: Throwable) {
                if (isPermanentKeysetCorruption(t)) {
                    Log.w(TAG, "Permanent keyset corruption during save; resetting keyset", t)
                    synchronized(aeadLock) { resetCorruptedKeyset() }
                    writeEncryptedSession(session)
                } else {
                    throw t
                }
            }
            _session.value = session
        }

        private fun writeEncryptedSession(session: Session) {
            val encrypted = encryptField(K_SESSION_V2, encodeSessionJson(session))
            prefs.edit {
                putString(K_SESSION_V2, encrypted)
                remove(K_RELAY_ID)
                remove(K_URL)
                remove(K_NAME)
                remove(K_DEVICE_ID)
                remove(K_TOKEN)
                remove(K_PUSH_KEY)
            }
        }

        override fun clear() {
            prefs.edit { clear() }
            _session.value = null
        }

        override fun newRelayId(): String =
            ByteArray(8)
                .also { rng.nextBytes(it) }
                .joinToString("") { "%02x".format(it) }

        private fun load(): Session? =
            try {
                if (prefs.contains(K_SESSION_V2)) {
                    val decoded = decryptField(K_SESSION_V2)?.let(::decodeSessionJson)
                    if (decoded == null) {
                        synchronized(aeadLock) { resetCorruptedKeyset() }
                        null
                    } else {
                        decoded
                    }
                } else {
                    loadLegacy()
                }
            } catch (t: Throwable) {
                if (isPermanentKeysetCorruption(t) || t is IllegalArgumentException) {
                    Log.w(TAG, "Permanent stored secret corruption; resetting keyset", t)
                    synchronized(aeadLock) { resetCorruptedKeyset() }
                } else {
                    Log.w(TAG, "Transient failure reading SecretStore; preserving stored credentials", t)
                }
                null
            }

        private fun loadLegacy(): Session? {
            val hasStoredSession = prefs.contains(K_RELAY_ID) || prefs.contains(K_TOKEN)
            if (!hasStoredSession) return null
            val relayId = decryptField(K_RELAY_ID)
            val relayUrl = decryptField(K_URL)
            val deviceId = decryptField(K_DEVICE_ID)
            val deviceToken = decryptField(K_TOKEN)
            val pushKey = decryptField(K_PUSH_KEY)
            return if (relayId == null || relayUrl == null || deviceId == null || deviceToken == null || pushKey == null) {
                synchronized(aeadLock) { resetCorruptedKeyset() }
                null
            } else {
                Session(
                    relayId = relayId,
                    relayUrl = relayUrl,
                    relayName = decryptField(K_NAME) ?: "",
                    deviceId = deviceId,
                    deviceToken = deviceToken,
                    pushKey = pushKey,
                )
            }
        }

        private fun encodeSessionJson(session: Session): String =
            buildJsonObject {
                put(K_RELAY_ID, session.relayId)
                put(K_URL, session.relayUrl)
                put(K_NAME, session.relayName)
                put(K_DEVICE_ID, session.deviceId)
                put(K_TOKEN, session.deviceToken)
                put(K_PUSH_KEY, session.pushKey)
            }.toString()

        private fun decodeSessionJson(rawJson: String): Session? {
            val obj = runCatching { RpJson.parseToJsonElement(rawJson).obj() }.getOrNull() ?: return null
            val relayId = obj.stringField(K_RELAY_ID) ?: return null
            val relayUrl = obj.stringField(K_URL) ?: return null
            val relayName = obj.stringField(K_NAME) ?: ""
            val deviceId = obj.stringField(K_DEVICE_ID) ?: return null
            val deviceToken = obj.stringField(K_TOKEN) ?: return null
            val pushKey = obj.stringField(K_PUSH_KEY) ?: return null
            return Session(
                relayId = relayId,
                relayUrl = relayUrl,
                relayName = relayName,
                deviceId = deviceId,
                deviceToken = deviceToken,
                pushKey = pushKey,
            )
        }

        private fun JsonObject.stringField(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun encryptField(
            key: String,
            value: String,
        ): String {
            val ct = getOrCreateAead().encrypt(value.toByteArray(Charsets.UTF_8), key.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(ct, Base64.NO_WRAP)
        }

        private fun decryptField(key: String): String? {
            val raw = prefs.getString(key, null) ?: return null
            val decoded = Base64.decode(raw, Base64.NO_WRAP)
            return String(getOrCreateAead().decrypt(decoded, key.toByteArray(Charsets.UTF_8)), Charsets.UTF_8)
        }

        internal companion object {
            private const val TAG = "SecretStore"
            const val PREFS = "rp_secrets"
            const val KEYSET_NAME = "rp_keyset"
            const val KEYSET_PREFS = "rp_keyset_prefs"
            const val MASTER_KEY_ALIAS = "rp_master_key"
            const val MASTER_KEY_URI = "android-keystore://$MASTER_KEY_ALIAS"
            const val K_SESSION_V2 = "session_v2"
            const val K_RELAY_ID = "relayId"
            const val K_URL = "relayUrl"
            const val K_NAME = "relayName"
            const val K_DEVICE_ID = "deviceId"
            const val K_TOKEN = "deviceToken"
            const val K_PUSH_KEY = "pushKey"

            /**
             * Only permanent keyset/AEAD corruption should reset the Keystore master key and wipe credentials.
             * Transient I/O, Keystore IPC, or generic runtime errors must preserve encrypted credentials on disk.
             */
            fun isPermanentKeysetCorruption(t: Throwable): Boolean {
                val chain = generateSequence(t) { it.cause }.toList()
                if (chain.any {
                        it is KeyPermanentlyInvalidatedException ||
                            it is AEADBadTagException ||
                            it.javaClass.simpleName == "InvalidProtocolBufferException"
                    }
                ) {
                    return true
                }
                if (chain.any { it is KeyStoreException || it is IOException || it is ProviderException }) {
                    return false
                }
                return chain.any { it is GeneralSecurityException }
            }
        }
    }
