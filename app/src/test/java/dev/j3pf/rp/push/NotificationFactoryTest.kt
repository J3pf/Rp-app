package dev.j3pf.rp.push

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.PromptCategory
import dev.j3pf.rp.protocol.PromptKind
import dev.j3pf.rp.protocol.PromptOption
import dev.j3pf.rp.protocol.PushPromptBody
import dev.j3pf.rp.protocol.PushSettledBody
import dev.j3pf.rp.util.stableId
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationFactoryTest {
    private lateinit var ctx: Application
    private lateinit var nm: NotificationManager
    private lateinit var factory: NotificationFactory

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        shadowOf(ctx).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        nm = ctx.getSystemService(NotificationManager::class.java)
        factory = NotificationFactory(ctx, NotificationChannels(ctx), SettingsRepository(ctx))
    }

    private fun body(
        kind: String = PromptKind.CONFIRM,
        category: String = PromptCategory.PERMISSION,
        options: List<PromptOption> = emptyList(),
        allowFreeText: Boolean = false,
        danger: String = Danger.HIGH,
    ) = PushPromptBody(
        promptId = "01PROMPT",
        agentId = "m:s",
        agentLabel = "api-gateway",
        machineId = "01MACH",
        machineName = "PC Empresa",
        machineContext = "work",
        kind = kind,
        category = category,
        title = "⚠ Permitir rm -rf dist?",
        message = "rm -rf dist",
        options = options,
        allowFreeText = allowFreeText,
        danger = danger,
    )

    private fun posted(): Notification =
        shadowOf(nm).getNotification(NotificationFactory.PROMPT_TAG, NotificationFactory.notificationId("01PROMPT"))
            ?: error("no se publicó la notificación")

    private fun titles(n: Notification) = n.actions.orEmpty().map { it.title.toString() }

    @Test
    fun `confirm muestra Permitir y Denegar y el titulo lleva la PC`() {
        runBlocking {
            factory.showPrompt(body())
            val n = posted()
            assertEquals(listOf("Permitir", "Denegar"), titles(n))
            assertTrue(n.extras.getString(Notification.EXTRA_TITLE)!!.contains("PC Empresa"))
            assertTrue(n.extras.getString(Notification.EXTRA_TITLE)!!.contains("api-gateway"))
            assertNotNull(n.contentIntent)
        }
    }

    @Test
    fun `select con tres opciones crea una accion por opcion`() {
        runBlocking {
            factory.showPrompt(
                body(
                    kind = PromptKind.SELECT,
                    options = listOf(PromptOption("0", "Permitir"), PromptOption("1", "Permitir siempre"), PromptOption("2", "Denegar")),
                ),
            )
            assertEquals(listOf("Permitir", "Permitir siempre", "Denegar"), titles(posted()))
        }
    }

    @Test
    fun `select con mas de tres opciones abre la hoja`() {
        runBlocking {
            factory.showPrompt(
                body(kind = PromptKind.SELECT, category = PromptCategory.QUESTION, options = (0..4).map { PromptOption("$it", "Op $it") }),
            )
            assertEquals(listOf("Abrir"), titles(posted()))
        }
    }

    @Test
    fun `input lleva RemoteInput`() {
        runBlocking {
            factory.showPrompt(body(kind = PromptKind.INPUT, category = PromptCategory.QUESTION))
            val reply = posted().actions.single { it.title.toString() == "Responder" }
            assertEquals(1, reply.remoteInputs.size)
            assertEquals(PromptActionReceiver.KEY_TEXT, reply.remoteInputs.single().resultKey)
        }
    }

    @Test
    fun `select con texto libre añade Responder ademas de las opciones`() {
        runBlocking {
            factory.showPrompt(
                body(
                    kind = PromptKind.SELECT,
                    category = PromptCategory.QUESTION,
                    options = listOf(PromptOption("0", "Sí"), PromptOption("1", "No")),
                    allowFreeText = true,
                ),
            )
            assertEquals(listOf("Sí", "No", "Responder"), titles(posted()))
        }
    }

    @Test
    fun `questionnaire nunca lleva botones de respuesta`() {
        runBlocking {
            factory.showPrompt(body(kind = PromptKind.QUESTIONNAIRE, category = PromptCategory.QUESTION))
            assertEquals(listOf("Abrir"), titles(posted()))
        }
    }

    @Test
    fun `cada PC tiene su grupo con cinco canales`() {
        runBlocking {
            factory.showPrompt(body())
            val groups = nm.notificationChannelGroups.map { it.id }
            assertTrue("rp.machine.01MACH" in groups)
            val ids = nm.notificationChannels.map { it.id }
            listOf("approvals", "questions", "terminal", "finished", "errors").forEach { assertTrue("rp.01MACH.$it" in ids) }
            // Una autorización va por el canal de autorizaciones de esa PC.
            assertEquals("rp.01MACH.approvals", posted().channelId)
        }
    }

    @Test
    fun `cancelPrompt retira la notificacion`() {
        runBlocking {
            factory.showPrompt(body())
            factory.cancelPrompt("01PROMPT")
            assertNull(shadowOf(nm).getNotification(NotificationFactory.PROMPT_TAG, NotificationFactory.notificationId("01PROMPT")))
        }
    }

    @Test
    fun `settled ofrece responder con RemoteInput`() {
        runBlocking {
            factory.showPrompt(body()) // para asegurar canales
            factory.showSettled(
                PushSettledBody(agentId = "m:s", agentLabel = "api", preview = "Listo", machineId = "01MACH", machineName = "PC"),
            )
            val n =
                shadowOf(nm).getNotification(
                    NotificationFactory.SETTLED_TAG,
                    dev.j3pf.rp.util
                        .stableId("settled:m:s"),
                )!!
            assertEquals(
                "Responder",
                n.actions
                    .single()
                    .title
                    .toString(),
            )
            assertEquals("rp.01MACH.finished", n.channelId)
        }
    }

    @Test
    fun `showWorking publica notificacion ongoing con abrir y cancelWorking la retira`() {
        runBlocking {
            val agent =
                dev.j3pf.rp.protocol.AgentSummary(
                    agentId = "m:s1",
                    machineId = "01MACH",
                    machineName = "PC Empresa",
                    machineContext = "work",
                    sessionName = "Refactor UI",
                    gitBranch = "feature/live",
                    status = dev.j3pf.rp.protocol.AgentStatus.WORKING,
                    activeTask = "Corriendo tests",
                )
            factory.showWorking(agent)
            val id = stableId("working:m:s1")
            val n = shadowOf(nm).getNotification(NotificationFactory.WORKING_TAG, id)
            assertNotNull(n)
            assertTrue((n.flags and Notification.FLAG_ONGOING_EVENT) != 0)
            assertEquals(listOf("Abrir"), titles(n))
            assertTrue(n.extras.getString(Notification.EXTRA_TITLE)!!.contains("PC Empresa"))
            assertTrue(n.extras.getString(Notification.EXTRA_TITLE)!!.contains("Refactor UI"))
            assertEquals("Corriendo tests", n.extras.getString(Notification.EXTRA_TEXT))
            assertEquals("⎇ feature/live", n.extras.getString(Notification.EXTRA_SUB_TEXT))

            factory.cancelWorking("m:s1")
            assertNull(shadowOf(nm).getNotification(NotificationFactory.WORKING_TAG, id))
        }
    }

    @Test
    fun `sin permiso showSending y showRetrying no publican notificaciones`() {
        shadowOf(ctx).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        factory.showSending(101, "api-gateway")
        assertTrue(shadowOf(nm).allNotifications.isEmpty())

        factory.showRetrying(102, "api-gateway")
        assertTrue(shadowOf(nm).allNotifications.isEmpty())
    }

    @Test
    fun `sin permiso no se publica nada`() {
        runBlocking {
            shadowOf(ctx).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
            factory.showPrompt(body())
            assertTrue(shadowOf(nm).allNotifications.isEmpty())
        }
    }
}
