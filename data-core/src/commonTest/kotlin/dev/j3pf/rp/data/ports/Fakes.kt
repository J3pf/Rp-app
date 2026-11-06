package dev.j3pf.rp.data.ports

import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.protocol.AttachmentRef
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
