package dev.j3pf.rp.client.ws

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.darwin.*
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.TimeSource

/** URLSession-backed WebSocket adapter; its native task is opaque to common socket policy. */

@OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)
class UrlSessionSocketDriver : RelaySocketDriver<NSURLRequest, NSURLSessionWebSocketTask> {
    private val lock = RelayLock()
    private val states = mutableMapOf<NSURLSessionWebSocketTask, SocketState>()
    private val delegate = SharedSocketDelegate()
    private var session: NSURLSession? = null

    private fun sharedSession(): NSURLSession =
        lock.withLock {
            session ?: NSURLSession
                .sessionWithConfiguration(
                    NSURLSessionConfiguration.defaultSessionConfiguration,
                    delegate,
                    null,
                ).also { session = it }
        }

    fun shutdown() {
        val (activeStates, currentSession) =
            lock.withLock {
                val snapshot = states.entries.map { it.key to it.value }
                states.clear()
                val existing = session
                session = null
                snapshot to existing
            }
        for ((socket, state) in activeStates) {
            state.open.store(CLOSED)
            state.terminal.store(TERMINAL)
            state.cancelTimers()
            socket.cancel()
        }
        currentSession?.finishTasksAndInvalidate()
    }

    override fun connect(
        request: NSURLRequest,
        listener: RelaySocketListener<NSURLSessionWebSocketTask>,
    ): NSURLSessionWebSocketTask {
        val state = SocketState(listener)
        val socket = sharedSession().webSocketTaskWithRequest(request)
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
        state.cancelTimers()
        socket.cancelWithCloseCode(code.toLong(), reason.toNSData())
    }

    override fun cancel(socket: NSURLSessionWebSocketTask) {
        val state = removeState(socket) ?: return
        state.open.store(CLOSED)
        state.terminal.store(TERMINAL)
        state.cancelTimers()
        socket.cancel()
    }

    private fun stateFor(socket: NSURLSessionWebSocketTask): SocketState? =
        lock.withLock { states[socket] }

    private fun removeState(socket: NSURLSessionWebSocketTask): SocketState? =
        lock.withLock { states.remove(socket) }

    private inner class SharedSocketDelegate : NSObject(), NSURLSessionWebSocketDelegateProtocol {
        override fun URLSession(
            session: NSURLSession,
            webSocketTask: NSURLSessionWebSocketTask,
            didOpenWithProtocol: String?,
        ) {
            val state = stateFor(webSocketTask) ?: return
            if (state.terminal.load() != ACTIVE) return
            state.open.store(OPEN)
            state.markActivity()
            state.listener.onOpen(webSocketTask)
            receiveNext(webSocketTask, state)
            schedulePing(webSocketTask, state, PING_INTERVAL_MS)
        }

        override fun URLSession(
            session: NSURLSession,
            webSocketTask: NSURLSessionWebSocketTask,
            didCloseWithCode: Long,
            reason: NSData?,
        ) {
            val state = stateFor(webSocketTask) ?: return
            state.close(webSocketTask, didCloseWithCode.toInt(), reason.toUtf8String(), this@UrlSessionSocketDriver::removeState)
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didCompleteWithError: NSError?,
        ) {
            val webSocketTask = task as? NSURLSessionWebSocketTask ?: return
            val state = stateFor(webSocketTask) ?: return
            if (didCompleteWithError != null) {
                handleSocketError(webSocketTask, state, didCompleteWithError)
            } else {
                val closeCode = webSocketTask.closeCode.toInt().takeIf { it != 0 } ?: 1000
                state.close(webSocketTask, closeCode, webSocketTask.closeReason.toUtf8String(), this@UrlSessionSocketDriver::removeState)
            }
        }

        private fun handleSocketError(
            socket: NSURLSessionWebSocketTask,
            state: SocketState,
            error: NSError,
        ) {
            val closeCode = socket.closeCode.toInt()
            if (closeCode != 0) {
                state.close(socket, closeCode, socket.closeReason.toUtf8String(), this@UrlSessionSocketDriver::removeState)
            } else {
                state.fail(socket, error, this@UrlSessionSocketDriver::removeState)
            }
        }

        private fun schedulePing(
            socket: NSURLSessionWebSocketTask,
            state: SocketState,
            delayMs: Long,
        ) {
            if (state.open.load() != OPEN || state.terminal.load() != ACTIVE) return
            val queue = dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)
            val timer =
                createOneShotTimer(delayMs, queue) {
                    if (state.open.load() != OPEN || state.terminal.load() != ACTIVE) return@createOneShotTimer
                    val elapsedMs = state.elapsedSinceActivityMs()
                    if (elapsedMs < PING_INTERVAL_MS) {
                        val remainingMs = (PING_INTERVAL_MS - elapsedMs).coerceAtLeast(1_000L)
                        schedulePing(socket, state, remainingMs)
                        return@createOneShotTimer
                    }

                    val pongReceived = AtomicInt(0)
                    val pongTimer =
                        createOneShotTimer(PONG_TIMEOUT_MS, queue) {
                            state.cancelPongTimer()
                            if (pongReceived.compareAndSet(0, 1)) {
                                if (state.open.load() == OPEN && state.terminal.load() == ACTIVE) {
                                    state.failMessage(socket, "WebSocket ping timeout", this@UrlSessionSocketDriver::removeState)
                                }
                            }
                        }
                    state.replacePongTimer(pongTimer)

                    socket.sendPingWithPongReceiveHandler { error ->
                        state.cancelPongTimer()
                        if (!pongReceived.compareAndSet(0, 1)) return@sendPingWithPongReceiveHandler
                        if (error != null) {
                            handleSocketError(socket, state, error)
                        } else {
                            state.markActivity()
                            schedulePing(socket, state, PING_INTERVAL_MS)
                        }
                    }
                }
            state.replacePingTimer(timer)
        }

