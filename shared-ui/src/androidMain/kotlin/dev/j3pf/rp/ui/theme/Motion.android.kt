package dev.j3pf.rp.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private fun isPowerOrThermalConstrained(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    if (pm.isPowerSaveMode) return true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) {
        return true
    }
    return false
}

@Composable
actual fun rememberIsReducedMotion(): Boolean {
    val context = LocalContext.current
    val animatorDisabled =
        remember(context) {
            try {
                val scale =
                    Settings.Global.getFloat(
                        context.contentResolver,
                        Settings.Global.ANIMATOR_DURATION_SCALE,
                        1.0f,
                    )
                scale == 0.0f
            } catch (_: Exception) {
                false
            }
        }
    var powerConstrained by remember(context) { mutableStateOf(isPowerOrThermalConstrained(context)) }

    DisposableEffect(context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context?,
                    intent: Intent?,
                ) {
                    powerConstrained = isPowerOrThermalConstrained(context)
                }
            }
        runCatching {
            context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        }
        val thermalListener =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm != null) {
                val listener =
                    PowerManager.OnThermalStatusChangedListener {
                        powerConstrained = isPowerOrThermalConstrained(context)
                    }
                runCatching { pm.addThermalStatusListener(context.mainExecutor, listener) }
                listener
            } else {
                null
            }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm != null && thermalListener != null) {
                runCatching { pm.removeThermalStatusListener(thermalListener) }
            }
        }
    }

    return animatorDisabled || powerConstrained
}
