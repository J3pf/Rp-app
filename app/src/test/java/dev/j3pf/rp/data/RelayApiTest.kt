package dev.j3pf.rp.data

import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.AnswerOutcome
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.MachinePatch
import dev.j3pf.rp.protocol.NotifyPrefs
import dev.j3pf.rp.protocol.PairDeviceResponse
import dev.j3pf.rp.protocol.UserMessage
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

    @Test
    fun `public HTTP contract differential probes`() =
        runBlocking {
            val store = FakeSecretStore(ApplicationProvider.getApplicationContext(), testSession(server.url("/").toString()))
            val timeouts = mutableListOf<Long>()
            val http =
                testClient()
                    .newBuilder()
                    .callTimeout(19, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        timeouts += chain.call().timeout().timeoutNanos()
                        chain.proceed(chain.request())
                    }.build()
            api = RelayApi(http, store)

            fun outcome(value: Any?): String =
                when (value) {
                    is ApiResult.Ok<*> -> {
                        val result = value.value
                        if (result is PairDeviceResponse) {
                            "Ok(pair:${result.deviceId},validCredentials=${result.deviceToken == "T" && result.pushKey == "K"})"
                        } else {
                            "Ok($result)"
                        }
                    }
                    is ApiResult.Network -> "Network(${value.error.javaClass.name}:${value.error.message})"
                    else -> value.toString()
                }

            suspend fun probe(
                label: String,
                response: String = "{}",
                code: Int = 200,
                authenticated: Boolean = true,
                action: suspend () -> Any?,
            ) {
                server.enqueue(MockResponse().setResponseCode(code).setBody(response))
                val result = action()
                val request = server.takeRequest(5, TimeUnit.SECONDS)!!
                assertEquals(if (authenticated) "Bearer ${store.current()!!.deviceToken}" else null, request.getHeader("Authorization"))
                val type = request.getHeader("Content-Type")?.substringBefore("; boundary=")
                val rawBody = request.body.readUtf8()
                val boundary = request.getHeader("Content-Type")?.substringAfter("boundary=", "")
                val body = if (boundary.isNullOrEmpty()) rawBody else rawBody.replace(boundary, "BOUNDARY")
                // Only synthetic fixtures are used; hash bodies rather than logging pairing/push fields.
                val digest =
                    MessageDigest
                        .getInstance(
                            "SHA-256",
                        ).digest(body.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                val expectedSeconds =
                    when {
                        label.startsWith("answer") || label == "message" -> 8L
                        label == "upload" -> 0L
                        else -> 19L
                    }
                assertEquals(TimeUnit.SECONDS.toNanos(expectedSeconds), timeouts.last())
                val resultText = outcome(result)
                val wire = "${request.method}|${request.path}|auth=$authenticated|$type|$digest|${timeouts.last()}"
                println("HTTP_PROBE|$label|$wire|$resultText")
            }

            probe("health", """{"ok":true,"version":"v","uptime":1.5}""", authenticated = false) {
                api.healthz(
                    server.url("/").toString() + "//",
                )
            }
            probe("pair", """{"deviceId":"D","deviceToken":"T","pushKey":"K"}""", authenticated = false) {
                api.pairDevice(server.url("/").toString(), "CODE", "Phone é", null)
            }
            probe("push") { api.putPush("synthetic-fcm", false) }
            probe("push-null") { api.putPush(null, true) }
            probe("push-test") { api.pushTest() }
            probe("prefs") { api.putPrefs(NotifyPrefs(categories = mapOf("ask" to false))) }
            probe("devices", """{"devices":[{"deviceId":"live","extra":1},{"deviceId":"old","revoked":true}]}""") { api.devices() }
            probe("revoke-device") { api.revokeDevice("d:1") }
            probe("machines", """{"machines":[{"machineId":"m","name":"Desk"}]}""") { api.machines() }
            probe(
                "patch-machine",
                """{"machineId":"m","name":"Desk"}""",
            ) {
                api.patchMachine("m:1", MachinePatch(name = "Desk"))
            }
            probe("revoke-machine") { api.revokeMachine("m:1") }
            probe("machine-code", """{"code":"CODE","expiresAt":12.5}""") { api.createMachineCode() }
            val special = "a b+c/é雪*~%?&=#"
            probe("mesh", """{"generatedAt":12.5}""") { api.getMesh(special) }
            probe("mesh-default", """{"generatedAt":1}""") { api.getMesh() }
            probe("conversation", """{"items":[],"nextBefore":42}""") { api.getMeshConversation(special, "other +/中", 100, 12) }
            probe("conversation-default", """{"items":[]}""") { api.getMeshConversation("", "") }
            probe("agent-comms", """{"items":[]}""") { api.getMeshAgentComms(special, 7) }
            probe("prompts", """{"prompts":[{"promptId":"p","agentId":"a","title":"Ask"}]}""") { api.openPrompts() }
            probe("answer") { api.answerPrompt("p:1", Answer.option("yes")) }
            for (code in listOf(400, 401, 403, 404, 409, 429, 500, 503)) {
                probe("answer-$code", "not-json", code) { api.answerPrompt("p", Answer.confirm(true)) }
            }
            probe("message") { api.sendMessage(UserMessage(agentId = "m:s", clientMsgId = "stable", text = "hola é")) }
            probe("raw-http", "  not json\n", 503) { api.devices() }
            probe("blank", " \n", 204) { api.pushTest() }
            probe("malformed", "{invalid") { api.pushTest() }
            probe("malformed-wrapper", """{"machines":42}""") { api.machines() }
            probe("upload", """{"blobId":"B","size":7}""") {
                val progress = mutableListOf<Pair<Long, Long>>()
                val result =
                    api.uploadBlob("test.txt", "text/plain", "payload".toRequestBody("text/plain".toMediaType())) { sent, total ->
                        progress +=
                            sent to total
                    }
                assertEquals(7L to 7L, progress.last())
                result
            }
            store.set(testSession(server.url("/rotated///").toString()).copy(deviceToken = "rotated-synthetic-token"))
            probe("rotated") { api.pushTest() }
            store.set(null)
            val sent = server.requestCount
            assertEquals(ApiResult.Http(401, "sin sesión"), api.devices())
            assertEquals(ApiResult.Http(401, "sin sesión"), api.uploadBlob("test.txt", "text/plain", "payload".toRequestBody()) { _, _ -> })
            assertEquals(sent, server.requestCount)
            println("HTTP_PROBE|no-session|401|sends=0")
            println("HTTP_PROBE|invalid-health|${outcome(api.healthz("not a valid url"))}")
            println("HTTP_PROBE|invalid-pair|${outcome(api.pairDevice("http://[::1]:bad", "CODE", "Phone", null))}")
        }

    @Test
    fun `cancelling public HTTP request cancels native call`() =
        runBlocking {
            val cancelled = CountDownLatch(1)
            val http =
                testClient()
                    .newBuilder()
                    .eventListener(
                        object : EventListener() {
                            override fun canceled(call: Call) {
                                cancelled.countDown()
                            }
                        },
                    ).build()
            val store = FakeSecretStore(ApplicationProvider.getApplicationContext(), testSession(server.url("/").toString()))
            api = RelayApi(http, store)
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val job = launch(start = CoroutineStart.UNDISPATCHED) { api.pushTest() }
            assertTrue(server.takeRequest(5, TimeUnit.SECONDS) != null)
            job.cancelAndJoin()
            assertTrue(cancelled.await(5, TimeUnit.SECONDS))
            assertTrue(job.isCancelled)
        }
}
