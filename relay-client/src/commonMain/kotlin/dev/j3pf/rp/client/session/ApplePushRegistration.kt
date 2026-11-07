package dev.j3pf.rp.client.session

/**
 * Shared entry point for APNs token registration.
 *
 * A non-empty [apnsToken] must use lowercase, separator-free hexadecimal encoding, as returned by
 * [ApnsTokenFormat.hexLowercase] for the token bytes; the iOS bridge performs this conversion for
 * native `NSData` values. An empty token clears APNs registration. [environment] is null, `sandbox`,
 * or `production`.
 *
 * This method must suspend because [SessionPolicy.registerApnsToken] may make a suspending relay
 * request.
 */
class ApplePushRegistration(
    private val session: () -> SessionPolicy?,
) {
    suspend fun register(
        apnsToken: String?,
        environment: String?,
    ): Boolean = session()?.registerApnsToken(apnsToken, environment) ?: false
}
