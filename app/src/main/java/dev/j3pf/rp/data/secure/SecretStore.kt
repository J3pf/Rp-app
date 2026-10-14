package dev.j3pf.rp.data.secure

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** Sesión emparejada con un relay. `relayId` permite varios relays en fase 4 sin migrar la base de datos. */
data class Session(
    val relayId: String,
    val relayUrl: String,
    val relayName: String,
    val deviceId: String,
    val deviceToken: String,
    /** Clave AES‑256 (base64) para descifrar los pushes de FCM. */
    val pushKey: String,
) {
    val wsBase: String get() = relayUrl.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
}

/**
 * Almacén cifrado respaldado por Android Keystore (Tink AES‑256‑GCM con clave maestra en Keystore).
 * `deviceToken` y `pushKey` sólo existen en disco cifrados y están excluidos de las copias de seguridad.
 */
@Singleton
open class SecretStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private val aead: Aead by lazy {
            AeadConfig.register()
            AndroidKeysetManager
                .Builder()
                .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
                .withKeyTemplate(
                    com.google.crypto.tink.KeyTemplates
                        .get("AES256_GCM"),
                ).withMasterKeyUri("android-keystore://rp_master_key")
                .build()
                .keysetHandle
                .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        }

        private val rng = SecureRandom()

        protected val _session: MutableStateFlow<Session?> = MutableStateFlow(load())
        val session: StateFlow<Session?> = _session.asStateFlow()

        /** Lectura síncrona (receivers y servicios). */
        open fun current(): Session? = _session.value

        fun save(session: Session) {
            prefs
                .edit()
                .putString(K_RELAY_ID, encryptField(K_RELAY_ID, session.relayId))
                .putString(K_URL, encryptField(K_URL, session.relayUrl))
                .putString(K_NAME, encryptField(K_NAME, session.relayName))
                .putString(K_DEVICE_ID, encryptField(K_DEVICE_ID, session.deviceId))
                .putString(K_TOKEN, encryptField(K_TOKEN, session.deviceToken))
                .putString(K_PUSH_KEY, encryptField(K_PUSH_KEY, session.pushKey))
                .apply()
            _session.value = session
        }

        fun clear() {
            prefs.edit().clear().apply()
            _session.value = null
        }

        fun newRelayId(): String =
            ByteArray(8)
                .also { rng.nextBytes(it) }
                .joinToString("") { "%02x".format(it) }

        private fun load(): Session? =
            runCatching {
                Session(
                    relayId = get(K_RELAY_ID) ?: return null,
                    relayUrl = get(K_URL) ?: return null,
                    relayName = get(K_NAME) ?: "",
                    deviceId = get(K_DEVICE_ID) ?: return null,
                    deviceToken = get(K_TOKEN) ?: return null,
                    pushKey = get(K_PUSH_KEY) ?: return null,
                )
            }.getOrNull()

        private fun encryptField(
            key: String,
            value: String,
        ): String {
            val ct = aead.encrypt(value.toByteArray(Charsets.UTF_8), key.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(ct, Base64.NO_WRAP)
        }

        private fun get(key: String): String? {
            val raw = prefs.getString(key, null) ?: return null
            return runCatching {
                String(aead.decrypt(Base64.decode(raw, Base64.NO_WRAP), key.toByteArray(Charsets.UTF_8)), Charsets.UTF_8)
            }.getOrNull()
        }

        private companion object {
            const val PREFS = "rp_secrets"
            const val KEYSET_NAME = "rp_keyset"
            const val KEYSET_PREFS = "rp_keyset_prefs"
            const val K_RELAY_ID = "relayId"
            const val K_URL = "relayUrl"
            const val K_NAME = "relayName"
            const val K_DEVICE_ID = "deviceId"
            const val K_TOKEN = "deviceToken"
            const val K_PUSH_KEY = "pushKey"
        }
    }
