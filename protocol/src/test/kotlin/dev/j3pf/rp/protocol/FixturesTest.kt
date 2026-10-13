package dev.j3pf.rp.protocol

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Contrato: todos los fixtures de `fixtures/` (copiados de rp-relay con scripts/sync-fixtures.sh) deben parsear. */
class FixturesTest {
    private fun fixture(name: String): String = checkNotNull(javaClass.getResource("/fixtures/$name")) { "falta fixture $name" }.readText()

    @Test
    fun `todos los fixtures JSON son JSON valido`() {
        val dir = File(checkNotNull(javaClass.getResource("/fixtures")).toURI())
        val files = dir.walkTopDown().filter { it.isFile && it.extension == "json" }.toList()
        assertTrue(files.isNotEmpty())
        for (f in files) RpJson.parseToJsonElement(f.readText())
    }

    /**
     * Contrato con rp-relay: cada fixture sincronizado (`scripts/sync-fixtures.sh`) con sobre `{v,t,d}` y un tipo
     * que la app recibe debe decodificarse con los tipos Kotlin. Sin fixtures sincronizados no hace nada.
     */
    @Test
    fun `fixtures sincronizados desde rp-relay se decodifican`() {
        val url = javaClass.getResource("/fixtures/relay") ?: return
        val files = File(url.toURI()).listFiles { f -> f.name.endsWith(".valid.json") }.orEmpty()
        for (f in files) {
            val el = RpJson.parseToJsonElement(f.readText())
            val envs =
                if (el is JsonArray) {
                    el.map { RpJson.decodeFromJsonElement(Envelope.serializer(), it) }
                } else {
                    listOf(RpJson.decodeFromJsonElement(Envelope.serializer(), el))
                }
            for (env in envs) {
                when (env.t) {
                    MsgType.APP_WELCOME -> env.body<AppWelcome>()
                    MsgType.MACHINES_UPDATE -> env.body<MachinesUpdate>()
                    MsgType.AGENTS_UPDATE -> env.body<AgentsUpdate>()
                    MsgType.AGENT_HISTORY -> env.body<AgentHistory>()
                    MsgType.AGENT_EVENT -> env.body<AgentEvent>()
                    MsgType.PROMPT_OPENED -> env.body<Prompt>()
                    MsgType.PROMPT_RESOLVED -> env.body<PromptResolved>()
                    MsgType.PROMPT_ANSWER_RESULT -> env.body<PromptAnswerResult>()
                    MsgType.USER_MESSAGE_ACK -> env.body<UserMessageAck>()
                    MsgType.PUSH_NOTIFY -> env.body<PushNotify>()
                    MsgType.HOSTS_UPDATE -> env.body<HostsUpdate>()
                    else -> Unit // mensajes del lado plugin/host que la app no consume
                }
            }
        }
    }

    @Test
    fun `app welcome`() {
        val env = Envelope.parse(fixture("app.welcome.json"))
        assertEquals(MsgType.APP_WELCOME, env.t)
        val w = env.body<AppWelcome>()
        assertEquals(2, w.machines.size)
        assertEquals("work", w.machines[1].context)
        assertEquals("#1565C0", w.machines[1].color)
        val a = w.agents.single()
        assertEquals("waiting_input", a.status)
        assertEquals("p3", a.herdr?.paneId)
        assertEquals("auto", a.gentleMode)
        assertEquals("claude-sonnet", a.model?.label)
        val p = w.openPrompts.single()
        assertEquals(PromptCategory.PERMISSION, p.category)
        assertEquals("high", p.danger)
        assertEquals("bash", p.tool?.name)
        assertEquals(3, p.options.size)
        assertNull(p.placeholder)
    }

