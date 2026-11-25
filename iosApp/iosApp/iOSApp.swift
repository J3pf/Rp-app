import CryptoKit
import Network
import os
import Security
import SharedUi
import SwiftUI
import UIKit
import UserNotifications

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    init() {
        KeychainStoreHelper.installBridge()
        CryptoKitAeadHelper.installBridge()
        #if DEBUG
        SharedUi.IosPushRegistrationBridge.shared.apnsEnvironment = "sandbox"
        #else
        SharedUi.IosPushRegistrationBridge.shared.apnsEnvironment = "production"
        #endif
    }

    var body: some Scene {
        WindowGroup {
            ZStack {
                ContentView()
                if scenePhase != .active && Self.isFlagSecureEnabled() {
                    PrivacyCoverView()
                        .zIndex(1)
                }
            }
        }
        .onChange(of: scenePhase) { newPhase in
            switch newPhase {
            case .background:
                SharedUi.IosAppScope.shared.onAppBackgrounded()
            case .active:
                SharedUi.IosAppScope.shared.onAppForegrounded()
            default:
                break
            }
        }
    }

    /// Reads the `flagSecure` preference written by `KeyValueSettingsStore` (`SettingsKeys.K_FLAG_SECURE`).
    private static func isFlagSecureEnabled() -> Bool {
        if let raw = UserDefaults.standard.string(forKey: "flagSecure") {
            return raw.caseInsensitiveCompare("true") == .orderedSame
        }
        return UserDefaults.standard.bool(forKey: "flagSecure")
    }
}

private struct PrivacyCoverView: View {
    var body: some View {
        ZStack {
            Color(uiColor: .systemBackground)
                .ignoresSafeArea()
            VStack(spacing: 12) {
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 40, weight: .semibold))
                    .foregroundStyle(.secondary)
                Text("RemotePi")
                    .font(.headline)
                    .foregroundStyle(.secondary)
            }
        }
        .accessibilityHidden(true)
    }
}

/// Remote-notification authorization and token hand-off. Relays the device token to the Kotlin
/// bridge verbatim as NSData; token conversion, environment, and relay PUT live in
/// `SharedUi.IosPushRegistrationBridge`. Never log the token or credentials.
final class AppDelegate: NSObject, UIApplicationDelegate {
    private static let pushLogger = Logger(subsystem: "dev.j3pf.rp.iosApp", category: "push")
    private var networkMonitor: NWPathMonitor?
    private let networkQueue = DispatchQueue(label: "dev.j3pf.rp.network")
    private var memoryWarningObserver: NSObjectProtocol?

    deinit {
        networkMonitor?.cancel()
        if let observer = memoryWarningObserver {
            NotificationCenter.default.removeObserver(observer)
        }
    }

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil,
    ) -> Bool {
        memoryWarningObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didReceiveMemoryWarningNotification,
            object: nil,
            queue: .main
        ) { _ in
            MainViewControllerKt.notifyIosMemoryWarning()
        }
        startNetworkMonitor()
        let center = UNUserNotificationCenter.current()
        center.getNotificationSettings { settings in
            if settings.authorizationStatus == .notDetermined {
                center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
                    if let error {
                        let nsError = error as NSError
                        Self.pushLogger.error(
                            "Notification authorization failed (domain=\(nsError.domain, privacy: .public), code=\(nsError.code, privacy: .public))"
                        )
                    } else {
                        Self.pushLogger.info("Notification authorization completed (granted=\(granted, privacy: .public))")
                    }
                    DispatchQueue.main.async {
                        application.registerForRemoteNotifications()
                    }
                }
            } else {
                DispatchQueue.main.async {
                    application.registerForRemoteNotifications()
                }
            }
        }
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data,
    ) {
        SharedUi.IosPushRegistrationBridge.shared.register(apnsToken: deviceToken) { _, error in
            if let error {
                let nsError = error as NSError
                Self.pushLogger.error(
                    "APNs token hand-off failed (domain=\(nsError.domain, privacy: .public), code=\(nsError.code, privacy: .public))"
                )
            }
        }
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error,
    ) {
        let nsError = error as NSError
        Self.pushLogger.error(
            "APNs remote notification registration failed (domain=\(nsError.domain, privacy: .public), code=\(nsError.code, privacy: .public))"
        )
    }

    private func startNetworkMonitor() {
        guard networkMonitor == nil else { return }
        let monitor = NWPathMonitor()
        monitor.pathUpdateHandler = { path in
            let satisfied = path.status == .satisfied
            let signature = Self.interfaceSignature(for: path)
            SharedUi.IosNetworkMonitorBridge.shared.updatePath(
                satisfied: satisfied,
                interfaceSignature: signature
            )
        }
        monitor.start(queue: networkQueue)
        networkMonitor = monitor
    }

    private static func interfaceSignature(for path: NWPath) -> String {
        var interfaces: [String] = []
        if path.usesInterfaceType(.wifi) {
            interfaces.append("wifi")
        }
        if path.usesInterfaceType(.cellular) {
            interfaces.append("cellular")
        }
        if path.usesInterfaceType(.wiredEthernet) {
            interfaces.append("wiredEthernet")
        }
        if path.usesInterfaceType(.other) {
            interfaces.append("other")
        }
        if interfaces.isEmpty {
            return path.status == .satisfied ? "unknown" : "none"
        }
        return interfaces.joined(separator: "+")
    }
}

