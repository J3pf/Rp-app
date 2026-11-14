import Security
import SharedUi
import SwiftUI
import UIKit

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    init() {
        KeychainStoreHelper.installBridge()
        #if DEBUG
        SharedUi.IosPushRegistrationBridge.shared.apnsEnvironment = "sandbox"
        #else
        SharedUi.IosPushRegistrationBridge.shared.apnsEnvironment = "production"
        #endif
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
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
}

/// Remote-notification token hand-off. The ONLY push wiring this app side owns: relay the device
/// token to the Kotlin bridge verbatim as NSData. Token conversion, environment and relay PUT live
/// in `SharedUi.IosPushRegistrationBridge` (O2's registered boundary). Never log the token.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil,
    ) -> Bool {
        KeychainStoreHelper.installBridge()
        // Token never arrives until iOS is asked to register; do NOT request notification
        // authorization here (product/UX decision pending).
        UIApplication.shared.registerForRemoteNotifications()
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data,
    ) {
        SharedUi.IosPushRegistrationBridge.shared.register(apnsToken: deviceToken) { _, _ in }
    }
}

private enum KeychainStoreHelper {
    private static let service = "dev.j3pf.rp.secrets"
    private static let missingEntitlementStatus: OSStatus = -34018

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
        var status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == missingEntitlementStatus, accessGroup != nil {
            return readString(account: account, accessGroup: nil)
        }
        guard status == errSecSuccess, let data = item as? Data else {
            return nil
        }
        return String(data: data, encoding: .utf8)
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
        }
    }

    private static func deleteItem(account: String, accessGroup: String?) {
        let query = baseQuery(account: account, accessGroup: accessGroup)
        let status = SecItemDelete(query as CFDictionary)
        if status == missingEntitlementStatus, accessGroup != nil {
            deleteItem(account: account, accessGroup: nil)
        }
    }
}
