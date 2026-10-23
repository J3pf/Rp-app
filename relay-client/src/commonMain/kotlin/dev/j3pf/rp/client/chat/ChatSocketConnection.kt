package dev.j3pf.rp.client.chat

import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.WsReply
import dev.j3pf.rp.protocol.Envelope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ChatSocketConnection {
    val isConnected: Boolean
    val state: StateFlow<ConnState>
    val messages: Flow<Envelope>

    fun send(env: Envelope): Boolean

    suspend fun request(
        env: Envelope,
        timeoutMs: Long = 10_000,
    ): WsReply
}
