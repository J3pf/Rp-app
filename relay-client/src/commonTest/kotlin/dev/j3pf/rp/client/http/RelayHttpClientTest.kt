package dev.j3pf.rp.client.http

import dev.j3pf.rp.data.net.AnswerOutcome
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.DeviceInfo
import dev.j3pf.rp.protocol.HealthResponse
import dev.j3pf.rp.protocol.MachinePatch
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.MeshConversationResponse
import dev.j3pf.rp.protocol.MeshSnapshot
import dev.j3pf.rp.protocol.NotifyPrefs
import dev.j3pf.rp.protocol.PairDeviceResponse
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.UserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RelayHttpClientTest {
    private var session: RelayCredentials? = RelayCredentials("https://first/", "first-token")
    private val requests = mutableListOf<RelayHttpRequest>()
    private var reply = RelayHttpResponse(200, "{}")
    private var credentialReads = 0
    private val client =
        RelayHttpClient(
            transport = { request ->
                requests += request
                ApiResult.Ok(reply)
            },
            credentials = {
                credentialReads++
                session
            },
            encodeQueryComponent = { it.replace(" ", "+").replace("/", "%2F") },
            invalidResponse = { IllegalStateException(it) },
        )

    @Test
    fun credentialsAreReadForEachRequestAndMissingSessionDoesNotSend() =
        runTest {
            client.pushTest()
            session = RelayCredentials("https://second///", "second-token")
            client.pushTest()
            session = null
            assertEquals(ApiResult.Http(401, "sin sesión"), client.pushTest())
            assertEquals(3, credentialReads)
            assertEquals(listOf("https://first/v1/devices/me/push-test", "https://second/v1/devices/me/push-test"), requests.map { it.url })
            assertEquals(listOf("Bearer first-token", "Bearer second-token"), requests.map { it.authorization })
            assertFalse(requests.first().toString().contains("first-token"))
            assertFalse(RelayCredentials("url", "secret-token").toString().contains("secret-token"))
        }

    @Test
    fun allAuthenticatedMethodsRefuseToSendWithoutSession() =
        runTest {
            session = null
            val results =
                listOf(
                    client.putPush(null, true),
                    client.pushTest(),
                    client.putPrefs(NotifyPrefs()),
                    client.devices(),
                    client.revokeDevice("d"),
                    client.machines(),
                    client.patchMachine("m", MachinePatch()),
                    client.revokeMachine("m"),
                    client.createMachineCode(),
                    client.getMesh(),
                    client.getMeshConversation("a", "b"),
                    client.getMeshAgentComms("a"),
                    client.openPrompts(),
                    client.sendMessage(UserMessage(agentId = "a", clientMsgId = "c", text = "hi")),
                )
            results.forEach { assertEquals(ApiResult.Http(401, "sin sesión"), it) }
            assertEquals(AnswerOutcome.Failed("HTTP 401"), client.answerPrompt("p", Answer.confirm(true)))
            assertTrue(requests.isEmpty())
        }

    @Test
    fun publicPairingRequestsNeverReadOrSendOldCredentials() =
        runTest {
            reply = RelayHttpResponse(200, """{"ok":true,"version":"v","uptime":1.5}""")
            assertEquals("v", assertIs<ApiResult.Ok<HealthResponse>>(client.healthz("https://new///")).value.version)
            reply = RelayHttpResponse(200, """{"deviceId":"D","deviceToken":"T","pushKey":"K"}""")
            assertEquals(
                "T",
                assertIs<ApiResult.Ok<PairDeviceResponse>>(
                    client.pairDevice("https://new", "CODE", "Phone", null),
                ).value.deviceToken,
            )
            assertEquals(0, credentialReads)
            assertEquals(listOf("https://new/healthz", "https://new/v1/pair/device"), requests.map { it.url })
            assertTrue(requests.all { it.authorization == null && it.timeout == RelayHttpTimeout.DEFAULT })
            assertEquals("GET", requests[0].method)
            assertEquals(null, requests[0].body)
            assertEquals("POST", requests[1].method)
            assertEquals("""{"code":"CODE","name":"Phone"}""", requests[1].body)
        }

    @Test
    fun endpointMethodsBodiesAndTimeoutsRemainUnchanged() =
        runTest {
            client.putPush("fcm", false)
            client.pushTest()
            client.putPrefs(NotifyPrefs(categories = mapOf("ask" to false)))
            client.revokeDevice("d:1")
            client.patchMachine("m:1", MachinePatch(name = "Desk"))
            client.revokeMachine("m:1")
            reply = RelayHttpResponse(200, """{"code":"CODE"}""")
            client.createMachineCode()
            reply = RelayHttpResponse(200, "{}")
            client.answerPrompt("p:1", Answer.option("yes"))
            client.sendMessage(UserMessage(agentId = "m:s", clientMsgId = "stable", text = "hola"))
            assertEquals(
                listOf(
                    "PUT /v1/devices/me/push",
                    "POST /v1/devices/me/push-test",
                    "PUT /v1/devices/me/prefs",
                    "DELETE /v1/devices/d:1",
                    "PATCH /v1/machines/m:1",
                    "DELETE /v1/machines/m:1",
                    "POST /v1/pair/codes",
                    "POST /v1/prompts/p:1/answer",
                    "POST /v1/agents/m:s/messages",
                ),
                requests.map { "${it.method} ${it.url.removePrefix("https://first")}" },
            )
            assertEquals(
                listOf(
                    """{"fcmToken":"fcm","direct":false}""",
                    "{}",
                    """{"categories":{"ask":false},"machines":{}}""",
                    null,
                    """{"name":"Desk"}""",
                    null,
                    "{}",
                    """{"answer":{"optionIds":["yes"]}}""",
                    """{"agentId":"m:s","clientMsgId":"stable","text":"hola","attachments":[],"delivery":"auto","method":"auto","asCommand":false}""",
                ),
                requests.map { it.body },
            )
            assertTrue(requests.take(7).all { it.timeout == RelayHttpTimeout.DEFAULT })
            assertTrue(requests.takeLast(2).all { it.timeout == RelayHttpTimeout.QUICK })
        }

    @Test
    fun wrappersFiltersAndMeshQueriesUseProtocolDecoding() =
        runTest {
            reply = RelayHttpResponse(200, """{"devices":[{"deviceId":"live","unknown":1},{"deviceId":"old","revoked":true}]}""")
            assertEquals(
                listOf("live"),
                assertIs<ApiResult.Ok<List<DeviceInfo>>>(client.devices()).value.map { it.deviceId },
            )
            reply = RelayHttpResponse(200, """{"machines":[{"machineId":"m","name":"Desk"}]}""")
            assertEquals("m", assertIs<ApiResult.Ok<List<MachineSummary>>>(client.machines()).value.single().machineId)
            reply = RelayHttpResponse(200, """{"prompts":[{"promptId":"p","agentId":"a","title":"Ask"}]}""")
            assertEquals("p", assertIs<ApiResult.Ok<List<Prompt>>>(client.openPrompts()).value.single().promptId)
            reply = RelayHttpResponse(200, """{"generatedAt":123,"unknown":true}""")
            assertEquals(123L, assertIs<ApiResult.Ok<MeshSnapshot>>(client.getMesh("one two")).value.generatedAt)
            client.getMesh()
            reply = RelayHttpResponse(200, """{"items":[],"nextBefore":88}""")
            assertEquals(
                88L,
                assertIs<ApiResult.Ok<MeshConversationResponse>>(
                    client.getMeshConversation("agent a", "agent/b", 100, 12),
                ).value.nextBefore,
            )
            client.getMeshConversation("a", "b")
            client.getMeshAgentComms("agent a/b", 7)
            assertEquals(
                listOf(
                    "/v1/devices",
                    "/v1/machines",
                    "/v1/prompts?state=open",
                    "/v1/mesh?machineId=one+two",
                    "/v1/mesh",
                    "/v1/mesh/conversation?a=agent+a&b=agent%2Fb&before=100&limit=12",
                    "/v1/mesh/conversation?a=a&b=b&limit=50",
                    "/v1/mesh/agents/agent%20a%2Fb/comms?limit=7",
                ),
                requests.map { it.url.removePrefix("https://first") },
            )
        }

    @Test
    fun unsuccessfulBodiesBlankSuccessMalformedJsonAndAnswerPolicy() =
        runTest {
            reply = RelayHttpResponse(503, "  not json\n")
            assertEquals(ApiResult.Http(503, "  not json\n"), client.devices())
            reply = RelayHttpResponse(204, " \n")
            assertIs<ApiResult.Ok<*>>(client.pushTest())
            reply = RelayHttpResponse(200, "{invalid")
            val error = assertIs<ApiResult.Network>(client.pushTest()).error
            assertIs<IllegalStateException>(error)
            assertTrue(error.message!!.startsWith("Respuesta inválida: "))
            for ((code, expected) in listOf(
                200 to AnswerOutcome.Sent,
                404 to AnswerOutcome.AlreadyResolved,
                409 to AnswerOutcome.AlreadyResolved,
                429 to AnswerOutcome.Queued,
                500 to AnswerOutcome.Queued,
                403 to AnswerOutcome.Failed("HTTP 403"),
            )) {
                reply = RelayHttpResponse(code, "{}")
                assertEquals(expected, client.answerPrompt("p", Answer.confirm(true)))
            }
        }

    @Test
    fun networkFailuresAreReturnedAndCancellationEscapes() =
        runTest {
            val failure = ApiResult.Network(IllegalStateException("offline"))
            val offline = RelayHttpClient({ failure }, { session }, { it }, { IllegalStateException(it) })
            assertEquals(failure, offline.machines())
            assertEquals(AnswerOutcome.Queued, offline.answerPrompt("p", Answer.confirm(true)))
            val cancelled =
                RelayHttpClient({ throw CancellationException("cancelled") }, { session }, { it }, { IllegalStateException(it) })
            assertFailsWith<CancellationException> { cancelled.machines() }
        }
}
