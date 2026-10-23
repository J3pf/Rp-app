package dev.j3pf.rp.client.http

import dev.j3pf.rp.data.net.AnswerOutcome
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.map
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.AnswerPromptRequest
import dev.j3pf.rp.protocol.DeviceInfo
import dev.j3pf.rp.protocol.DevicesResponse
import dev.j3pf.rp.protocol.HealthResponse
import dev.j3pf.rp.protocol.MachinePatch
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.MachinesResponse
import dev.j3pf.rp.protocol.MeshAgentCommsResponse
import dev.j3pf.rp.protocol.MeshConversationResponse
import dev.j3pf.rp.protocol.MeshSnapshot
import dev.j3pf.rp.protocol.NotifyPrefs
import dev.j3pf.rp.protocol.OkResponse
import dev.j3pf.rp.protocol.PairCodeResponse
import dev.j3pf.rp.protocol.PairDeviceRequest
import dev.j3pf.rp.protocol.PairDeviceResponse
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptsResponse
import dev.j3pf.rp.protocol.PushChannelsRequest
import dev.j3pf.rp.protocol.RpJson
import dev.j3pf.rp.protocol.UserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject

/**
 * HTTP endpoint and reply policy; credentials are sampled once per authenticated request.
 * [encodeQueryComponent] must match UTF-8 java.net.URLEncoder (spaces as '+'); path segments
 * reuse that encoding with '+' replaced by '%20'. Native adapters also supply the decode-failure type.
 */
