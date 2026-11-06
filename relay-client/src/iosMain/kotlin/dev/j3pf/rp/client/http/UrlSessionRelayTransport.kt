package dev.j3pf.rp.client.http

import kotlinx.cinterop.*
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.*
import platform.darwin.*
import kotlin.coroutines.resume
import dev.j3pf.rp.data.net.ApiResult

/** JSON HTTP adapter backed by Foundation URLSession. */

class UrlSessionRelayTransport : RelayHttpTransport {
    override suspend fun execute(request: RelayHttpRequest): ApiResult<RelayHttpResponse> {
        val url = NSURL.URLWithString(request.url)
            ?: return ApiResult.Network(IllegalArgumentException("Invalid relay URL"))
        val timeout = if (request.timeout == RelayHttpTimeout.QUICK) QUICK_TIMEOUT_SECONDS else DEFAULT_TIMEOUT_SECONDS
        val nativeRequest = NSMutableURLRequest(url, NSURLRequestUseProtocolCachePolicy, timeout)
        nativeRequest.setHTTPMethod(request.method)
        if (request.authorization != null) {
            nativeRequest.setValue(request.authorization, AUTHORIZATION_HEADER)
        }
        if (request.body != null) {
            nativeRequest.setValue(JSON_CONTENT_TYPE, CONTENT_TYPE_HEADER)
            val body = request.body
            nativeRequest.HTTPBody = body.toNSData()
        }

        return suspendCancellableCoroutine { continuation ->
            val delegate = HttpDelegate(continuation)
            val session = NSURLSession.sessionWithConfiguration(
                NSURLSessionConfiguration.defaultSessionConfiguration,
                delegate,
                null,
            )
            delegate.session = session
            val task = session.dataTaskWithRequest(nativeRequest)
            continuation.invokeOnCancellation {
                task.cancel()
                session.invalidateAndCancel()
            }
            task.resume()
        }
    }

    private class HttpDelegate(
        private val continuation: CancellableContinuation<ApiResult<RelayHttpResponse>>,
    ) : NSObject(), NSURLSessionDataDelegateProtocol {
        private val responseChunks = mutableListOf<ByteArray>()
        lateinit var session: NSURLSession

        override fun URLSession(
            session: NSURLSession,
            dataTask: NSURLSessionDataTask,
            didReceiveData: NSData,
        ) {
            responseChunks += didReceiveData.toByteArray()
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didCompleteWithError: NSError?,
        ) {
            val result = when {
                didCompleteWithError != null -> ApiResult.Network(IllegalStateException(didCompleteWithError.localizedDescription))
                task.response !is NSHTTPURLResponse -> ApiResult.Network(IllegalStateException("URLSession returned no HTTP response"))
                else -> {
                    val response = task.response as NSHTTPURLResponse
                    ApiResult.Ok(RelayHttpResponse(response.statusCode.toInt(), responseChunks.toUtf8String()))
                }
            }
            if (continuation.isActive) continuation.resume(result)
            session.finishTasksAndInvalidate()
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 60.0
        const val QUICK_TIMEOUT_SECONDS = 8.0
        const val AUTHORIZATION_HEADER = "Authorization"
        const val CONTENT_TYPE_HEADER = "Content-Type"
        const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun String.toNSData(): NSData {
    val encoded = encodeToByteArray()
    if (encoded.isEmpty()) return NSData.data()
    val size = encoded.size.toULong()
    return encoded.usePinned { pinned -> NSData.dataWithBytes(pinned.addressOf(0), size) } ?: NSData.data()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val byteCount = length.toInt()
    if (byteCount == 0) return ByteArray(0)
    val pointer = bytes ?: return ByteArray(0)
    return pointer.readBytes(byteCount)
}

private fun List<ByteArray>.toUtf8String(): String {
    val combined = ByteArray(sumOf(ByteArray::size))
    var offset = 0
    forEach { chunk ->
        chunk.copyInto(combined, destinationOffset = offset)
        offset += chunk.size
    }
    return combined.decodeToString()
}
