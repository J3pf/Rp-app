package dev.j3pf.rp.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Sabor `fcm`: token de Firebase si hay `google-services.json`; si no, `null` (sólo canal directo). */
@Module
@InstallIn(SingletonComponent::class)
object PushTokenModule {
    @Provides
    @Singleton
    fun tokenProvider(
        @ApplicationContext context: Context,
    ): PushTokenProvider =
        object : PushTokenProvider {
            override suspend fun currentToken(): String? {
                if (FirebaseApp.getApps(context).isEmpty()) return null
                return suspendCancellableCoroutine { cont ->
                    FirebaseMessaging.getInstance().token.addOnCompleteListener { t ->
                        if (cont.isActive) cont.resume(if (t.isSuccessful) t.result else null)
                    }
                }
            }
        }
}
