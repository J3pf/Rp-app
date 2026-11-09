package dev.j3pf.rp.push

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.j3pf.rp.BuildConfig
import dev.j3pf.rp.MainActivity
import dev.j3pf.rp.R
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.ReconnectingSocket
import dev.j3pf.rp.data.ports.AppLifecycleMonitor
import dev.j3pf.rp.data.ports.NetworkMonitor
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.MachinesRepository
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.protocol.AgentStatus
import dev.j3pf.rp.protocol.AgentsUpdate
import dev.j3pf.rp.protocol.AppHello
import dev.j3pf.rp.protocol.AppInfo
import dev.j3pf.rp.protocol.AppWelcome
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.PushAck
import dev.j3pf.rp.protocol.PushNotify
import dev.j3pf.rp.protocol.Ulid
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

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

    @Inject lateinit var agents: AgentsRepository

    @Inject lateinit var factory: NotificationFactory

    @Inject lateinit var baseClient: OkHttpClient

    @Inject lateinit var networkMonitor: NetworkMonitor

    @Inject lateinit var lifecycleMonitor: AppLifecycleMonitor

    private lateinit var socket: ReconnectingSocket

    private val screenInteractive = MutableStateFlow(true)
    private val deviceIdleOrSaver = MutableStateFlow(false)
    private val idleStandby = MutableStateFlow(false)
    private var powerReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        channels.ensureServiceChannel()
        goForeground(buildNotification(0, false, false))

        val powerManager = getSystemService(PowerManager::class.java)

        fun refreshPowerState() {
            val interactive = powerManager?.isInteractive ?: true
            val isDoze = powerManager?.isDeviceIdleMode == true
            val isSaver = powerManager?.isPowerSaveMode == true
            screenInteractive.value = interactive
            deviceIdleOrSaver.value = isDoze || (isSaver && !interactive)
            if (interactive) {
                idleStandby.value = false
            }
        }
        refreshPowerState()

        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    refreshPowerState()
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        powerReceiver = receiver

        val client = baseClient.newBuilder().pingInterval(270, TimeUnit.SECONDS).build()
        socket =
            ReconnectingSocket(
                networkMonitor = networkMonitor,
                client = client,
                scope = lifecycleScope,
                requestProvider = {
                    secrets.current()?.let { s ->
                        Request
                            .Builder()
                            .url("${s.wsBase}/ws/app?mode=direct")
                            .header("Authorization", "Bearer ${s.deviceToken}")
                            .build()
                    }
                },
                onSocketOpen = open@{ ws ->
                    val s = secrets.current() ?: return@open
                    ws.send(
                        Envelope.of(MsgType.APP_HELLO, AppHello(s.deviceId, AppInfo(BuildConfig.VERSION_NAME)), id = Ulid.new()).encode(),
                    )
                },
                onSocketText = ::onText,
            )

        lifecycleScope.launch {
            socket.state.collect { st ->
                if (st is ConnState.Fatal) stopSelf()
            }
        }

        val hasActiveWork =
            agents.agents
                .map { list ->
                    list.any {
                        it.status == AgentStatus.WORKING ||
                            it.status == AgentStatus.COMPACTING ||
                            it.openPromptCount > 0
                    }
                }.distinctUntilChanged()

        lifecycleScope.launch {
            combine(lifecycleMonitor.isForeground, screenInteractive, hasActiveWork) { fg, interactive, active ->
                !fg && !interactive && !active
            }.distinctUntilChanged()
                .collectLatest { canSleep ->
                    if (canSleep) {
                        delay(IDLE_STANDBY_TIMEOUT_MS)
                        idleStandby.value = true
                    } else {
                        idleStandby.value = false
                    }
                }
        }

        lifecycleScope.launch {
            combine(
                lifecycleMonitor.isForeground,
                deviceIdleOrSaver,
                idleStandby,
                hasActiveWork,
            ) { inForeground, constrained, standby, active ->
                val sleeping = (constrained && !active) || standby
                !inForeground && !sleeping
            }.distinctUntilChanged()
                .collect { shouldRunDirectSocket ->
                    if (!shouldRunDirectSocket) {
                        socket.stop()
                    } else if (secrets.current() != null) {
                        socket.start()
                        socket.retryNow()
                    }
                }
        }

        lifecycleScope.launch {
            combine(
                socket.state,
                lifecycleMonitor.isForeground,
                machines.machinesState,
                deviceIdleOrSaver,
                idleStandby,
            ) { st, fg, ms, constrained, standby ->
                val connected = (st == ConnState.Connected) || fg
                val sleeping = !fg && !connected && (constrained || standby)
                Triple(connected, ms.size, sleeping)
            }.distinctUntilChanged()
                .collect { (connected, pcs, sleeping) ->
                    val n = buildNotification(pcs, connected, sleeping)
                    runCatching { notify(n) }
                }
        }

        lifecycleScope.launch {
            val currentlyWorking = mutableSetOf<String>()
            agents.agents
                .map { list -> list.filter { it.status == AgentStatus.WORKING || it.status == AgentStatus.COMPACTING } }
                .distinctUntilChanged()
                .collect { working ->
                    val workingIds = working.map { it.agentId }.toSet()

                    val stopped = currentlyWorking - workingIds
                    for (id in stopped) {
                        factory.cancelWorking(id)
                    }

                    for (a in working) {
                        factory.showWorking(a)
                    }

                    currentlyWorking.clear()
                    currentlyWorking.addAll(workingIds)
                }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        if (secrets.current() == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        idleStandby.value = false
        if (!lifecycleMonitor.isForeground.value && !deviceIdleOrSaver.value) {
            socket.start()
            socket.retryNow()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        powerReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        powerReceiver = null
        if (::socket.isInitialized) {
            socket.stop()
        }
        super.onDestroy()
    }

    private fun onText(text: String) {
        val env = Envelope.parseOrNull(text) ?: return
        when (env.t) {
            MsgType.PUSH_NOTIFY ->
                lifecycleScope.launch {
                    idleStandby.value = false
                    val n = runCatching { env.body<PushNotify>() }.getOrNull() ?: return@launch
                    router.handleDirect(n.k, n.body, n.pushId)
                    n.pushId?.let { socket.send(Envelope.of(MsgType.PUSH_ACK, PushAck(it)).encode()) }
                }
            MsgType.PROMPT_OPENED, MsgType.PROMPT_RESOLVED -> {
                if (env.t == MsgType.PROMPT_OPENED) {
                    idleStandby.value = false
                }
                bus.publish(env)
            }
            MsgType.AGENTS_UPDATE ->
                lifecycleScope.launch {
                    val u = runCatching { env.body<AgentsUpdate>() }.getOrNull() ?: return@launch
                    for (a in u.upsert) {
                        if (a.status == AgentStatus.WORKING || a.status == AgentStatus.COMPACTING) {
                            idleStandby.value = false
                            factory.showWorking(a)
                        } else {
                            factory.cancelWorking(a.agentId)
                        }
                    }
                    for (id in u.remove) {
                        factory.cancelWorking(id)
                    }
                }
            MsgType.APP_WELCOME ->
                lifecycleScope.launch {
                    val w = runCatching { env.body<AppWelcome>() }.getOrNull() ?: return@launch
                    for (a in w.agents) {
                        if (a.status == AgentStatus.WORKING || a.status == AgentStatus.COMPACTING) {
                            idleStandby.value = false
                            factory.showWorking(a)
                        } else {
                            factory.cancelWorking(a.agentId)
                        }
                    }
                }
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

    private fun buildNotification(
        pcs: Int,
        connected: Boolean,
        sleeping: Boolean,
    ): Notification {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val text =
            when {
                sleeping -> "RP en reposo · ahorro de batería"
                !connected -> "Reconectando…"
                pcs == 1 -> "RP conectado · 1 PC"
                pcs > 1 -> "RP conectado · $pcs PCs"
                else -> getString(R.string.service_title)
            }
        return NotificationCompat
            .Builder(this, NotificationChannels.SERVICE_CHANNEL)
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
        private const val IDLE_STANDBY_TIMEOUT_MS = 10 * 60_000L

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
