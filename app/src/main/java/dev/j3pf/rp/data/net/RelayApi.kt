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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
import java.io.File
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

        /** Cliente sin callTimeout global para subidas de blobs grandes, con readTimeout acotado. */
        private val uploadClient: OkHttpClient by lazy {
            client
                .newBuilder()
                .callTimeout(0, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
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
            platform: String? = null,
            apnsToken: String? = null,
            apnsEnvironment: String? = null,
        ): ApiResult<OkResponse> = shared.putPush(fcmToken, direct, platform, apnsToken, apnsEnvironment)

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
            return withContext(Dispatchers.IO) {
                try {
                    uploadClient.newCall(request).await().use { response ->
                        shared.decodeResponse(RelayHttpResponse(response.code, response.body.string()), BlobInfo.serializer())
                    }
                } catch (e: IOException) {
                    ApiResult.Network(e)
                }
            }
        }

        /** Downloads a blob previously uploaded by this device into [target], bounded by [maxBytes]. */
        suspend fun downloadBlob(
            blobId: String,
            target: File,
            maxBytes: Long,
        ): ApiResult<File> {
            val session = secrets.current() ?: return ApiResult.Http(401, "sin sesión")
            val encodedId = URLEncoder.encode(blobId, "UTF-8")
            val request =
                runCatching {
                    Request
                        .Builder()
                        .url(session.relayUrl.trimEnd('/') + "/v1/blobs/$encodedId")
                        .header("Authorization", "Bearer ${session.deviceToken}")
                        .get()
                        .build()
                }.getOrElse { return ApiResult.Network(it) }
            return withContext(Dispatchers.IO) {
                val tmp = File(target.parentFile, "${target.name}.tmp-${System.nanoTime()}")
                try {
                    uploadClient.newCall(request).await().use { response ->
                        if (!response.isSuccessful) {
                            return@withContext ApiResult.Http(response.code, response.message)
                        }
                        val contentLen = response.body.contentLength()
                        if (contentLen > maxBytes) {
                            return@withContext ApiResult.Http(413, "Blob demasiado grande")
                        }
                        response.body.byteStream().use { input ->
                            tmp.outputStream().use { output ->
                                val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                                var total = 0L
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val read = input.read(buf)
                                    if (read < 0) break
                                    total += read
                                    if (total > maxBytes) {
                                        return@withContext ApiResult.Http(413, "Blob demasiado grande")
                                    }
                                    output.write(buf, 0, read)
                                }
                            }
                        }
                        coroutineContext.ensureActive()
                        if (target.exists()) target.delete()
                        if (!tmp.renameTo(target)) {
                            tmp.copyTo(target, overwrite = true)
                            tmp.delete()
                        }
                        ApiResult.Ok(target)
                    }
                } catch (e: IOException) {
                    tmp.delete()
                    ApiResult.Network(e)
                } catch (t: Throwable) {
                    tmp.delete()
                    throw t
                }
            }
        }
    }

/** `Call.await()` cancelable; cierra la respuesta si la corrutina se cancela al resumir. */
@OptIn(ExperimentalCoroutinesApi::class)
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
                    if (cont.isActive) {
                        cont.resume(response) { _, _, _ ->
                            runCatching { response.close() }
                        }
                    } else {
                        runCatching { response.close() }
                    }
                }
            },
        )
        cont.invokeOnCancellation { runCatching { cancel() } }
    }

/** Cuerpo que informa de los bytes enviados con throttling (>=5 % o >=100 ms, siempre 0 % y 100 %). */
class ProgressBody(
    private val delegate: RequestBody,
    private val onProgress: (sent: Long, total: Long) -> Unit,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
) : RequestBody() {
    override fun contentType() = delegate.contentType()

    override fun contentLength() = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        val startMs = clockMs()
        onProgress(0L, total)
        var written = 0L
        var lastReportedBytes = 0L
        var lastReportedPct = 0
        var lastReportedMs = startMs
        var reportedFinal = total == 0L

        val counting =
            object : ForwardingSink(sink) {
                override fun write(
                    source: Buffer,
                    byteCount: Long,
                ) {
                    super.write(source, byteCount)
                    written += byteCount
                    if (total > 0L && written >= total) {
                        if (!reportedFinal) {
                            reportedFinal = true
                            lastReportedBytes = written
                            lastReportedPct = 100
                            lastReportedMs = clockMs()
                            onProgress(written, total)
                        }
                        return
                    }
                    val nowMs = clockMs()
                    val pct = if (total > 0L) ((written * 100L) / total).toInt() else -1
                    val pctDeltaMet = total > 0L && (pct - lastReportedPct >= MIN_PERCENT_DELTA)
                    val byteDeltaMet = total <= 0L && (written - lastReportedBytes >= MIN_STEP_BYTES)
                    val timeDeltaMet = (nowMs - lastReportedMs >= MIN_INTERVAL_MS) && (written > lastReportedBytes)
                    if (pctDeltaMet || byteDeltaMet || timeDeltaMet) {
                        lastReportedBytes = written
                        if (pct >= 0) lastReportedPct = pct
                        lastReportedMs = nowMs
                        onProgress(written, total)
                    }
                }
            }.buffer()
        delegate.writeTo(counting)
        counting.flush()
        if (!reportedFinal && written > lastReportedBytes) {
            reportedFinal = true
            onProgress(written, total)
        }
    }

    companion object {
        const val MIN_PERCENT_DELTA = 5
        const val MIN_INTERVAL_MS = 100L
        const val MIN_STEP_BYTES = 8192L
    }
}
