package dev.j3pf.rp.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.MainActivity
import dev.j3pf.rp.R
import dev.j3pf.rp.data.repo.NotifyCategory
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.protocol.PushKind
import dev.j3pf.rp.protocol.PushPromptBody
import dev.j3pf.rp.protocol.PushSettledBody
import dev.j3pf.rp.protocol.str
import dev.j3pf.rp.util.contextEmoji
import dev.j3pf.rp.util.stableId
import dev.j3pf.rp.util.truncate
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Construye todas las notificaciones (ambos canales de push llaman aquí).
 *
 *  - confirm → [Permitir] [Denegar]; select ≤3 opciones → una acción por opción; >3 o questionnaire → abre la hoja.
 *  - input / allowFreeText → acción "Responder" con `RemoteInput`.
 *  - `setAuthenticationRequired(true)` en acciones de aprobación si el ajuste está activo (API 31+).
 *  - Agrupadas por agente con resumen; `setTimeoutAfter` = `expiresAt`.
 */
@Singleton
class NotificationFactory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val channels: NotificationChannels,
        private val settings: SettingsRepository,
    ) {
        private val nm get() = NotificationManagerCompat.from(context)

        // ───────────────────────────── Prompt ─────────────────────────────

        @SuppressLint("MissingPermission") // Guarded by canNotify() which checks POST_NOTIFICATIONS on API 33+
        suspend fun showPrompt(p: PushPromptBody) {
            if (!canNotify()) return
            val s = settings.settings.first()
            val machineName = p.machineName.ifEmpty { "RP" }
            channels.ensureMachine(p.machineId.ifEmpty { "default" }, machineName, p.machineContext)
            val isPermission = p.category == PromptCategory.PERMISSION
            val category = if (isPermission) NotifyCategory.APPROVALS else NotifyCategory.QUESTIONS
            val channel = channels.channelId(p.machineId.ifEmpty { "default" }, category)
            val id = notificationId(p.promptId)
            val authRequired =
                s.requireUnlockToApprove &&
                    Build.VERSION.SDK_INT >= 31 &&
                    (isPermission || p.danger == Danger.HIGH)

            val open = openIntent(p.promptId, p.agentId, id)
            val builder =
                NotificationCompat
                    .Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_stat_rp)
                    .setContentTitle("${contextEmoji(p.machineContext)} $machineName · ${p.agentLabel.ifEmpty { "agente" }}")
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
            if (p.danger == Danger.HIGH) {
                builder.setColor(0xFFB3261E.toInt())
                builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            } else if (isPermission) {
                builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
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
            val machineId = s.machineId.ifEmpty { "default" }
            channels.ensureMachine(machineId, s.machineName.ifEmpty { "RP" }, s.machineContext)
            val id = stableId("settled:${s.agentId}")
            val auth = settings.settings.first().requireUnlockToApprove && Build.VERSION.SDK_INT >= 31
            val remote = RemoteInput.Builder(PromptActionReceiver.KEY_TEXT).setLabel(context.getString(R.string.notif_reply_hint)).build()
            val reply =
                Intent(context, PromptActionReceiver::class.java).apply {
                    action = PromptActionReceiver.ACTION_REPLY_AGENT
                    putExtra(PromptActionReceiver.EXTRA_AGENT_ID, s.agentId)
                    putExtra(PromptActionReceiver.EXTRA_NOTIF_ID, id)
                    putExtra(PromptActionReceiver.EXTRA_AGENT_LABEL, s.agentLabel)
                }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val replyPi = PendingIntent.getBroadcast(context, id, reply, flags)
            val n =
                NotificationCompat
                    .Builder(context, channels.channelId(machineId, NotifyCategory.FINISHED))
                    .setSmallIcon(R.drawable.ic_stat_rp)
                    .setContentTitle(
                        "${contextEmoji(s.machineContext)} ${s.machineName.ifEmpty { "RP" }} · ${s.agentLabel.ifEmpty { "agente" }}",
                    ).setContentText(s.preview ?: s.message ?: "Terminó")
                    .setStyle(NotificationCompat.BigTextStyle().bigText(s.preview ?: s.message ?: "Terminó"))
                    .setContentIntent(openIntent(null, s.agentId, id))
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .addAction(
                        NotificationCompat.Action
                            .Builder(0, context.getString(R.string.action_reply), replyPi)
                            .addRemoteInput(remote)
                            .also { if (auth) it.setAuthenticationRequired(true) }
                            .build(),
                    ).build()
            nm.notify(SETTLED_TAG, id, n)
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
                    .setContentTitle("${contextEmoji(body.str("machineContext") ?: "personal")} $machineName · $label")
                    .setContentText(truncate(text, 140))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(openIntent(null, agentId, id))
                    .setAutoCancel(true)
                    .build()
            nm.notify(SIMPLE_TAG, id, n)
        }

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
        ): Notification {
            val channel = channelId ?: existingChannel(notifIdHint) ?: NotificationChannels.SERVICE_CHANNEL
            return NotificationCompat
                .Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_rp)
                .setContentTitle(agentLabel.ifEmpty { "RP" })
                .setContentText(text)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .setProgress(0, 0, ongoing)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }

        /** Canal de la notificación activa (para mantenerlo al reemplazarla por "Enviando…"). */
        private fun existingChannel(id: Int): String? =
            runCatching {
                context
                    .getSystemService(android.app.NotificationManager::class.java)
                    .activeNotifications
                    .firstOrNull { it.id == id && it.tag == PROMPT_TAG }
                    ?.notification
                    ?.channelId
            }.getOrNull()

        /** Retira la notificación de un prompt (respondido en la terminal / otro dispositivo / cancelado). */
        fun cancelPrompt(promptId: String) {
            nm.cancel(PROMPT_TAG, notificationId(promptId))
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

            /** id = hash de `promptId`. */
            fun notificationId(promptId: String) = stableId(promptId)
        }
    }
