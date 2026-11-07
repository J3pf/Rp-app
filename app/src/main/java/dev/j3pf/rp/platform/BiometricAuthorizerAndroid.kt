package dev.j3pf.rp.platform

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import dev.j3pf.rp.data.ports.BiometricAuthResult
import dev.j3pf.rp.data.ports.BiometricAuthorizer
import dev.j3pf.rp.util.BiometricGate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter from the frozen [BiometricAuthorizer] port to the platform-locked [BiometricGate]
 * (Risk: item 2 seam — O1 b5a5689/port overload + orchestrator ruling).
 *
 * Freshness (VALIDITY_MS 5-min), `recordAuthTimestamp`, `force=false` and failure semantics
 * are owned by [BiometricGate] and are NOT re-implemented here; the adapter only resolves the
 * foreground [FragmentActivity] so the three `:app` call sites routed via the bound VM
 * (`vm.gate`) keep their byte-identical literals:
 *   title   "Enviar como tecleado"  / text "Este mensaje autoriza acciones en modo Auto"
 *   title   "Confirma tu identidad" / text `p.title.take(80)`
 * No bypass semantics, no re-auth interception, no force; a precondition failure surfaces the
 * same short-circuit path the screens use today. When no foreground FragmentActivity exists,
 * the adapter mirrors [BiometricGate] by returning [BiometricAuthResult.UNAVAILABLE] (there is
 * no platform surface to present the dialog from).
 */
@Singleton
class BiometricAuthorizerAndroid
    @Inject
    constructor(
        private val gate: BiometricGate,
        private val application: Application,
    ) : BiometricAuthorizer,
        Application.ActivityLifecycleCallbacks {
        @Volatile private var foreground: FragmentActivity? = null

        init {
            application.registerActivityLifecycleCallbacks(this)
        }

        /** Single-arg port member: title falls back to the shared security-screen default. */
        override suspend fun authenticate(reason: String): BiometricAuthResult =
            authenticate("Confirma tu identidad", reason)

        /** Two-string member of the port (title + reason) — the exact literals path. */
        override suspend fun authenticate(
            title: String,
            reason: String,
        ): BiometricAuthResult {
            val activity = foreground
            if (activity == null || activity.isFinishing || activity.isDestroyed) {
                return BiometricAuthResult.UNAVAILABLE
            }
            val platform = gate.authenticate(activity, title, reason)
            return when (platform) {
                dev.j3pf.rp.util.AuthResult.OK -> dev.j3pf.rp.data.ports.BiometricAuthResult.OK
                dev.j3pf.rp.util.AuthResult.FAILED -> dev.j3pf.rp.data.ports.BiometricAuthResult.FAILED
                dev.j3pf.rp.util.AuthResult.UNAVAILABLE -> dev.j3pf.rp.data.ports.BiometricAuthResult.UNAVAILABLE
            }
        }

        // ─────────────── Application.ActivityLifecycleCallbacks (foreground tracker) ───────────────

        override fun onActivityStarted(activity: Activity) {
            if (activity is FragmentActivity && foreground == null) foreground = activity
        }

        override fun onActivityStopped(activity: Activity) {
            if (activity === foreground) foreground = null
        }

        override fun onActivityResumed(activity: Activity) {
            if (activity is FragmentActivity) foreground = activity
        }

        override fun onActivitySaveInstanceState(
            activity: Activity,
            outState: Bundle,
        ) {
        }

        override fun onActivityDestroyed(activity: Activity) {
        }

        override fun onActivityCreated(
            activity: Activity,
            savedInstanceState: Bundle?,
        ) {
        }

        override fun onActivityPaused(activity: Activity) {
        }
    }
