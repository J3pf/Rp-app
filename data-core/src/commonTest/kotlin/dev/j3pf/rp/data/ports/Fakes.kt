package dev.j3pf.rp.data.ports

import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.AttachmentRef
import dev.j3pf.rp.protocol.HerdrLayout
import dev.j3pf.rp.protocol.HostDirs
import dev.j3pf.rp.protocol.HostGitDiffResult
import dev.j3pf.rp.protocol.HostGitDiscardResult
import dev.j3pf.rp.protocol.HostInfo
import dev.j3pf.rp.protocol.HostSpawn
import dev.j3pf.rp.protocol.HostSpawned
import dev.j3pf.rp.protocol.NotifyPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Test fakes for the T3a port contracts (consumable by :app/:shared-ui/other-slice tests). */

class FakeSecretStore(
    initial: Session? = null,
) : SecretStore {
    private val flow = MutableStateFlow(initial)

    override val session: StateFlow<Session?> = flow

    override fun current(): Session? = flow.value

    override fun save(session: Session) {
        flow.value = session
    }

    override fun clear() {
        flow.value = null
    }

    private var counter = 0

    override fun newRelayId(): String {
        counter += 1
        return counter.toString(16).padStart(16, 'f')
    }
}

/** Reversible codec `enc[<field>]:<value>`: decrypting under a foreign field returns null (SC-03 AD discipline). */
class RecordingSecureCrypto : SecureCrypto {
    override fun encrypt(
        field: String,
        value: String,
    ): String = "enc[$field]:$value"

    override fun decrypt(
        field: String,
        value: String,
    ): String? {
        if (!value.startsWith("enc[")) return null
        val fieldEnd = value.indexOf(']')
        if (fieldEnd < 0) return null
        val markedField = value.substring(4, fieldEnd)
        if (markedField != field) return null
        return value.substring(fieldEnd + 2) // skip the closing ']' and the ':' separator
    }
}

class FakeSettingsStore(
    initial: AppSettings = AppSettings(),
) : SettingsStore {
    private val flow = MutableStateFlow(initial)

    override val settings: MutableStateFlow<AppSettings> = flow
    val wiped: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override suspend fun setSelectedContext(v: String) {
        flow.value = flow.value.copy(selectedContext = v)
    }

    override suspend fun setDirectPush(v: Boolean) {
        flow.value = flow.value.copy(directPush = v)
    }

    override suspend fun setRequireUnlock(v: Boolean) {
        flow.value = flow.value.copy(requireUnlockToApprove = v)
    }

    override suspend fun setCriticalAlways(v: Boolean) {
        flow.value = flow.value.copy(criticalAlways = v)
    }

    override suspend fun setAppLock(v: Boolean) {
        flow.value = flow.value.copy(appLock = v)
    }

    override suspend fun setFlagSecure(v: Boolean) {
        flow.value = flow.value.copy(flagSecure = v)
    }

    override suspend fun setOnboardingDone(v: Boolean) {
        flow.value = flow.value.copy(onboardingDone = v)
    }

    override suspend fun setBatteryAsked(v: Boolean) {
        flow.value = flow.value.copy(batteryAsked = v)
    }

    override suspend fun setNotifyPrefs(v: NotifyPrefs) {
        flow.value = flow.value.copy(notifyPrefs = v)
    }

    override suspend fun setAgentMuted(agentId: String, muted: Boolean) {
        val cur = flow.value.mutedAgents
        flow.value = flow.value.copy(mutedAgents = if (muted) cur + agentId else cur - agentId)
    }

    override suspend fun setAppTheme(v: String) {
        flow.value = flow.value.copy(appTheme = v)
    }

    override suspend fun setChatFontScale(v: Float) {
        flow.value = flow.value.copy(chatFontScale = v)
    }

    override suspend fun setChatMessageSpacing(v: String) {
        flow.value = flow.value.copy(chatMessageSpacing = v)
    }

    override suspend fun setMeshSelectedMachine(machineId: String?) {
        flow.value = flow.value.copy(selectedMeshMachine = machineId)
    }

    override suspend fun setMeshSelectedGroup(groupId: String?) {
        flow.value = flow.value.copy(selectedMeshGroup = groupId)
    }

    override suspend fun wipe() {
        flow.value = AppSettings()
        wiped.value = true
    }
}

class RecordingBackgroundScheduler : BackgroundScheduler {
    val outboxFlushes = MutableStateFlow(0)
    val answerRetries = MutableStateFlow(0)
    val cancellations = MutableStateFlow(0)

    override fun enqueueOutboxFlush() {
        outboxFlushes.value += 1
    }

