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

/**
 * Media/document/camera port, negotiated with O3 (chat UI, 2026-11-06) and platform-neutral by
 * design review: every phone keeps the camera (S5 — no lost functionality).
 *
 * Android impl: `PickMultipleVisualMedia(10)` images, the all-MIME wildcard documents, camera
 * intent into an internal FileProvider target (`AttachmentsRepository.newCameraTarget()` stays
 * internal to the Android impl). iOS impl (fresh, S12): PHPickerViewController /
 * UIDocumentPickerViewController / UIImagePickerController (or AVFoundation) capture.
 *
 * Binding: the Android implementation must own its ActivityResult launchers, so it is created by
 * a `rememberAttachmentsHost()`-style composable factory in shared-ui (or a composition-root
 * object holding the launcher state and wrapped behind this interface); O3 never touches the
 * launcher plumbing. The iOS implementation is constructed by the iOS composition root and
 * presents controllers through the application window.
 */
interface AttachmentsPlatformHost {
    suspend fun pickImages(maxItems: Int = 10): List<AttachmentRef>

    suspend fun pickDocuments(mime: List<String> = listOf("*/*")): List<AttachmentRef>

    /**
     * Platform-native camera capture, returning the already-prepared attachment (compressed /
     * renamed as the upload base). Only null when the user cancels or camera permission is denied;
     * implementations without a camera surface must not report having one (the UP-facing question
     * is answered by `isCameraAvailable`).
     */
    suspend fun capturePhoto(): AttachmentRef?

    /** True on Android phones with a camera app; true on iPhones; false where none exists. */
    val isCameraAvailable: Boolean

    /** Additive staging read for platform-selected URIs (Unit P6). */
    suspend fun readAttachment(uri: String): Result<StagedAttachment> =
        Result.failure(UnsupportedOperationException("readAttachment not supported by this host"))

    /** Additive staging deletion for a staged attachment path (Unit P6). */
    fun deleteStaging(stagingPath: String) {}

    /** Additive stale-staging cleanup older than [olderThanMs] (Unit P6). */
    fun cleanStaleStaging(olderThanMs: Long = 24 * 3600_000L) {}
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

    /**
     * ADDITIVE overload (O3 M4 request 2026-11-07; orchestrator ruling 2026-11-07): the DEFAULT
     * body delegates to the single-arg member so every EXISTING implementation, fake and
     * iosMain actual keeps compiling and behaving exactly as today; implementations that CAN
     * render both strings (Android adapter via the :app lifecycle seam, iOS actual) OVERRIDE
     * it and render `title` + `reason` while keeping the same freshness/timestamp semantics —
     * it preserves the byte-locked Spanish screen literals rendered in the platform
     * BiometricPrompt today (ChatScreen.kt:318/:350, PromptSheet.kt:95). Adds NO bypass; the
     * timestamp is recorded with the today-default (always true at all three call sites, per
     * O3's sweep). No android types in the port. iOS override contract: title = fallback
     * heading, reason = body.
     */
    suspend fun authenticate(
        title: String,
        reason: String,
    ): BiometricAuthResult = authenticate(reason)

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
