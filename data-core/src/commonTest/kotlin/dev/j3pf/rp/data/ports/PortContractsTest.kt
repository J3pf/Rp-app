package dev.j3pf.rp.data.ports

import dev.j3pf.rp.data.net.AnswerOutcome
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.AttachmentRef
import dev.j3pf.rp.protocol.HerdrLayout
import dev.j3pf.rp.protocol.HostDirs
import dev.j3pf.rp.protocol.HostGitDiffResult
import dev.j3pf.rp.protocol.HostGitDiscardResult
import dev.j3pf.rp.protocol.HostSpawn
import dev.j3pf.rp.protocol.HostSpawned
import dev.j3pf.rp.protocol.NotifyPrefs
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Sanity pins for the fake contracts so :app/iosMain code can rely on them in later slices. */
class PortContractsTest {
    private val session =
        Session(
            relayId = "rp0",
            relayUrl = "https://relay.example",
            relayName = "PC",
            deviceId = "d1",
            deviceToken = "t1",
            pushKey = "k1",
        )

    @Test
    fun fakeSecretStoreSavesReadsClearsAndYieldsUniqueIds() {
        val store = FakeSecretStore()
        assertNull(store.current())
        store.save(session)
        assertEquals(session, store.current())
        store.clear()
        assertNull(store.current())
        val a = store.newRelayId()
        val b = store.newRelayId()
        assertEquals(16, a.length)
        assertNotEquals(a, b)
    }

    @Test
    fun recordingCryptoHoldsTheFieldAssociationDiscipline() {
        val crypto = RecordingSecureCrypto()
        val ct = crypto.encrypt("deviceToken", "secret-1")
        assertEquals("secret-1", crypto.decrypt("deviceToken", ct))
        // The same ciphertext under another field must not decrypt (SC-03).
        assertNull(crypto.decrypt("device_id", ct))
        assertNull(crypto.decrypt("deviceToken", "garbage"))
        assertNull(crypto.decrypt("deviceToken", "enc[otherField]:x"))
    }

    @Test
    fun fakeSettingsStoreRoundTripsAndWipes() = runTest {
        val store = FakeSettingsStore()
        store.setAppTheme("dark")
        store.setAgentMuted("a1", true)
        store.setMeshSelectedGroup("g1")
        assertEquals("dark", store.settings.value.appTheme)
        assertEquals(setOf("a1"), store.settings.value.mutedAgents)
        assertEquals("g1", store.settings.value.selectedMeshGroup)
        store.wipe()
        assertTrue(store.wiped.value)
        assertEquals(AppSettings(), store.settings.value)
        assertTrue(store.settings.value.notifyPrefs == NotifyPrefs())
    }

    @Test
    fun fakesRecordSchedulerHostAndVoiceCalls() = runTest {
        val sched = RecordingBackgroundScheduler()
        sched.enqueueOutboxFlush()
        sched.enqueueAnswerRetry()
        sched.cancelAllWork()
        assertEquals(1, sched.outboxFlushes.value)
        assertEquals(1, sched.answerRetries.value)
        assertEquals(1, sched.cancellations.value)

        val bio = FakeBiometricAuthorizer()
        assertEquals(BiometricAuthResult.OK, bio.authenticate("Desbloqueo cancelado"))
        assertEquals(1, bio.reasons.value.size)
        bio.result = BiometricAuthResult.UNAVAILABLE
        assertEquals(BiometricAuthResult.UNAVAILABLE, bio.authenticate("Configura un bloqueo de pantalla"))

        val voice = FakeVoiceInputHost()
        assertFalse(voice.listening.value)
        voice.start(object : VoiceListener {
            override fun onPartial(text: String) {}
            override fun onFinal(text: String) {}
            override fun onError(code: Int) {}
        })
        assertTrue(voice.listening.value)
        voice.stop()
        assertFalse(voice.listening.value)

        val host = FakeAttachmentsPlatformHost(camera = AttachmentRef("blob-1", "foto.jpg", "image/jpeg", 10))
        assertEquals(AttachmentRef("blob-1", "foto.jpg", "image/jpeg", 10), host.capturePhoto())
        assertTrue(host.isCameraAvailable)
        // No-camera environment (e.g. a device class without camera) must report it, not pretend.
        val headless = FakeAttachmentsPlatformHost(cameraAvailable = false)
        assertFalse(headless.isCameraAvailable)
    }

