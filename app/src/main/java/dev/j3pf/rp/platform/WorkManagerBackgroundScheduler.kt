package dev.j3pf.rp.platform

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.data.ports.BackgroundScheduler
import dev.j3pf.rp.push.SendMessageWorker
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.TimeUnit

@Singleton
class WorkManagerBackgroundScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BackgroundScheduler {
        override fun enqueueOutboxFlush() = enqueueDrain()

        /**
         * Android quick-reply answers are saved in the outbox before delivery (PromptActionReceiver),
         * and network failure schedules its SendMessageWorker drain. This parameterless generic retry
         * therefore reuses that outbox drain; per-prompt AnswerWorker retries remain call-site-owned.
         */
        override fun enqueueAnswerRetry() = enqueueDrain()

        override fun cancelAllWork() {
            WorkManager.getInstance(context).cancelAllWork()
        }

        private fun enqueueDrain() {
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(SEND_OUTBOX_WORK_NAME, ExistingWorkPolicy.REPLACE, buildSendRequest())
        }

        @VisibleForTesting
        internal fun buildSendRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SendMessageWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

        companion object {
            internal const val SEND_OUTBOX_WORK_NAME = "send-outbox"
        }
    }
