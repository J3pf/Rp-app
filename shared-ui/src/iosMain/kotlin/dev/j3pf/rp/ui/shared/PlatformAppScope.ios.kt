package dev.j3pf.rp.ui.shared

import dev.j3pf.rp.client.http.UrlSessionRelayTransport
import dev.j3pf.rp.client.session.ApplePushRegistration
import dev.j3pf.rp.client.session.SessionPushPlatform
import dev.j3pf.rp.client.ws.ReconnectingSocketPolicy
import dev.j3pf.rp.client.ws.UrlSessionSocketDriver
import dev.j3pf.rp.data.net.WsReply
import dev.j3pf.rp.data.ports.BiometricAuthResult
import dev.j3pf.rp.data.ports.BiometricAuthorizer
import dev.j3pf.rp.data.secure.EncryptedKeyValueSecretStore
import dev.j3pf.rp.data.secure.IosKeychainBridge
import dev.j3pf.rp.data.secure.SecretKeyValueBacking
import dev.j3pf.rp.data.secure.SecretStoreIos
import dev.j3pf.rp.data.settings.SettingsKeys
import dev.j3pf.rp.ui.push.IosPushRegistrationBridge
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLRequestUseProtocolCachePolicy
import platform.Foundation.NSUserDefaults
import platform.Foundation.setValue
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.UIKit.UIDevice
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume

/**
 * Swift-facing bridge in `SharedUi` so `iOSApp.swift` can register native `Security.framework`
 * (`kSecClassGenericPassword`) callbacks into [IosKeychainBridge] without needing to export
 * `:data-core` symbols directly in the Objective-C framework header.
 */
object IosKeychainStoreBridge {
    @Volatile
    private var getHook: ((key: String, accessGroup: String?) -> String?)? = null

    @Volatile
    private var putHook: ((key: String, value: String, accessGroup: String?) -> Unit)? = null

    @Volatile
    private var removeHook: ((key: String, accessGroup: String?) -> Unit)? = null

    fun configure(
        onGet: (key: String, accessGroup: String?) -> String?,
        onPut: (key: String, value: String, accessGroup: String?) -> Unit,
        onRemove: (key: String, accessGroup: String?) -> Unit,
    ) {
        getHook = onGet
        putHook = onPut
        removeHook = onRemove
        IosKeychainBridge.backingFactory = { group ->
            SwiftHookSecretBacking(group)
        }
        IosKeychainBridge.onPushKeyChanged = { pushKey, group ->
            if (pushKey != null) {
                putHook?.invoke(EncryptedKeyValueSecretStore.K_NSE_PUSH_KEY, pushKey, group)
            } else {
                removeHook?.invoke(EncryptedKeyValueSecretStore.K_NSE_PUSH_KEY, group)
            }
        }
    }

    internal fun putMutedAgents(
        mutedAgentsCsv: String,
        accessGroup: String?,
    ) {
        putHook?.invoke("rp_nse_muted_agents", mutedAgentsCsv, accessGroup)
    }

    internal fun putCriticalAlways(
        criticalAlways: Boolean,
        accessGroup: String?,
    ) {
        putHook?.invoke("rp_nse_critical_always", criticalAlways.toString(), accessGroup)
    }

    private class SwiftHookSecretBacking(
        private val accessGroup: String?,
    ) : SecretKeyValueBacking {
        private val knownKeys =
            listOf(
                EncryptedKeyValueSecretStore.K_MASTER_KEY,
                EncryptedKeyValueSecretStore.K_RELAY_ID,
                EncryptedKeyValueSecretStore.K_URL,
                EncryptedKeyValueSecretStore.K_NAME,
                EncryptedKeyValueSecretStore.K_DEVICE_ID,
                EncryptedKeyValueSecretStore.K_TOKEN,
                EncryptedKeyValueSecretStore.K_PUSH_KEY,
                EncryptedKeyValueSecretStore.K_NSE_PUSH_KEY,
                "rp_nse_muted_agents",
                "rp_nse_critical_always",
            )

        override fun getString(key: String): String? = getHook?.invoke(key, accessGroup)

        override fun putString(
            key: String,
            value: String,
        ) {
            putHook?.invoke(key, value, accessGroup)
        }

        override fun remove(key: String) {
            removeHook?.invoke(key, accessGroup)
        }

        override fun clear() {
            knownKeys.forEach { removeHook?.invoke(it, accessGroup) }
        }
    }
}

private class NsUserDefaultsSettingsBacking : SecretKeyValueBacking {
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(
        key: String,
        value: String,
    ) {
        defaults.setObject(value, forKey = key)
    }

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }

    override fun clear() {
        SettingsKeys.ALL_NAMES.forEach { defaults.removeObjectForKey(it) }
    }
}

