package dev.j3pf.rp.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberIsReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
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
}
