package dev.j3pf.rp.client.ws

import dev.j3pf.rp.data.net.WsReply
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.ErrorBody
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.PROTOCOL_VERSION
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withTimeoutOrNull

/** Correlation policy; secure IDs and the connected socket remain native inputs. */
class RelayWsClient(
    private val isConnected: () -> Boolean,
    private val sendText: (String) -> Boolean,
    private val newId: () -> String,
    private val bus: RelayMessageBus,
) {
    private val lock = RelayLock()
    private val pending = mutableMapOf<String, CompletableDeferred<WsReply>>()
    val messages: SharedFlow<Envelope> get() = bus.messages

    fun send(env: Envelope): Boolean = sendText(env.encode())

    suspend fun request(
        env: Envelope,
        timeoutMs: Long = 10_000,
    ): WsReply {
        if (!isConnected()) return WsReply.NotConnected
        val id = env.id ?: newId()
        val withId = if (env.id == null) env.copy(id = id) else env
        val deferred = CompletableDeferred<WsReply>()
        lock.withLock { pending[id] = deferred }
        if (!sendText(withId.encode())) {
            lock.withLock {
                if (pending[id] === deferred) {
                    pending.remove(id)
                }
            }
            return WsReply.NotConnected
        }
        return try {
            withTimeoutOrNull(timeoutMs) { deferred.await() } ?: WsReply.Timeout
        } finally {
            lock.withLock {
                if (pending[id] === deferred) {
                    pending.remove(id)
                }
            }
        }
    }

    fun onText(text: String) {
        val env = Envelope.parseOrNull(text) ?: return
        if (env.v > PROTOCOL_VERSION) return
        val deferred = env.re?.let { re -> lock.withLock { pending[re] } }
        if (deferred != null) {
            if (env.t == MsgType.ERROR) {
                val error = runCatching { env.body<ErrorBody>() }.getOrNull()
                deferred.complete(WsReply.Error(error?.code ?: "internal", error?.message.orEmpty()))
            } else {
                deferred.complete(WsReply.Ok(env))
            }
        }
        bus.publish(env)
    }

    fun failPending(reply: WsReply) {
        val deferreds =
            lock.withLock {
                pending.values.toList().also { pending.clear() }
            }
        // Completing may resume a request inline; never iterate a live mutable map here.
        deferreds.forEach { it.complete(reply) }
    }
}