    override fun enqueueAnswerRetry() {
        answerRetries.value += 1
    }

    override fun cancelAllWork() {
        cancellations.value += 1
    }
}

class FakeAttachmentsPlatformHost(
    var images: List<AttachmentRef> = emptyList(),
    var documents: List<AttachmentRef> = emptyList(),
    var camera: AttachmentRef? = null,
    var cameraAvailable: Boolean = true,
) : AttachmentsPlatformHost {
    override suspend fun pickImages(maxItems: Int): List<AttachmentRef> = images.take(maxItems)

    override suspend fun pickDocuments(mime: List<String>): List<AttachmentRef> = documents

    override suspend fun capturePhoto(): AttachmentRef? = camera

    override val isCameraAvailable: Boolean = cameraAvailable
}

class FakeBiometricAuthorizer(
    var result: BiometricAuthResult = BiometricAuthResult.OK,
) : BiometricAuthorizer {
    val reasons = MutableStateFlow<List<String>>(emptyList())

    override suspend fun authenticate(reason: String): BiometricAuthResult {
        reasons.value = reasons.value + reason
        return result
    }

    // INTENTIONALLY does NOT override the two-arg overload: the default body must delegate to
    // the single-arg member (that delegation is the Risk: item 2 contract pinned by
    // PortContractsTest biometricOverloadDefaultsDelegateAndReachesSingleArg).
}

class FakeVoiceInputHost : VoiceInputHost {
    private val state = MutableStateFlow(false)

    override val listening: StateFlow<Boolean> = state
    var listener: VoiceListener? = null

    override fun start(listener: VoiceListener) {
        this.listener = listener
        state.value = true
    }

    override fun stop() {
        listener = null
        state.value = false
    }
}

/** Recording fake for the HostGateway contract; it never transforms arguments or results. */
class FakeHostGateway(
    initialHosts: Map<String, HostInfo> = emptyMap(),
) : HostGateway {
    private val hostState = MutableStateFlow(initialHosts)
    override val hosts: StateFlow<Map<String, HostInfo>> = hostState
    var startCalls = 0
        private set

    val listDirsCalls = mutableListOf<Pair<String, String>>()
    val herdrLayoutCalls = mutableListOf<String>()
    val spawnCalls = mutableListOf<HostSpawn>()
    val gitDiffCalls = mutableListOf<Triple<String, String, String?>>()
    val gitDiscardCalls = mutableListOf<Triple<String, String, String>>()
    val resumeAgentCalls = mutableListOf<AgentSummary>()

    var listDirsResult: HostResult<HostDirs> = HostResult.Failed("not configured")
    var herdrLayoutResult: HostResult<HerdrLayout> = HostResult.Failed("not configured")
    var spawnResult: HostResult<HostSpawned> = HostResult.Failed("not configured")
    var gitDiffResult: HostResult<HostGitDiffResult> = HostResult.Failed("not configured")
    var gitDiscardResult: HostResult<HostGitDiscardResult> = HostResult.Failed("not configured")
    var resumeAgentResult: HostResult<HostSpawned> = HostResult.Failed("not configured")

    override fun start() {
        startCalls += 1
    }

    override fun clear() {
        hostState.value = emptyMap()
    }

    override suspend fun listDirs(machineId: String, path: String): HostResult<HostDirs> {
        listDirsCalls += machineId to path
        return listDirsResult
    }

    override suspend fun herdrLayout(machineId: String): HostResult<HerdrLayout> {
        herdrLayoutCalls += machineId
        return herdrLayoutResult
    }

    override suspend fun spawn(req: HostSpawn): HostResult<HostSpawned> {
        spawnCalls += req
        return spawnResult
    }

    override suspend fun gitDiff(
        machineId: String,
        cwd: String,
        path: String?,
    ): HostResult<HostGitDiffResult> {
        gitDiffCalls += Triple(machineId, cwd, path)
        return gitDiffResult
    }

    override suspend fun gitDiscard(
        machineId: String,
        cwd: String,
        path: String,
    ): HostResult<HostGitDiscardResult> {
        gitDiscardCalls += Triple(machineId, cwd, path)
        return gitDiscardResult
    }

    override suspend fun resumeAgent(agent: AgentSummary): HostResult<HostSpawned> {
        resumeAgentCalls += agent
        return resumeAgentResult
    }
}

/** Simple mutable fakes for the device ports (orchestrator decision (a)) and O2's dedup chain (e). */
class FakeNotificationHealth(
    var enabled: Boolean = true,
    var outcome: NotificationPermissionOutcome = NotificationPermissionOutcome.GRANTED,
) : NotificationHealth {
    val requested = MutableStateFlow(0)

    override fun isEnabled(): Boolean = enabled

    override suspend fun request(): NotificationPermissionOutcome {
        requested.value += 1
        return outcome
    }
}

