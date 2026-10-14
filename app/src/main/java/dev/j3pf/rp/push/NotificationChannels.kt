package dev.j3pf.rp.push

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.R
import dev.j3pf.rp.data.repo.NotifyCategory
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.util.contextEmoji
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Un `NotificationChannelGroup` por PC (`rp.machine.<machineId>`) con sus cinco canales
 * (`rp.<machineId>.approvals|questions|terminal|finished|errors`). Así se puede silenciar la PC de la
 * empresa desde los ajustes del sistema sin afectar a la personal. (PLAN-APP §5.1)
 */
@Singleton
class NotificationChannels @Inject constructor(@ApplicationContext private val context: Context) {
    private val nm: NotificationManager get() = context.getSystemService(NotificationManager::class.java)
    private val ensuredMachines = ConcurrentHashMap<String, String>()

    fun groupId(machineId: String) = "rp.machine.$machineId"
    fun channelId(machineId: String, category: String) = "rp.$machineId.$category"

    /** Canal de la notificación permanente del servicio directo. */
    fun ensureServiceChannel() {
        val ch = NotificationChannel(SERVICE_CHANNEL, context.getString(R.string.channel_service), NotificationManager.IMPORTANCE_MIN)
            .apply {
                description = context.getString(R.string.channel_service_desc)
                setShowBadge(false)
            }
        nm.createNotificationChannel(ch)
    }

    /** Crea (idempotente) el grupo y los canales de una PC; renombra el grupo si cambió el nombre. */
    fun ensureMachine(machineId: String, name: String, contextKind: String = "personal") {
        val fingerprint = "$name|$contextKind"
        if (ensuredMachines[machineId] == fingerprint) return
        val title = "${contextEmoji(contextKind)} $name"
        nm.createNotificationChannelGroup(NotificationChannelGroup(groupId(machineId), title))
        for (cat in NotifyCategory.all) {
            val (label, importance) = when (cat) {
                NotifyCategory.APPROVALS -> context.getString(R.string.channel_approvals) to NotificationManager.IMPORTANCE_HIGH
                NotifyCategory.QUESTIONS -> context.getString(R.string.channel_questions) to NotificationManager.IMPORTANCE_HIGH
                NotifyCategory.TERMINAL -> context.getString(R.string.channel_terminal) to NotificationManager.IMPORTANCE_DEFAULT
                NotifyCategory.FINISHED -> context.getString(R.string.channel_finished) to NotificationManager.IMPORTANCE_DEFAULT
                else -> context.getString(R.string.channel_errors) to NotificationManager.IMPORTANCE_DEFAULT
            }
            val ch = NotificationChannel(channelId(machineId, cat), label, importance).apply {
                group = groupId(machineId)
                if (importance == NotificationManager.IMPORTANCE_HIGH) enableVibration(true)
            }
            nm.createNotificationChannel(ch)
        }
        ensuredMachines[machineId] = fingerprint
    }

    fun sync(machines: List<MachineSummary>) {
        machines.forEach { ensureMachine(it.machineId, it.name, it.context) }
        val keepIds = machines.map { it.machineId }.toSet()
        ensuredMachines.keys.retainAll(keepIds)
        val keep = machines.map { groupId(it.machineId) }.toSet()
        nm.notificationChannelGroups.filter { it.id.startsWith("rp.machine.") && it.id !in keep }
            .forEach { nm.deleteNotificationChannelGroup(it.id) }
    }

    /** Al revocar/olvidar una PC se borra su grupo (y con él sus canales). */
    fun removeMachine(machineId: String) {
        ensuredMachines.remove(machineId)
        nm.deleteNotificationChannelGroup(groupId(machineId))
    }

    fun removeAll() {
        ensuredMachines.clear()
        nm.notificationChannelGroups.filter { it.id.startsWith("rp.machine.") }.forEach { nm.deleteNotificationChannelGroup(it.id) }
        NotificationManagerCompat.from(context).cancelAll()
    }

    /** `true` si el canal `approvals` de alguna PC está silenciado (para el estado en Ajustes). */
    fun approvalsMuted(machineIds: List<String>): Boolean = machineIds.any {
        nm.getNotificationChannel(channelId(it, NotifyCategory.APPROVALS))?.importance == NotificationManager.IMPORTANCE_NONE
    }

    companion object {
        const val SERVICE_CHANNEL = "rp.service"
    }
}
