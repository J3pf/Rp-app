package dev.j3pf.rp.push

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.j3pf.rp.MainActivity
import dev.j3pf.rp.R
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.ReconnectingSocket
import dev.j3pf.rp.data.repo.MachinesRepository
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.BuildConfig
import dev.j3pf.rp.protocol.AppHello
import dev.j3pf.rp.protocol.AppInfo
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.PushAck
import dev.j3pf.rp.protocol.PushNotify
import dev.j3pf.rp.protocol.Ulid
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Canal directo de push (independiente de Google): servicio en primer plano `remoteMessaging` con WebSocket a
 * `/ws/app?mode=direct`, ping cada 4,5 min, reconexión con backoff + `NetworkCallback`. Recibe `push.notify` y
 * responde `push.ack`. Se arranca en `BOOT_COMPLETED` y al abrir la app. (PLAN-APP §4.1)
 *
 * Sólo reenvía al bus los mensajes `prompt.*`: `agents.update`/`app.welcome` de este modo vienen resumidos y
 * sobrescribirían datos completos de la conexión de primer plano.
 */
@AndroidEntryPoint
class DirectPushService : LifecycleService() {
    @Inject lateinit var secrets: SecretStore
    @Inject lateinit var bus: MessageBus
    @Inject lateinit var router: PushRouter
    @Inject lateinit var channels: NotificationChannels
    @Inject lateinit var machines: MachinesRepository
    @Inject lateinit var baseClient: OkHttpClient

    private lateinit var socket: ReconnectingSocket

    override fun onCreate() {
        super.onCreate()
        channels.ensureServiceChannel()
        goForeground(buildNotification(0, false))

        val client = baseClient.newBuilder().pingInterval(270, TimeUnit.SECONDS).build()
        socket = ReconnectingSocket(
            context = this,
            client = client,
            scope = lifecycleScope,
            requestProvider = {
                secrets.current()?.let { s ->
                    Request.Builder().url("${s.wsBase}/ws/app?mode=direct")
                        .header("Authorization", "Bearer ${s.deviceToken}").build()
                }
            },
            onSocketOpen = open@{ ws ->
                val s = secrets.current() ?: return@open
                ws.send(Envelope.of(MsgType.APP_HELLO, AppHello(s.deviceId, AppInfo(BuildConfig.VERSION_NAME)), id = Ulid.new()).encode())
            },
            onSocketText = ::onText,
        )

        lifecycleScope.launch {
            combine(socket.state, machines.machinesState) { st, ms -> st to ms }.collect { (st, ms) ->
                val n = buildNotification(ms.size, st == ConnState.Connected)
                runCatching { notify(n) }
                if (st is ConnState.Fatal) stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (secrets.current() == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        socket.start()
        socket.retryNow()
        return START_STICKY
    }

    override fun onDestroy() {
        socket.stop()
        super.onDestroy()
    }

    private fun onText(text: String) {
        val env = Envelope.parseOrNull(text) ?: return
        when (env.t) {
            MsgType.PUSH_NOTIFY -> lifecycleScope.launch {
                val n = runCatching { env.body<PushNotify>() }.getOrNull() ?: return@launch
                router.handleDirect(n.k, n.body, n.pushId)
                n.pushId?.let { socket.send(Envelope.of(MsgType.PUSH_ACK, PushAck(it)).encode()) }
            }
            MsgType.PROMPT_OPENED, MsgType.PROMPT_RESOLVED -> bus.publish(env)
        }
    }

    private fun goForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun notify(n: Notification) {
        getSystemService(android.app.NotificationManager::class.java).notify(NOTIF_ID, n)
    }

    private fun buildNotification(pcs: Int, connected: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = when {
            !connected -> "Reconectando…"
            pcs == 1 -> "RP conectado · 1 PC"
            pcs > 1 -> "RP conectado · $pcs PCs"
            else -> getString(R.string.service_title)
        }
        return NotificationCompat.Builder(this, NotificationChannels.SERVICE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rp)
            .setContentTitle(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 4200

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, DirectPushService::class.java))
            } // en Android 12+ puede rechazarse desde segundo plano; la app lo reintenta al abrirse
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DirectPushService::class.java))
        }
    }
}
