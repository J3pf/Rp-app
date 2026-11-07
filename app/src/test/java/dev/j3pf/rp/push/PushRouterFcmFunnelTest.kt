package dev.j3pf.rp.push

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.platform.AndroidPushEnvelopeCrypto
import dev.j3pf.rp.data.db.RpDatabase
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.protocol.PushCrypto
import dev.j3pf.rp.protocol.PushKind
import dev.j3pf.rp.protocol.PushPromptBody
import dev.j3pf.rp.protocol.RpJson
import dev.j3pf.rp.testSession
import dev.j3pf.rp.util.stableId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Characterization of the real FCM/direct push funnel on unchanged Android code (T1; S5/S11/S15). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PushRouterFcmFunnelTest {
    private lateinit var context: Application
    private lateinit var notificationManager: NotificationManager
    private lateinit var db: RpDatabase
    private lateinit var secrets: FakeSecretStore
    private lateinit var deduper: NotificationDeduper
    private lateinit var router: PushRouter

    private val agentId = "machine:agent"
    private val promptId = "01PROMPT"

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancelAll()

        db = inMemoryDb(context)
        secrets = FakeSecretStore(context, testSession("https://relay.example"))
        deduper = NotificationDeduper(db.dedup())
        val settings = SettingsRepository(context)
        val channels = NotificationChannels(context)
        val factory = NotificationFactory(context, channels, settings, deduper)
        router = PushRouter(factory, deduper, AndroidPushEnvelopeCrypto(), secrets, settings, db.prompts())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun promptBody() =
        PushPromptBody(
            promptId = promptId,
            agentId = agentId,
            agentLabel = "api",
            machineId = "M1",
            machineName = "PC",
            kind = PromptKind.CONFIRM,
            category = PromptCategory.PERMISSION,
            title = "¿Permitir?",
            danger = Danger.LOW,
        )

    private fun promptJson(body: PushPromptBody = promptBody()) =
        RpJson.encodeToJsonElement(PushPromptBody.serializer(), body) as JsonObject

    private fun encryptedFcm(
        kind: String = PushKind.PROMPT,
        body: PushPromptBody = promptBody(),
        nonceSeed: Int,
    ): Map<String, String> {
        val nonce = ByteArray(12) { (nonceSeed + it).toByte() }
        val plain = RpJson.encodeToString(JsonObject.serializer(), promptJson(body))
        return mapOf(
            "k" to kind,
            "n" to Base64.getEncoder().encodeToString(nonce),
            "c" to PushCrypto.encrypt(secrets.current()!!.pushKey, nonce, plain),
        )
    }

    private fun postedPrompt(): Notification? =
        shadowOf(notificationManager).getNotification(
            NotificationFactory.PROMPT_TAG,
            NotificationFactory.notificationId(promptId),
        )

    @Test
    fun `encrypted FCM and direct deliveries of one prompt share the dedupe funnel`() = runBlocking {
        assertTrue(router.handleFcm(encryptedFcm(nonceSeed = 1)))
        val fcmNotification = postedPrompt()
        assertNotNull(fcmNotification, "valid encrypted FCM payload posts the prompt")
        val summary =
            shadowOf(notificationManager).getNotification(
                NotificationFactory.SUMMARY_TAG,
                stableId("summary:$agentId"),
            )
        assertNotNull(summary)
        assertEquals(2, shadowOf(notificationManager).allNotifications.size, "one prompt plus its agent summary is visible")

        assertTrue(router.handleDirect(PushKind.PROMPT, promptJson(), pushId = null))
        assertSame(fcmNotification, postedPrompt(), "the duplicate direct delivery does not repost or replace the visible prompt")
        assertSame(
            summary,
            shadowOf(notificationManager).getNotification(
                NotificationFactory.SUMMARY_TAG,
                stableId("summary:$agentId"),
            ),
            "the tray remains unchanged after the direct-channel replay",
        )
        assertEquals(2, shadowOf(notificationManager).allNotifications.size)
        assertNotNull(db.dedup().seenAt(NotificationDeduper.promptKey(promptId)))
        assertFalse(deduper.firstSeen(NotificationDeduper.promptKey(promptId)), "the shared prompt key is already claimed")

        assertTrue(router.handleFcm(encryptedFcm(nonceSeed = 17)), "a valid replay is consumed silently")
        assertSame(fcmNotification, postedPrompt(), "a fresh FCM nonce does not cause a second prompt post")
        assertEquals(2, shadowOf(notificationManager).allNotifications.size)
        assertFalse(deduper.firstSeen(NotificationDeduper.promptKey(promptId)))
    }

    @Test
    fun `unknown kind and garbage ciphertext are ignored without notifications`() = runBlocking {
        assertFalse(router.handleFcm(encryptedFcm(kind = "unsupported", nonceSeed = 33)), "unknown decrypted kind returns false")
        assertFalse(
            router.handleFcm(mapOf("k" to PushKind.PROMPT, "n" to "bm9uY2U=", "c" to "bm90LWFuLWFlcy1jaXBoZXJ0ZXh0")),
            "invalid ciphertext returns false",
        )
        assertEquals(emptyList(), shadowOf(notificationManager).allNotifications)
    }
}