        private fun createOneShotTimer(
            delayMs: Long,
            queue: dispatch_queue_t?,
            block: () -> Unit,
        ): dispatch_source_t? {
            val timer = dispatch_source_create(DISPATCH_SOURCE_TYPE_TIMER, 0uL, 0uL, queue) ?: return null
            val delayNs = delayMs.coerceAtLeast(1L) * 1_000_000L
            val start = dispatch_time(DISPATCH_TIME_NOW, delayNs)
            dispatch_source_set_timer(timer, start, DISPATCH_TIME_FOREVER, TIMER_LEEWAY_NS)
            dispatch_source_set_event_handler(timer) {
                if (dispatch_source_testcancel(timer) != 0L) return@dispatch_source_set_event_handler
                dispatch_source_cancel(timer)
                block()
            }
            dispatch_resume(timer)
            return timer
        }

        private fun receiveNext(
            socket: NSURLSessionWebSocketTask,
            state: SocketState,
        ) {
            if (state.terminal.load() != ACTIVE) return
            socket.receiveMessageWithCompletionHandler { message, error ->
                when {
                    error != null -> handleSocketError(socket, state, error)
                    message != null -> {
                        state.markActivity()
                        message.string?.let(state.listener::onText)
                        receiveNext(socket, state)
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
        private val timerLock = RelayLock()
        private var pingTimer: dispatch_source_t? = null
        private var pongTimer: dispatch_source_t? = null
        private var lastActivityMark = TimeSource.Monotonic.markNow()

        fun markActivity() {
            timerLock.withLock {
                lastActivityMark = TimeSource.Monotonic.markNow()
            }
        }

        fun elapsedSinceActivityMs(): Long =
            timerLock.withLock {
                lastActivityMark.elapsedNow().inWholeMilliseconds
            }

        fun replacePingTimer(next: dispatch_source_t?) {
            val (previous, cancelNext) =
                timerLock.withLock {
                    val prev = pingTimer
                    if (terminal.load() != ACTIVE) {
                        pingTimer = null
                        prev to true
                    } else {
                        pingTimer = next
                        prev to false
                    }
                }
            if (previous != null && previous !== next) {
                dispatch_source_cancel(previous)
            }
            if (cancelNext && next != null) {
                dispatch_source_cancel(next)
            }
        }

        fun replacePongTimer(next: dispatch_source_t?) {
            val (previous, cancelNext) =
                timerLock.withLock {
                    val prev = pongTimer
                    if (terminal.load() != ACTIVE) {
                        pongTimer = null
                        prev to true
                    } else {
                        pongTimer = next
                        prev to false
                    }
                }
            if (previous != null && previous !== next) {
                dispatch_source_cancel(previous)
            }
            if (cancelNext && next != null) {
                dispatch_source_cancel(next)
            }
        }

        fun cancelPongTimer() {
            val current =
                timerLock.withLock {
                    val t = pongTimer
                    pongTimer = null
                    t
                }
            if (current != null) {
                dispatch_source_cancel(current)
            }
        }

        fun cancelTimers() {
            val (currentPing, currentPong) =
                timerLock.withLock {
                    val p = pingTimer
                    val po = pongTimer
                    pingTimer = null
                    pongTimer = null
                    p to po
                }
            if (currentPing != null) {
                dispatch_source_cancel(currentPing)
            }
            if (currentPong != null) {
                dispatch_source_cancel(currentPong)
            }
        }

        fun close(
            socket: NSURLSessionWebSocketTask,
            code: Int,
            reason: String,
            remove: (NSURLSessionWebSocketTask) -> SocketState?,
        ) {
            if (!terminal.compareAndSet(ACTIVE, TERMINAL)) return
            open.store(CLOSED)
            cancelTimers()
            listener.onClosed(code, reason)
            remove(socket)
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
            cancelTimers()
            val response = socket.response as? NSHTTPURLResponse
            listener.onFailure(response?.statusCode?.toInt(), message)
            remove(socket)
        }
    }

    private companion object {
        const val ACTIVE = 0
        const val OPEN = 1
        const val CLOSED = 0
        const val TERMINAL = 1
        const val PING_INTERVAL_SECONDS = 45L
        const val PONG_TIMEOUT_SECONDS = 15L
        const val PING_INTERVAL_MS = PING_INTERVAL_SECONDS * 1_000L
        const val PONG_TIMEOUT_MS = PONG_TIMEOUT_SECONDS * 1_000L
        const val TIMER_LEEWAY_NS = 1_000_000_000uL
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun String.toNSData(): NSData {
    val encoded = encodeToByteArray()
    val data =
        NSMutableData.dataWithCapacity(encoded.size.toULong())
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
