package dev.j3pf.rp.client.http

import dev.j3pf.rp.client.ws.RelayLock
import dev.j3pf.rp.data.net.ApiResult
import kotlinx.cinterop.*
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.*
import platform.darwin.*
import kotlin.coroutines.resume

/** JSON HTTP adapter backed by a reused Foundation URLSession with resource timeout and body size cap. */

class UrlSessionRelayTransport : RelayHttpTransport {
    private val delegate = SharedHttpDelegate()
    private val session: NSURLSession =
        NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.defaultSessionConfiguration.apply {
                timeoutIntervalForRequest = DEFAULT_TIMEOUT_SECONDS
                timeoutIntervalForResource = RESOURCE_TIMEOUT_SECONDS
            },
            delegate,
            null,
        )

    fun close() {
        session.finishTasksAndInvalidate()
    }

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
            val task = session.dataTaskWithRequest(nativeRequest)
            delegate.register(task, continuation)
            continuation.invokeOnCancellation {
                delegate.remove(task)
                task.cancel()
            }
            task.resume()
        }
    }

    private class TaskState(
        val continuation: CancellableContinuation<ApiResult<RelayHttpResponse>>,
    ) {
        val responseChunks = mutableListOf<ByteArray>()
        var totalBytes: Int = 0
        var exceededLimit: Boolean = false
    }

    private class SharedHttpDelegate : NSObject(), NSURLSessionDataDelegateProtocol {
        private val lock = RelayLock()
        private val tasks = mutableMapOf<NSURLSessionTask, TaskState>()

        fun register(
            task: NSURLSessionTask,
            continuation: CancellableContinuation<ApiResult<RelayHttpResponse>>,
        ) {
            lock.withLock {
                tasks[task] = TaskState(continuation)
            }
        }

        fun remove(task: NSURLSessionTask): TaskState? =
            lock.withLock {
                tasks.remove(task)
            }

        override fun URLSession(
            session: NSURLSession,
            dataTask: NSURLSessionDataTask,
            didReceiveData: NSData,
        ) {
            val chunk = didReceiveData.toByteArray()
            var cancelForSize = false
            lock.withLock {
                val state = tasks[dataTask] ?: return@withLock
                if (state.exceededLimit) return@withLock
                val nextSize = state.totalBytes.toLong() + chunk.size.toLong()
                if (nextSize > MAX_RESPONSE_BYTES.toLong()) {
                    state.exceededLimit = true
                    state.responseChunks.clear()
                    cancelForSize = true
                } else {
                    state.responseChunks += chunk
                    state.totalBytes = nextSize.toInt()
                }
            }
            if (cancelForSize) {
                dataTask.cancel()
            }
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didCompleteWithError: NSError?,
        ) {
            val state = remove(task) ?: return
            val result =
                when {
                    state.exceededLimit -> {
                        ApiResult.Network(IllegalStateException("Response body exceeded $MAX_RESPONSE_BYTES bytes limit"))
                    }
                    didCompleteWithError != null -> {
                        ApiResult.Network(IllegalStateException(didCompleteWithError.localizedDescription))
                    }
                    task.response !is NSHTTPURLResponse -> {
                        ApiResult.Network(IllegalStateException("URLSession returned no HTTP response"))
                    }
                    else -> {
                        val response = task.response as NSHTTPURLResponse
                        ApiResult.Ok(RelayHttpResponse(response.statusCode.toInt(), state.responseChunks.toUtf8String()))
                    }
                }
            if (state.continuation.isActive) {
                state.continuation.resume(result)
            }
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 60.0
        const val RESOURCE_TIMEOUT_SECONDS = 60.0
        const val QUICK_TIMEOUT_SECONDS = 8.0
        const val MAX_RESPONSE_BYTES = 16 * 1024 * 1024
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
