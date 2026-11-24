package dev.j3pf.rp.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.di.AppScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Arranca el canal directo al encender el teléfono o actualizar la app. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var secrets: SecretStore

    @Inject lateinit var settings: SettingsRepository

    @Inject @AppScope
    lateinit var scope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext
        scope.launch {
            try {
                if (secrets.current() != null && settings.current().directPush) DirectPushService.start(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("BootReceiver", "Unhandled error handling boot broadcast", e)
            } finally {
                pending.finish()
            }
        }
    }
}
