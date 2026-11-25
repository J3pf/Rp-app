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
import dev.j3pf.rp.data.repo.ChatRepository

/** Vacía la bandeja de salida (mensajes enviados sin red) con el mismo `clientMsgId` (idempotente). */
@HiltWorker
class SendMessageWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val chat: ChatRepository,
        private val channels: NotificationChannels,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            if (chat.flushOutbox()) {
                Result.success()
            } else if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure()
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
            const val MAX_ATTEMPTS = 8
            private const val FOREGROUND_ID = 4202
        }
    }
