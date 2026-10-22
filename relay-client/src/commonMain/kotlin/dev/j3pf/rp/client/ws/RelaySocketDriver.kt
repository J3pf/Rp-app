package dev.j3pf.rp.client.ws

/** Request is opaque: credentials and transport timeouts belong to the native adapter. */
interface RelaySocketDriver<Request, Socket> {
    fun connect(
        request: Request,
        listener: RelaySocketListener<Socket>,
    ): Socket

    fun send(
        socket: Socket,
        text: String,
    ): Boolean

    fun close(
        socket: Socket,
        code: Int,
        reason: String,
    )

    fun cancel(socket: Socket)
}

interface RelaySocketListener<Socket> {
    fun onOpen(socket: Socket)

    fun onText(text: String)

    fun onClosed(
        code: Int,
        reason: String,
    )

    fun onFailure(
        httpCode: Int?,
        message: String,
    )
}
