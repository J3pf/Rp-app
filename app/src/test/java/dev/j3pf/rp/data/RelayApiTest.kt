package dev.j3pf.rp.data

import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.AnswerOutcome
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.UserMessage
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RelayApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: RelayApi

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val store = FakeSecretStore(ApplicationProvider.getApplicationContext(), testSession(server.url("/").toString()))
        api = RelayApi(testClient(), store)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `responder prompt envia token y cuerpo y devuelve Sent`() {
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            assertEquals(AnswerOutcome.Sent, api.answerPrompt("P1", Answer.option("1")))
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/v1/prompts/P1/answer", req.path)
            assertEquals("Bearer tok-123", req.getHeader("Authorization"))
            assertEquals("""{"answer":{"optionIds":["1"]}}""", req.body.readUtf8())
        }
    }

    @Test
    fun `409 ya respondido cuenta como resuelto`() {
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"already_resolved"}"""))
            assertEquals(AnswerOutcome.AlreadyResolved, api.answerPrompt("P1", Answer.confirm(true)))
        }
    }

    @Test
    fun `5xx y corte de red se reintentan`() {
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503))
            assertEquals(AnswerOutcome.Queued, api.answerPrompt("P1", Answer.confirm(true)))
            server.shutdown()
            assertEquals(AnswerOutcome.Queued, api.answerPrompt("P1", Answer.confirm(true)))
        }
    }

    @Test
    fun `400 es un fallo definitivo`() {
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(400))
            assertIs<AnswerOutcome.Failed>(api.answerPrompt("P1", Answer.confirm(true)))
        }
    }

    @Test
    fun `enviar mensaje usa clientMsgId como idempotencia`() {
        runBlocking {
            server.enqueue(MockResponse().setBody("{}"))
            val msg = UserMessage(agentId = "m:s", clientMsgId = "01CLIENT", text = "hola")
            assertTrue(api.sendMessage(msg) is ApiResult.Ok)
            val req = server.takeRequest()
            assertEquals("/v1/agents/m:s/messages", req.path)
            assertTrue(""""clientMsgId":"01CLIENT"""" in req.body.readUtf8())
        }
    }

    @Test
    fun `emparejar dispositivo`() {
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"deviceId":"D","deviceToken":"T","pushKey":"K","relayName":"Mi relay"}"""))
            val r = api.pairDevice(server.url("/").toString().trimEnd('/'), "ABCD2345", "Pixel", null)
            assertIs<ApiResult.Ok<*>>(r)
            assertEquals("T", (r as ApiResult.Ok).value.deviceToken)
            assertEquals("/v1/pair/device", server.takeRequest().path)
        }
    }

    @Test
    fun `healthz`() {
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"ok":true,"version":"0.1.0","uptime":12.5}"""))
            val r = api.healthz(server.url("/").toString().trimEnd('/'))
            assertTrue((r as ApiResult.Ok).value.ok)
        }
    }

    @Test
    fun `url mal formada devuelve ApiResult Network en lugar de lanzar excepcion`() {
        runBlocking {
            assertIs<ApiResult.Network>(api.healthz("not a valid url"))
            assertIs<ApiResult.Network>(api.pairDevice("http://[::1]:bad", "ABCD2345", "Pixel", null))
        }
    }

    @Test
    fun `devices decodifica el wrapper json exacto del relay y filtra dispositivos revocados tolerando campos desconocidos`() {
        runBlocking {
            val json =
                """
                {
                  "devices": [
                    {
                      "deviceId": "dev-current",
                      "name": "Pixel 8",
                      "createdAt": 1700000000000,
                      "lastSeenAt": 1700001000000,
                      "revoked": false,
                      "current": true,
                      "direct": true,
                      "fcm": false,
                      "connected": true,
                      "unknownProperty": "extra"
                    },
                    {
                      "deviceId": "dev-revoked",
                      "name": "Antiguo Teléfono",
                      "createdAt": 1690000000000,
                      "lastSeenAt": 1690001000000,
                      "revoked": true,
                      "current": false,
                      "direct": false,
                      "fcm": false,
                      "connected": false
                    }
                  ],
                  "extraWrapperMeta": 123
                }
                """.trimIndent()
            server.enqueue(MockResponse().setBody(json))

            val res = api.devices()
            assertIs<ApiResult.Ok<List<dev.j3pf.rp.protocol.DeviceInfo>>>(res)
            val list = res.value
            assertEquals(1, list.size, "El dispositivo revocado debe ser excluido")
            assertEquals("dev-current", list[0].deviceId)
            assertEquals("Pixel 8", list[0].name)
            assertTrue(list[0].current)
            assertEquals("/v1/devices", server.takeRequest().path)
        }
    }

    @Test
    fun `machines decodifica el wrapper json del relay y tolera campos desconocidos`() {
        runBlocking {
            val json =
                """
                {
                  "machines": [
                    {
                      "machineId": "m-work",
                      "name": "PC Empresa",
                      "context": "work",
                      "color": "#7AA2F7",
                      "online": true,
                      "hostOnline": true,
                      "agentCount": 3,
                      "unknownServerProp": "ignorado"
                    }
                  ],
                  "serverTime": 99999999
                }
                """.trimIndent()
            server.enqueue(MockResponse().setBody(json))

            val res = api.machines()
            assertIs<ApiResult.Ok<List<dev.j3pf.rp.protocol.MachineSummary>>>(res)
            val list = res.value
            assertEquals(1, list.size)
            assertEquals("m-work", list[0].machineId)
            assertEquals("PC Empresa", list[0].name)
            assertEquals("/v1/machines", server.takeRequest().path)
        }
    }

    @Test
    fun `openPrompts decodifica el wrapper json del relay y tolera campos desconocidos`() {
        runBlocking {
            val json =
                """
                {
                  "prompts": [
                    {
                      "promptId": "prompt-1",
                      "agentId": "a-1",
                      "title": "Autorizar comando",
                      "message": "rm -rf build",
                      "category": "permission",
                      "danger": "high",
                      "state": "open",
                      "unknownField": [1, 2, 3]
                    }
                  ]
                }
                """.trimIndent()
            server.enqueue(MockResponse().setBody(json))

            val res = api.openPrompts()
            assertIs<ApiResult.Ok<List<dev.j3pf.rp.protocol.Prompt>>>(res)
            val list = res.value
            assertEquals(1, list.size)
            assertEquals("prompt-1", list[0].promptId)
            assertEquals("Autorizar comando", list[0].title)
            assertEquals("/v1/prompts?state=open", server.takeRequest().path)
        }
    }
}
