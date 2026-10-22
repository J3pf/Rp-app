package dev.j3pf.rp.data.net

import dev.j3pf.rp.client.http.RelayCredentials
import dev.j3pf.rp.client.http.RelayHttpClient
import dev.j3pf.rp.client.http.RelayHttpResponse
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.BlobInfo
import dev.j3pf.rp.protocol.DeviceInfo
import dev.j3pf.rp.protocol.HealthResponse
import dev.j3pf.rp.protocol.MachinePatch
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.MeshAgentCommsResponse
import dev.j3pf.rp.protocol.MeshConversationResponse
import dev.j3pf.rp.protocol.MeshSnapshot
import dev.j3pf.rp.protocol.NotifyPrefs
import dev.j3pf.rp.protocol.OkResponse
import dev.j3pf.rp.protocol.PairCodeResponse
import dev.j3pf.rp.protocol.PairDeviceResponse
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.UserMessage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class RelayApi
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val secrets: SecretStore,
    ) {
        private val shared =
            RelayHttpClient(
                transport = OkHttpRelayTransport(client),
                credentials = { secrets.current()?.let { RelayCredentials(it.relayUrl, it.deviceToken) } },
                encodeQueryComponent = { URLEncoder.encode(it, "UTF-8") },
                invalidResponse = { IOException(it) },
            )

        /** Cliente sin callTimeout global para subidas de blobs grandes. */
        private val uploadClient: OkHttpClient by lazy {
            client.newBuilder().callTimeout(0, TimeUnit.SECONDS).build()
        }

        suspend fun healthz(baseUrl: String): ApiResult<HealthResponse> = shared.healthz(baseUrl)

        suspend fun pairDevice(
            baseUrl: String,
            code: String,
            name: String,
            fcmToken: String?,
        ): ApiResult<PairDeviceResponse> = shared.pairDevice(baseUrl, code, name, fcmToken)

        suspend fun putPush(
            fcmToken: String?,
            direct: Boolean,
        ): ApiResult<OkResponse> = shared.putPush(fcmToken, direct)

        suspend fun pushTest(): ApiResult<OkResponse> = shared.pushTest()

        suspend fun putPrefs(prefs: NotifyPrefs): ApiResult<OkResponse> = shared.putPrefs(prefs)

        suspend fun devices(): ApiResult<List<DeviceInfo>> = shared.devices()

        suspend fun revokeDevice(id: String): ApiResult<OkResponse> = shared.revokeDevice(id)

        suspend fun machines(): ApiResult<List<MachineSummary>> = shared.machines()

        suspend fun patchMachine(
            id: String,
            patch: MachinePatch,
        ): ApiResult<MachineSummary> = shared.patchMachine(id, patch)

        suspend fun revokeMachine(id: String): ApiResult<OkResponse> = shared.revokeMachine(id)

        suspend fun createMachineCode(): ApiResult<PairCodeResponse> = shared.createMachineCode()

        suspend fun getMesh(machineId: String? = null): ApiResult<MeshSnapshot> = shared.getMesh(machineId)

        suspend fun getMeshConversation(
            a: String,
            b: String,
            before: Long? = null,
            limit: Int = 50,
        ): ApiResult<MeshConversationResponse> = shared.getMeshConversation(a, b, before, limit)

        suspend fun getMeshAgentComms(
            agentId: String,
            limit: Int = 50,
        ): ApiResult<MeshAgentCommsResponse> = shared.getMeshAgentComms(agentId, limit)

        suspend fun openPrompts(): ApiResult<List<Prompt>> = shared.openPrompts()

        suspend fun answerPrompt(
            promptId: String,
            answer: Answer,
        ): AnswerOutcome = shared.answerPrompt(promptId, answer)

        suspend fun sendMessage(msg: UserMessage): ApiResult<JsonObject> = shared.sendMessage(msg)

        /** Sube un archivo (≤25 MiB) con progreso en bytes. */
        suspend fun uploadBlob(
            name: String,
            mime: String,
            body: RequestBody,
            onProgress: (sent: Long, total: Long) -> Unit,
        ): ApiResult<BlobInfo> {
            val part =
                MultipartBody
                    .Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", name, ProgressBody(body, onProgress))
                    .build()
            val session = secrets.current() ?: return ApiResult.Http(401, "sin sesión")
            val request =
                runCatching {
                    Request
                        .Builder()
                        .url(session.relayUrl.trimEnd('/') + "/v1/blobs")
                        .header("Authorization", "Bearer ${session.deviceToken}")
                        .post(part)
                        .build()
                }.getOrElse { return ApiResult.Network(it) }
            val response =
                try {
                    uploadClient.newCall(request).await()
                } catch (e: IOException) {
                    return ApiResult.Network(e)
                }
            return response.use { shared.decodeResponse(RelayHttpResponse(it.code, it.body.string()), BlobInfo.serializer()) }
        }
    }

/** `Call.await()` cancelable. */
suspend fun Call.await(): Response =
    suspendCancellableCoroutine { cont ->
        enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    if (!cont.isCancelled) cont.resumeWithException(e)
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    cont.resume(response)
                }
            },
        )
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
        val step = if (total > 0) (total / 100).coerceAtLeast(8192L) else 8192L
        val counting =
            object : ForwardingSink(sink) {
                var written = 0L
                var lastReported = 0L

                override fun write(
                    source: Buffer,
                    byteCount: Long,
                ) {
                    super.write(source, byteCount)
                    written += byteCount
                    if (written == total || written - lastReported >= step) {
                        lastReported = written
                        onProgress(written, total)
                    }
                }
            }.buffer()
        delegate.writeTo(counting)
        counting.flush()
    }
}
