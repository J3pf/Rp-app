package dev.j3pf.rp.client.http

import dev.j3pf.rp.data.net.ApiResult

/** A request-local snapshot, never persisted or cached by the shared client. */
class RelayCredentials(
    val relayUrl: String,
    val deviceToken: String,
)

enum class RelayHttpTimeout {
    DEFAULT,
    QUICK,
}

/** JSON requests only. Streaming multipart uploads stay in the native adapter. No credential-bearing toString. */
class RelayHttpRequest(
    val url: String,
    val method: String,
    val body: String? = null,
    val authorization: String? = null,
    val timeout: RelayHttpTimeout = RelayHttpTimeout.DEFAULT,
)

class RelayHttpResponse(
    val code: Int,
    val body: String,
)

/** Implementations retain native URL validation, I/O failures and cancellation. */
fun interface RelayHttpTransport {
    suspend fun execute(request: RelayHttpRequest): ApiResult<RelayHttpResponse>
}
