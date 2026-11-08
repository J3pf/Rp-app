package dev.j3pf.rp.data.ports

/**
 * Notification allowlist port (orchestrator decision 2026-11-06, (a)).
 *
 * Android impl (androidMain, O2/O4): `POST_NOTIFICATIONS` runtime permission check/request; the
 * request itself is Activity-bound, so the UI-facing part is bound in shared-ui the same way as
 * [BiometricAuthorizer] (composition-root or remember-style factory consumer, not by :data-core).
 * iOS impl: `UNUserNotificationCenter.getNotificationSettings/didChangeAuthorizationSettings`.
 */
interface NotificationHealth {
    fun isEnabled(): Boolean

    suspend fun request(): NotificationPermissionOutcome
}

enum class NotificationPermissionOutcome {
    GRANTED,
    DENIED,
    UNAVAILABLE,
}

/**
 * Battery-optimization exemption port (orchestrator decision 2026-11-06, (a)).
 *
 * Android impl: PowerManager.isIgnoringBatteryOptimizations + the ACTION_REQUEST_IGNORE_*
 * intents stay in the Android actual (device-only row in docs/parity, QA step). iOS impl:
 * `supported = false` and `isIgnoring() = true` unconditionally — iOS has nothing to exempt.
 */
interface BatteryExemption {
    /** False where no battery-optimization concept exists (iOS). */
    val isSupported: Boolean

    suspend fun isIgnoring(): Boolean
}

/**
 * Platform/version info port for diagnostics, header checks and QA display
 * (orchestrator decision 2026-11-06, (a)).
 *
 * Android impl: Build.VERSION + build.gradle flavor (direct/fcm). iOS impl: NSBundle
 * read of CFBundleShortVersionString / CFBundleVersion.
 */
interface PlatformInfo {
    val versionName: String

    val versionCode: Long

    /** `direct` | `fcm` on Android; `ios` elsewhere. */
    val flavor: String
}

/**
 * Notification dedup window port for O2 (orchestrator decision 2026-11-06, (e)).
 * Contract = the observed `DedupDao.firstSeen` semantics: true exactly once per key while the
 * entry is younger than the impl's window (24 h today); internally purges aged rows.
 */
interface DedupStore {
    suspend fun firstSeen(
        key: String,
        now: Long,
    ): Boolean
}

/**
 * Open-prompt guard for the O2 dedup chain (decision (e)): a notification may not resurrect a
 * prompt that is already answered INSIDE the replay window (DB-06), and answers reuse its data.
 * Scoped by the CURRENT session's relayId, like today's repositories.
 */
interface OpenPromptGuard {
    suspend fun isOpen(promptId: String): Boolean
}
