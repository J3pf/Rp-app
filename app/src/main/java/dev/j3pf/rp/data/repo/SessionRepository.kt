package dev.j3pf.rp.data.repo

import android.content.Context
import android.os.Build
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.data.db.DedupDao
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.push.DirectPushService
import dev.j3pf.rp.push.NotificationChannels
import dev.j3pf.rp.push.PushTokenProvider
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface PairResult {
    data object Success : PairResult
    data class Failure(val message: String) : PairResult
}

/** Emparejamiento (PLAN-APP §6), registro de canales de push y cierre de sesión. */
@Singleton
class SessionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: RelayApi,
    private val secrets: SecretStore,
    private val settings: SettingsRepository,
    private val connection: RelayConnection,
    private val machines: MachinesRepository,
    private val agents: AgentsRepository,
    private val chat: ChatRepository,
    private val prompts: PromptsRepository,
    private val hosts: HostRepository,
    private val mesh: MeshRepository,
    private val dedup: DedupDao,
    private val tokens: PushTokenProvider,
    private val channels: NotificationChannels,
) {
    /**
     * `POST /v1/pair/device {code, name, fcmToken?}` → guarda `deviceToken` y `pushKey` cifrados, registra
     * `PUT /v1/devices/me/push {direct:true}` y arranca el canal directo. Comprueba `/healthz` antes.
     */
    suspend fun pair(rawUrl: String, rawCode: String): PairResult {
        val url = normalizeUrl(rawUrl) ?: return PairResult.Failure("URL del relay no válida (debe ser https://…)")
        val code = rawCode.trim().uppercase().replace(" ", "").replace("-", "")
        if (code.length < 6) return PairResult.Failure("El código es demasiado corto")

        when (val h = api.healthz(url)) {
            is ApiResult.Ok -> if (!h.value.ok) return PairResult.Failure("El relay no está listo")
            is ApiResult.Http -> return PairResult.Failure("El relay respondió HTTP ${h.code}")
            is ApiResult.Network -> return PairResult.Failure("No se pudo conectar con el relay: ${h.error.message}")
        }

        val fcm = tokens.currentToken()
        val res = api.pairDevice(url, code, Build.MODEL ?: "Android", fcm)
        val paired = when (res) {
            is ApiResult.Ok -> res.value
            is ApiResult.Http -> return PairResult.Failure(
                when (res.code) {
                    400, 404 -> "Código incorrecto o caducado"
                    429 -> "Demasiados intentos. Espera un minuto"
                    else -> "El relay rechazó el emparejamiento (HTTP ${res.code})"
                },
            )
            is ApiResult.Network -> return PairResult.Failure("Error de red: ${res.error.message}")
        }

        if (secrets.current() != null) {
            connection.stop()
            WorkManager.getInstance(context).cancelAllWork()
            clearLocalSessionData()
            channels.removeAll()
        }

        secrets.save(
            Session(
                relayId = secrets.newRelayId(),
                relayUrl = url,
                relayName = paired.relayName.ifEmpty { url.removePrefix("https://") },
                deviceId = paired.deviceId,
                deviceToken = paired.deviceToken,
                pushKey = paired.pushKey,
            ),
        )
        val direct = settings.current().directPush
        api.putPush(fcm, direct)
        if (direct) DirectPushService.start(context)
        connection.start()
        return PairResult.Success
    }

    /** Aplica el interruptor de canal directo/FCM en el relay y arranca/detiene el servicio. */
    suspend fun applyPushChannels(direct: Boolean) {
        settings.setDirectPush(direct)
        api.putPush(tokens.currentToken(), direct)
        if (direct) DirectPushService.start(context) else DirectPushService.stop(context)
    }

    /** `onNewToken` de FCM → `PUT /v1/devices/me/push`. */
    suspend fun registerFcmToken(token: String?): Boolean {
        if (secrets.current() == null) return true
        return when (api.putPush(token, settings.current().directPush)) {
            is ApiResult.Ok -> true
            else -> false
        }
    }

    /** Cerrar sesión: revoca (best-effort) el dispositivo, borra credenciales, base de datos y canales. */
    suspend fun logout() {
        val s = secrets.current()
        if (s != null) runCatching { api.revokeDevice(s.deviceId) }
        DirectPushService.stop(context)
        connection.stop()
        WorkManager.getInstance(context).cancelAllWork()
        clearLocalSessionData()
        channels.removeAll()
        secrets.clear()
        settings.wipe()
    }

    private suspend fun clearLocalSessionData() {
        machines.clear()
        agents.clear()
        chat.clear()
        prompts.clear()
        hosts.clear()
        mesh.clear()
        dedup.clear()
    }

    companion object {
        /** Acepta `rp://pair?u=…&c=…`, URL con/sin esquema; fuerza https salvo emulador de depuración. */
        fun normalizeUrl(raw: String): String? {
            var u = raw.trim().trimEnd('/')
            if (u.isEmpty()) return null
            if (!u.contains("://")) u = "https://$u"
            val parsed = u.toHttpUrlOrNull() ?: return null
            if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty() || parsed.query != null || parsed.fragment != null) {
                return null
            }
            val host = parsed.host.lowercase()
            if (host.isBlank() || host.startsWith(".") || host.endsWith(".")) return null
            val ok = when (parsed.scheme.lowercase()) {
                "https" -> true
                "http" -> host == "localhost" || host == "10.0.2.2" || host == "127.0.0.1"
                else -> false
            }
            return if (ok) parsed.toString().trimEnd('/') else null
        }

        /** Extrae (url, code) de un `rp://pair?u=…&c=…`. */
        fun parsePairingUri(text: String): Pair<String, String>? {
            val t = text.trim()
            if (!t.startsWith("rp://pair")) return null
            val uri = android.net.Uri.parse(t)
            val u = uri.getQueryParameter("u") ?: return null
            val c = uri.getQueryParameter("c") ?: return null
            return u to c
        }
    }
}
