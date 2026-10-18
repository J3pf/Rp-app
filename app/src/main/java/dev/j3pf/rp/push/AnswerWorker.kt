package dev.j3pf.rp.push

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.j3pf.rp.R
import dev.j3pf.rp.data.db.PromptDao
import dev.j3pf.rp.data.net.AnswerOutcome
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.ResolvedBy
import dev.j3pf.rp.protocol.RpJson

/** Reintenta una respuesta a prompt cuando vuelve la red (WorkManager *expedited*, PLAN-APP §3.2). */
@HiltWorker
class AnswerWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val api: RelayApi,
        private val promptDao: PromptDao,
        private val secrets: SecretStore,
        private val notifications: NotificationFactory,
        private val channels: NotificationChannels,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val promptId = inputData.getString(KEY_PROMPT_ID) ?: return Result.failure()
            val answer =
                inputData
                    .getString(KEY_ANSWER)
                    ?.let { runCatching { RpJson.decodeFromString(Answer.serializer(), it) }.getOrNull() }
                    ?: return Result.failure()
            return when (api.answerPrompt(promptId, answer)) {
                AnswerOutcome.Sent, AnswerOutcome.AlreadyResolved -> {
                    secrets.current()?.let { promptDao.markResolved(it.relayId, promptId, ResolvedBy.REMOTE, System.currentTimeMillis()) }
                    notifications.cancelPrompt(promptId)
                    Result.success()
                }
                AnswerOutcome.Queued -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
                is AnswerOutcome.Failed -> Result.failure()
            }
        }

        override suspend fun getForegroundInfo(): ForegroundInfo {
            channels.ensureServiceChannel()
            val n =
                NotificationCompat
                    .Builder(applicationContext, NotificationChannels.SERVICE_CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_rp)
                    .setContentTitle(applicationContext.getString(R.string.notif_sending))
                    .setOngoing(true)
                    .build()
            return ForegroundInfo(FOREGROUND_ID, n)
        }

        companion object {
            const val KEY_PROMPT_ID = "promptId"
            const val KEY_ANSWER = "answer"
            const val MAX_ATTEMPTS = 8
            private const val FOREGROUND_ID = 4201
        }
    }