    @Test
fun devicePortsAndDedupFakesRecordTheirCalls() = runTest {
    val health = FakeNotificationHealth(enabled = true)
    assertTrue(health.isEnabled())
    assertEquals(NotificationPermissionOutcome.GRANTED, health.request())
    assertEquals(1, health.requested.value)

    val battery = FakeBatteryExemption(supported = false, ignoring = true)
    assertFalse(battery.isSupported)
    assertTrue(battery.isIgnoring())

    val info: PlatformInfo = FakePlatformInfo()
    assertEquals("0.6.1", info.versionName)
    assertEquals(999, info.versionCode)
    assertEquals("direct", info.flavor)

    val dedup = RecordingDedupStore().askFor("k1")
    assertEquals(true, dedup.firstSeen("k1", now = 1))
    assertEquals(false, dedup.firstSeen("k2", now = 2))
    assertEquals(2, dedup.firstSeenCalls.value.size)

    val guard = MapOpenPromptGuard(openIds = setOf("p-open"))
    assertTrue(guard.isOpen("p-open"))
    assertFalse(guard.isOpen("p-closed"))
}

@Test
fun hostGatewayFakeShapeContractRecordsCallsAndCannedFailures() = runTest {
    val gateway = FakeHostGateway()
    val dirs = HostDirs(path = "/home/user", dirs = listOf("src"))
    val layout = HerdrLayout()
    val spawnRequest = HostSpawn(machineId = "m-spawn", cwd = "/workspace")
    val spawned = HostSpawned(spawnId = "spawn-1")
    val diffFailure = HostResult.Failed("herdr no responde")
    val discardFailure = HostResult.Failed("git_locked")

    gateway.listDirsResult = HostResult.Ok(dirs)
    gateway.herdrLayoutResult = HostResult.Ok(layout)
    gateway.spawnResult = HostResult.Ok(spawned)
    gateway.gitDiffResult = diffFailure
    gateway.gitDiscardResult = discardFailure

    val dirsResult = gateway.listDirs("m-dirs", "/home/user")
    val layoutResult = gateway.herdrLayout("m-layout")
    val spawnResult = gateway.spawn(spawnRequest)
    val diffResult = gateway.gitDiff("m-diff", "/repo", "src/Main.kt")
    val discardResult = gateway.gitDiscard("m-discard", "/repo", "src/Main.kt")

    assertEquals(1, gateway.listDirsCalls.size)
    assertEquals("m-dirs" to "/home/user", gateway.listDirsCalls.single())
    assertEquals(HostResult.Ok(dirs), dirsResult)
    assertEquals(1, gateway.herdrLayoutCalls.size)
    assertEquals("m-layout", gateway.herdrLayoutCalls.single())
    assertEquals(HostResult.Ok(layout), layoutResult)
    assertEquals(1, gateway.spawnCalls.size)
    assertEquals(spawnRequest, gateway.spawnCalls.single())
    assertEquals(HostResult.Ok(spawned), spawnResult)
    assertEquals(1, gateway.gitDiffCalls.size)
    assertEquals(Triple("m-diff", "/repo", "src/Main.kt"), gateway.gitDiffCalls.single())
    assertEquals(diffFailure, diffResult)
    assertEquals(1, gateway.gitDiscardCalls.size)
    assertEquals(Triple("m-discard", "/repo", "src/Main.kt"), gateway.gitDiscardCalls.single())
    assertEquals(discardFailure, discardResult)

    // resumeAgent remains a pass-through here; Android's derivation is proved in its existing suite.
    val emptyAgent = AgentSummary(agentId = "", machineId = "m-empty")
    gateway.resumeAgent(emptyAgent)
    assertEquals(1, gateway.resumeAgentCalls.size)
    assertEquals(emptyAgent, gateway.resumeAgentCalls.single())
    assertEquals("", gateway.resumeAgentCalls.single().agentId)
}

@Test
fun lifecyclePortsRecordObserversAndFireBothCallbacks() = runTest {
    val net = RecordingNetworkMonitor()
    val seen = mutableListOf<Boolean>()
    net.register { seen += it }
    assertEquals(0, seen.size)
    net.goOffline()
    assertEquals(false, net.isOnline.value)
    net.goOnline()
    assertEquals(listOf(false, true), seen)
    assertEquals(true, net.isOnline.value)
    assertEquals(1, net.registerCalls.value)
    net.unregister()
    assertEquals(1, net.unregisterCalls.value)

    val life = RecordingAppLifecycleMonitor()
    var fg = 0
    var bg = 0
    life.observe(onForeground = { fg += 1 }, onBackground = { bg += 1 })
    life.fireBackground()
    life.fireForeground()
    assertEquals(1, bg)
    assertEquals(1, fg)
    assertEquals(true, life.isForeground.value)

    // Handover contract: the default member is a no-op for source compatibility, and the
    // monitor fires onNetworkIdentityChange() on VALIDATED identity changes.
    val counting = CountingNetworkObserver()
    counting.onNetworkIdentityChange()
    assertEquals(1, counting.identityChangeCalls.value)
    counting.onAvailability(true)
    assertEquals(1, counting.availabilityCalls.value)
    net.register(counting)
    net.fireNetworkIdentityChange()
    assertEquals(2, counting.identityChangeCalls.value)

    // Risk: item 2 pin (verifier advisory 1): the two-arg overload's DEFAULT body must
    // (i) propagate the single-arg member's result UNCHANGED — including UNAVAILABLE/FAILED,
    // not just OK — and (ii) pass the `reason` through to the single-arg member verbatim.
    val coal = FakeBiometricAuthorizer(result = BiometricAuthResult.OK)
    assertEquals(BiometricAuthResult.OK, coal.authenticate("t1", "r1"))
    // Verbatim and EXACTLY ONCE: the single-arg member saw exactly one call and its argument
    // was the reason exactly as passed (the title "t1" alone never satisfies this — the
    // assertion pins the full value, not a substring).
    assertEquals(1, coal.reasons.value.size)
    assertEquals("r1", coal.reasons.value.single())
    val curtain = FakeBiometricAuthorizer(result = BiometricAuthResult.UNAVAILABLE)
    assertEquals(BiometricAuthResult.UNAVAILABLE, curtain.authenticate("t-unavail", "r-unavail"))
    assertEquals(1, curtain.reasons.value.size)
    assertEquals("r-unavail", curtain.reasons.value.single())
    val fail = FakeBiometricAuthorizer(result = BiometricAuthResult.FAILED)
    assertEquals(BiometricAuthResult.FAILED, fail.authenticate("t-fail", "r-fail"))
    assertEquals(1, fail.reasons.value.size)
    assertEquals("r-fail", fail.reasons.value.single())
}

@Test
fun agentsGateFakeShapeContractRecordsCallsAndDerivedObserve() = runTest {
    val a1 = AgentSummary(agentId = "m1:a1", machineId = "m1", sessionName = "One")
    val a2 = AgentSummary(agentId = "m1:a2", machineId = "m1", sessionName = "Two")
    val gate = FakeAgentsGate(listOf(a1, a2))

    assertEquals(a1, gate.observe("m1:a1").first())
    assertNull(gate.observe("m1:missing").first())

    gate.start()
    assertEquals(1, gate.startCalls)

    val closeFailure: CloseOutcome = CloseOutcome.Failed("No se pudo cerrar el agente")
    gate.closeResult = closeFailure
    assertEquals(closeFailure, gate.close("m1:a1"))
    assertEquals(listOf("m1:a1"), gate.closeCalls)

    val archivedFailure: ArchivedAgentsResult = ArchivedAgentsResult.Failed("No se pudieron cargar los agentes archivados")
    gate.archivedResult = archivedFailure
    assertEquals(
        archivedFailure,
        gate.archived("m1", 42L),
    )
    assertEquals<List<Pair<String?, Long?>>>(listOf("m1" to 42L), gate.archivedCalls)

    gate.purgeResult = false
    assertFalse(gate.purge("m1:a1"))
    assertEquals(listOf("m1:a1"), gate.purgeCalls)

    gate.clear()
    assertEquals(1, gate.clearCalls)
    assertTrue(gate.agents.first().isEmpty())
}

@Test
fun promptSheetGateFakeShapeContractRecordsAnswersAndOpenFlow() = runTest {
    val p1 = Prompt(promptId = "p-1", agentId = "m1:a1", kind = PromptKind.CONFIRM)
    val p2 = Prompt(promptId = "p-2", agentId = "m1:a2", kind = PromptKind.CONFIRM)
    val gate = FakePromptSheetGate(listOf(p1, p2))

    assertEquals(p1, gate.observe("p-1").first())
    assertEquals(listOf(p2), gate.openFor("m1:a2").first())

    gate.start()
    gate.refresh()
    assertEquals(1, gate.startCalls)
    assertEquals(1, gate.refreshCalls)

    val alreadyResolved: AnswerOutcome = AnswerOutcome.AlreadyResolved
    gate.answerResult = alreadyResolved
    assertEquals(alreadyResolved, gate.answer("p-1", Answer.Cancelled))
    assertEquals<List<Pair<String, Answer>>>(listOf("p-1" to Answer.Cancelled), gate.answerCalls)

    gate.clear()
    assertEquals(1, gate.clearCalls)
    assertTrue(gate.open.first().isEmpty())
}
}
