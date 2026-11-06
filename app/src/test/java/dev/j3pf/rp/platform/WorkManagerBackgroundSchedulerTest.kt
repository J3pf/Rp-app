package dev.j3pf.rp.platform

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.NetworkType
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import dev.j3pf.rp.push.SendMessageWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkManagerBackgroundSchedulerTest {
    private lateinit var context: Application
    private lateinit var scheduler: WorkManagerBackgroundScheduler
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerBackgroundScheduler(context)
    }

    @After
    fun tearDown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun sendRequestPinsConnectedBackoffAndWorker() {
        val request = scheduler.buildSendRequest()
        val spec = request.workSpec

        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(15_000L, spec.backoffDelayDuration)
        assertTrue(spec.expedited)
        assertEquals(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST, spec.outOfQuotaPolicy)
        assertEquals(SendMessageWorker::class.java.name, spec.workerClassName)
        assertTrue(request.tags.contains(SendMessageWorker::class.java.name))
    }

    @Test
    fun enqueueOutboxFlushCreatesUniqueWorkAndCancelAllWorkClearsIt() = runBlocking {
        assertEnqueuedAndCancelled { scheduler.enqueueOutboxFlush() }
    }

    @Test
    fun enqueueAnswerRetryCreatesUniqueWorkAndCancelAllWorkClearsIt() = runBlocking {
        assertEnqueuedAndCancelled { scheduler.enqueueAnswerRetry() }
    }

    private fun assertEnqueuedAndCancelled(enqueue: () -> Unit) {
        enqueue()
        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerBackgroundScheduler.SEND_OUTBOX_WORK_NAME).get()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)

        scheduler.cancelAllWork()
        // REAL WorkManager semantics (pinned from the red run): getWorkInfosForUniqueWork keeps
        // returning the record after cancelAllWork -- it moves to CANCELLED, it is not removed.
        val after = workManager.getWorkInfosForUniqueWork(WorkManagerBackgroundScheduler.SEND_OUTBOX_WORK_NAME).get()
        assertEquals(1, after.size)
        assertEquals(WorkInfo.State.CANCELLED, after.single().state)
    }
}
