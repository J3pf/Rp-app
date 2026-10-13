package dev.j3pf.rp.data.net

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.BuildConfig
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.di.AppScope
import dev.j3pf.rp.protocol.AppHello
import dev.j3pf.rp.protocol.AppInfo
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.ErrorBody
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.PROTOCOL_VERSION
import dev.j3pf.rp.protocol.Ulid
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/** Resultado de una petición con `id` sobre el WS. */
sealed interface WsReply {
    data class Ok(val envelope: Envelope) : WsReply
    data class Error(val code: String, val message: String) : WsReply
    data object Timeout : WsReply
    data object NotConnected : WsReply
}

/**
 * WebSocket completo `/ws/app?mode=foreground` mientras la app está en primer plano (`ProcessLifecycleOwner`).
 * Expone [messages] (bus compartido) y [state]; las peticiones con `id` se resuelven con un
 * `CompletableDeferred` por id y timeout de 10 s.
 */
@Singleton
class RelayConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secrets: SecretStore,
    private val bus: MessageBus,
    client: OkHttpClient,
    @AppScope private val scope: CoroutineScope,
) {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<WsReply>>()

    private val socket = ReconnectingSocket(
        context = context,
        client = client,
        scope = scope,
        requestProvider = {
            secrets.current()?.let { s ->
                Request.Builder().url("${s.wsBase}/ws/app?mode=foreground")
                    .header("Authorization", "Bearer ${s.deviceToken}").build()
            }
        },
        onSocketOpen = open@{ ws ->
            val s = secrets.current() ?: return@open
            ws.send(
                Envelope.of(
                    MsgType.APP_HELLO,
                    AppHello(s.deviceId, AppInfo(BuildConfig.VERSION_NAME)),
                    id = Ulid.new(),
                ).encode(),
            )
        },
        onSocketText = ::onText,
        onSocketClosed = { failPending(WsReply.NotConnected) },
    )

    val state: StateFlow<ConnState> get() = socket.state
    val messages: SharedFlow<Envelope> get() = bus.messages
    val isConnected: Boolean get() = socket.isConnected

    /** Engancha el ciclo de vida del proceso y reinicia al cambiar la sesión. Llamar una vez desde `RpApp`. */
    fun attach() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = start()
            override fun onStop(owner: LifecycleOwner) = stop()
        })
        secrets.session.distinctUntilChanged { a, b -> a?.deviceToken == b?.deviceToken && a?.relayUrl == b?.relayUrl }
            .onEach {
                socket.stop()
                if (it != null && ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                    socket.start()
                }
            }
            .launchIn(scope)
    }

    fun start() {
        if (secrets.current() != null) socket.start()
    }

    fun stop() = socket.stop()
    fun retryNow() = socket.retryNow()

    /** Envía sin esperar respuesta. */
    fun send(env: Envelope): Boolean = socket.send(env.encode())

    /** Envía y espera la respuesta cuyo `re` coincide con el `id` (timeout 10 s). */
    suspend fun request(env: Envelope, timeoutMs: Long = 10_000): WsReply {
        if (!socket.isConnected) return WsReply.NotConnected
        val id = env.id ?: Ulid.new()
        val withId = if (env.id == null) env.copy(id = id) else env
        val deferred = CompletableDeferred<WsReply>()
        pending[id] = deferred
        if (!socket.send(withId.encode())) {
            pending.remove(id)
            return WsReply.NotConnected
        }
        val reply = withTimeoutOrNull(timeoutMs) { deferred.await() } ?: WsReply.Timeout
        pending.remove(id)
        return reply
    }

    private fun onText(text: String) {
        val env = Envelope.parseOrNull(text) ?: return
        if (env.v > PROTOCOL_VERSION) return
        val re = env.re
        if (re != null) {
            val d = pending[re]
            if (d != null) {
                if (env.t == MsgType.ERROR) {
                    val e = runCatching { env.body<ErrorBody>() }.getOrNull()
                    d.complete(WsReply.Error(e?.code ?: "internal", e?.message.orEmpty()))
                } else d.complete(WsReply.Ok(env))
            }
        }
        bus.publish(env)
    }

    private fun failPending(reply: WsReply) {
        val it = pending.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.value.complete(reply)
            it.remove()
        }
    }
}
