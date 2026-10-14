package dev.j3pf.rp.data.net

import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.AnswerPromptRequest
import dev.j3pf.rp.protocol.BlobInfo
import dev.j3pf.rp.protocol.DeviceInfo
import dev.j3pf.rp.protocol.HealthResponse
import dev.j3pf.rp.protocol.MachinePatch
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.NotifyPrefs
import dev.j3pf.rp.protocol.OkResponse
import dev.j3pf.rp.protocol.PairCodeResponse
import dev.j3pf.rp.protocol.PairDeviceRequest
import dev.j3pf.rp.protocol.PairDeviceResponse
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PushChannelsRequest
import dev.j3pf.rp.protocol.RpJson
import dev.j3pf.rp.protocol.UserMessage
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

/** Resultado de una llamada HTTP al relay. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    /** Respuesta no 2xx. `409` = `already_resolved`. */
    data class Http(val code: Int, val body: String) : ApiResult<Nothing> {
        val isConflict get() = code == 409
        val isUnauthorized get() = code == 401 || code == 403
    }

    data class Network(val error: Throwable) : ApiResult<Nothing>
}

inline fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Ok)?.value

/** Resultado de responder un prompt. */
sealed interface AnswerOutcome {
    data object Sent : AnswerOutcome

    /** 409: ya respondido en otro lugar. También cuenta como "listo" para quitar la notificación. */
    data object AlreadyResolved : AnswerOutcome

    /** Sin red / error temporal: se reintenta con WorkManager. */
    data object Queued : AnswerOutcome

    data class Failed(val message: String) : AnswerOutcome
}

