package dev.j3pf.rp.client.ws

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.darwin.*
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** URLSession-backed WebSocket adapter; its native task is opaque to common socket policy. */

@OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)


class UrlSessionSocketDriver : RelaySocketDriver<NSURLRequest, NSURLSessionWebSocketTask> {
    private val lock = RelayLock()
    private val states = mutableMapOf<NSURLSessionWebSocketTask, SocketState>()

    override fun connect(
        request: NSURLRequest,
        listener: RelaySocketListener<NSURLSessionWebSocketTask>,
    ): NSURLSessionWebSocketTask {
        val state = SocketState(listener)
        val delegate = SocketDelegate(state)
        val session = NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.defaultSessionConfiguration,
            delegate,
            null,
        )
        val socket = session.webSocketTaskWithRequest(request)
        state.bind(session)
        lock.withLock { states[socket] = state }
        socket.resume()
        return socket
    }

    override fun send(
        socket: NSURLSessionWebSocketTask,
        text: String,
    ): Boolean {
        val state = stateFor(socket) ?: return false
        if (state.open.load() != OPEN || state.terminal.load() != ACTIVE) return false

        socket.sendMessage(NSURLSessionWebSocketMessage(string = text)) { error ->
            if (error != null) {
                val closeCode = socket.closeCode.toInt()
                if (closeCode != 0) {
                    state.close(socket, closeCode, socket.closeReason.toUtf8String(), this::removeState)
                } else {
                    state.fail(socket, error, this::removeState)
                }
            }
        }
        return true
    }

    override fun close(
        socket: NSURLSessionWebSocketTask,
        code: Int,
        reason: String,
    ) {
        val state = removeState(socket) ?: return
        state.open.store(CLOSED)
        state.terminal.store(TERMINAL)
        socket.cancelWithCloseCode(code.toLong(), reason.toNSData())
        state.session?.finishTasksAndInvalidate()
    }

    override fun cancel(socket: NSURLSessionWebSocketTask) {
        val state = removeState(socket) ?: return
        state.open.store(CLOSED)
        state.terminal.store(TERMINAL)
        socket.cancel()
        state.session?.invalidateAndCancel()
    }

    private fun stateFor(socket: NSURLSessionWebSocketTask): SocketState? =
        lock.withLock { states[socket] }

    private fun removeState(socket: NSURLSessionWebSocketTask): SocketState? =
        lock.withLock { states.remove(socket) }

    private inner class SocketDelegate(
        private val state: SocketState,
    ) : NSObject(), NSURLSessionWebSocketDelegateProtocol {
        override fun URLSession(
            session: NSURLSession,
            webSocketTask: NSURLSessionWebSocketTask,
            didOpenWithProtocol: String?,
        ) {
            if (state.terminal.load() != ACTIVE) return
            state.open.store(OPEN)
            state.listener.onOpen(webSocketTask)
            receiveNext(webSocketTask)
            schedulePing(webSocketTask)
        }

        override fun URLSession(
            session: NSURLSession,
            webSocketTask: NSURLSessionWebSocketTask,
            didCloseWithCode: Long,
            reason: NSData?,
        ) {
            state.close(webSocketTask, didCloseWithCode.toInt(), reason.toUtf8String(), this@UrlSessionSocketDriver::removeState)
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didCompleteWithError: NSError?,
        ) {
            val webSocketTask = task as? NSURLSessionWebSocketTask ?: return
            if (didCompleteWithError != null) {
                handleSocketError(webSocketTask, didCompleteWithError)
            } else {
                val closeCode = webSocketTask.closeCode.toInt().takeIf { it != 0 } ?: 1000
                state.close(webSocketTask, closeCode, webSocketTask.closeReason.toUtf8String(), this@UrlSessionSocketDriver::removeState)
            }
        }

        private fun handleSocketError(
            socket: NSURLSessionWebSocketTask,
            error: NSError,
        ) {
            val closeCode = socket.closeCode.toInt()
            if (closeCode != 0) {
                state.close(socket, closeCode, socket.closeReason.toUtf8String(), this@UrlSessionSocketDriver::removeState)
            } else {
                state.fail(socket, error, this@UrlSessionSocketDriver::removeState)
            }
        }

        private fun schedulePing(socket: NSURLSessionWebSocketTask) {
            val delayNs = PING_INTERVAL_SECONDS * NSEC_PER_SEC.toLong()
            val queue = dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delayNs), queue) {
                if (state.open.load() != OPEN || state.terminal.load() != ACTIVE) return@dispatch_after
                val pongReceived = AtomicInt(0)
                val pongDeadlineNs = PONG_TIMEOUT_SECONDS * NSEC_PER_SEC.toLong()
                dispatch_after(dispatch_time(DISPATCH_TIME_NOW, pongDeadlineNs), queue) {
                    if (pongReceived.compareAndSet(0, 1)) {
                        if (state.open.load() == OPEN && state.terminal.load() == ACTIVE) {
                            state.failMessage(socket, "WebSocket ping timeout", this@UrlSessionSocketDriver::removeState)
                        }
                    }
                }
                socket.sendPingWithPongReceiveHandler { error ->
                    if (!pongReceived.compareAndSet(0, 1)) return@sendPingWithPongReceiveHandler
                    if (error != null) {
                        handleSocketError(socket, error)
                    } else {
                        schedulePing(socket)
                    }
                }
            }
        }

        private fun receiveNext(socket: NSURLSessionWebSocketTask) {
            if (state.terminal.load() != ACTIVE) return
            socket.receiveMessageWithCompletionHandler { message, error ->
                when {
                    error != null -> handleSocketError(socket, error)
                    message != null -> {
                        message.string?.let(state.listener::onText)
                        receiveNext(socket)
                    }
                }
            }
        }
    }

    private inner class SocketState(
        val listener: RelaySocketListener<NSURLSessionWebSocketTask>,
    ) {
        val open = AtomicInt(CLOSED)
        val terminal = AtomicInt(ACTIVE)
        var session: NSURLSession? = null
            private set

        fun bind(session: NSURLSession) {
            this.session = session
        }

        fun close(
            socket: NSURLSessionWebSocketTask,
            code: Int,
            reason: String,
            remove: (NSURLSessionWebSocketTask) -> SocketState?,
        ) {
            if (!terminal.compareAndSet(ACTIVE, TERMINAL)) return
            open.store(CLOSED)
            listener.onClosed(code, reason)
            remove(socket)
            session?.finishTasksAndInvalidate()
        }

        fun fail(
            socket: NSURLSessionWebSocketTask,
            error: NSError,
            remove: (NSURLSessionWebSocketTask) -> SocketState?,
        ) {
            failMessage(socket, error.localizedDescription, remove)
        }

        fun failMessage(
            socket: NSURLSessionWebSocketTask,
            message: String,
            remove: (NSURLSessionWebSocketTask) -> SocketState?,
        ) {
            if (!terminal.compareAndSet(ACTIVE, TERMINAL)) return
            open.store(CLOSED)
            val response = socket.response as? NSHTTPURLResponse
            listener.onFailure(response?.statusCode?.toInt(), message)
            remove(socket)
            session?.invalidateAndCancel()
        }
    }

    private companion object {
        const val ACTIVE = 0
        const val OPEN = 1
        const val CLOSED = 0
        const val TERMINAL = 1
        const val PING_INTERVAL_SECONDS = 20L
        const val PONG_TIMEOUT_SECONDS = 15L
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun String.toNSData(): NSData {
    val encoded = encodeToByteArray()
    val data = NSMutableData.dataWithCapacity(encoded.size.toULong())
        ?: throw IllegalStateException("Could not allocate WebSocket close reason")
    if (encoded.isNotEmpty()) {
        val count = encoded.size.toULong()
        encoded.usePinned { data.appendBytes(it.addressOf(0), count) }
    }
    return data
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData?.toUtf8String(): String {
    val byteCount = this?.length?.toInt() ?: return ""
    if (byteCount == 0) return ""
    val pointer = this?.bytes ?: return ""
    return pointer.readBytes(byteCount).decodeToString()
}
