package dev.j3pf.rp.data.net

import dev.j3pf.rp.client.ws.RelaySocketDriver
import dev.j3pf.rp.client.ws.RelaySocketListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** Keeps native Request opaque to shared policy and preserves the supplied client's timeouts. */
internal class OkHttpSocketDriver(
    private val client: OkHttpClient,
) : RelaySocketDriver<Request, WebSocket> {
    override fun connect(
        request: Request,
        listener: RelaySocketListener<WebSocket>,
    ): WebSocket =
        client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response,
                ) = listener.onOpen(webSocket)

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String,
                ) = listener.onText(text)

                override fun onClosing(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String,
                ) {
                    webSocket.close(code, reason)
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String,
                ) = listener.onClosed(code, reason)

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?,
                ) = listener.onFailure(response?.code, t.message.orEmpty())
            },
        )

    override fun send(
        socket: WebSocket,
        text: String,
    ): Boolean = socket.send(text)

    override fun close(
        socket: WebSocket,
        code: Int,
        reason: String,
    ) {
        socket.close(code, reason)
    }

    override fun cancel(socket: WebSocket) = socket.cancel()
}
