package dev.j3pf.rp.ui.theme

import android.content.Context
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private fun isThermallyConstrained(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
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
    var thermallyConstrained by remember(context) { mutableStateOf(isThermallyConstrained(context)) }

    DisposableEffect(context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermalListener =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm != null) {
                val listener =
                    PowerManager.OnThermalStatusChangedListener {
                        thermallyConstrained = isThermallyConstrained(context)
                    }
                runCatching { pm.addThermalStatusListener(context.mainExecutor, listener) }
                listener
            } else {
                null
            }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm != null && thermalListener != null) {
                runCatching { pm.removeThermalStatusListener(thermalListener) }
            }
        }
    }

    return animatorDisabled || thermallyConstrained
}

@Composable
actual fun rememberIsAppForeground(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var isResumed by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer =
            LifecycleEventObserver { _, _ ->
                isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return isResumed
}
