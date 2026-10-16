package dev.j3pf.rp.data.net

import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.protocol.MeshAgentCommsResponse
import dev.j3pf.rp.protocol.MeshConversationResponse
import dev.j3pf.rp.protocol.MeshSnapshot
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RelayApiMeshTest {
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
    fun `getMesh decodes response and sends auth with optional machine query`() {
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"machineId":"machine-1","generatedAt":123,"groups":[],"nodes":[],"edges":[]}"""))
            val result = api.getMesh("machine & one")
            val value = assertIs<ApiResult.Ok<MeshSnapshot>>(result).value
            assertEquals("machine-1", value.machineId)
            assertEquals(123L, value.generatedAt)

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/v1/mesh", request.requestUrl!!.encodedPath)
            assertEquals("machine & one", request.requestUrl!!.queryParameter("machineId"))
            assertEquals("Bearer tok-123", request.getHeader("Authorization"))
        }
    }

    @Test
    fun `getMesh omits machine query when no machine is selected`() {
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"generatedAt":5}"""))
            assertEquals(5L, assertIs<ApiResult.Ok<MeshSnapshot>>(api.getMesh()).value.generatedAt)
            val request = server.takeRequest()
            assertEquals("/v1/mesh", request.path)
            assertEquals(null, request.requestUrl!!.queryParameter("machineId"))
        }
    }

    @Test
    fun `getMeshConversation sends endpoints and paging query and decodes items`() {
        runBlocking {
            server.enqueue(
                MockResponse().setBody(
                    """{"items":[{"commKey":"c1","fromAgentId":"a","toAgentId":"b","fromName":"A","toName":"B","action":"ask","text":"Help?","at":99}],"nextBefore":88}""",
                ),
            )
            val result = api.getMeshConversation("agent a", "agent/b", before = 100, limit = 12)
            val value = assertIs<ApiResult.Ok<MeshConversationResponse>>(result).value
            assertEquals("c1", value.items.single().commKey)
            assertEquals(88L, value.nextBefore)

            val request = server.takeRequest()
            assertEquals("/v1/mesh/conversation", request.requestUrl!!.encodedPath)
            assertEquals("agent a", request.requestUrl!!.queryParameter("a"))
            assertEquals("agent/b", request.requestUrl!!.queryParameter("b"))
            assertEquals("100", request.requestUrl!!.queryParameter("before"))
            assertEquals("12", request.requestUrl!!.queryParameter("limit"))
            assertEquals("Bearer tok-123", request.getHeader("Authorization"))
        }
    }

    @Test
    fun `getMeshConversation defaults limit and omits before`() {
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"items":[]}"""))
            assertEquals(emptyList(), assertIs<ApiResult.Ok<MeshConversationResponse>>(api.getMeshConversation("a", "b")).value.items)
            val request = server.takeRequest()
            assertEquals("50", request.requestUrl!!.queryParameter("limit"))
            assertEquals(null, request.requestUrl!!.queryParameter("before"))
        }
    }

    @Test
    fun `getMeshAgentComms encodes response and query`() {
        runBlocking {
            server.enqueue(
                MockResponse().setBody(
                    """{"items":[{"commKey":"c2","fromAgentId":"a","toAgentId":"agent-1","fromName":"A","toName":"One","action":"reply","at":42}]}""",
                ),
            )
            val result = api.getMeshAgentComms("agent-1", limit = 7)
            val value = assertIs<ApiResult.Ok<MeshAgentCommsResponse>>(result).value
            assertEquals("c2", value.items.single().commKey)
            assertEquals(42L, value.items.single().at)

            val request = server.takeRequest()
            assertEquals("/v1/mesh/agents/agent-1/comms", request.requestUrl!!.encodedPath)
            assertEquals("7", request.requestUrl!!.queryParameter("limit"))
            assertEquals("Bearer tok-123", request.getHeader("Authorization"))
        }
    }

    @Test
    fun `mesh endpoints return HTTP errors without decoding`() {
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503).setBody("relay unavailable"))
            server.enqueue(MockResponse().setResponseCode(404).setBody("conversation missing"))
            server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))

            val meshError = assertIs<ApiResult.Http>(api.getMesh())
            assertEquals(503, meshError.code)
            assertEquals("relay unavailable", meshError.body)
            val conversationError = assertIs<ApiResult.Http>(api.getMeshConversation("a", "b"))
            assertEquals(404, conversationError.code)
            assertEquals("conversation missing", conversationError.body)
            val agentCommsError = assertIs<ApiResult.Http>(api.getMeshAgentComms("agent"))
            assertEquals(401, agentCommsError.code)
            assertEquals("unauthorized", agentCommsError.body)
        }
    }
}
