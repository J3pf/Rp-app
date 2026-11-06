package dev.j3pf.rp.platform

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.data.ports.BatteryExemption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBatteryExemption
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BatteryExemption {
        override val isSupported: Boolean = true

        override suspend fun isIgnoring(): Boolean =
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
