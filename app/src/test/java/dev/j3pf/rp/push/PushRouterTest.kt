package dev.j3pf.rp.push

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.db.RpDatabase
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.protocol.PushKind
import dev.j3pf.rp.protocol.PushPromptBody
import dev.j3pf.rp.protocol.RpJson
import dev.j3pf.rp.testSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Caracterización del filtro único de pushes [PushRouter] (canal directo y FCM se deduplican aquí).
 * Código de producción SIN cambios (Gate A, S5/S15): los tests describen lo que hace hoy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PushRouterTest {
    private lateinit var ctx: Application
    private lateinit var nm: NotificationManager
    private lateinit var db: RpDatabase
    private lateinit var secrets: FakeSecretStore
    private lateinit var settings: SettingsRepository
    private lateinit var factory: NotificationFactory
    private lateinit var router: PushRouter

    private val agentId = "m:s"

    @Before
    fun setUp() {
        runBlocking {
            ctx = ApplicationProvider.getApplicationContext()
            // DataStore global: deja un estado limpio para cada test (los tests anteriores
            // pueden haber dejado silencio/criticalAlways activados).
            val cleanSettings = SettingsRepository(ctx)
            cleanSettings.setAgentMuted(agentId, muted = false)
            cleanSettings.setCriticalAlways(false)
            shadowOf(ctx).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            nm = ctx.getSystemService(NotificationManager::class.java)
            val server = okhttp3.mockwebserver.MockWebServer().also { it.start() }
            db = inMemoryDb(ctx)
            secrets = FakeSecretStore(ctx, testSession(server.url("/").toString()))
            settings = SettingsRepository(ctx)
            factory =
                NotificationFactory(ctx, NotificationChannels(ctx), settings, NotificationDeduper(db.dedup()))
            router = PushRouter(factory, NotificationDeduper(db.dedup()), secrets, settings, db.prompts())
        }
    }

    private fun promptBody(danger: String = Danger.LOW) =
        PushPromptBody(
            promptId = "01PROMPT",
            agentId = agentId,
            agentLabel = "api",
            machineId = "M1",
            machineName = "PC",
            kind = PromptKind.CONFIRM,
            category = PromptCategory.PERMISSION,
            title = "¿Permitir?",
            danger = danger,
        )

    private fun promptJson(danger: String = Danger.LOW): JsonObject =
        RpJson.encodeToJsonElement(PushPromptBody.serializer(), promptBody(danger)) as JsonObject

    private fun posted() = shadowOf(nm).getNotification(NotificationFactory.PROMPT_TAG, NotificationFactory.notificationId("01PROMPT"))

    @Test
    fun `agente silenciado no publica salvo autorizacion critica`() {
        runBlocking {
            settings.setAgentMuted(agentId, muted = true)
            settings.setCriticalAlways(false)
            // PushRouter devuelve true (push consumido, silenciado a proposito) cuando suprime por silencio.
            // Caracterización: la clave de dedupe se registra ANTES del filtro de silencio, así que un
            // replay del mismo promptId sigue suprimido aunque el usuario deje de silenciar (ventana 24 h).
            assertTrue(router.handleDirect(PushKind.PROMPT, promptJson(danger = Danger.LOW), pushId = null))
            assertEquals(null, posted(), "agente silenciado sin autorizacion critica no publica")

            // Un prompt nuevo (otro promptId) con danger alto y criticalAlways=true salta el silencio.
            settings.setCriticalAlways(true)
            val critico = RpJson.encodeToJsonElement(
                PushPromptBody.serializer(),
                promptBody(danger = Danger.HIGH).copy(promptId = "01CRITICO"),
            ) as JsonObject
            assertTrue(router.handleDirect(PushKind.PROMPT, critico, pushId = null))
            assertTrue(
                shadowOf(nm).getNotification(
                    NotificationFactory.PROMPT_TAG,
                    NotificationFactory.notificationId("01CRITICO"),
                ) != null,
                "la autorización crítica debe publicarse",
            )
        }
    }

    @Test
    fun `replay tras dejar de silenciar sigue suprimido por la clave de dedupe`() {
        runBlocking {
            settings.setAgentMuted(agentId, muted = true)
            assertTrue(router.handleDirect(PushKind.PROMPT, promptJson(), pushId = null))
            assertEquals(null, posted(), "silenciado no publica")

            // El usuario deja de silenciar el agente y el relay reenvía el MISMO prompt: sigue
            // suprimido porque la clave "prompt:<id>" se registró antes del filtro de silencio.
            settings.setAgentMuted(agentId, muted = false)
            assertTrue(router.handleDirect(PushKind.PROMPT, promptJson(), pushId = null))
            assertEquals(null, posted(), "replay del mismo prompt tras desmutear no puede publicar")
            assertTrue(db.dedup().seenAt(NotificationDeduper.promptKey(promptBody().promptId)) != null, "la fila de dedupe existe")

            // Un prompt NUEVO (otro promptId) del agente ya desmuteado sí se publica.
            val nuevo = RpJson.encodeToJsonElement(
                PushPromptBody.serializer(),
                promptBody().copy(promptId = "01NUEVO"),
            ) as JsonObject
            assertTrue(router.handleDirect(PushKind.PROMPT, nuevo, pushId = null))
            assertTrue(
                shadowOf(nm).getNotification(
                    NotificationFactory.PROMPT_TAG,
                    NotificationFactory.notificationId("01NUEVO"),
                ) != null,
                "prompt nuevo del agente desmuteado se publica",
            )
            settings.setAgentMuted(agentId, muted = false)
        }
    }

    @Test
    fun `el bypass critico exige danger alto y criticalAlways activo`() {
        runBlocking {
            settings.setAgentMuted(agentId, muted = true)

            // danger alto SIN criticalAlways: el silencio manda (el push no publica).
            settings.setCriticalAlways(false)
            val alto = RpJson.encodeToJsonElement(PushPromptBody.serializer(), promptBody(Danger.HIGH).copy(promptId = "01ALTO")) as JsonObject
            assertTrue(router.handleDirect(PushKind.PROMPT, alto, pushId = null), "alto sin criticalAlways se consume en silencio")
            assertEquals(null, shadowOf(nm).getNotification(NotificationFactory.PROMPT_TAG, NotificationFactory.notificationId("01ALTO")))

            // criticalAlways con danger bajo: también silenciado.
            settings.setCriticalAlways(true)
            val bajo = RpJson.encodeToJsonElement(PushPromptBody.serializer(), promptBody(Danger.LOW).copy(promptId = "01BAJO")) as JsonObject
            assertTrue(router.handleDirect(PushKind.PROMPT, bajo, pushId = null), "bajo con criticalAlways se consume en silencio")
            assertEquals(null, shadowOf(nm).getNotification(NotificationFactory.PROMPT_TAG, NotificationFactory.notificationId("01BAJO")))

            // Limpia el estado global de DataStore para los tests siguientes.
            settings.setAgentMuted(agentId, muted = false)
            settings.setCriticalAlways(false)
        }
    }

    @Test
    fun `un push repetido del mismo promptId publica una sola vez`() {
        runBlocking {
            assertTrue(router.handleDirect(PushKind.PROMPT, promptJson(), pushId = null))
            assertTrue(router.handleDirect(PushKind.PROMPT, promptJson(), pushId = null))
            assertTrue(posted() != null)
        }
    }

    @Test
    fun `cancel retira la notificacion del prompt`() {
        runBlocking {
            assertTrue(router.handleDirect(PushKind.PROMPT, promptJson(), pushId = null))
            assertTrue(router.handleDirect(PushKind.CANCEL, RpJson.encodeToJsonElement(dev.j3pf.rp.protocol.PushCancelBody.serializer(), dev.j3pf.rp.protocol.PushCancelBody("01PROMPT")) as JsonObject, pushId = null))
            assertEquals(null, posted(), "tras cancel la notificación no puede existir")
        }
    }

    @Test
    fun `settled marca dedupe por pushId y no reavisa al repetirse`() {
        runBlocking {
            val settled =
                RpJson.encodeToJsonElement(
                    dev.j3pf.rp.protocol.PushSettledBody.serializer(),
                    dev.j3pf.rp.protocol.PushSettledBody(agentId = agentId, agentLabel = "api", preview = "Listo", machineId = "M1", machineName = "PC"),
                ) as JsonObject
            assertTrue(router.handleDirect(PushKind.SETTLED, settled, pushId = "push-1"))
            assertTrue(router.handleDirect(PushKind.SETTLED, settled, pushId = "push-1"), "repetido se consume sin volver a publicar")
            // La clave de dedupe por pushId quedó registrada; otro pushId tiene su propia clave.
            assertTrue(db.dedup().seenAt("settled:push-1") != null, "la clave settled:<pushId> se marca en el deduper")
            assertTrue(router.handleDirect(PushKind.SETTLED, settled, pushId = "push-2"))
            assertTrue(db.dedup().seenAt("settled:push-2") != null)
        }
    }

    @Test
    fun `error repetido con la misma clave no se publica dos veces`() {
        runBlocking {
            val err = JsonObject(mapOf("agentId" to JsonPrimitive(agentId), "title" to JsonPrimitive("Falló el spawn")))
            assertTrue(router.handleDirect(PushKind.ERROR, err, pushId = null))
            assertTrue(router.handleDirect(PushKind.ERROR, err, pushId = null), "repetido se consume sin volver a publicar")
            assertTrue(db.dedup().seenAt("error:m:s:" + "Falló el spawn".hashCode()) != null, "la clave de error se marca en el deduper")
        }
    }

    @Test
    fun `fcm con payload indescifrable se ignora sin tocar la bandeja`() {
        val bad = mapOf("k" to PushKind.PROMPT, "n" to "no-es-nonce", "c" to "no-es-ciphertext")
        val r = kotlinx.coroutines.runBlocking { router.handleFcm(bad) }
        assertFalse(r, "payload malformado -> false")
    }
}
