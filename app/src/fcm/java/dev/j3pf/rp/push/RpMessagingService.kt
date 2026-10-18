package dev.j3pf.rp.push

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import dev.j3pf.rp.data.repo.SessionRepository
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Recepción FCM (opcional): descifra `c` con AES‑256‑GCM (pushKey del Keystore, AAD "rp1") y delega en
 * [PushRouter], igual que el canal directo (deduplicación por `promptId`).
 */
@AndroidEntryPoint
class RpMessagingService : FirebaseMessagingService() {
    @Inject lateinit var router: PushRouter

    override fun onMessageReceived(message: RemoteMessage) {
        // El servicio dispone de ~20 s en segundo plano; el trabajo es corto (descifrar + notificar).
        runBlocking { router.handleFcm(message.data) }
    }

    override fun onNewToken(token: String) {
        val req =
            OneTimeWorkRequestBuilder<FcmTokenWorker>()
                .setInputData(Data.Builder().putString("token", token).build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork("fcm-token", ExistingWorkPolicy.REPLACE, req)
    }
}

/** `onNewToken` → `PUT /v1/devices/me/push` con reintentos. */
@HiltWorker
class FcmTokenWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val session: SessionRepository,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val token = inputData.getString("token")
            return if (session.registerFcmToken(token)) Result.success() else Result.retry()
        }
    }