object IosBiometricAuthorizer : BiometricAuthorizer {
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun authenticate(reason: String): BiometricAuthResult =
        suspendCancellableCoroutine { cont ->
            val context = LAContext()
            val canEval = context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)
            if (!canEval) {
                cont.resume(BiometricAuthResult.UNAVAILABLE)
                return@suspendCancellableCoroutine
            }
            context.evaluatePolicy(
                policy = LAPolicyDeviceOwnerAuthentication,
                localizedReason = reason,
            ) { success, _ ->
                if (cont.isActive) {
                    cont.resume(if (success) BiometricAuthResult.OK else BiometricAuthResult.FAILED)
                }
            }
        }
}

/**
 * iOS composition root (`IosAppScope`, O3/O4) wiring `SecretStoreIos`, `NSUserDefaults`-backed
 * `SettingsStore`, `UrlSessionRelayTransport`, `UrlSessionSocketDriver`, `ReconnectingSocketPolicy`,
 * `SessionPolicy`, and `IosPushRegistrationBridge.registration`.
 */
object IosAppScope {
    const val DEFAULT_KEYCHAIN_ACCESS_GROUP = "19410625785.dev.j3pf.rp.iosApp"

    @Volatile
    var activeController: SharedAppController? = null
        private set

    fun onAppBackgrounded() {
        activeController?.onAppBackgrounded()
    }

    fun onAppForegrounded() {
        activeController?.onAppForegrounded()
    }

    fun createController(
        scope: CoroutineScope,
        accessGroup: String? = DEFAULT_KEYCHAIN_ACCESS_GROUP,
    ): SharedAppController {
        val secrets = SecretStoreIos(accessGroup = accessGroup)
        val settingsStore =
            KeyValueSettingsStore(
                backing = NsUserDefaultsSettingsBacking(),
                onMutedAgentsChanged = { muted ->
                    IosKeychainStoreBridge.putMutedAgents(muted.joinToString(","), accessGroup)
                },
                onCriticalAlwaysChanged = { criticalAlways ->
                    IosKeychainStoreBridge.putCriticalAlways(criticalAlways, accessGroup)
                },
            )
        val httpTransport = UrlSessionRelayTransport()
        val socketDriver = UrlSessionSocketDriver()
        var controllerRef: SharedAppController? = null

        val socketPolicy =
            ReconnectingSocketPolicy(
                scope = scope,
                driver = socketDriver,
                requestProvider = {
                    val s = secrets.current() ?: return@ReconnectingSocketPolicy null
                    buildWsRequest(s.relayUrl, s.deviceToken)
                },
                onSocketOpen = { controllerRef?.sendAppHello() },
                onSocketText = { text -> controllerRef?.wsClient?.onText(text) },
                onSocketClosed = { controllerRef?.wsClient?.failPending(WsReply.NotConnected) },
            )

        val controller =
            SharedAppController(
                scope = scope,
                secrets = secrets,
                settingsStore = settingsStore,
                httpTransport = httpTransport,
                biometricAuthorizer = IosBiometricAuthorizer,
                pushPlatform = SessionPushPlatform.IOS,
                deviceNameProvider = {
                    val name = UIDevice.currentDevice.name
                    name.ifBlank { "iPhone" }
                },
                apnsEnvironmentProvider = { IosPushRegistrationBridge.apnsEnvironment },
                currentApnsTokenProvider = { IosPushRegistrationBridge.lastApnsTokenHex },
                startSocketConnection = { socketPolicy.start() },
                stopSocketConnection = { socketPolicy.stop() },
                retrySocketConnection = { socketPolicy.retryNow() },
                isSocketConnected = { socketPolicy.isConnected },
                sendSocketText = { socketPolicy.send(it) },
                connState = socketPolicy.state,
            )
        controllerRef = controller
        activeController = controller
        val applePushRegistration = ApplePushRegistration { controller.sessionPolicy }
        IosPushRegistrationBridge.registration = applePushRegistration
        val cachedToken = IosPushRegistrationBridge.lastApnsTokenHex
        if (cachedToken != null && secrets.current() != null) {
            scope.launch {
                applePushRegistration.register(cachedToken, IosPushRegistrationBridge.apnsEnvironment)
            }
        }
        return controller
    }

    private fun buildWsRequest(
        relayUrl: String,
        deviceToken: String,
    ): NSURLRequest? {
        val trimmed = relayUrl.trimEnd('/')
        val wsUrlStr =
            when {
                trimmed.startsWith("https://", ignoreCase = true) ->
                    "wss://" + trimmed.substring("https://".length) + "/ws/app?mode=foreground"
                trimmed.startsWith("http://", ignoreCase = true) ->
                    "ws://" + trimmed.substring("http://".length) + "/ws/app?mode=foreground"
                else -> "wss://$trimmed/ws/app?mode=foreground"
            }
        val nsUrl = NSURL.URLWithString(wsUrlStr) ?: return null
        val req = NSMutableURLRequest(nsUrl, NSURLRequestUseProtocolCachePolicy, 15.0)
        req.setValue("Bearer $deviceToken", "Authorization")
        return req
    }
}

actual fun createPlatformAppController(scope: CoroutineScope): SharedAppController = IosAppScope.createController(scope)
