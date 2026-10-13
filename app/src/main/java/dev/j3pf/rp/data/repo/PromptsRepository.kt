package dev.j3pf.rp.data.repo

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.data.db.PromptDao
import dev.j3pf.rp.data.net.AnswerOutcome
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.net.WsReply
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.di.AppScope
import dev.j3pf.rp.domain.model.toEntity
import dev.j3pf.rp.domain.model.toModel
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.AppWelcome
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.ErrorCodes
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptAnswerRequest
import dev.j3pf.rp.protocol.PromptAnswerResult
import dev.j3pf.rp.protocol.PromptResolved
import dev.j3pf.rp.protocol.ResolvedBy
import dev.j3pf.rp.protocol.RpJson
import dev.j3pf.rp.protocol.Ulid
import dev.j3pf.rp.push.AnswerWorker
import dev.j3pf.rp.push.NotificationFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PromptsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dao: PromptDao,
        private val secrets: SecretStore,
        private val bus: MessageBus,
        private val connection: RelayConnection,
        private val api: RelayApi,
        private val notifications: NotificationFactory,
        @AppScope private val scope: CoroutineScope,
    ) {
        /** Todos los prompts abiertos, ordenados por peligro y antigüedad. */
        val open: Flow<List<Prompt>> =
            secrets.session.flatMapLatest { s ->
                if (s == null) emptyFlow() else dao.observeOpen(s.relayId).map { l -> l.map { it.toModel() } }
            }

        fun openFor(agentId: String): Flow<List<Prompt>> =
            secrets.session.flatMapLatest { s ->
                if (s == null) emptyFlow() else dao.observeOpenFor(s.relayId, agentId).map { l -> l.map { it.toModel() } }
            }

        fun observe(promptId: String): Flow<Prompt?> =
            secrets.session.flatMapLatest { s ->
                if (s == null) emptyFlow() else dao.observeOne(s.relayId, promptId).map { it?.toModel() }
            }

        suspend fun get(promptId: String): Prompt? = secrets.current()?.let { dao.get(it.relayId, promptId)?.toModel() }

        fun start() {
            bus.messages
                .onEach { env ->
                    val relayId = secrets.current()?.relayId ?: return@onEach
                    val now = System.currentTimeMillis()
                    when (env.t) {
                        MsgType.APP_WELCOME -> {
                            val w = env.body<AppWelcome>()
                            dao.upsert(w.openPrompts.map { it.toEntity(relayId, now) })
                            dao.resolveMissing(relayId, w.openPrompts.map { it.promptId }.ifEmpty { listOf("") }, now)
                        }

                        MsgType.PROMPT_OPENED -> {
                            dao.upsert(listOf(env.body<Prompt>().toEntity(relayId, now)))
                        }

                        MsgType.PROMPT_RESOLVED -> {
                            val r = env.body<PromptResolved>()
                            dao.markResolved(relayId, r.promptId, r.by, now)
                            notifications.cancelPrompt(r.promptId)
                        }
                    }
                    if (env.t == MsgType.APP_WELCOME) dao.purgeResolved(now - 24 * 3600_000L)
                }.launchIn(scope)
        }

        /** Sincroniza la bandeja por HTTP (fallback sin WS). */
        suspend fun refresh() {
            val relayId = secrets.current()?.relayId ?: return
            val res = api.openPrompts()
            if (res is ApiResult.Ok) {
                val now = System.currentTimeMillis()
                dao.upsert(res.value.map { it.toEntity(relayId, now) })
                dao.resolveMissing(relayId, res.value.map { it.promptId }.ifEmpty { listOf("") }, now)
            }
        }

        /**
         * Responde por WS si está conectado; si no (o falla) por HTTP; sin red → WorkManager expedited.
         * "La primera respuesta gana": 409 = [AnswerOutcome.AlreadyResolved].
         */
        suspend fun answer(
            promptId: String,
            answer: Answer,
        ): AnswerOutcome {
            val relayId = secrets.current()?.relayId ?: return AnswerOutcome.Failed("sin sesión")
            var outcome: AnswerOutcome? = null
            if (connection.isConnected) {
                val reply =
                    connection.request(
                        Envelope.of(MsgType.PROMPT_ANSWER, PromptAnswerRequest(promptId, answer), id = Ulid.new()),
                    )
                outcome =
                    when (reply) {
                        is WsReply.Ok -> {
                            val r = runCatching { reply.envelope.body<PromptAnswerResult>() }.getOrNull()
                            when {
                                r == null || r.ok -> AnswerOutcome.Sent
                                r.alreadyResolved -> AnswerOutcome.AlreadyResolved
                                r.reason == "agent_offline_queued" -> AnswerOutcome.Sent
                                else -> null
                            }
                        }

                        is WsReply.Error -> {
                            if (reply.code == ErrorCodes.CONFLICT) AnswerOutcome.AlreadyResolved else null
                        }

                        else -> {
                            null
                        }
                    }
            }
            val result = outcome ?: api.answerPrompt(promptId, answer)
            when (result) {
                AnswerOutcome.Sent, AnswerOutcome.AlreadyResolved -> {
                    dao.markResolved(relayId, promptId, ResolvedBy.REMOTE, System.currentTimeMillis())
                    notifications.cancelPrompt(promptId)
                }

                AnswerOutcome.Queued -> {
                    enqueueAnswer(context, promptId, answer)
                }

                is AnswerOutcome.Failed -> {
                    Unit
                }
            }
            return result
        }

        companion object {
            /** Cola un reintento con red disponible (también lo usa el receiver de la notificación). */
            fun enqueueAnswer(
                context: Context,
                promptId: String,
                answer: Answer,
            ) {
                val data =
                    Data
                        .Builder()
                        .putString(AnswerWorker.KEY_PROMPT_ID, promptId)
                        .putString(AnswerWorker.KEY_ANSWER, RpJson.encodeToString(Answer.serializer(), answer))
                        .build()
                val req =
                    OneTimeWorkRequestBuilder<AnswerWorker>()
                        .setInputData(data)
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                WorkManager.getInstance(context).enqueueUniqueWork("answer-$promptId", ExistingWorkPolicy.KEEP, req)
            }
        }
    }