class RelayHttpClient(
    private val transport: RelayHttpTransport,
    private val credentials: () -> RelayCredentials?,
    private val encodeQueryComponent: (String) -> String,
    private val invalidResponse: (String) -> Throwable,
) {
    private val json = RpJson

    suspend fun healthz(baseUrl: String): ApiResult<HealthResponse> =
        call(RelayHttpRequest(url(baseUrl, "/healthz"), "GET"), HealthResponse.serializer())

    suspend fun pairDevice(
        baseUrl: String,
        code: String,
        name: String,
        fcmToken: String?,
    ): ApiResult<PairDeviceResponse> =
        call(
            RelayHttpRequest(
                url(baseUrl, "/v1/pair/device"),
                "POST",
                encode(PairDeviceRequest.serializer(), PairDeviceRequest(code, name, fcmToken)),
            ),
            PairDeviceResponse.serializer(),
        )

    suspend fun putPush(
        fcmToken: String?,
        direct: Boolean,
    ): ApiResult<OkResponse> =
        authed(
            "PUT",
            "/v1/devices/me/push",
            encode(PushChannelsRequest.serializer(), PushChannelsRequest(fcmToken, direct)),
            OkResponse.serializer(),
        )

    suspend fun pushTest(): ApiResult<OkResponse> = authed("POST", "/v1/devices/me/push-test", emptyBody(), OkResponse.serializer())

    suspend fun putPrefs(prefs: NotifyPrefs): ApiResult<OkResponse> =
        authed("PUT", "/v1/devices/me/prefs", encode(NotifyPrefs.serializer(), prefs), OkResponse.serializer())

    suspend fun devices(): ApiResult<List<DeviceInfo>> =
        authed("GET", "/v1/devices", null, DevicesResponse.serializer())
            .map { it.devices.filterNot { d -> d.revoked } }

    suspend fun revokeDevice(id: String): ApiResult<OkResponse> =
        authed("DELETE", "/v1/devices/${encodePathSegment(id)}", null, OkResponse.serializer())

    // ───────────────────────────── Máquinas ─────────────────────────────

    suspend fun machines(): ApiResult<List<MachineSummary>> =
        authed("GET", "/v1/machines", null, MachinesResponse.serializer())
            .map { it.machines }

    suspend fun patchMachine(
        id: String,
        patch: MachinePatch,
    ): ApiResult<MachineSummary> =
        authed("PATCH", "/v1/machines/${encodePathSegment(id)}", encode(MachinePatch.serializer(), patch), MachineSummary.serializer())

    suspend fun revokeMachine(id: String): ApiResult<OkResponse> =
        authed("DELETE", "/v1/machines/${encodePathSegment(id)}", null, OkResponse.serializer())

    suspend fun createMachineCode(): ApiResult<PairCodeResponse> =
        authed("POST", "/v1/pair/codes", emptyBody(), PairCodeResponse.serializer())

    // ───────────────────────────── Agent mesh ─────────────────────────────

    suspend fun getMesh(machineId: String? = null): ApiResult<MeshSnapshot> =
        authed("GET", queryPath("/v1/mesh", "machineId" to machineId), null, MeshSnapshot.serializer())

    suspend fun getMeshConversation(
        a: String,
        b: String,
        before: Long? = null,
        limit: Int = 50,
    ): ApiResult<MeshConversationResponse> =
        authed(
            "GET",
            queryPath("/v1/mesh/conversation", "a" to a, "b" to b, "before" to before?.toString(), "limit" to limit.toString()),
            null,
            MeshConversationResponse.serializer(),
        )

    suspend fun getMeshAgentComms(
        agentId: String,
        limit: Int = 50,
    ): ApiResult<MeshAgentCommsResponse> =
        authed(
            "GET",
            queryPath("/v1/mesh/agents/${encodePathSegment(agentId)}/comms", "limit" to limit.toString()),
            null,
            MeshAgentCommsResponse.serializer(),
        )

    // ───────────────────────────── Prompts y mensajes ─────────────────────────────

    suspend fun openPrompts(): ApiResult<List<Prompt>> =
        authed("GET", "/v1/prompts?state=open", null, PromptsResponse.serializer())
            .map { it.prompts }

    /** Usado por los botones de la notificación (HTTPS directo, timeout 8 s). */
    suspend fun answerPrompt(
        promptId: String,
        answer: Answer,
    ): AnswerOutcome {
        val res =
            authed(
                "POST",
                "/v1/prompts/${encodePathSegment(promptId)}/answer",
                encode(AnswerPromptRequest.serializer(), AnswerPromptRequest(answer)),
                OkResponse.serializer(),
                RelayHttpTimeout.QUICK,
            )
        return when (res) {
            is ApiResult.Ok -> {
                AnswerOutcome.Sent
            }

            is ApiResult.Http -> {
                when {
                    res.isConflict -> AnswerOutcome.AlreadyResolved
                    res.code == 404 -> AnswerOutcome.AlreadyResolved
                    res.code >= 500 || res.code == 429 -> AnswerOutcome.Queued
                    else -> AnswerOutcome.Failed("HTTP ${res.code}")
                }
            }

            is ApiResult.Network -> {
                AnswerOutcome.Queued
            }
        }
    }

    suspend fun sendMessage(msg: UserMessage): ApiResult<JsonObject> =
        authed(
            "POST",
            "/v1/agents/${encodePathSegment(msg.agentId)}/messages",
            encode(UserMessage.serializer(), msg),
            JsonObject.serializer(),
            RelayHttpTimeout.QUICK,
        )

    private suspend fun <T> authed(
        method: String,
        path: String,
        body: String?,
        serializer: KSerializer<T>,
        timeout: RelayHttpTimeout = RelayHttpTimeout.DEFAULT,
    ): ApiResult<T> {
        val session = credentials() ?: return ApiResult.Http(401, "sin sesión")
        return call(
            RelayHttpRequest(
                url(session.relayUrl, path),
                method,
                body ?: if (method == "GET" || method == "DELETE") null else emptyBody(),
                "Bearer ${session.deviceToken}",
                timeout,
            ),
            serializer,
        )
    }

    private suspend fun <T> call(
        request: RelayHttpRequest,
        serializer: KSerializer<T>,
    ): ApiResult<T> =
        when (val result = transport.execute(request)) {
            is ApiResult.Ok -> decodeResponse(result.value, serializer)
            is ApiResult.Http -> result
            is ApiResult.Network -> result
        }

    /** Also used by native streaming uploads without moving their bodies into shared memory. */
    fun <T> decodeResponse(
        response: RelayHttpResponse,
        serializer: KSerializer<T>,
    ): ApiResult<T> {
        if (response.code !in 200..299) return ApiResult.Http(response.code, response.body)
        return try {
            ApiResult.Ok(json.decodeFromString(serializer, response.body.ifBlank { "{}" }))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Network(invalidResponse("Respuesta inválida: ${e.message}"))
        }
    }

    private fun <T> encode(
        serializer: KSerializer<T>,
        value: T,
    ): String = json.encodeToString(serializer, value)

    private fun emptyBody(): String = "{}"

    private fun queryPath(
        path: String,
        vararg params: Pair<String, String?>,
    ): String {
        val query =
            params
                .filter { it.second != null }
                .joinToString("&") { (key, value) -> "${encodeQueryComponent(key)}=${encodeQueryComponent(value!!)}" }
        return if (query.isEmpty()) path else "$path?$query"
    }

    private fun encodePathSegment(value: String): String {
        val encoded =
            encodeQueryComponent(value)
                .replace("+", "%20")
                .replace("%3A", ":")
                .replace("%3a", ":")
        return when (encoded) {
            "." -> "%2E"
            ".." -> "%2E%2E"
            else -> encoded
        }
    }

    private fun url(
        base: String,
        path: String,
    ) = base.trimEnd('/') + path
}
