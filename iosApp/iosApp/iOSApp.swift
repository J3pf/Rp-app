import SharedUi
import SwiftUI
import UIKit

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

/// Remote-notification token hand-off. The ONLY push wiring this app side owns: relay the device
/// token to the Kotlin bridge verbatim as NSData. Token conversion, environment and relay PUT live
/// in `SharedUi.IosPushRegistrationBridge` (O2's registered boundary). Never log the token.
///
/// TODO(push): registration stays INERT until O3's iOS composition root sets
/// `IosPushRegistrationBridge.registration` (named gap in docs; no requestAuthorization here — the
/// UX prompt decision is deliberately NOT made at launch).
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil,
    ) -> Bool {
        // Token never arrives until iOS is asked to register; do NOT request notification
        // authorization here (product/UX decision pending).
        UIApplication.shared.registerForRemoteNotifications()
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data,
    ) {
        // Inert until the iOS composition root sets SharedUi bridge's registration anchor
        // (named gap); never logs the token or any failure detail.
        SharedUi.IosPushRegistrationBridge.shared.register(apnsToken: deviceToken) { _, _ in }
    }
}
