package dev.j3pf.rp.ui.push

import dev.j3pf.rp.client.session.ApnsTokenFormat
import dev.j3pf.rp.client.session.ApplePushRegistration
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import platform.Foundation.NSData

/**
 * iOS APNs registration bridge. Keep the environment aligned with the app's APNs entitlement: if
 * it is wrong, fix [DEFAULT_APNS_ENVIRONMENT] and issue registration again; never retry against
 * another environment.
 *
 * O4 Swift delegate-hook tripwire: call [register] from
 * `application(_:didRegisterForRemoteNotificationsWithDeviceToken:)`.
 * APNs token bytes are sent as lowercase, separator-free hexadecimal. [apnsTokenHex] bridges
 * native `NSData` to the JVM-tested common counterpart [ApnsTokenFormat.hexLowercase]. Relay-side
 * token normalization is a separate queued Rp-relay task.
 */
object IosPushRegistrationBridge {
    const val DEFAULT_APNS_ENVIRONMENT = "sandbox"

    @OptIn(ExperimentalForeignApi::class)
    fun apnsTokenHex(data: NSData): String {
        val byteCount = data.length.toInt()
        if (byteCount == 0) return ApnsTokenFormat.hexLowercase(byteArrayOf())
        val bytes =
            checkNotNull(data.bytes) {
                "NSData bytes are unavailable for a non-empty APNs token"
            }
        return ApnsTokenFormat.hexLowercase(bytes.readBytes(byteCount))
    }

    suspend fun register(
        registration: ApplePushRegistration,
        apnsToken: NSData?,
    ): Boolean =
        registration.register(
            apnsToken?.let { apnsTokenHex(it) },
            DEFAULT_APNS_ENVIRONMENT,
        )
}
