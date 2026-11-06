package dev.j3pf.rp.data.ports

import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.protocol.AttachmentRef
import dev.j3pf.rp.protocol.NotifyPrefs
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
}