private enum CryptoKitAeadHelper {
    private static let keyByteCount = 32

    static func installBridge() {
        SharedUi.IosAeadBridge.shared.configure(
            onSeal: { keyBase64, plaintext, aad in
                seal(keyBase64: keyBase64, plaintext: plaintext, aad: aad)
            },
            onOpen: { keyBase64, combinedBase64, aad in
                open(keyBase64: keyBase64, combinedBase64: combinedBase64, aad: aad)
            }
        )
    }

    /// Encrypts plaintext using AES-256-GCM with a fresh 12-byte random nonce and UTF-8 AAD.
    /// Wire format: Base64(`nonce (12 bytes) || ciphertext || tag (16 bytes)`), matching Kotlin `Aes256Gcm`.
    private static func seal(keyBase64: String, plaintext: String, aad: String) -> String? {
        guard
            let keyData = Data(base64Encoded: keyBase64),
            keyData.count == keyByteCount
        else {
            return nil
        }
        let symmetricKey = SymmetricKey(data: keyData)
        let plaintextData = Data(plaintext.utf8)
        let aadData = Data(aad.utf8)
        do {
            let sealed = try AES.GCM.seal(
                plaintextData,
                using: symmetricKey,
                nonce: AES.GCM.Nonce(),
                authenticating: aadData
            )
            return sealed.combined?.base64EncodedString()
        } catch {
            return nil
        }
    }

    /// Decrypts Base64(`nonce (12 bytes) || ciphertext || tag (16 bytes)`) authenticated with UTF-8 AAD.
    private static func open(keyBase64: String, combinedBase64: String, aad: String) -> String? {
        guard
            let keyData = Data(base64Encoded: keyBase64),
            keyData.count == keyByteCount,
            let combined = Data(base64Encoded: combinedBase64)
        else {
            return nil
        }
        let symmetricKey = SymmetricKey(data: keyData)
        let aadData = Data(aad.utf8)
        do {
            let sealedBox = try AES.GCM.SealedBox(combined: combined)
            let plaintextData = try AES.GCM.open(
                sealedBox,
                using: symmetricKey,
                authenticating: aadData
            )
            return String(data: plaintextData, encoding: .utf8)
        } catch {
            return nil
        }
    }
}

private enum KeychainStoreHelper {
    private static let service = "dev.j3pf.rp.secrets"
    private static let missingEntitlementStatus: OSStatus = -34018
    /// Must match `EncryptedKeyValueSecretStore.TRANSIENT_READ_ERROR_SENTINEL` so Kotlin aborts
    /// `load()` without clearing Keychain entries on transient errors (e.g. `errSecInteractionNotAllowed`).
    private static let transientReadErrorSentinel = "__RP_TRANSIENT_KEYCHAIN_ERROR__"
    private static let logger = Logger(subsystem: "dev.j3pf.rp.iosApp", category: "keychain")

    static func installBridge() {
        SharedUi.IosKeychainStoreBridge.shared.configure(
            onGet: { key, accessGroup in
                readString(account: key, accessGroup: accessGroup)
            },
            onPut: { key, value, accessGroup in
                writeString(account: key, value: value, accessGroup: accessGroup)
            },
            onRemove: { key, accessGroup in
                deleteItem(account: key, accessGroup: accessGroup)
            },
        )
    }

    private static func baseQuery(account: String, accessGroup: String?) -> [String: Any] {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        if let accessGroup, !accessGroup.isEmpty {
            query[kSecAttrAccessGroup as String] = accessGroup
        }
        return query
    }

    private static func readString(account: String, accessGroup: String?) -> String? {
        var query = baseQuery(account: account, accessGroup: accessGroup)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == missingEntitlementStatus, accessGroup != nil {
            return readString(account: account, accessGroup: nil)
        }
        switch status {
        case errSecSuccess:
            guard let data = item as? Data else {
                return nil
            }
            return String(data: data, encoding: .utf8)
        case errSecItemNotFound:
            return nil
        default:
            // Return sentinel so EncryptedKeyValueSecretStore.load() treats this as a transient
            // error (e.g. errSecInteractionNotAllowed while locked) instead of missing/corrupt data.
            logger.error("Keychain read failed with transient OSStatus=\(status, privacy: .public)")
            return transientReadErrorSentinel
        }
    }

    private static func writeString(account: String, value: String, accessGroup: String?) {
        let data = Data(value.utf8)
        let query = baseQuery(account: account, accessGroup: accessGroup)
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        var status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            var addQuery = query
            addQuery[kSecValueData as String] = data
            addQuery[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            status = SecItemAdd(addQuery as CFDictionary, nil)
        }
        if status == missingEntitlementStatus, accessGroup != nil {
            writeString(account: account, value: value, accessGroup: nil)
            return
        }
        if status != errSecSuccess {
            logger.error("Keychain write failed with OSStatus=\(status, privacy: .public)")
        }
    }

    private static func deleteItem(account: String, accessGroup: String?) {
        let query = baseQuery(account: account, accessGroup: accessGroup)
        let status = SecItemDelete(query as CFDictionary)
        if status == missingEntitlementStatus, accessGroup != nil {
            deleteItem(account: account, accessGroup: nil)
            return
        }
        if status != errSecSuccess && status != errSecItemNotFound {
            logger.error("Keychain delete failed with OSStatus=\(status, privacy: .public)")
        }
    }
}
