package dev.j3pf.rp.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.MainActivity
import dev.j3pf.rp.R
import dev.j3pf.rp.data.repo.NotifyCategory
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.protocol.PushKind
import dev.j3pf.rp.protocol.PushPromptBody
import dev.j3pf.rp.protocol.PushSettledBody
import dev.j3pf.rp.protocol.str
import dev.j3pf.rp.util.agentTitle
import dev.j3pf.rp.util.contextEmoji
import dev.j3pf.rp.util.lastSegment
import dev.j3pf.rp.util.stableId
import dev.j3pf.rp.util.truncate
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Construye todas las notificaciones (ambos canales de push llaman aquí).
 *
 *  - confirm → [Permitir] [Denegar]; select ≤3 opciones → una acción por opción; >3 o questionnaire → abre la hoja.
 *  - input / allowFreeText → acción "Responder" con `RemoteInput`.
 *  - `setAuthenticationRequired(true)` en acciones de aprobación si el ajuste está activo (API 31+).
 *  - Agrupadas por agente con resumen; `setTimeoutAfter` = `expiresAt`.
 *  - Al asentar un prompt ([cancelPrompt]) se retira el child, su resumen huérfano y la clave del deduper.
 */
@Singleton
class NotificationFactory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val channels: NotificationChannels,
        private val settings: SettingsRepository,
        private val deduper: NotificationDeduper,
    ) {
        private val nm get() = NotificationManagerCompat.from(context)
        private val activeWorkingAgents = ConcurrentHashMap<String, AgentSummary>()
        private val workingOrder = CopyOnWriteArrayList<String>()
        private val workingStartTimes = ConcurrentHashMap<String, Long>()

        @Volatile
        private var currentWorkingNotifId: Int? = null

        @Volatile
        private var currentWorkingSignature: String? = null

        @Volatile
        private var foregroundSlotId: Int? = null

        @Volatile
        private var onForegroundSlotIdle: (() -> Unit)? = null

        fun bindForegroundSlot(
            notifId: Int,
            onIdle: () -> Unit,
        ) {
            foregroundSlotId = notifId
            onForegroundSlotIdle = onIdle
        }

        fun unbindForegroundSlot() {
            foregroundSlotId = null
            onForegroundSlotIdle = null
        }

        fun hasWorkingAgents(): Boolean = activeWorkingAgents.isNotEmpty()

        suspend fun requireUnlockToApprove(): Boolean = settings.current().requireUnlockToApprove

        // ───────────────────────────── Prompt ─────────────────────────────

        @SuppressLint("MissingPermission") // Guarded by canNotify() which checks POST_NOTIFICATIONS on API 33+
        suspend fun showPrompt(p: PushPromptBody) {
            if (!canNotify()) return
            // Si el agente está preguntando no está "terminado": el aviso anterior es basura en la bandeja.
            nm.cancel(SETTLED_TAG, stableId("settled:${p.agentId}"))
            pruneOrphanSummaries()
            val s = settings.settings.first()
            val machineName = p.machineName.ifEmpty { "RP" }
            channels.ensureMachine(p.machineId.ifEmpty { "default" }, machineName, p.machineContext)
            val isPermission = p.category == PromptCategory.PERMISSION
            val category = if (isPermission) NotifyCategory.APPROVALS else NotifyCategory.QUESTIONS
            val channel = channels.channelId(p.machineId.ifEmpty { "default" }, category)
            val id = notificationId(p.promptId)
            val authRequired =
                s.requireUnlockToApprove &&
                    (isPermission || p.danger == Danger.HIGH)

            val open = openIntent(p.promptId, p.agentId, id)
            val contentTitle = "${contextEmoji(p.machineContext)} $machineName · ${p.agentLabel.ifEmpty { "agente" }}".trim()
            val builder =
                NotificationCompat
                    .Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_stat_rp)
                    .setContentTitle(contentTitle)
                    .setContentText(p.title)
                    .setStyle(
                        NotificationCompat
                            .BigTextStyle()
                            .bigText(
                                listOfNotNull(p.title.takeIf { it.isNotBlank() }, p.message?.takeIf { it.isNotBlank() }).joinToString("\n"),
                            ),
                    ).setContentIntent(open)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(false)
                    .setOnlyAlertOnce(true)
                    .setGroup(groupKey(p.agentId))
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true)
            p.expiresAt?.let { exp ->
                val remaining = exp - System.currentTimeMillis()
                if (remaining > 0) builder.setTimeoutAfter(remaining)
            }
            if (p.danger == Danger.HIGH || isPermission) {
                if (p.danger == Danger.HIGH) {
                    builder.setColor(0xFFB3261E.toInt())
                }
                builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                val publicNotif =
                    NotificationCompat
                        .Builder(context, channel)
                        .setSmallIcon(R.drawable.ic_stat_rp)
                        .setContentTitle(contentTitle)
                        .setContentText(context.getString(R.string.channel_approvals))
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .build()
                builder.setPublicVersion(publicNotif)
            }

            // Acciones
            when {
                p.kind == PromptKind.QUESTIONNAIRE -> {
                    builder.addAction(openAction(R.string.action_open, open))
                }

                p.kind == PromptKind.CONFIRM && p.options.isEmpty() -> {
                    builder.addAction(
                        answerAction(R.string.action_allow, p, id, PromptActionReceiver.ANSWER_CONFIRM_YES, null, authRequired),
                    )
                    builder.addAction(answerAction(R.string.action_deny, p, id, PromptActionReceiver.ANSWER_CONFIRM_NO, null, authRequired))
                }

                p.options.isNotEmpty() && p.options.size <= 3 && !(p.kind == PromptKind.INPUT) -> {
                    p.options.forEachIndexed { i, opt ->
                        builder.addAction(answerActionRaw(opt.label, p, id, PromptActionReceiver.ANSWER_OPTION, opt.id, authRequired, i))
                    }
                }

                else -> {
                    builder.addAction(openAction(R.string.action_open, open))
                }
            }
            if ((p.kind == PromptKind.INPUT || p.allowFreeText) && p.kind != PromptKind.QUESTIONNAIRE) {
                builder.addAction(replyAction(p, id, authRequired))
            }
            // Se avisa la primera vez con sonido; cada actualización posterior es silenciosa.
            nm.notify(PROMPT_TAG, id, builder.build())
            postAgentSummary(p.agentId, channel, p.agentLabel.ifEmpty { "agente" })
        }

        private fun answerAction(
            label: Int,
            p: PushPromptBody,
            id: Int,
            kind: String,
            optionId: String?,
            auth: Boolean,
        ) = answerActionRaw(context.getString(label), p, id, kind, optionId, auth, kind.hashCode())

        private fun answerActionRaw(
            label: String,
            p: PushPromptBody,
            id: Int,
            kind: String,
            optionId: String?,
            auth: Boolean,
            salt: Int,
        ): NotificationCompat.Action {
            val intent =
                Intent(context, PromptActionReceiver::class.java).apply {
                    action = PromptActionReceiver.ACTION_ANSWER
                    putExtra(PromptActionReceiver.EXTRA_PROMPT_ID, p.promptId)
                    putExtra(PromptActionReceiver.EXTRA_NOTIF_ID, id)
                    putExtra(PromptActionReceiver.EXTRA_AGENT_LABEL, p.agentLabel)
                    putExtra(PromptActionReceiver.EXTRA_ANSWER_KIND, kind)
                    putExtra(PromptActionReceiver.EXTRA_AUTH_REQUIRED, auth)
                    optionId?.let { putExtra(PromptActionReceiver.EXTRA_OPTION_ID, it) }
                }
            val pi =
                PendingIntent.getBroadcast(
                    context,
                    id * 31 + salt,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            return NotificationCompat.Action
                .Builder(0, label, pi)
                .also { b ->
                    if (auth && Build.VERSION.SDK_INT >= 31) b.setAuthenticationRequired(true)
                }.build()
        }

        private fun replyAction(
            p: PushPromptBody,
            id: Int,
            auth: Boolean,
        ): NotificationCompat.Action {
            val remote = RemoteInput.Builder(PromptActionReceiver.KEY_TEXT).setLabel(context.getString(R.string.notif_reply_hint)).build()
            val intent =
                Intent(context, PromptActionReceiver::class.java).apply {
                    action = PromptActionReceiver.ACTION_ANSWER
                    putExtra(PromptActionReceiver.EXTRA_PROMPT_ID, p.promptId)
                    putExtra(PromptActionReceiver.EXTRA_NOTIF_ID, id)
                    putExtra(PromptActionReceiver.EXTRA_AGENT_LABEL, p.agentLabel)
                    putExtra(PromptActionReceiver.EXTRA_ANSWER_KIND, PromptActionReceiver.ANSWER_TEXT)
                    putExtra(PromptActionReceiver.EXTRA_AUTH_REQUIRED, auth)
                }
            // RemoteInput necesita un PendingIntent mutable para que el sistema rellene el texto.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(context, id * 31 + 7, intent, flags)
            return NotificationCompat.Action
                .Builder(0, context.getString(R.string.action_reply), pi)
                .addRemoteInput(remote)
                .setAllowGeneratedReplies(false)
                .also { if (auth && Build.VERSION.SDK_INT >= 31) it.setAuthenticationRequired(true) }
                .build()
        }

        private fun openAction(
            label: Int,
            pi: PendingIntent,
        ) = NotificationCompat.Action.Builder(0, context.getString(label), pi).build()

        // ───────────────────────────── Otros tipos ─────────────────────────────

        /** `k=settled`: el agente terminó; acción "Responder" (RemoteInput → `POST /v1/agents/{id}/messages`). */
        @SuppressLint("MissingPermission") // Guarded by canNotify() which checks POST_NOTIFICATIONS on API 33+
        suspend fun showSettled(s: PushSettledBody) {
            if (!canNotify()) return
            cancelWorking(s.agentId)
            val machineId = s.machineId.ifEmpty { "default" }
            val channel = channels.channelId(machineId, NotifyCategory.FINISHED)
            channels.ensureMachine(machineId, s.machineName.ifEmpty { "RP" }, s.machineContext)
            val id = stableId("settled:${s.agentId}")
            val requireUnlock = settings.settings.first().requireUnlockToApprove
            val auth = requireUnlock && Build.VERSION.SDK_INT >= 31
            val remote = RemoteInput.Builder(PromptActionReceiver.KEY_TEXT).setLabel(context.getString(R.string.notif_reply_hint)).build()
            val reply =
                Intent(context, PromptActionReceiver::class.java).apply {
                    action = PromptActionReceiver.ACTION_REPLY_AGENT
                    putExtra(PromptActionReceiver.EXTRA_AGENT_ID, s.agentId)
                    putExtra(PromptActionReceiver.EXTRA_NOTIF_ID, id)
                    putExtra(PromptActionReceiver.EXTRA_AGENT_LABEL, s.agentLabel)
                    putExtra(PromptActionReceiver.EXTRA_AUTH_REQUIRED, requireUnlock)
                }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val replyPi = PendingIntent.getBroadcast(context, id, reply, flags)
            val rawBody = s.preview ?: s.message ?: "Terminó"
            val bodyText = cleanPreview(rawBody, maxLen = 140) ?: "Terminó"
            val n =
                NotificationCompat
                    .Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_stat_rp)
                    .setContentTitle(
                        "${contextEmoji(s.machineContext)} ${s.machineName.ifEmpty { "RP" }} · ${s.agentLabel.ifEmpty { "agente" }}".trim(),
                    ).setContentText(bodyText)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
                    .setContentIntent(openIntent(null, s.agentId, id))
                    .setAutoCancel(true)
                    .setGroup(SETTLED_GROUP_KEY)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .addAction(
                        NotificationCompat.Action
                            .Builder(0, context.getString(R.string.action_reply), replyPi)
                            .addRemoteInput(remote)
                            .also { if (auth) it.setAuthenticationRequired(true) }
                            .build(),
                    ).build()
            nm.notify(SETTLED_TAG, id, n)
            postSettledSummaryIfNeeded(channel)
        }

        /** `k=error` y `k=terminal_wait`: aviso simple que abre el chat del agente. */
        @SuppressLint("MissingPermission") // Guarded by canNotify() which checks POST_NOTIFICATIONS on API 33+
        suspend fun showSimple(
            kind: String,
            body: JsonObject,
        ) {
            if (!canNotify()) return
            val agentId = body.str("agentId") ?: return
            val machineId = body.str("machineId")?.ifEmpty { null } ?: "default"
            val machineName = body.str("machineName") ?: "RP"
            channels.ensureMachine(machineId, machineName, body.str("machineContext") ?: "personal")
            val category = if (kind == PushKind.TERMINAL_WAIT) NotifyCategory.TERMINAL else NotifyCategory.ERRORS
            val label = body.str("agentLabel") ?: "agente"
            val text =
                if (kind == PushKind.TERMINAL_WAIT) {
                    "Esperando tu respuesta en la terminal" + (body.str("title")?.let { ": $it" } ?: "")
                } else {
                    body.str("message") ?: body.str("title") ?: "El agente tuvo un error"
                }
            val id = stableId("$kind:$agentId")
            val n =
                NotificationCompat
                    .Builder(context, channels.channelId(machineId, category))
                    .setSmallIcon(R.drawable.ic_stat_rp)
                    .setContentTitle("${contextEmoji(body.str("machineContext") ?: "personal")} $machineName · $label".trim())
                    .setContentText(truncate(text, 140))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(openIntent(null, agentId, id))
                    .setAutoCancel(true)
                    .build()
            nm.notify(SIMPLE_TAG, id, n)
        }

        suspend fun reconcileWorkingAgents(working: List<AgentSummary>) {
            val workingIds = working.map { it.agentId }.toSet()
            val stopped = activeWorkingAgents.keys.toSet() - workingIds
            for (id in stopped) {
                cancelWorking(id)
            }
            for (a in working) {
                showWorking(a)
            }
        }

        /**
         * Agente(s) en ejecución: Live Activity promocionada (Android 16 ProgressStyle / One UI 8) con 3 líneas
         * visibles (actividad en vivo, proyecto/rama/PC y prompt o resumen de múltiples agentes) + barra de progreso.
         */
        @SuppressLint("MissingPermission") // Guarded by canNotify() which checks POST_NOTIFICATIONS on API 33+
        suspend fun showWorking(agent: AgentSummary) {
            if (agent.agentId in settings.current().mutedAgents) {
                cancelWorking(agent.agentId)
                return
            }
            if (!canNotify()) return
            // Si el agente volvió a trabajar, cualquier notificación previa de "Terminó" es obsoleta.
            nm.cancel(SETTLED_TAG, stableId("settled:${agent.agentId}"))
            pruneOrphanSummaries()

            val prev = activeWorkingAgents[agent.agentId]
            val merged =
                if (prev == null) {
                    agent
                } else {
                    agent.copy(
                        sessionName = if (agent.redacted) null else (agent.sessionName?.takeIf { it.isNotBlank() } ?: prev.sessionName),
                        cwd = agent.cwd?.takeIf { it.isNotBlank() } ?: prev.cwd,
                        gitBranch = if (agent.redacted) null else (agent.gitBranch?.takeIf { it.isNotBlank() } ?: prev.gitBranch),
                        activeTask = if (agent.redacted) null else (agent.activeTask?.takeIf { it.isNotBlank() } ?: prev.activeTask),
                        lastMessagePreview =
                            if (agent.redacted) {
                                null
                            } else {
                                agent.lastMessagePreview?.takeIf { it.isNotBlank() } ?: prev.lastMessagePreview
                            },
                        todoTotal = if (agent.todoTotal > 0) agent.todoTotal else prev.todoTotal,
                        todoCompleted = if (agent.todoTotal > 0) agent.todoCompleted else prev.todoCompleted,
                    )
                }
            activeWorkingAgents[agent.agentId] = merged
            workingStartTimes.putIfAbsent(agent.agentId, System.currentTimeMillis())
            if (!workingOrder.contains(agent.agentId)) {
                workingOrder.add(0, agent.agentId)
            } else if (!agent.activeTask.isNullOrBlank() && agent.activeTask != prev?.activeTask) {
                workingOrder.remove(agent.agentId)
                workingOrder.add(0, agent.agentId)
            }

            val machineId = merged.machineId.ifEmpty { "default" }
            val machineName = merged.machineName.ifEmpty { "RP" }
            channels.ensureMachine(machineId, machineName, merged.machineContext)
            renderWorkingNotification()
        }

        fun cancelWorking(agentId: String) {
            val id = stableId("working:$agentId")
            val removed = activeWorkingAgents.remove(agentId) != null
            workingOrder.remove(agentId)
            workingStartTimes.remove(agentId)
            val slotId = foregroundSlotId

            if (activeWorkingAgents.isEmpty()) {
                val activeId = currentWorkingNotifId
                currentWorkingNotifId = null
                currentWorkingSignature = null
                if (activeId != null && activeId != slotId) {
                    nm.cancel(WORKING_TAG, activeId)
                }
                if (removed || isWorkingNotificationActive(id)) {
                    nm.cancel(WORKING_TAG, id)
                }
                cancelOtherWorkingNotifications(keepId = -1)
                if (slotId != null) {
                    onForegroundSlotIdle?.invoke()
                }
            } else {
                if (id != currentWorkingNotifId) {
                    nm.cancel(WORKING_TAG, id)
                }
                if (canNotify()) {
                    renderWorkingNotification()
                }
            }
        }

        @SuppressLint("MissingPermission") // Guarded by canNotify() which checks POST_NOTIFICATIONS on API 33+
        private fun renderWorkingNotification() {
            val allWorking =
                workingOrder.mapNotNull { activeWorkingAgents[it] }.ifEmpty {
                    activeWorkingAgents.values.toList()
                }
            val primary = allWorking.firstOrNull() ?: return
            val machineId = primary.machineId.ifEmpty { "default" }
            val machineName = primary.machineName.ifEmpty { "RP" }
            val channel = channels.channelId(machineId, NotifyCategory.TERMINAL)
            val slotId = foregroundSlotId
            val id = slotId ?: stableId("working:${primary.agentId}")
            val targetTag = if (slotId != null) null else WORKING_TAG
            val rawParts = parseAgentParts(primary)
            val parts = if (primary.redacted) rawParts.copy(topic = null) else rawParts
            val branch = if (primary.redacted) "" else primary.gitBranch?.takeIf { it.isNotBlank() }.orEmpty()
            val hasTodos = allWorking.any { it.todoTotal > 0 }
            val totalTodos = allWorking.sumOf { it.todoTotal.coerceAtLeast(0) }
            val doneTodos = allWorking.sumOf { it.todoCompleted.coerceIn(0, it.todoTotal.coerceAtLeast(0)) }

            val rawTask =
                if (primary.redacted) {
                    null
                } else {
                    primary.activeTask?.takeIf { it.isNotBlank() }
                        ?: primary.lastMessagePreview?.takeIf { it.isNotBlank() }
                }
            val cleanTask = cleanPreview(rawTask, maxLen = 100)

            val title: String
            val subText: String
            val text: String
            val expandedText: String
            val shortCritical: String

            if (allWorking.size == 1) {
                title = cleanTask ?: parts.topic ?: "Trabajando en ${parts.project}…"
                subText =
                    buildString {
                        append(parts.project)
                        if (branch.isNotEmpty()) append(" · ⎇ $branch")
                        append(" · $machineName")
                    }
                val todoPrefix = if (hasTodos) "Tareas: $doneTodos/$totalTodos · " else ""
                text =
                    when {
                        parts.topic != null -> "${todoPrefix}Tú: ${parts.topic}"
                        cleanTask != null ->
                            if (hasTodos) {
                                "Tareas: $doneTodos/$totalTodos · $cleanTask"
                            } else {
                                cleanTask
                            }
                        else -> "${todoPrefix}${context.getString(R.string.agent_working_desc)}"
                    }
                expandedText =
                    buildString {
                        append(text)
                        if (parts.topic != null && cleanTask != null && cleanTask != title) {
                            append("\n")
                            append(cleanTask)
                        }
                    }
                shortCritical =
                    if (hasTodos) {
                        "${truncate(parts.project, 10)} $doneTodos/$totalTodos"
                    } else {
                        truncate(parts.project, 12)
                    }
            } else {
                val otherProjects = allWorking.drop(1).map { parseAgentParts(it).project }.distinct()
                val primaryActivity = cleanTask ?: parts.topic ?: "Trabajando…"
                title = "⚡ ${allWorking.size} agentes activos · ${parts.project}: $primaryActivity"
                subText =
                    buildString {
                        append(parts.project)
                        if (branch.isNotEmpty()) append(" (⎇ $branch)")
                        if (otherProjects.isNotEmpty()) {
                            append(" · +${allWorking.size - 1} en curso: ")
                            append(otherProjects.joinToString(", "))
                        }
                    }
                text =
                    allWorking.joinToString(" · ") { a ->
                        val p = parseAgentParts(a)
                        val t =
                            if (a.redacted) {
                                "trabajando"
                            } else {
                                cleanPreview(a.activeTask, 36)
                                    ?: cleanPreview(a.lastMessagePreview, 36)
                                    ?: p.topic
                                    ?: "trabajando"
                            }
                        "${p.project}: ${truncate(t, 36)}"
                    }
                expandedText =
                    allWorking.joinToString("\n") { a ->
                        val p = parseAgentParts(a)
                        val branchName = if (a.redacted) null else a.gitBranch?.takeIf { it.isNotBlank() }
                        val b = if (branchName != null) " (⎇ $branchName)" else ""
                        val t =
                            if (a.redacted) {
                                "Trabajando…"
                            } else {
                                cleanPreview(a.activeTask, 70)
                                    ?: cleanPreview(a.lastMessagePreview, 70)
                                    ?: p.topic
                                    ?: "Trabajando…"
                            }
                        "• ${p.project}$b: $t"
                    }
                shortCritical = "${allWorking.size} agentes"
            }

            val signature = "$channel|$targetTag|$id|${allWorking.size}|$title|$subText|$text|$doneTodos/$totalTodos"
            if (currentWorkingSignature == signature &&
                currentWorkingNotifId == id &&
                isNotificationActive(targetTag, id)
            ) {
                return
            }

            val prevId = currentWorkingNotifId
            if (prevId != null && prevId != id && prevId != slotId) {
                nm.cancel(WORKING_TAG, prevId)
            }
            cancelOtherWorkingNotifications(keepId = if (slotId != null) -1 else id)

            val open = openIntent(null, primary.agentId, id)
            val startTime = workingStartTimes[primary.agentId] ?: System.currentTimeMillis()
            val isPromotedCapable = Build.VERSION.SDK_INT >= 35

            val builder =
                NotificationCompat
                    .Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_stat_rp)
                    .setContentTitle(title)
                    .setSubText(subText)
                    .setContentText(text)
                    .setContentIntent(open)
                    .setOngoing(true)
                    .setSilent(true)
                    .setOnlyAlertOnce(true)
                    .setProgress(if (hasTodos) totalTodos.coerceAtLeast(1) else 100, if (hasTodos) doneTodos else 0, !hasTodos)
                    .setWhen(startTime)
                    .setShowWhen(true)
                    .setUsesChronometer(!isPromotedCapable)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(
                        if (isPromotedCapable) {
                            NotificationCompat.CATEGORY_STATUS
                        } else {
                            NotificationCompat.CATEGORY_PROGRESS
                        },
                    ).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .addAction(
                        NotificationCompat.Action
                            .Builder(
                                0,
                                context.getString(R.string.action_open),
                                open,
                            ).build(),
                    )
            if (!isPromotedCapable) {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            }

            val notif = builder.build()
            if (isPromotedCapable) {
                notif.flags = notif.flags or FLAG_PROMOTED_ONGOING
                notif.extras.apply {
                    remove("androidx.core.app.extra.COMPAT_TEMPLATE")
                    putString(Notification.EXTRA_TEMPLATE, "android.app.Notification\$ProgressStyle")
                    putBoolean("android.styledByProgress", true)
                    putBoolean("android.requestPromotedOngoing", true)
                    putString("android.shortCriticalText", shortCritical)
                    putInt(Notification.EXTRA_PROGRESS, if (hasTodos) doneTodos else 0)
                    putInt(Notification.EXTRA_PROGRESS_MAX, if (hasTodos) totalTodos.coerceAtLeast(1) else 100)
                    putBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, !hasTodos)
                    putParcelableArrayList("android.progressPoints", ArrayList<Bundle>())
                    val segment =
                        Bundle().apply {
                            putInt("length", if (hasTodos) totalTodos.coerceAtLeast(1) else 100)
                            putInt("colorInt", -10777345)
                            putInt("id", 0)
                            putInt("semanticStyle", 0)
                        }
                    putParcelableArrayList("android.progressSegments", arrayListOf(segment))
                }
            }

            currentWorkingNotifId = id
            currentWorkingSignature = signature
            nm.notify(targetTag, id, notif)
        }

        private fun cancelOtherWorkingNotifications(keepId: Int) {
            runCatching {
                context
                    .getSystemService(android.app.NotificationManager::class.java)
                    .activeNotifications
                    .filter { it.tag == WORKING_TAG && it.id != keepId }
                    .forEach { nm.cancel(WORKING_TAG, it.id) }
            }
        }

        private fun isWorkingNotificationActive(id: Int): Boolean = isNotificationActive(WORKING_TAG, id)

        private fun isNotificationActive(
            tag: String?,
            id: Int,
        ): Boolean =
            runCatching {
                context
                    .getSystemService(android.app.NotificationManager::class.java)
                    .activeNotifications
                    .any { it.id == id && it.tag == tag }
            }.getOrDefault(false)

        // ───────────────────────────── Estado desde el receiver ─────────────────────────────

        /** Feedback inmediato tras pulsar una acción: "Enviando…" sin botones. */
        @SuppressLint("MissingPermission") // Guarded by canNotify() which checks POST_NOTIFICATIONS on API 33+
        fun showSending(
            notifId: Int,
            agentLabel: String,
            channelId: String? = null,
        ) {
            if (!canNotify()) return
            nm.notify(
                PROMPT_TAG,
                notifId,
                statusNotification(
                    agentLabel,
                    context.getString(R.string.notif_sending),
                    ongoing = true,
                    channelId = channelId,
                    notifIdHint = notifId,
                    timeoutMs = SENDING_TIMEOUT_MS,
                ),
            )
        }

        @SuppressLint("MissingPermission") // Guarded by canNotify() which checks POST_NOTIFICATIONS on API 33+
        fun showRetrying(
            notifId: Int,
            agentLabel: String,
            channelId: String? = null,
        ) {
            if (!canNotify()) return
            nm.notify(
                PROMPT_TAG,
                notifId,
                statusNotification(
                    agentLabel,
                    context.getString(R.string.notif_retrying),
                    ongoing = false,
                    channelId = channelId,
                    notifIdHint = notifId,
                ),
            )
        }

        private fun statusNotification(
            agentLabel: String,
            text: String,
            ongoing: Boolean,
            channelId: String?,
            notifIdHint: Int,
            timeoutMs: Long? = null,
        ): Notification {
            val existing = existingNotification(notifIdHint)
            val builder =
                NotificationCompat
                    .Builder(context, channelId ?: existing?.channelId ?: NotificationChannels.SERVICE_CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_rp)
                    .setContentTitle(agentLabel.ifEmpty { "RP" })
                    .setContentText(text)
                    .setOngoing(ongoing)
                    .setOnlyAlertOnce(true)
                    .setProgress(0, 0, ongoing)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
            // Conserva el grupo del prompt: si no, el resumen del agente queda huérfano al reemplazarlo.
            existing?.group?.let { builder.setGroup(it) }
            // "Enviando…" es transitorio: si el flujo muere a medias no puede quedarse ongoing para siempre.
            timeoutMs?.let { builder.setTimeoutAfter(it) }
            return builder.build()
        }

        /** Notificación activa con ese id (para conservar canal y grupo al reemplazarla). */
        private fun existingNotification(id: Int): Notification? =
            runCatching {
                context
                    .getSystemService(android.app.NotificationManager::class.java)
                    .activeNotifications
                    .firstOrNull { it.id == id && it.tag == PROMPT_TAG }
                    ?.notification
            }.getOrNull()

        /**
         * Asienta un prompt: retira el child, su resumen de grupo si quedó huérfano y la clave del deduper
         * (para que un id reutilizado legítimamente se pueda volver a notificar).
         */
        suspend fun cancelPrompt(promptId: String) {
            nm.cancel(PROMPT_TAG, notificationId(promptId))
            deduper.forget(NotificationDeduper.promptKey(promptId))
            deduper.markAnswered(promptId)
            pruneOrphanSummaries()
        }

        /**
         * Retira los resúmenes de grupo que se quedaron sin hijos: respuesta desde la bandeja, respuesta en
         * otro dispositivo, prompt cancelado por el relay o un proceso que murió a medias.
         */
        private fun pruneOrphanSummaries() {
            val active = runCatching { nm.activeNotifications.toList() }.getOrNull() ?: return
            val childGroups = active.filter { it.tag != SUMMARY_TAG }.mapNotNull { it.notification.group }.toSet()
            active
                .filter { it.tag == SUMMARY_TAG && it.notification.group !in childGroups }
                .forEach { nm.cancel(SUMMARY_TAG, it.id) }
        }

        fun cancelNotification(
            tag: String,
            id: Int,
        ) = nm.cancel(tag, id)

        // ───────────────────────────── Utilidades ─────────────────────────────

        @SuppressLint("MissingPermission") // Guarded by canNotify() which checks POST_NOTIFICATIONS on API 33+
        private fun postAgentSummary(
            agentId: String,
            channel: String,
            label: String,
        ) {
            if (!canNotify()) return
            val summary =
                NotificationCompat
                    .Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_stat_rp)
                    .setContentTitle(label)
                    .setGroup(groupKey(agentId))
                    .setGroupSummary(true)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .build()
            nm.notify(SUMMARY_TAG, stableId("summary:$agentId"), summary)
        }

        @SuppressLint("MissingPermission") // Guarded by canNotify() which checks POST_NOTIFICATIONS on API 33+
        private fun postSettledSummaryIfNeeded(channel: String) {
            if (!canNotify()) return
            val activeSettledCount =
                runCatching {
                    context
                        .getSystemService(android.app.NotificationManager::class.java)
                        .activeNotifications
                        .count { it.tag == SETTLED_TAG }
                }.getOrDefault(0)
            if (activeSettledCount < 2) return
            val summary =
                NotificationCompat
                    .Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_stat_rp)
                    .setContentTitle("$activeSettledCount agentes terminaron")
                    .setSubText("$activeSettledCount finalizados")
                    .setGroup(SETTLED_GROUP_KEY)
                    .setGroupSummary(true)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .build()
            nm.notify(SUMMARY_TAG, stableId("summary:settled"), summary)
        }

        private data class AgentLabelParts(
            val project: String,
            val topic: String?,
        )

        private fun parseAgentParts(agent: AgentSummary): AgentLabelParts {
            val rawName = agent.sessionName?.trim().orEmpty()
            val cwdProject = lastSegment(agent.cwd).takeIf { it.isNotBlank() }
            if (rawName.contains(" · ")) {
                val left = rawName.substringBefore(" · ").trim()
                val right = rawName.substringAfter(" · ").trim()
                val project = left.ifEmpty { cwdProject ?: agentTitle(agent.sessionName, agent.cwd, agent.agentId) }
                val topic =
                    right.takeIf {
                        it.isNotBlank() &&
                            !it.equals("sin título", ignoreCase = true) &&
                            !it.equals("sin titulo", ignoreCase = true) &&
                            !it.equals("untitled", ignoreCase = true) &&
                            !it.equals(project, ignoreCase = true)
                    }
                return AgentLabelParts(project = project, topic = topic)
            }
            val fallbackTitle = agentTitle(agent.sessionName, agent.cwd, agent.agentId)
            val project = cwdProject ?: fallbackTitle
            val topic =
                rawName.takeIf {
                    it.isNotBlank() &&
                        !it.equals("sin título", ignoreCase = true) &&
                        !it.equals("sin titulo", ignoreCase = true) &&
                        !it.equals("untitled", ignoreCase = true) &&
                        !it.equals(project, ignoreCase = true)
                }
            return AgentLabelParts(project = project, topic = topic)
        }

        private fun isRawJsonOrCode(s: String): Boolean {
            val t = s.trim()
            if (t.startsWith("{") || t.startsWith("[") || t.startsWith("```")) return true
            if (t.startsWith("{\"") || t.startsWith("[\"")) return true
            return false
        }

        private fun cleanPreview(
            raw: String?,
            maxLen: Int = 100,
        ): String? {
            if (raw.isNullOrBlank()) return null
            if (isRawJsonOrCode(raw)) return null
            val cleaned =
                raw
                    .lineSequence()
                    .map { line ->
                        line
                            .trim()
                            .replace(MD_HEADER_REGEX, "")
                            .replace(MD_BULLET_REGEX, "")
                    }.filter { line ->
                        line.isNotBlank() && !isRawJsonOrCode(line)
                    }
                    .joinToString(" ")
                    .replace("**", "")
                    .replace("__", "")
                    .replace("`", "")
                    .replace(WHITESPACE_REGEX, " ")
                    .trim()
            if (cleaned.isEmpty() || isRawJsonOrCode(cleaned)) return null
            return truncate(cleaned, maxLen)
        }

        private fun openIntent(
            promptId: String?,
            agentId: String?,
            requestCode: Int,
        ): PendingIntent {
            val i =
                Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_OPEN
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    promptId?.let { putExtra(MainActivity.EXTRA_PROMPT_ID, it) }
                    agentId?.let { putExtra(MainActivity.EXTRA_AGENT_ID, it) }
                }
            return PendingIntent.getActivity(context, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun canNotify(): Boolean {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            return nm.areNotificationsEnabled()
        }

        private fun groupKey(agentId: String) = "rp.agent.$agentId"

        companion object {
            const val PROMPT_TAG = "rp.prompt"
            const val SETTLED_TAG = "rp.settled"
            const val SIMPLE_TAG = "rp.simple"
            const val SUMMARY_TAG = "rp.summary"
            const val WORKING_TAG = "rp.working"

            const val WORKING_GROUP_KEY = "rp.group.working"
            const val SETTLED_GROUP_KEY = "rp.group.settled"
            const val SERVICE_GROUP_KEY = "rp.group.service"

            private const val FLAG_PROMOTED_ONGOING = 0x00040000
            private val MD_HEADER_REGEX = Regex("^#{1,6}\\s+")
            private val MD_BULLET_REGEX = Regex("^[-*]\\s+")
            private val WHITESPACE_REGEX = Regex("\\s+")

            /** "Enviando…" nunca debe sobrevivir al flujo que lo publicó. */
            private const val SENDING_TIMEOUT_MS = 30_000L

            /** id = hash de `promptId`. */
            fun notificationId(promptId: String) = stableId(promptId)
        }
    }
