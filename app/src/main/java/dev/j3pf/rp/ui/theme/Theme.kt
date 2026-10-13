package dev.j3pf.rp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F378B),
    secondary = Color(0xFF625B71),
    tertiary = Color(0xFF7D5260),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFCFBCFF),
    secondary = Color(0xFFCCC2DC),
    tertiary = Color(0xFFEFB8C8),
)

/** Tema Material 3 con color dinámico (Material You, Android 12+) y modo oscuro automático. */
@Composable
fun RpTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Colores semánticos que no existen en Material (peligro, éxito). */
object RpColors {
    val danger = Color(0xFFB3261E)
    val warning = Color(0xFFB26A00)
    val success = Color(0xFF2E7D32)
}
