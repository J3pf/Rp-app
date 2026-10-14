package dev.j3pf.rp.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import dagger.hilt.android.AndroidEntryPoint
import dev.j3pf.rp.data.db.OutboxDao
import dev.j3pf.rp.data.db.OutboxEntity
import dev.j3pf.rp.data.db.PromptDao
import dev.j3pf.rp.data.net.AnswerOutcome
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.repo.ChatRepository
import dev.j3pf.rp.data.repo.PromptsRepository
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.di.AppScope
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.ResolvedBy
import dev.j3pf.rp.protocol.RpJson
import dev.j3pf.rp.protocol.Ulid
import dev.j3pf.rp.protocol.UserMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Acciones de las notificaciones (PLAN-APP §5.3): `onReceive → goAsync → api.answer (HTTPS directo, 8 s)`.
 * La lógica vive en [PromptActionHandler] para poder probarla sin Hilt.
 */
@AndroidEntryPoint
class PromptActionReceiver : BroadcastReceiver() {
    @Inject lateinit var handler: PromptActionHandler

    @Inject @field:AppScope
    lateinit var scope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        val app = context.applicationContext
        scope.launch {
            try {
                handler.handle(app, intent)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ANSWER = "dev.j3pf.rp.action.ANSWER"
        const val ACTION_REPLY_AGENT = "dev.j3pf.rp.action.REPLY_AGENT"
        const val EXTRA_PROMPT_ID = "promptId"
        const val EXTRA_AGENT_ID = "agentId"
        const val EXTRA_AGENT_LABEL = "agentLabel"
        const val EXTRA_NOTIF_ID = "notifId"
        const val EXTRA_ANSWER_KIND = "answerKind"
        const val EXTRA_OPTION_ID = "optionId"
        const val KEY_TEXT = "rp_reply_text"
        const val ANSWER_CONFIRM_YES = "confirm_yes"
        const val ANSWER_CONFIRM_NO = "confirm_no"
        const val ANSWER_OPTION = "option"
        const val ANSWER_TEXT = "text"
    }
}

/**
 * ```
 * handle(intent) → api.answer
 *   ok o 409 → se quita la notificación
 *   sin red  → "Sin conexión · se reintentará" + WorkManager expedited
 * ```
 * Tras pulsar, la notificación pasa a "Enviando…" de inmediato.
 */
class PromptActionHandler
    @Inject
    constructor(
        private val api: RelayApi,
        private val factory: NotificationFactory,
        private val promptDao: PromptDao,
        private val outboxDao: OutboxDao,
        private val secrets: SecretStore,
    ) {
        suspend fun handle(
            context: Context,
            intent: Intent,
        ) {
            when (intent.action) {
                PromptActionReceiver.ACTION_ANSWER -> handleAnswer(context, intent)
                PromptActionReceiver.ACTION_REPLY_AGENT -> handleReply(context, intent)
            }
        }

        private suspend fun handleAnswer(
            context: Context,
            intent: Intent,
        ) {
            val promptId = intent.getStringExtra(PromptActionReceiver.EXTRA_PROMPT_ID) ?: return
            val notifId = intent.getIntExtra(PromptActionReceiver.EXTRA_NOTIF_ID, NotificationFactory.notificationId(promptId))
            val label = intent.getStringExtra(PromptActionReceiver.EXTRA_AGENT_LABEL).orEmpty()
            val answer =
                when (intent.getStringExtra(PromptActionReceiver.EXTRA_ANSWER_KIND)) {
                    PromptActionReceiver.ANSWER_CONFIRM_YES -> {
                        Answer.confirm(true)
                    }

                    PromptActionReceiver.ANSWER_CONFIRM_NO -> {
                        Answer.confirm(false)
                    }

                    PromptActionReceiver.ANSWER_OPTION -> {
                        Answer.option(intent.getStringExtra(PromptActionReceiver.EXTRA_OPTION_ID) ?: return)
                    }

                    PromptActionReceiver.ANSWER_TEXT -> {
                        val text =
                            RemoteInput
                                .getResultsFromIntent(intent)
                                ?.getCharSequence(PromptActionReceiver.KEY_TEXT)
                                ?.toString()
                                ?.trim()
                        if (text.isNullOrEmpty()) return
                        Answer.text(text)
                    }

                    else -> {
                        return
                    }
                }
            factory.showSending(notifId, label)
            when (val outcome = api.answerPrompt(promptId, answer)) {
                AnswerOutcome.Sent, AnswerOutcome.AlreadyResolved -> {
                    // 409 = ya respondido en otro lugar → también se quita.
                    secrets.current()?.let { promptDao.markResolved(it.relayId, promptId, ResolvedBy.REMOTE, System.currentTimeMillis()) }
                    factory.cancelNotification(NotificationFactory.PROMPT_TAG, notifId)
                }

                AnswerOutcome.Queued -> {
                    factory.showRetrying(notifId, label)
                    PromptsRepository.enqueueAnswer(context, promptId, answer)
                }

                is AnswerOutcome.Failed -> {
                    factory.showRetrying(notifId, label)
                }
            }
        }

        /** "Responder" en la notificación de agente terminado → `POST /v1/agents/{id}/messages`. */
        private suspend fun handleReply(
            context: Context,
            intent: Intent,
        ) {
            val agentId = intent.getStringExtra(PromptActionReceiver.EXTRA_AGENT_ID) ?: return
            val notifId = intent.getIntExtra(PromptActionReceiver.EXTRA_NOTIF_ID, 0)
            val text =
                RemoteInput
                    .getResultsFromIntent(intent)
                    ?.getCharSequence(PromptActionReceiver.KEY_TEXT)
                    ?.toString()
                    ?.trim()
            if (text.isNullOrEmpty()) return
            val s = secrets.current() ?: return
            val msg = UserMessage(agentId = agentId, clientMsgId = Ulid.new(), text = text, deviceId = s.deviceId)
            outboxDao.put(
                OutboxEntity(
                    msg.clientMsgId,
                    s.relayId,
                    agentId,
                    RpJson.encodeToString(UserMessage.serializer(), msg),
                    0,
                    System.currentTimeMillis(),
                ),
            )
            when (val r = api.sendMessage(msg)) {
                is ApiResult.Ok -> {
                    outboxDao.delete(msg.clientMsgId)
                    factory.cancelNotification(NotificationFactory.SETTLED_TAG, notifId)
                }

                is ApiResult.Http -> {
                    if (r.code in 400..499 && r.code != 429 && r.code != 401 && r.code != 403) {
                        outboxDao.delete(msg.clientMsgId)
                        factory.cancelNotification(NotificationFactory.SETTLED_TAG, notifId)
                    } else {
                        ChatRepository.enqueueSend(context)
                    }
                }

                is ApiResult.Network -> {
                    ChatRepository.enqueueSend(context)
                }
            }
        }
    }
