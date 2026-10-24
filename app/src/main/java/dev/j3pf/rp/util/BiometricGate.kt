package dev.j3pf.rp.util

import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

enum class AuthResult { OK, FAILED, UNAVAILABLE }

/**
 * Puerta biométrica (BiometricPrompt) con validez de 5 minutos. Se exige para enviar "como tecleado"
 * (cuenta como autorización humana en gentle-pi Auto), para aprobaciones críticas y para abrir la app (fase 4).
 */
@Singleton
class BiometricGate
    @Inject
    constructor() {
        @Volatile private var hasAuthenticated = false

        @Volatile private var lastAuthAt = 0L

        fun isFresh(now: Long = SystemClock.elapsedRealtime()) = hasAuthenticated && now >= lastAuthAt && now - lastAuthAt < VALIDITY_MS

        fun invalidate() {
            hasAuthenticated = false
            lastAuthAt = 0L
        }

        internal fun recordSuccessForTest(atElapsedRealtimeMs: Long = SystemClock.elapsedRealtime()) {
            lastAuthAt = atElapsedRealtimeMs
            hasAuthenticated = true
        }

        private val authenticators: Int
            get() = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

        fun isAvailable(activity: FragmentActivity): Boolean {
            val bm = BiometricManager.from(activity)
            if (bm.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) return true
            // Android 10: PIN/patrón como alternativa vía setDeviceCredentialAllowed.
            return Build.VERSION.SDK_INT < 30 &&
                activity.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure == true
        }

        suspend fun authenticate(
            activity: FragmentActivity,
            title: String,
            subtitle: String? = null,
            force: Boolean = false,
            recordAuthTimestamp: Boolean = true,
        ): AuthResult {
            if (!force && isFresh()) return AuthResult.OK
            if (!isAvailable(activity)) return AuthResult.UNAVAILABLE
            return suspendCancellableCoroutine { cont ->
                val prompt =
                    BiometricPrompt(
                        activity,
                        ContextCompat.getMainExecutor(activity),
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                if (recordAuthTimestamp) {
                                    lastAuthAt = SystemClock.elapsedRealtime()
                                    hasAuthenticated = true
                                }
                                if (cont.isActive) cont.resume(AuthResult.OK)
                            }

                            override fun onAuthenticationError(
                                errorCode: Int,
                                errString: CharSequence,
                            ) {
                                if (cont.isActive) cont.resume(AuthResult.FAILED)
                            }
                        },
                    )
                val info =
                    BiometricPrompt.PromptInfo
                        .Builder()
                        .setTitle(title)
                        .apply {
                            subtitle?.let(::setSubtitle)
                            if (Build.VERSION.SDK_INT >= 30) {
                                setAllowedAuthenticators(authenticators)
                            } else {
                                // Deprecated on newer Android, but required for API 29 device-credential fallback.
                                @Suppress("DEPRECATION")
                                setDeviceCredentialAllowed(true)
                            }
                        }.build()
                prompt.authenticate(info)
                cont.invokeOnCancellation { runCatching { prompt.cancelAuthentication() } }
            }
        }

        companion object {
            const val VALIDITY_MS = 5 * 60_000L
        }
    }
