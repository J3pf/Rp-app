package dev.j3pf.rp.data.secure

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import com.google.crypto.tink.Aead
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.data.ports.SecretStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import java.security.SecureRandom
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

        private fun buildAead(): Aead {
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
                    runCatching { buildAead() }.getOrElse {
                        resetCorruptedKeyset()
                        buildAead()
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
            runCatching { writeEncryptedSession(session) }
                .recoverCatching {
                    synchronized(aeadLock) { resetCorruptedKeyset() }
                    writeEncryptedSession(session)
                }.getOrThrow()
            _session.value = session
        }

        private fun writeEncryptedSession(session: Session) {
            prefs.edit {
                putString(K_RELAY_ID, encryptField(K_RELAY_ID, session.relayId))
                putString(K_URL, encryptField(K_URL, session.relayUrl))
                putString(K_NAME, encryptField(K_NAME, session.relayName))
                putString(K_DEVICE_ID, encryptField(K_DEVICE_ID, session.deviceId))
                putString(K_TOKEN, encryptField(K_TOKEN, session.deviceToken))
                putString(K_PUSH_KEY, encryptField(K_PUSH_KEY, session.pushKey))
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
            runCatching {
                val hasStoredSession = prefs.contains(K_RELAY_ID) || prefs.contains(K_TOKEN)
                val relayId = get(K_RELAY_ID)
                val relayUrl = get(K_URL)
                val deviceId = get(K_DEVICE_ID)
                val deviceToken = get(K_TOKEN)
                val pushKey = get(K_PUSH_KEY)
                if (relayId == null || relayUrl == null || deviceId == null || deviceToken == null || pushKey == null) {
                    if (hasStoredSession) {
                        synchronized(aeadLock) { resetCorruptedKeyset() }
                    }
                    return null
                }
                Session(
                    relayId = relayId,
                    relayUrl = relayUrl,
                    relayName = get(K_NAME) ?: "",
                    deviceId = deviceId,
                    deviceToken = deviceToken,
                    pushKey = pushKey,
                )
            }.getOrNull()

        private fun encryptField(
            key: String,
            value: String,
        ): String {
            val ct = getOrCreateAead().encrypt(value.toByteArray(Charsets.UTF_8), key.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(ct, Base64.NO_WRAP)
        }

        private fun get(key: String): String? {
            val raw = prefs.getString(key, null) ?: return null
            return runCatching {
                String(getOrCreateAead().decrypt(Base64.decode(raw, Base64.NO_WRAP), key.toByteArray(Charsets.UTF_8)), Charsets.UTF_8)
            }.getOrNull()
        }

        private companion object {
            const val PREFS = "rp_secrets"
            const val KEYSET_NAME = "rp_keyset"
            const val KEYSET_PREFS = "rp_keyset_prefs"
            const val MASTER_KEY_ALIAS = "rp_master_key"
            const val MASTER_KEY_URI = "android-keystore://$MASTER_KEY_ALIAS"
            const val K_RELAY_ID = "relayId"
            const val K_URL = "relayUrl"
            const val K_NAME = "relayName"
            const val K_DEVICE_ID = "deviceId"
            const val K_TOKEN = "deviceToken"
            const val K_PUSH_KEY = "pushKey"
        }
    }
