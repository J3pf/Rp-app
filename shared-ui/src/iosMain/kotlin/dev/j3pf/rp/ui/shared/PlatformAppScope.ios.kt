package dev.j3pf.rp.ui.shared

import dev.j3pf.rp.client.http.UrlSessionRelayTransport
import dev.j3pf.rp.client.session.ApplePushRegistration
import dev.j3pf.rp.client.session.SessionPushPlatform
import dev.j3pf.rp.client.ws.ReconnectingSocketPolicy
import dev.j3pf.rp.client.ws.RelayLock
import dev.j3pf.rp.client.ws.UrlSessionSocketDriver
import dev.j3pf.rp.data.crypto.Aes256Gcm
import dev.j3pf.rp.data.net.WsReply
import dev.j3pf.rp.data.ports.BiometricAuthResult
import dev.j3pf.rp.data.ports.BiometricAuthorizer
import dev.j3pf.rp.data.ports.NetworkMonitor
import dev.j3pf.rp.data.ports.NetworkObserver
import dev.j3pf.rp.data.ports.SecureCrypto
import dev.j3pf.rp.data.secure.EncryptedKeyValueSecretStore
import dev.j3pf.rp.data.secure.IosKeychainBridge
import dev.j3pf.rp.data.secure.IosNativeAeadBridge
import dev.j3pf.rp.data.secure.SecretKeyValueBacking
import dev.j3pf.rp.data.secure.SecretStoreIos
import dev.j3pf.rp.data.settings.SettingsKeys
import dev.j3pf.rp.ui.push.IosPushRegistrationBridge
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSBundle
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
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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

    @Volatile
    internal var nseAccessGroup: String? = null

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
            val targetGroup = group ?: nseAccessGroup ?: IosAppScope.resolveSharedKeychainAccessGroup()
            if (pushKey != null) {
                putHook?.invoke(EncryptedKeyValueSecretStore.K_NSE_PUSH_KEY, pushKey, targetGroup)
            } else {
                removeHook?.invoke(EncryptedKeyValueSecretStore.K_NSE_PUSH_KEY, targetGroup)
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

/**
 * Swift-facing bridge in `SharedUi` so `iOSApp.swift` can register Apple `CryptoKit` (`AES.GCM`)
 * seal/open callbacks into [IosNativeAeadBridge] without needing to export `:data-core` symbols
 * directly in the Objective-C framework header.
 *
 * Callbacks exchange the 32-byte master key and combined ciphertext
 * (`nonce (12 bytes) || ciphertext || tag (16 bytes)`) as standard Base64 strings, with [aad]
 * passed as the UTF-8 field name string.
 */
@OptIn(ExperimentalEncodingApi::class)
object IosAeadBridge {
    @Volatile
    private var sealHook: ((keyBase64: String, plaintext: String, aad: String) -> String?)? = null

    @Volatile
    private var openHook: ((keyBase64: String, combinedBase64: String, aad: String) -> String?)? = null

    fun configure(
        onSeal: (keyBase64: String, plaintext: String, aad: String) -> String?,
        onOpen: (keyBase64: String, combinedBase64: String, aad: String) -> String?,
    ) {
        sealHook = onSeal
        openHook = onOpen
        IosNativeAeadBridge.cryptoFactory = { masterKey ->
            SwiftHookSecureCrypto(masterKey)
        }
    }

    private class SwiftHookSecureCrypto(
        masterKey: ByteArray,
    ) : SecureCrypto {
        private val keyBase64: String

        init {
            require(masterKey.size == Aes256Gcm.KEY_BYTES) { "Master key must be 32 bytes" }
            keyBase64 = Base64.encode(masterKey)
        }

        override fun encrypt(
            field: String,
            value: String,
        ): String =
            sealHook?.invoke(keyBase64, value, field)
                ?: throw IllegalStateException("iOS CryptoKit AEAD seal failed for field $field")

        override fun decrypt(
            field: String,
            value: String,
        ): String? = openHook?.invoke(keyBase64, value, field)
    }
}

/**
 * iOS [NetworkMonitor] driven by Apple's `Network.NWPathMonitor` via [IosNetworkMonitorBridge].
 *
 * Tracks reachability (`satisfied`) and active interface signature (`wifi`, `cellular`,
 * `wiredEthernet`, etc.) so connectivity loss triggers `networkLost()` and offline->online
 * recovery or WiFi <-> Cellular handoffs trigger an immediate forced reconnect.
 */
class IosNetworkMonitor : NetworkMonitor {
    private val lock = RelayLock()
    private val online = MutableStateFlow(false)
    private var observer: NetworkObserver? = null
    private var hasSeenPath = false
    private var lastSatisfied = false
    private var lastInterfaceSignature: String? = null

    override val isOnline: StateFlow<Boolean> = online.asStateFlow()

    override fun register(observer: NetworkObserver) {
        lock.withLock {
            this.observer = observer
        }
    }

    override fun unregister() {
        lock.withLock {
            this.observer = null
        }
    }

    fun updatePath(
        satisfied: Boolean,
        interfaceSignature: String,
    ) {
        val (action, currentObserver) =
            lock.withLock {
                val previousHasSeen = hasSeenPath
                val previousSatisfied = lastSatisfied
                val previousSignature = lastInterfaceSignature
                hasSeenPath = true
                lastSatisfied = satisfied
                online.value = satisfied
                val nextAction =
                    if (!satisfied) {
                        if (!previousHasSeen || previousSatisfied) {
                            PathAction.LOST
                        } else {
                            PathAction.NONE
                        }
                    } else {
                        lastInterfaceSignature = interfaceSignature
                        val signatureChanged = previousSignature != null && previousSignature != interfaceSignature
                        val recoveredFromOffline = previousHasSeen && !previousSatisfied
                        if (signatureChanged || recoveredFromOffline) {
                            PathAction.IDENTITY_CHANGED
                        } else {
                            PathAction.AVAILABLE
                        }
                    }
                nextAction to observer
            }
        when (action) {
            PathAction.LOST -> currentObserver?.onAvailability(false)
            PathAction.AVAILABLE -> currentObserver?.onAvailability(true)
            PathAction.IDENTITY_CHANGED -> currentObserver?.onNetworkIdentityChange()
            PathAction.NONE -> Unit
        }
    }

    private enum class PathAction {
        NONE,
        LOST,
        AVAILABLE,
        IDENTITY_CHANGED,
    }
}

/**
 * Swift-facing reachability bridge in `SharedUi` receiving updates from `Network.NWPathMonitor`
 * in `iOSApp.swift` and forwarding them to [monitor].
 */
object IosNetworkMonitorBridge {
    val monitor: IosNetworkMonitor = IosNetworkMonitor()

    fun updatePath(
        satisfied: Boolean,
        interfaceSignature: String,
    ) {
        monitor.updatePath(satisfied, interfaceSignature)
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
            cont.invokeOnCancellation {
                context.invalidate()
            }
            val canEval = context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)
            if (!canEval) {
                if (cont.isActive) {
                    cont.resume(BiometricAuthResult.UNAVAILABLE)
                }
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
    fun resolveSharedKeychainAccessGroup(): String? {
        val raw = NSBundle.mainBundle.objectForInfoDictionaryKey("RPKeychainAccessGroup") as? String
        return raw?.trim()?.takeIf { it.isNotEmpty() && !it.contains("$(") }
    }

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
        accessGroup: String? = resolveSharedKeychainAccessGroup(),
    ): SharedAppController {
        val sharedNseAccessGroup = accessGroup ?: resolveSharedKeychainAccessGroup()
        IosKeychainStoreBridge.nseAccessGroup = sharedNseAccessGroup
        // Main-app secrets stay in the app-private default Keychain group (accessGroup = null)
        // so NotificationService cannot read rp_master_key or deviceToken; only rp_nse_push_key,
        // rp_nse_muted_agents, and rp_nse_critical_always are mirrored to sharedNseAccessGroup.
        val secrets = SecretStoreIos(accessGroup = null)
        val settingsStore =
            KeyValueSettingsStore(
                backing = NsUserDefaultsSettingsBacking(),
                onMutedAgentsChanged = { muted ->
                    IosKeychainStoreBridge.putMutedAgents(muted.joinToString(","), sharedNseAccessGroup)
                },
                onCriticalAlwaysChanged = { criticalAlways ->
                    IosKeychainStoreBridge.putCriticalAlways(criticalAlways, sharedNseAccessGroup)
                },
            )
        val httpTransport = UrlSessionRelayTransport()
        val socketDriver = UrlSessionSocketDriver()
        val socketLock = RelayLock()
        val networkMonitor = IosNetworkMonitorBridge.monitor
        var controllerRef: SharedAppController? = null
        var socketPolicyRef: ReconnectingSocketPolicy<*, *>? = null
        val networkObserver =
            object : NetworkObserver {
                override fun onAvailability(available: Boolean) {
                    socketLock.withLock {
                        if (available) {
                            socketPolicyRef?.retryNow(force = false)
                        } else {
                            socketPolicyRef?.networkLost()
                        }
                    }
                }

                override fun onNetworkIdentityChange() {
                    socketLock.withLock {
                        socketPolicyRef?.retryNow(force = true)
                    }
                }
            }

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
                onStart = { networkMonitor.register(networkObserver) },
                onStop = networkMonitor::unregister,
                lock = socketLock,
            )
        socketPolicyRef = socketPolicy

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
                forceRetrySocketConnection = { socketPolicy.retryNow(force = true) },
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
