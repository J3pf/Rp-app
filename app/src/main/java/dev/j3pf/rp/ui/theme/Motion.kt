package dev.j3pf.rp.ui.theme

import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

/** CompositionLocal para propagar si el usuario tiene activado "reducir movimiento". */
val LocalReducedMotion = compositionLocalOf { false }

/** Determina si el sistema tiene desactivadas las animaciones (animator_duration_scale == 0). */
@Composable
fun rememberIsReducedMotion(): Boolean {
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

/** Tokens centralizados de movimiento con física de resortes (Spring). */
object RpMotion {
    // Escala táctil al presionar tarjetas o botones
    const val PRESSED_SCALE = 0.97f
    const val DEFAULT_SCALE = 1.0f

    // Especificaciones de resorte para diferentes intenciones
    val springBouncy: SpringSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        )

    val springSnappy: SpringSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )

    val springSmooth: SpringSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        )

    val springDp: SpringSpec<Dp> =
        spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )

    val springOffset: SpringSpec<IntOffset> =
        spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )

    val tweenStandard: TweenSpec<Float> =
        tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing,
        )

    /** Devuelve una spec adaptativa que respeta reduceMotion (snap si está activo). */
    fun <T> adaptiveSpec(
        reducedMotion: Boolean,
        defaultSpec: AnimationSpec<T>,
    ): AnimationSpec<T> = if (reducedMotion) snap() else defaultSpec

    // Transiciones de navegación sincronizadas estilo Material 3 (sin saltos al volver atrás)
    private val navSlideIntSpec =
        tween<IntOffset>(
            durationMillis = 250,
            easing = FastOutSlowInEasing,
        )

    val navEnter: EnterTransition =
        slideInHorizontally(
            initialOffsetX = { fullWidth -> (fullWidth * 0.14f).toInt() },
            animationSpec = navSlideIntSpec,
        ) + fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))

    val navExit: ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { fullWidth -> -(fullWidth * 0.06f).toInt() },
            animationSpec = navSlideIntSpec,
        ) + fadeOut(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing))

    val navPopEnter: EnterTransition =
        slideInHorizontally(
            initialOffsetX = { fullWidth -> -(fullWidth * 0.06f).toInt() },
            animationSpec = navSlideIntSpec,
        ) + fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))

    val navPopExit: ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { fullWidth -> (fullWidth * 0.14f).toInt() },
            animationSpec = navSlideIntSpec,
        ) + fadeOut(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing))
}
