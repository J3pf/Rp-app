package dev.j3pf.rp.platform

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.data.ports.NotificationHealth
import dev.j3pf.rp.data.ports.NotificationPermissionOutcome
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidNotificationHealth
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : NotificationHealth {
        override fun isEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

        /**
         * Android 13+ runtime notification requests are made by the Settings/Onboarding UI launchers;
         * production never auto-prompts from background. This method reports current availability only.
         */
        override suspend fun request(): NotificationPermissionOutcome =
            when {
                isEnabled() -> NotificationPermissionOutcome.GRANTED
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> NotificationPermissionOutcome.DENIED
                else -> NotificationPermissionOutcome.UNAVAILABLE
            }
    }
