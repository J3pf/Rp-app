import Foundation
import UserNotifications

// RemotePi Notification Service Extension (NSE) skeleton.
//
// Contract (O2 app-bridge <-> NSE boundary, relay v0.7.0):
// - Payload is the OPAQUE {k, n, c} envelope verbatim (k = AES-256 key, n = 12-byte nonce,
//   c = ciphertext); `aps.mutable-content` = 1.
// - Decrypt: AES-256-GCM, AAD = ASCII "rp1"; key = the 32-byte Base64 `pushKey` fetched from the
//   iOS Keychain ACCESS GROUP shared by app and NSE (entitlements, S12-reserved plumbing).
// - `aps-collapse-id` = promptId (relay clamps to 64 B); dedupe by promptId is registered BEFORE
//   the mute filter (Android-pinned order).
// - The NSE must NOT: re-register tokens, transform the envelope beyond the contract, log
//   ciphertext or key material, or assume any FCM budget (APNs path: relay final JSON <= 4041 B,
//   body budget 2900 B).
//
// TODO(push): decrypt is intentionally UNIMPLEMENTED (skeleton, compile-verified only):
//   - TODO(nse-keychain): fetch/decode the shared keychain-access-group pushKey (S12 entitlements).
//   - TODO(nse-crypto): AES-256-GCM decrypt with AAD "rp1" (spans O1/O3 module ownership).
//   - TODO(nse-dedupe): promptId dedupe before mute filter (mirror the Android-pinned order).
// No ciphertext or key material is ever logged.
final class NotificationService: UNNotificationServiceExtension {
    private var contentHandler: ((UNNotificationContent) -> Void)?
    private var bestAttemptContent: UNMutableNotificationContent?

    override func didReceive(
        _ request: UNNotificationRequest,
        withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void,
    ) {
        self.contentHandler = contentHandler
        let best = UNMutableNotificationContent()
        // Skeleton behavior: fall through with the barest valid content so a skipped decrypt
        // never crashes the delivery pipeline.
        best.userInfo = request.content.userInfo
        bestAttemptContent = best
        contentHandler(best)
    }

    override func serviceExtensionTimeWillExpire() {
        if let contentHandler, let bestAttemptContent {
            contentHandler(bestAttemptContent)
        }
    }
}