class FakeBatteryExemption(
    var supported: Boolean = true,
    var ignoring: Boolean = true,
) : BatteryExemption {
    override val isSupported: Boolean = supported

    override suspend fun isIgnoring(): Boolean = ignoring
}

class FakePlatformInfo(
    override val versionName: String = "0.6.1",
    override val versionCode: Long = 999,
    override val flavor: String = "direct",
) : PlatformInfo

class RecordingDedupStore : DedupStore {
    val firstSeenCalls = MutableStateFlow<List<Pair<String, Long>>>(emptyList())
    private val asked = MutableStateFlow<Set<String>>(emptySet())

    fun askFor(vararg keys: String): RecordingDedupStore {
        asked.value = asked.value + keys
        return this
    }

    override suspend fun firstSeen(
        key: String,
        now: Long,
    ): Boolean {
        firstSeenCalls.value = firstSeenCalls.value + (key to now)
        return key in asked.value
    }
}

class MapOpenPromptGuard(
    private var openIds: Set<String> = emptySet(),
) : OpenPromptGuard {
    override suspend fun isOpen(promptId: String): Boolean = promptId in openIds
}

class RecordingNetworkMonitor : NetworkMonitor {
    private val online = MutableStateFlow(true)
    private val observers = MutableStateFlow<List<NetworkObserver>>(emptyList())
    val registerCalls = MutableStateFlow(0)
    val unregisterCalls = MutableStateFlow(0)

    override fun register(observer: NetworkObserver) {
        observers.value = observers.value + observer
        registerCalls.value += 1
    }

    override fun unregister() {
        unregisterCalls.value += 1
    }

    override val isOnline: StateFlow<Boolean> = online

    fun goOnline() {
        online.value = true
        observers.value.forEach { it.onAvailability(true) }
    }

    fun goOffline() {
        online.value = false
        observers.value.forEach { it.onAvailability(false) }
    }

    fun fireNetworkIdentityChange() {
        observers.value.forEach { it.onNetworkIdentityChange() }
    }
}

class CountingNetworkObserver : NetworkObserver {
    val availabilityCalls = MutableStateFlow(0)
    val identityChangeCalls = MutableStateFlow(0)

    override fun onAvailability(available: Boolean) {
        availabilityCalls.value += 1
    }

    override fun onNetworkIdentityChange() {
        identityChangeCalls.value += 1
    }
}

class RecordingAppLifecycleMonitor : AppLifecycleMonitor {
    private val foreground = MutableStateFlow(true)
    val foregroundCallbacks = MutableStateFlow(0)
    val backgroundCallbacks = MutableStateFlow(0)

    private var onForeground: (() -> Unit)? = null
    private var onBackground: (() -> Unit)? = null

    override val isForeground: StateFlow<Boolean> = foreground

    override fun observe(
        onForeground: () -> Unit,
        onBackground: () -> Unit,
    ) {
        this.onForeground = onForeground
        this.onBackground = onBackground
    }

    fun fireForeground() {
        foreground.value = true
        onForeground?.invoke()
        foregroundCallbacks.value += 1
    }

    fun fireBackground() {
        foreground.value = false
        onBackground?.invoke()
        backgroundCallbacks.value += 1
    }
}

class FakeAgentsGate(
    initialAgents: List<AgentSummary> = emptyList(),
) : AgentsGate {
    val agentsFlow = MutableStateFlow(initialAgents)
    override val agents: StateFlow<List<AgentSummary>> = agentsFlow

    var startCalls = 0
        private set
    var clearCalls = 0
        private set

    val closeCalls = mutableListOf<String>()
    val archivedCalls = mutableListOf<Pair<String?, Long?>>()
    val purgeCalls = mutableListOf<String>()

    var closeResult: CloseOutcome = CloseOutcome.Closed
    var archivedResult: ArchivedAgentsResult = ArchivedAgentsResult.Ok(emptyList(), null)
    var purgeResult: Boolean = true

    override fun start() {
        startCalls += 1
    }

    override suspend fun clear() {
        clearCalls += 1
        agentsFlow.value = emptyList()
    }

    override suspend fun close(agentId: String): CloseOutcome {
        closeCalls += agentId
        return closeResult
    }

    override suspend fun archived(
        machineId: String?,
        before: Long?,
    ): ArchivedAgentsResult {
        archivedCalls += machineId to before
        return archivedResult
    }

    override suspend fun purge(agentId: String): Boolean {
        purgeCalls += agentId
        return purgeResult
    }
}
