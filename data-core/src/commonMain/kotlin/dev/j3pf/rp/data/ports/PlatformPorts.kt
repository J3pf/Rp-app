package dev.j3pf.rp.data.ports

import dev.j3pf.rp.protocol.AttachmentRef
import kotlinx.coroutines.flow.StateFlow

/**
 * Background work scheduling port (CH-06/PR-02 semantics).
 *
 * Android impl: WorkManager unique work `send-outbox` (CONNECTED constraint, EXPONENTIAL 15 s
 * backoff, expedited fallback) + `answer-outbox` retry. iOS impl (O2, T3d+): BGTaskScheduler; on
 * iOS long-lived socket wakeups are replaced by APNs push (needs the Apple provisioning step
 * flagged in the orchestrator log L2 — no code can compensate for that).
 */
interface BackgroundScheduler {
    fun enqueueOutboxFlush()

    fun enqueueAnswerRetry()

    /** Logout path: cancel every scheduled unit (SE-05). */
    fun cancelAllWork()
}

/** A picked attachment already materialized locally (compressed/renamed), ready for upload. */
data class PreparedLocalFile(
    val fileRef: String,
    val name: String,
    val mime: String,
    val size: Long,
)

/**
 * Media/document picker port, negotiated with O3 (chat UI, 2026-11-06) and recorded in ADR S2/S11.
 *
 * Android impl: `PickMultipleVisualMedia(10)` images, the all-MIME wildcard documents, camera via
 * `AttachmentsRepository.newCameraTarget()` (FileProvider). iOS impl (fresh, S12):
 * PHPickerViewController / UIDocumentPickerViewController; `cameraTarget()` is Android-only and
 * returns null on iOS (photo-library fallback), never touching file provider machinery.
 */
interface AttachmentsPlatformHost {
    suspend fun pickImages(maxItems: Int = 10): List<AttachmentRef>

    suspend fun pickDocuments(mime: List<String> = listOf("*/*")): List<AttachmentRef>

    /** Android camera intent target (`Uri` under a FileProvider-capable area); null elsewhere. */
    suspend fun cameraTarget(): PreparedLocalFile?
}

/** Result codes for the biometric authorizer, mirroring the current Android `util.AuthResult`. */
enum class BiometricAuthResult {
    OK,
    FAILED,
    UNAVAILABLE,
}

/**
 * Biometric approval port, negotiated with O3 (2026-11-06), recorded in ADR S2/S11.
 *
 * The 5-minute freshness window (`VALIDITY_MS`) lives INSIDE each implementation — UI code only
 * passes the `reason` (the two existing Spanish snackbar strings stay with the UI slice).
 *
 * Android impl: wrapper over the existing `util/BiometricGate.kt` (BiometricPrompt +
 * FragmentActivity plumbing stays inside the wrapper). iOS impl (fresh): `LAContext.evaluatePolicy`
 * + Keychain persistence needs the `keychain-access-groups` entitlement (S12).
 */
interface BiometricAuthorizer {
    suspend fun authenticate(reason: String): BiometricAuthResult

    companion object {
        /** Freshness window of the current Android gate; implementations must honor it. */
        const val VALIDITY_MS = 5 * 60_000L
    }
}

/** Documented error codes for voice input (decouple SpeechRecognizer codes from iOS). */
object VoiceErrorCode {
    const val RECOGNIZER_BANNED = 1
    const val NETWORK = 2
    const val NO_RECOGNIZER = 3
    const val CLIENT = 4
    const val BUSY = 5
    const val NO_PERMISSIONS = 6
}

interface VoiceListener {
    fun onPartial(text: String)

    fun onFinal(text: String)

    fun onError(code: Int)
}

/**
 * Speech-input port for the composer, negotiated with O3 (2026-11-06). Error codes MUST come from
 * [VoiceErrorCode]; consumers map codes to the existing placeholder/stop behavior without naming
 * platform APIs.
 *
 * Android impl: `SpeechRecognizer` (O3 snapshot path). iOS impl (fresh): `SFSpeechRecognizer` +
 * `AVAudioSession` record permission request.
 */
interface VoiceInputHost {
    fun start(listener: VoiceListener)

    fun stop()

    val listening: StateFlow<Boolean>
}
