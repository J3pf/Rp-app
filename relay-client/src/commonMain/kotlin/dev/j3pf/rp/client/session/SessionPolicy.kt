package dev.j3pf.rp.client.session

import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.repo.PairResult
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.protocol.HealthResponse
import dev.j3pf.rp.protocol.OkResponse
import dev.j3pf.rp.protocol.PairDeviceResponse

/** Pairing handshake and session transitions; URL parsing and native side effects stay injected. */
class SessionPolicy(
    private val normalizeUrl: (String) -> String?,
    private val healthz: suspend (String) -> ApiResult<HealthResponse>,
    private val pairDevice: suspend (String, String, String, String?) -> ApiResult<PairDeviceResponse>,
    private val putPush: suspend (String?, Boolean) -> ApiResult<OkResponse>,
    private val revokeDevice: suspend (String) -> ApiResult<OkResponse>,
    private val platform: SessionPlatform,
) {
    suspend fun pair(
        rawUrl: String,
        rawCode: String,
    ): PairResult {
        val url = normalizeUrl(rawUrl) ?: return PairResult.Failure("URL del relay no válida (debe ser https://…)")
        val code =
            rawCode
                .trim()
                .uppercase()
                .replace(" ", "")
                .replace("-", "")
        if (code.length < 6) return PairResult.Failure("El código es demasiado corto")

        when (val h = healthz(url)) {
            is ApiResult.Ok -> if (!h.value.ok) return PairResult.Failure("El relay no está listo")
            is ApiResult.Http -> return PairResult.Failure("El relay respondió HTTP ${h.code}")
            is ApiResult.Network -> return PairResult.Failure("No se pudo conectar con el relay: ${h.error.message}")
        }

        val fcm = platform.currentToken()
        val paired =
            when (val res = pairDevice(url, code, platform.deviceName(), fcm)) {
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

        if (platform.currentSession() != null) platform.clearLocalSession()
        platform.saveSession(
            Session(
                relayId = platform.newRelayId(),
                relayUrl = url,
                relayName = paired.relayName.ifEmpty { url.removePrefix("https://") },
                deviceId = paired.deviceId,
                deviceToken = paired.deviceToken,
                pushKey = paired.pushKey,
            ),
        )
        val direct = platform.directPush()
        putPush(fcm, direct)
        if (direct) platform.startDirectPush()
        platform.startConnection()
        return PairResult.Success
    }

    suspend fun applyPushChannels(direct: Boolean) {
        platform.setDirectPush(direct)
        putPush(platform.currentToken(), direct)
        if (direct) platform.startDirectPush() else platform.stopDirectPush()
    }

    suspend fun registerFcmToken(token: String?): Boolean {
        if (platform.currentSession() == null) return true
        return when (putPush(token, platform.directPush())) {
            is ApiResult.Ok -> true
            else -> false
        }
    }

    suspend fun logout() {
        val s = platform.currentSession()
        if (s != null) runCatching { revokeDevice(s.deviceId) }
        platform.stopDirectPush()
        platform.clearLocalSession()
        platform.clearCredentialsAndSettings()
    }
}
