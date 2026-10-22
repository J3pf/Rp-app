package dev.j3pf.rp.data.net

import dev.j3pf.rp.client.http.RelayHttpRequest
import dev.j3pf.rp.client.http.RelayHttpResponse
import dev.j3pf.rp.client.http.RelayHttpTimeout
import dev.j3pf.rp.client.http.RelayHttpTransport
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Native JSON transport; the existing cancellable Call.await bridge remains the I/O boundary. */
class OkHttpRelayTransport(
    private val client: OkHttpClient,
) : RelayHttpTransport {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** Cliente con timeout corto para las acciones de notificación (8 s, PLAN-APP §5.3). */
    private val quick: OkHttpClient by lazy {
        client.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()
    }

    override suspend fun execute(request: RelayHttpRequest): ApiResult<RelayHttpResponse> {
        val native =
            runCatching {
                Request
                    .Builder()
                    .url(request.url)
                    .apply { request.authorization?.let { header("Authorization", it) } }
                    .method(request.method, request.body?.toRequestBody(jsonType))
                    .build()
            }.getOrElse { return ApiResult.Network(it) }
        val http = if (request.timeout == RelayHttpTimeout.QUICK) quick else client
        val response =
            try {
                http.newCall(native).await()
            } catch (e: IOException) {
                return ApiResult.Network(e)
            }
        return response.use { ApiResult.Ok(RelayHttpResponse(it.code, it.body.string())) }
    }
}