    @Test
    fun `questionnaire`() {
        val p = Envelope.parse(fixture("prompt.questionnaire.json")).body<Prompt>()
        assertEquals(PromptKind.QUESTIONNAIRE, p.kind)
        val qs = checkNotNull(p.questions)
        assertEquals(2, qs.size)
        assertTrue(qs[1].multiSelect)
        assertEquals("# SQLite\nsimple", qs[0].options[0].preview)
        assertTrue(p.source!!.subagent)
        assertEquals(1760349999000, p.expiresAt)
    }

    @Test
    fun `history`() {
        val h = Envelope.parse(fixture("agent.history.json")).body<AgentHistory>()
        assertEquals(3, h.entries.size)
        assertEquals(EntryKind.TOOL_RESULT, h.entries[2].kind)
        assertEquals(4, h.liveFromSeq)
        assertEquals(4, h.events.single().seq)
    }

    @Test
    fun `ack y push`() {
        val ack = Envelope.parse(fixture("user.message.ack.json")).body<UserMessageAck>()
        assertEquals("typed", ack.disposition)
        assertEquals("interactive", ack.method)
        assertNull(ack.error)
        assertFalse(ack.isFailure)
        val n = Envelope.parse(fixture("push.notify.json")).body<PushNotify>()
        assertEquals(PushKind.PROMPT, n.k)
        val body = RpJson.decodeFromJsonElement(PushPromptBody.serializer(), n.body)
        assertEquals("api-gateway", body.agentLabel)
        assertEquals(2, body.options.size)
    }

    @Test
    fun `envelope roundtrip omite nulos`() {
        val msg = UserMessage(agentId = "m:s", clientMsgId = Ulid.new(), text = "hola")
        val env = Envelope.of(MsgType.USER_MESSAGE, msg, id = Ulid.new())
        val json = env.encode()
        assertTrue(""""v":1""" in json)
        assertFalse("null" in json)
        val back = Envelope.parse(json).body<UserMessage>()
        assertEquals(msg, back)
    }

    @Test
    fun `answer serializa solo el campo usado`() {
        assertEquals("""{"optionIds":["1"]}""", RpJson.encodeToString(Answer.serializer(), Answer.option("1")))
        assertEquals("""{"confirmed":true}""", RpJson.encodeToString(Answer.serializer(), Answer.confirm(true)))
        assertEquals("""{"cancelled":true}""", RpJson.encodeToString(Answer.serializer(), Answer.Cancelled))
        val q = Answer.questionnaire(listOf(QuestionAnswer(0, optionIds = listOf("1")), QuestionAnswer(1, text = "x")))
        assertEquals(
            """{"answers":[{"questionIndex":0,"optionIds":["1"]},{"questionIndex":1,"text":"x"}]}""",
            RpJson.encodeToString(Answer.serializer(), q),
        )
    }

    @Test
    fun `parseOrNull tolera basura y campos desconocidos`() {
        assertNull(Envelope.parseOrNull("no es json"))
        val env = assertNotNull(Envelope.parseOrNull("""{"t":"x","extra":1}"""))
        assertEquals(1, env.v)
        assertTrue(env.d is JsonObject)
    }

    @Test
    fun `ulid valido y ordenable`() {
        val a = Ulid.new(1_000_000)
        val b = Ulid.new(2_000_000)
        assertTrue(Ulid.isValid(a))
        assertTrue(a < b)
    }

    @Test
    fun `codigos de cierre fatales`() {
        assertTrue(CloseCodes.isFatal(4001))
        assertTrue(CloseCodes.isFatal(4002))
        assertFalse(CloseCodes.isFatal(1012))
        assertFalse(CloseCodes.isFatal(4003))
    }

    @Test
    fun `events stream es un array`() {
        val arr = RpJson.parseToJsonElement(fixture("events.stream.json"))
        assertTrue(arr is JsonArray)
        arr.let { (it as JsonArray).forEach { e -> RpJson.decodeFromJsonElement(AgentEvent.serializer(), e) } }
        arr.let { check((it as JsonArray)[0].jsonObject.containsKey("e")) }
    }
}
