package dev.j3pf.rp.push

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.db.RpDatabase
import dev.j3pf.rp.data.net.MessageBus
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.PromptsRepository
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.domain.model.toEntity
import dev.j3pf.rp.inMemoryDb
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.MsgType
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.protocol.PromptOption
import dev.j3pf.rp.protocol.PromptResolved
import dev.j3pf.rp.protocol.PushKind
import dev.j3pf.rp.protocol.PushPromptBody
import dev.j3pf.rp.protocol.PushSettledBody
import dev.j3pf.rp.protocol.RpJson
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import dev.j3pf.rp.util.stableId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Ciclo de vida completo de la notificación de un prompt: al responder desde la bandeja no puede quedar
 * ningún residuo (child, resumen de grupo, "Enviando…" ongoing), un resync no puede resucitarlo y un
 * prompt legítimamente reabierto sí debe mostrarse.
 *
 * Las esperas son de tiempo real (no virtual) porque Room publica las invalidaciones en su propio executor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationZombieTest {
    private lateinit var ctx: Application
    private lateinit var nm: NotificationManager
    private lateinit var server: MockWebServer
    private lateinit var db: RpDatabase
    private lateinit var secrets: FakeSecretStore
    private lateinit var deduper: NotificationDeduper
    private lateinit var factory: NotificationFactory
    private lateinit var handler: PromptActionHandler
    private lateinit var router: PushRouter
    private lateinit var repoScope: CoroutineScope

    private val agentId = "m:s"
    private val notifId = NotificationFactory.notificationId("01PROMPT")
    private val summaryId = stableId("summary:m:s")
    private val group = "rp.agent.m:s"

    private fun promptBody() =
        PushPromptBody(
            promptId = "01PROMPT",
            agentId = agentId,
            agentLabel = "api",
            machineId = "M1",
            machineName = "PC",
            kind = PromptKind.CONFIRM,
            category = PromptCategory.PERMISSION,
            title = "¿Permitir?",
            options = listOf(PromptOption("0", "Permitir"), PromptOption("2", "Denegar")),
            danger = Danger.HIGH,
        )

    private fun promptModel() =
        Prompt(
            promptId = "01PROMPT",
            agentId = agentId,
            kind = PromptKind.SELECT,
            title = "¿Permitir?",
            options = listOf(PromptOption("0", "Permitir"), PromptOption("2", "Denegar")),
        )

    private fun pushJson() = RpJson.encodeToJsonElement(PushPromptBody.serializer(), promptBody()) as JsonObject

    @Before
    fun setUp() {
        runBlocking {
            ctx = ApplicationProvider.getApplicationContext()
            WorkManagerTestInitHelper.initializeTestWorkManager(ctx, Configuration.Builder().build())
            shadowOf(ctx).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            server = MockWebServer().also { it.start() }
            nm = ctx.getSystemService(NotificationManager::class.java)
            db = inMemoryDb(ctx)
            secrets = FakeSecretStore(ctx, testSession(server.url("/").toString()))
            deduper = NotificationDeduper(db.dedup())
            factory = NotificationFactory(ctx, NotificationChannels(ctx), SettingsRepository(ctx), NotificationDeduper(db.dedup()))
            handler = PromptActionHandler(RelayApi(testClient(), secrets), factory, db.prompts(), db.outbox(), secrets)
            router = PushRouter(factory, deduper, secrets, SettingsRepository(ctx), db.prompts())
            repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            // El push real entra por el router (deduplica); sembrar aquí es lo que hace que el
            // deduper tenga la clave tomada, como en producción.
            router.handleDirect(PushKind.PROMPT, pushJson(), pushId = null)
        }
    }

    @After
    fun tearDown() {
        repoScope.cancel()
        runCatching { server.shutdown() }
    }

    private fun repository(bus: MessageBus) =
        PromptsRepository(
            ctx,
            db.prompts(),
            secrets,
            bus,
            RelayConnection(ctx, secrets, bus, testClient(), repoScope),
            RelayApi(testClient(), secrets),
            factory,
            repoScope,
        )

    private fun answerIntent(
        kind: String,
        option: String? = null,
    ) = Intent(PromptActionReceiver.ACTION_ANSWER).apply {
        putExtra(PromptActionReceiver.EXTRA_PROMPT_ID, "01PROMPT")
        putExtra(PromptActionReceiver.EXTRA_NOTIF_ID, notifId)
        putExtra(PromptActionReceiver.EXTRA_AGENT_LABEL, "api")
        putExtra(PromptActionReceiver.EXTRA_ANSWER_KIND, kind)
        option?.let { putExtra(PromptActionReceiver.EXTRA_OPTION_ID, it) }
    }

    private fun promptNotif() = shadowOf(nm).getNotification(NotificationFactory.PROMPT_TAG, notifId)

    private fun summary() = shadowOf(nm).getNotification(NotificationFactory.SUMMARY_TAG, summaryId)

    /** Todo lo que quedaría visible para ese agente en la bandeja. */
    private fun groupSurvivors() = shadowOf(nm).allNotifications.filter { it.group == group }

    /** Espera de tiempo real acotada: la aceptación exige ≤ ~1 s. */
    private suspend fun awaitGone(
        timeoutMs: Long = 2000,
        probe: suspend () -> Any?,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (probe() == null) return true
            delay(20)
        }
        return probe() == null
    }

    private suspend fun promptState(): String? = db.prompts().get("r1", "01PROMPT")?.state

    // ───────────────────────── S1: no zombie tras responder ─────────────────────────

    @Test
    fun `permitir desde la push no deja ni child ni resumen ni supervivientes`() {
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            assertNotNull(promptNotif())
            assertNotNull(summary())

            handler.handle(ctx, answerIntent(PromptActionReceiver.ANSWER_OPTION, "0"))

            assertTrue(awaitGone { promptNotif() }, "el child del prompt debe desaparecer")
            assertTrue(awaitGone { summary() }, "el resumen de grupo no puede quedar huérfano en la bandeja")
            assertEquals(emptyList(), groupSurvivors(), "no debe quedar nada del agente en la bandeja")
        }
    }

    @Test
    fun `rechazar desde la push no deja residuos`() {
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            handler.handle(ctx, answerIntent(PromptActionReceiver.ANSWER_OPTION, "2"))
            assertTrue(awaitGone { promptNotif() })
            assertTrue(awaitGone { summary() })
        }
    }

    @Test
    fun `si quedan otros prompts del agente el resumen se conserva`() {
        runBlocking {
            factory.showPrompt(promptBody().copy(promptId = "01OTRO", title = "¿Otra?"))
            server.enqueue(MockResponse().setBody("""{"ok":true}"""))

            handler.handle(ctx, answerIntent(PromptActionReceiver.ANSWER_OPTION, "0"))

            assertTrue(awaitGone { promptNotif() })
            assertNotNull(summary(), "el agente todavía tiene un prompt abierto")
            assertNotNull(shadowOf(nm).getNotification(NotificationFactory.PROMPT_TAG, NotificationFactory.notificationId("01OTRO")))
        }
    }

    @Test
    fun `prompt resolved por WS retira el resumen`() {
        runBlocking {
            val bus = MessageBus()
            repository(bus).start()
            assertNotNull(summary())

            bus.publish(Envelope.of(MsgType.PROMPT_RESOLVED, PromptResolved("01PROMPT", "terminal")))

            assertTrue(awaitGone { summary() })
        }
    }

    @Test
    fun `un settled obsoleto no sobrevive a un prompt nuevo del mismo agente`() {
        runBlocking {
            factory
                .showSettled(
                    PushSettledBody(
                        agentId = agentId,
                        agentLabel = "api",
                        preview = "Listo",
                        machineId = "M1",
                        machineName = "PC",
                    ),
                )
            assertNotNull(shadowOf(nm).getNotification(NotificationFactory.SETTLED_TAG, stableId("settled:$agentId")))

            factory.showPrompt(promptBody().copy(promptId = "01NUEVO"))

            assertTrue(
                awaitGone { shadowOf(nm).getNotification(NotificationFactory.SETTLED_TAG, stableId("settled:$agentId")) },
                "si el agente está preguntando, el «Terminó» anterior es basura",
            )
        }
    }

    // ───────────────────────── S3: reintento sin red ─────────────────────────

    @Test
    fun `el reintento sin red se conserva y desaparece cuando la respuesta encolada tiene exito`() {
        runBlocking {
            server.shutdown()
            handler.handle(ctx, answerIntent(PromptActionReceiver.ANSWER_CONFIRM_YES))

            val retrying = promptNotif()
            assertNotNull(retrying, "sin red la notificación se conserva")
            assertEquals(ctx.getString(dev.j3pf.rp.R.string.notif_retrying), retrying.extras.getString(android.app.Notification.EXTRA_TEXT))
            assertEquals(0, retrying.flags and android.app.Notification.FLAG_ONGOING_EVENT, "«se reintentará» no es ongoing")
            assertNotNull(summary())

            // Lo mismo que hace AnswerWorker al confirmar la respuesta encolada.
            factory.cancelPrompt("01PROMPT")

            assertTrue(awaitGone { promptNotif() })
            assertTrue(awaitGone { summary() })
        }
    }

    // ───────────────────────── S2/S4: deduper y replay ─────────────────────────

    @Test
    fun `al asentar un prompt se olvida la clave del deduper`() {
        runBlocking {
            assertTrue(!deduper.firstSeen("prompt:01PROMPT"), "la clave ya estaba tomada por el push original")

            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            handler.handle(ctx, answerIntent(PromptActionReceiver.ANSWER_OPTION, "0"))

            assertTrue(deduper.firstSeen("prompt:01PROMPT"), "un prompt legítimamente reabierto debe poder notificarse otra vez")
        }
    }

    @Test
    fun `un push repetido de un prompt ya respondido no vuelve a mostrarse`() {
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            handler.handle(ctx, answerIntent(PromptActionReceiver.ANSWER_OPTION, "0"))
            assertTrue(awaitGone { promptNotif() })

            val shown = router.handleDirect(PushKind.PROMPT, pushJson(), pushId = null)

            assertTrue(shown)
            assertTrue(awaitGone { promptNotif() }, "un replay no puede resucitar el prompt respondido")
        }
    }

    @Test
    fun `un prompt resuelto hace mucho si vuelve a mostrarse`() {
        runBlocking {
            val now = System.currentTimeMillis()
            db.prompts().upsert(
                listOf(promptModel().toEntity("r1", now - 11 * 60_000L).copy(state = "resolved", resolvedAt = now - 11 * 60_000L)),
            )
            nm.cancelAll()
            deduper.forget("prompt:01PROMPT") // entrega nueva, no replay

            router.handleDirect(PushKind.PROMPT, pushJson(), pushId = null)

            assertNotNull(promptNotif(), "fuera de la ventana de replay se vuelve a mostrar")
        }
    }

    @Test
    fun `un replay de prompt abierto no reabre un prompt ya respondido`() {
        runBlocking {
            val bus = MessageBus()
            val repo = repository(bus)
            repo.start()

            bus.publish(Envelope.of(MsgType.PROMPT_OPENED, promptModel()))
            assertTrue(await { promptState() == "open" })
            bus.publish(Envelope.of(MsgType.PROMPT_RESOLVED, PromptResolved("01PROMPT", "local")))
            assertTrue(await { promptState() == "resolved" })

            // Resync/replay de WS después de responder.
            bus.publish(Envelope.of(MsgType.PROMPT_OPENED, promptModel()))
            delay(600)

            assertEquals("resolved", promptState(), "un replay no puede reabrir un prompt ya respondido")
        }
    }

    private suspend fun await(
        timeoutMs: Long = 2000,
        probe: suspend () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (probe()) return true
            delay(20)
        }
        return probe()
    }
}
