import CryptoKit
import Foundation
import Security
import UserNotifications

// RemotePi Notification Service Extension (NSE).
//
// Contract (O2 app-bridge <-> NSE boundary, relay v0.7.0, O1 Move 3):
// - Payload is the OPAQUE {k, n, c} envelope verbatim (k = push kind, n = 12-byte Base64 nonce,
//   c = Base64 ciphertext || 16-byte GCM tag); `aps.mutable-content` = 1.
// - Decrypt: AES-256-GCM, AAD = ASCII "rp1"; key = the 32-byte Base64 `pushKey` fetched from the
//   iOS Keychain ACCESS GROUP shared by app and NSE (entitlements, S12-reserved plumbing).
// - `aps-collapse-id` = promptId (relay clamps to 64 B); dedupe by promptId is registered BEFORE
//   the mute filter (Android-pinned order).
// - The NSE must NOT: re-register tokens, transform the envelope beyond the contract, log
//   ciphertext or key material, or assume any FCM budget (APNs path: relay final JSON <= 4041 B,
//   body budget 2900 B).
final class NotificationService: UNNotificationServiceExtension {
    private static let aad = Data("rp1".utf8)
    private static let keychainService = "dev.j3pf.rp.secrets"
    private static let pushKeyAccount = "rp_nse_push_key"
    private static let mutedAgentsAccount = "rp_nse_muted_agents"
    private static let dedupeLock = NSLock()
    private static var seenPromptIds = Set<String>()

    private var contentHandler: ((UNNotificationContent) -> Void)?
    private var bestAttemptContent: UNMutableNotificationContent?

    override func didReceive(
        _ request: UNNotificationRequest,
        withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void,
    ) {
        self.contentHandler = contentHandler
        let best = (request.content.mutableCopy() as? UNMutableNotificationContent) ?? UNMutableNotificationContent()
        best.userInfo = request.content.userInfo
        bestAttemptContent = best

        guard
            let k = request.content.userInfo["k"] as? String,
            let nonceB64 = request.content.userInfo["n"] as? String,
            let cipherB64 = request.content.userInfo["c"] as? String,
            let pushKeyB64 = Self.readKeychainString(account: Self.pushKeyAccount),
            let plaintext = Self.decryptEnvelope(pushKeyB64: pushKeyB64, nonceB64: nonceB64, cipherB64: cipherB64),
            let json = (try? JSONSerialization.jsonObject(with: Data(plaintext.utf8))) as? [String: Any]
        else {
            contentHandler(best)
            return
        }

        let promptId = (json["promptId"] as? String) ?? (request.content.userInfo["aps-collapse-id"] as? String)
        if let promptId, !promptId.isEmpty {
            // Register promptId dedupe BEFORE the mute filter (Android-pinned order).
            if !Self.markFirstSeen(promptId: promptId) {
                contentHandler(best)
                return
            }
        }

        let agentId = json["agentId"] as? String
        let danger = json["danger"] as? String
        if let agentId, Self.isMuted(agentId: agentId, isCritical: danger == "high") {
            contentHandler(best)
            return
        }

        let machineName = ((json["machineName"] as? String)?.isEmpty == false ? (json["machineName"] as? String) : nil) ?? "RP"
        let agentLabel = ((json["agentLabel"] as? String)?.isEmpty == false ? (json["agentLabel"] as? String) : nil) ?? "agent"
        best.title = "\(machineName) · \(agentLabel)"

        let titleText = (json["title"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let previewText = (json["preview"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let messageText = (json["message"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)

        switch k {
        case "prompt":
            if let titleText, !titleText.isEmpty, let messageText, !messageText.isEmpty {
                best.body = "\(titleText)\n\(messageText)"
            } else {
                best.body = titleText ?? messageText ?? "Action required"
            }
        case "settled":
            best.body = previewText ?? messageText ?? "Finished"
        case "terminal_wait":
            if let titleText, !titleText.isEmpty {
                best.body = "Waiting for input in terminal: \(titleText)"
            } else {
                best.body = "Waiting for input in terminal"
            }
        case "error":
            best.body = messageText ?? titleText ?? "Agent encountered an error"
        default:
            best.body = titleText ?? previewText ?? messageText ?? best.body
        }

        if let agentId, !agentId.isEmpty {
            best.threadIdentifier = "rp.agent.\(agentId)"
        }

        contentHandler(best)
    }

    override func serviceExtensionTimeWillExpire() {
        if let contentHandler, let bestAttemptContent {
            contentHandler(bestAttemptContent)
        }
    }

    private static func markFirstSeen(promptId: String) -> Bool {
        dedupeLock.lock()
        defer { dedupeLock.unlock() }
        return seenPromptIds.insert(promptId).inserted
    }

    private static func isMuted(agentId: String, isCritical: Bool) -> Bool {
        if isCritical { return false }
        guard let raw = readKeychainString(account: mutedAgentsAccount), !raw.isEmpty else {
            return false
        }
        let muted = Set(raw.split(separator: ",").map(String.init))
        return muted.contains(agentId)
    }

    private static func decryptEnvelope(
        pushKeyB64: String,
        nonceB64: String,
        cipherB64: String,
    ) -> String? {
        guard
            let keyData = Data(base64Encoded: pushKeyB64),
            keyData.count == 32,
            let nonceData = Data(base64Encoded: nonceB64),
            nonceData.count == 12,
            let combinedCipher = Data(base64Encoded: cipherB64),
            combinedCipher.count >= 16
        else {
            return nil
        }

        let ciphertext = combinedCipher.prefix(combinedCipher.count - 16)
        let tag = combinedCipher.suffix(16)

        guard
            let nonce = try? AES.GCM.Nonce(data: nonceData),
            let sealedBox = try? AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertext, tag: tag),
            let decrypted = try? AES.GCM.open(
                sealedBox,
                using: SymmetricKey(data: keyData),
                authenticating: aad,
            )
        else {
            return nil
        }
        return String(data: decrypted, encoding: .utf8)
    }

    private static func readKeychainString(account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else {
            return nil
        }
        return String(data: data, encoding: .utf8)
    }
}
