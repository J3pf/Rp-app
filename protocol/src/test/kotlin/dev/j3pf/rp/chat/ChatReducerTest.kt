package dev.j3pf.rp.chat

import dev.j3pf.rp.protocol.AgentEvent
import dev.j3pf.rp.protocol.AgentHistory
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Entry
import dev.j3pf.rp.protocol.Envelope
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.PromptResolved
import dev.j3pf.rp.protocol.RpJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatReducerTest {
    private fun events(): List<AgentEvent> =
        RpJson.decodeFromString(
            ListSerializer(AgentEvent.serializer()),
            checkNotNull(javaClass.getResource("/fixtures/events.stream.json")).readText(),
        )

    private fun ev(
        seq: Long,
        json: String,
        epoch: String = "E1",
    ) = AgentEvent("a", epoch, seq, RpJson.parseToJsonElement(json) as JsonObject)

    @Test
    fun `reproduce un turno completo con streaming y herramienta`() {
        var s = ChatState()
        val all = events()
        s = ChatReducer.reduce(s, all[0], now = 100)
        assertTrue(s.working)
        s = ChatReducer.reduce(s, all[1], now = 100)
        s = ChatReducer.reduce(s, all[2], now = 100)
        s = ChatReducer.reduce(s, all[3], now = 100)
        assertEquals("Hola mundo", (s.streaming!!.blocks[0] as Block.Text).text)
        assertTrue(s.items.isEmpty()) // nada persistido hasta message_end
        s = ChatReducer.reduce(s, all[4], now = 100)
        assertNull(s.streaming)
        val msg = s.items.single() as AssistantMsg
        assertEquals("Hola mundo", msg.plainText)
        assertEquals("t9", s.tools["t9"]?.toolCallId)
        s = ChatReducer.reduce(s, all[5], now = 1000)
        assertEquals(ToolStatus.RUNNING, s.tools["t9"]!!.status)
        s = ChatReducer.reduce(s, all[6], now = 1100)
        assertEquals("h", s.tools["t9"]!!.partial)
        s = ChatReducer.reduce(s, all[7], now = 1500)
        val t = s.tools.getValue("t9")
        assertEquals(ToolStatus.OK, t.status)
        assertEquals("hi", t.result)
        assertEquals(500, t.durationMs)
        assertNull(t.partial)
        s = ChatReducer.reduce(s, all[8])
        assertFalse(s.working)
        assertFalse(s.needsResync)
        assertEquals(9, s.lastSeq)
    }

    @Test
    fun `duplicados y seq antiguos se ignoran`() {
        var s = ChatState()
        val d = ev(1, """{"type":"message_delta","contentIndex":0,"kind":"text","delta":"a"}""")
        s = ChatReducer.reduce(s, d)
        s = ChatReducer.reduce(s, d)
        s = ChatReducer.reduce(s, ev(2, """{"type":"message_delta","contentIndex":0,"kind":"text","delta":"b"}"""))
        s = ChatReducer.reduce(s, ev(1, """{"type":"message_delta","contentIndex":0,"kind":"text","delta":"X"}"""))
        assertEquals("ab", (s.streaming!!.blocks[0] as Block.Text).text)
    }

    @Test
    fun `un hueco de seq pide resync`() {
        var s = ChatReducer.reduce(ChatState(), ev(1, """{"type":"agent_start"}"""))
        s = ChatReducer.reduce(s, ev(5, """{"type":"agent_settled"}"""))
        assertTrue(s.needsResync)
    }

    @Test
    fun `delta sin message_start crea streaming y deltas de varios indices`() {
        var s = ChatReducer.reduce(ChatState(), ev(1, """{"type":"message_delta","contentIndex":1,"kind":"text","delta":"B"}"""))
        s = ChatReducer.reduce(s, ev(2, """{"type":"message_delta","contentIndex":0,"kind":"thinking","delta":"pienso"}"""))
        val b = s.streaming!!.blocks
        assertEquals(Block.Thinking("pienso"), b[0])
        assertEquals(Block.Text("B"), b[1])
    }

    @Test
    fun `contentIndex negativo o excesivo en message_delta se ignora`() {
        var s = ChatReducer.reduce(ChatState(), ev(1, """{"type":"message_delta","contentIndex":-1,"kind":"text","delta":"mal"}"""))
        assertNull(s.streaming)
        s = ChatReducer.reduce(s, ev(2, """{"type":"message_delta","contentIndex":100000,"kind":"text","delta":"mal"}"""))
        assertNull(s.streaming)
        s = ChatReducer.reduce(s, ev(3, """{"type":"message_delta","contentIndex":0,"kind":"text","delta":"ok"}"""))
        assertEquals(1, s.streaming!!.blocks.size)
        assertEquals(Block.Text("ok"), s.streaming.blocks[0])
    }

    @Test
    fun `toolcall parcial se completa`() {
        var s =
            ChatReducer.reduce(
                ChatState(),
                ev(1, """{"type":"message_delta","contentIndex":0,"kind":"toolcall","delta":"{\"command\":\"ls -"}"""),
            )
        val c1 = s.streaming!!.blocks[0] as Block.ToolCall
        assertNotNull(c1.args)
        s = ChatReducer.reduce(s, ev(2, """{"type":"message_delta","contentIndex":0,"kind":"toolcall","delta":"la\"}"}"""))
        val c2 = s.streaming!!.blocks[0] as Block.ToolCall
        assertEquals("{\"command\":\"ls -la\"}", c2.rawArgs)
    }

    @Test
    fun `aborto cierra streaming y marca herramientas`() {
        var s = ChatReducer.reduce(ChatState(), ev(1, """{"type":"message_delta","contentIndex":0,"kind":"text","delta":"par"}"""))
        s =
            ChatReducer.reduce(
                s,
                ev(2, """{"type":"tool_execution_start","toolCallId":"x","toolName":"bash","args":{"command":"sleep 9"}}"""),
            )
        s = ChatReducer.reduce(s, ev(3, """{"type":"agent_settled","aborted":true}"""))
        assertNull(s.streaming)
        assertEquals("aborted", (s.items.single() as AssistantMsg).stopReason)
        assertEquals(ToolStatus.ERROR, s.tools["x"]!!.status)
    }

    @Test
    fun `reconexion con sinceSeq no duplica`() {
        val all = events()
        var s = ChatState()
        for (e in all.take(5)) s = ChatReducer.reduce(s, e)
        val before = s.items
        // El relay reenvía desde 3 (solapamiento)
        for (e in all.drop(2)) s = ChatReducer.reduce(s, e)
        assertEquals(before, s.items)
        assertEquals(9, s.lastSeq)
    }

    @Test
    fun `epoch nuevo reinicia contador`() {
        var s = ChatReducer.reduce(ChatState(), ev(10, """{"type":"agent_start"}"""))
        s = ChatReducer.reduce(s, ev(1, """{"type":"agent_settled"}""", epoch = "E2"))
        assertEquals("E2", s.epoch)
        assertEquals(1, s.lastSeq)
        assertFalse(s.working)
    }

    @Test
    fun `historial reconstruye mensajes y herramientas, y los eventos posteriores se aplican`() {
        val h = Envelope.parse(checkNotNull(javaClass.getResource("/fixtures/agent.history.json")).readText()).body<AgentHistory>()
        val s = ChatReducer.applyHistory(ChatState(), h)
        val kinds = s.items.map { it::class.simpleName }
        assertEquals(listOf("UserMsg", "AssistantMsg", "AssistantMsg"), kinds)
        assertEquals("hola", (s.items[0] as UserMsg).text)
        val call = (s.items[1] as AssistantMsg).blocks.filterIsInstance<Block.ToolCall>().single()
        assertEquals("tc1", call.toolCallId)
        assertEquals("a.txt\nb.txt", s.tools["tc1"]!!.result)
        assertEquals("ls", s.tools["tc1"]!!.summary())
        assertEquals("Hay dos archivos", (s.items[2] as AssistantMsg).plainText)
        assertEquals(4, s.lastSeq)
        assertEquals("E1", s.epoch)
    }

    @Test
    fun `eventos en history con seq menor a liveFromSeq no se descartan`() {
        // Reproduce el bug donde liveFromSeq inflaba lastSeq antes de procesar h.events
        val h =
            AgentHistory(
                agentId = "a",
                epoch = "E1",
                liveFromSeq = 10L,
                entries = emptyList(),
                events =
                    listOf(
                        AgentEvent("a", "E1", 8L, RpJson.parseToJsonElement("""{"type":"agent_start"}""") as JsonObject),
                        AgentEvent(
                            "a",
                            "E1",
                            9L,
                            RpJson.parseToJsonElement(
                                """{"type":"message_end","message":{"role":"assistant","timestamp":2000,"content":[{"type":"text","text":"Respuesta lista"}]}}""",
                            ) as JsonObject,
                        ),
                        AgentEvent("a", "E1", 10L, RpJson.parseToJsonElement("""{"type":"agent_settled","aborted":false}""") as JsonObject),
                    ),
            )
        val s = ChatReducer.applyHistory(ChatState(), h)
        assertEquals(1, s.items.size)
        val msg = s.items.single() as AssistantMsg
        assertEquals("Respuesta lista", msg.plainText)
        assertEquals(10L, s.lastSeq)
        assertFalse(s.working)
    }

    @Test
    fun `historial y evento solapados se desduplican por clave`() {
        val e =
            Entry(
                "a2",
                "assistant",
                1300,
                RpJson.parseToJsonElement(
                    """{"role":"assistant","timestamp":1300,"content":[{"type":"text","text":"Hay dos archivos"}]}""",
                ) as JsonObject,
            )
        var s = ChatReducer.applyEntries(ChatState(), listOf(e), "E1")
        s =
            ChatReducer.reduce(
                s,
                ev(
                    1,
                    """{"type":"message_end","message":{"role":"assistant","timestamp":1300,"content":[{"type":"text","text":"Hay dos archivos"}]}}""",
                ),
            )
        assertEquals(1, s.items.size)
    }

    @Test
    fun `mensaje enviado desde el movil se marca`() {
        var s = ChatReducer.markLocalSent(ChatState(), "  haz X ")
        s = ChatReducer.reduce(s, ev(1, """{"type":"message_end","message":{"role":"user","timestamp":5,"content":"haz X"}}"""))
        val u = s.items.single() as UserMsg
        assertTrue(u.fromMobile)
        assertTrue(s.localSent.isEmpty())
        val other =
            ChatReducer.reduce(
                ChatState(),
                ev(1, """{"type":"message_end","message":{"role":"user","timestamp":6,"content":"terminal"}}"""),
            )
        assertFalse((other.items.single() as UserMsg).fromMobile)
    }

    @Test
    fun `imagenes del usuario se cuentan`() {
        val s =
            ChatReducer.reduce(
                ChatState(),
                ev(
                    1,
                    """{"type":"message_end","message":{"role":"user","timestamp":5,"content":[{"type":"text","text":"mira"},{"type":"image_ref","mimeType":"image/png","bytes":10}]}}""",
                ),
            )
        val u = s.items.single() as UserMsg
        assertEquals("mira", u.text)
        assertEquals(1, u.imageCount)
    }

    @Test
    fun `terminal_wait activa y desactiva`() {
        var s = ChatReducer.reduce(ChatState(), ev(1, """{"type":"terminal_wait","kind":"custom","title":"Panel","active":true}"""))
        assertEquals("Panel", s.terminalWait?.title)
        s = ChatReducer.reduce(s, ev(2, """{"type":"terminal_wait","kind":"custom","active":false}"""))
        assertNull(s.terminalWait)
    }

    @Test
    fun `notas de sistema`() {
        var s = ChatReducer.reduce(ChatState(), ev(1, """{"type":"model_select","model":{"provider":"x","id":"m1"}}"""))
        s = ChatReducer.reduce(s, ev(2, """{"type":"session_info_changed","name":"nuevo"}"""))
        s = ChatReducer.reduce(s, ev(3, """{"type":"session_compact","summary":"resumen"}"""))
        assertEquals(listOf(NoteKind.MODEL_CHANGE, NoteKind.SESSION_RENAMED, NoteKind.COMPACTION), s.items.map { (it as SystemNote).kind })
        assertEquals("m1", s.model)
        assertEquals("nuevo", s.sessionName)
    }

    @Test
    fun `tarjetas de prompt`() {
        val p = Prompt(promptId = "P", agentId = "a", title = "t")
        var s = ChatReducer.applyPromptOpened(ChatState(), p)
        s = ChatReducer.applyPromptOpened(s, p)
        assertEquals(1, s.items.size)
        s = ChatReducer.applyPromptResolved(s, PromptResolved("P", "remote", Answer.confirm(true)))
        val c = s.items.single() as PromptCard
        assertTrue(c.resolved)
        assertEquals("remote", c.resolvedBy)
    }

    @Test
    fun `resumen y diff de herramientas`() {
        val edit = ToolRun("1", "edit", RpJson.parseToJsonElement("""{"path":"a.kt","oldText":"x\ny\nz","newText":"x\nQ\nz"}"""))
        assertEquals("a.kt", edit.summary())
        val (o, n) = edit.editDiff()!!
        val d = simpleDiff(o, n)
        assertEquals(listOf(DiffKind.SAME, DiffKind.REMOVED, DiffKind.ADDED, DiffKind.SAME), d.map { it.kind })
        val bash = ToolRun("2", "bash", RpJson.parseToJsonElement("""{"command":"npm test\nfoo"}"""))
        assertEquals("npm test foo", bash.summary())
    }

    @Test
    fun `todo_update actualiza la lista de tareas en ChatState`() {
        val json =
            """
            {
                "type": "todo_update",
                "tasks": [
                    { "id": 1, "title": "Diseñar contrato", "status": "done", "note": "aprobado" },
                    { "id": 2, "title": "Implementar UI", "status": "in_progress", "note": "en curso" },
                    { "id": 3, "title": "Escribir tests", "status": "pending" }
                ]
            }
            """.trimIndent()
        val s = ChatReducer.reduce(ChatState(), ev(1, json))
        assertEquals(3, s.todos.size)
        assertEquals(1, s.todos[0].id)
        assertEquals("done", s.todos[0].status)
        assertEquals("Implementar UI", s.todos[1].title)
        assertEquals("in_progress", s.todos[1].status)
        assertEquals("Escribir tests", s.todos[2].title)
        assertEquals("pending", s.todos[2].status)
    }

    @Test
    fun `subagent_update registra subagente en subagents e items y enriquece tools`() {
        var s = ChatState()
        s = s.copy(tools = mapOf("task-123" to ToolRun(toolCallId = "task-123", name = "subagent_run")))

        val json =
            """
            {
                "type": "subagent_update",
                "taskId": "task-123",
                "agent": "sdd-explore",
                "label": "Mapear dependencias",
                "status": "running",
                "model": "claude-3-5-sonnet",
                "thinking": "Explorando módulos del sistema...",
                "startedAt": 1000,
                "turns": 2,
                "toolCalls": 5,
                "tokens": {
                    "input": 1500,
                    "output": 350,
                    "cacheRead": 200,
                    "cacheWrite": 0,
                    "total": 2050,
                    "cost": 0.012
                },
                "tools": ["read", "grep"]
            }
            """.trimIndent()

        s = ChatReducer.reduce(s, ev(1, json), now = 1500)

        val sub = s.subagents["task-123"]
        assertNotNull(sub)
        assertEquals("sdd-explore", sub.agent)
        assertEquals("Mapear dependencias", sub.label)
        assertEquals("claude-3-5-sonnet", sub.model)
        assertEquals("Explorando módulos del sistema...", sub.thinking)
        assertEquals(2050L, sub.tokens?.total)
        assertEquals(listOf("read", "grep"), sub.tools)

        assertEquals(sub, s.tools["task-123"]?.subagent)

        val item = s.items.firstOrNull { it.key == "subagent:task-123" } as? SubagentCardItem
        assertNotNull(item)
        assertEquals(sub, item.info)
    }

    @Test
    fun `settle marca subagentes activos como completados o abortados`() {
        var s = ChatState(tools = mapOf("task-1" to ToolRun(toolCallId = "task-1", name = "subagent_run")))
        val json = """{"type":"subagent_update","taskId":"task-1","agent":"worker","status":"running"}"""
        s = ChatReducer.reduce(s, ev(1, json))
        assertEquals("running", s.subagents["task-1"]?.status)
        assertEquals("running", s.tools["task-1"]?.subagent?.status)
        assertEquals("running", (s.items.single() as SubagentCardItem).info.status)

        s = ChatReducer.reduce(s, ev(2, """{"type":"agent_settled","aborted":false}"""))
        assertEquals("done", s.subagents["task-1"]?.status)
        assertEquals("done", s.tools["task-1"]?.subagent?.status)
        assertEquals("done", (s.items.single() as SubagentCardItem).info.status)

        s = ChatReducer.reduce(s, ev(3, json))
        s = ChatReducer.reduce(s, ev(4, """{"type":"agent_settled","aborted":true}"""))
        assertEquals("aborted", s.subagents["task-1"]?.status)
        assertEquals("aborted", s.tools["task-1"]?.subagent?.status)
        assertEquals("aborted", (s.items.single() as SubagentCardItem).info.status)
    }

    @Test
    fun `cambio de epoch limpia todos y subagents`() {
        var s = ChatState()
        s = ChatReducer.reduce(s, ev(1, """{"type":"todo_update","tasks":[{"id":1,"title":"T","status":"done"}]}""", epoch = "E1"))
        s = ChatReducer.reduce(s, ev(2, """{"type":"subagent_update","taskId":"t1","agent":"w","status":"running"}""", epoch = "E1"))
        assertEquals(1, s.todos.size)
        assertEquals(1, s.subagents.size)

        s = ChatReducer.reduce(s, ev(1, """{"type":"agent_start"}""", epoch = "E2"))
        assertTrue(s.todos.isEmpty())
        assertTrue(s.subagents.isEmpty())
    }
}