@Singleton
class RelayApi @Inject constructor(
    private val client: OkHttpClient,
    private val secrets: SecretStore,
) {
    private val json = RpJson
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** Cliente con timeout corto para las acciones de notificación (8 s, PLAN-APP §5.3). */
    private val quick: OkHttpClient by lazy {
        client.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()
    }

    // ───────────────────────────── Emparejamiento ─────────────────────────────

    suspend fun healthz(baseUrl: String): ApiResult<HealthResponse> =
        runCatching { Request.Builder().url(url(baseUrl, "/healthz")).get().build() }
            .fold(
                onSuccess = { call(it, HealthResponse.serializer()) },
                onFailure = { ApiResult.Network(it) },
            )

    suspend fun pairDevice(baseUrl: String, code: String, name: String, fcmToken: String?): ApiResult<PairDeviceResponse> =
        runCatching {
            Request.Builder().url(url(baseUrl, "/v1/pair/device"))
                .post(encode(PairDeviceRequest.serializer(), PairDeviceRequest(code, name, fcmToken))).build()
        }.fold(
            onSuccess = { call(it, PairDeviceResponse.serializer()) },
            onFailure = { ApiResult.Network(it) },
        )

    // ───────────────────────────── Dispositivo / push ─────────────────────────────

    suspend fun putPush(fcmToken: String?, direct: Boolean): ApiResult<OkResponse> =
        authed("PUT", "/v1/devices/me/push", encode(PushChannelsRequest.serializer(), PushChannelsRequest(fcmToken, direct)), OkResponse.serializer())

    suspend fun pushTest(): ApiResult<OkResponse> =
        authed("POST", "/v1/devices/me/push-test", emptyBody(), OkResponse.serializer())

    suspend fun putPrefs(prefs: NotifyPrefs): ApiResult<OkResponse> =
        authed("PUT", "/v1/devices/me/prefs", encode(NotifyPrefs.serializer(), prefs), OkResponse.serializer())

    suspend fun devices(): ApiResult<List<DeviceInfo>> =
        authed("GET", "/v1/devices", null, ListSerializer(DeviceInfo.serializer()))

    suspend fun revokeDevice(id: String): ApiResult<OkResponse> =
        authed("DELETE", "/v1/devices/$id", null, OkResponse.serializer())

    // ───────────────────────────── Máquinas ─────────────────────────────

    suspend fun machines(): ApiResult<List<MachineSummary>> =
        authed("GET", "/v1/machines", null, ListSerializer(MachineSummary.serializer()))

    suspend fun patchMachine(id: String, patch: MachinePatch): ApiResult<MachineSummary> =
        authed("PATCH", "/v1/machines/$id", encode(MachinePatch.serializer(), patch), MachineSummary.serializer())

    suspend fun revokeMachine(id: String): ApiResult<OkResponse> =
        authed("DELETE", "/v1/machines/$id", null, OkResponse.serializer())

    suspend fun createMachineCode(): ApiResult<PairCodeResponse> =
        authed("POST", "/v1/pair/codes", emptyBody(), PairCodeResponse.serializer())

    // ───────────────────────────── Prompts y mensajes ─────────────────────────────

    suspend fun openPrompts(): ApiResult<List<Prompt>> =
        authed("GET", "/v1/prompts?state=open", null, ListSerializer(Prompt.serializer()))

    /** Usado por los botones de la notificación (HTTPS directo, timeout 8 s). */
    suspend fun answerPrompt(promptId: String, answer: Answer): AnswerOutcome {
        val res = authed(
            "POST", "/v1/prompts/$promptId/answer",
            encode(AnswerPromptRequest.serializer(), AnswerPromptRequest(answer)),
            OkResponse.serializer(), quick,
        )
        return when (res) {
            is ApiResult.Ok -> AnswerOutcome.Sent
            is ApiResult.Http -> when {
                res.isConflict -> AnswerOutcome.AlreadyResolved
                res.code == 404 -> AnswerOutcome.AlreadyResolved
                res.code >= 500 || res.code == 429 -> AnswerOutcome.Queued
                else -> AnswerOutcome.Failed("HTTP ${res.code}")
            }
            is ApiResult.Network -> AnswerOutcome.Queued
        }
    }

    suspend fun sendMessage(msg: UserMessage): ApiResult<JsonObject> =
        authed("POST", "/v1/agents/${msg.agentId}/messages", encode(UserMessage.serializer(), msg), JsonObject.serializer(), quick)

    // ───────────────────────────── Adjuntos ─────────────────────────────

    /** Sube un archivo (≤25 MiB) con progreso en bytes. */
    suspend fun uploadBlob(
        name: String,
        mime: String,
        body: RequestBody,
        onProgress: (sent: Long, total: Long) -> Unit,
    ): ApiResult<BlobInfo> {
        val part = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", name, ProgressBody(body, onProgress)).build()
        return authed("POST", "/v1/blobs", part, BlobInfo.serializer(), client.newBuilder().callTimeout(0, TimeUnit.SECONDS).build())
    }

    // ───────────────────────────── Interno ─────────────────────────────

    private suspend fun <T> authed(
        method: String,
        path: String,
        body: RequestBody?,
        serializer: KSerializer<T>,
        http: OkHttpClient = client,
    ): ApiResult<T> {
        val s = secrets.current() ?: return ApiResult.Http(401, "sin sesión")
        val req = runCatching {
            Request.Builder().url(url(s.relayUrl, path))
                .header("Authorization", "Bearer ${s.deviceToken}")
                .method(method, body ?: if (method == "GET" || method == "DELETE") null else emptyBody())
                .build()
        }.getOrElse { return ApiResult.Network(it) }
        return call(req, serializer, http)
    }

    private suspend fun <T> call(req: Request, serializer: KSerializer<T>, http: OkHttpClient = client): ApiResult<T> {
        val response = try {
            http.newCall(req).await()
        } catch (e: IOException) {
            return ApiResult.Network(e)
        }
        return response.use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) return@use ApiResult.Http(r.code, text)
            try {
                ApiResult.Ok(json.decodeFromString(serializer, text.ifBlank { "{}" }))
            } catch (e: Exception) {
                ApiResult.Network(IOException("Respuesta inválida: ${e.message}"))
            }
        }
    }

    private fun <T> encode(s: KSerializer<T>, v: T): RequestBody = json.encodeToString(s, v).toRequestBody(jsonType)
    private fun emptyBody(): RequestBody = "{}".toRequestBody(jsonType)
    private fun url(base: String, path: String) = base.trimEnd('/') + path
}

/** `Call.await()` cancelable. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

/** Cuerpo que informa de los bytes enviados. */
class ProgressBody(
    private val delegate: RequestBody,
    private val onProgress: (sent: Long, total: Long) -> Unit,
) : RequestBody() {
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        val counting = object : ForwardingSink(sink) {
            var written = 0L
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                written += byteCount
                onProgress(written, total)
            }
        }.buffer()
        delegate.writeTo(counting)
        counting.flush()
    }
}
