package dev.j3pf.rp.ui.mesh

import dev.j3pf.rp.protocol.MeshSubagent
import dev.j3pf.rp.ui.mesh.layout.SubagentPlacement
import dev.j3pf.rp.ui.mesh.layout.SubagentSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ME-13 (paridad o4-shell, Gate A T1b): reglas del Fase D sin construir una malla entera,
 * clipped del objeto puro `SubagentPlacement` (SubagentPlacement.kt:20-49).
 */
class SubagentPlacementTest {
    private fun sub(
        status: String,
        label: String? = null,
        agent: String = "pi",
        taskId: String = "t1",
    ) = MeshSubagent(taskId = taskId, agent = agent, status = status, label = label)

    @Test
    fun `slot maps only running and working to NODE and everything else to PILL`() {
        assertEquals(SubagentSlot.NODE, SubagentPlacement.slot("running"))
        assertEquals(SubagentSlot.NODE, SubagentPlacement.slot(" working "))
        assertEquals(SubagentSlot.NODE, SubagentPlacement.slot("WORKING"))

        // Estados terminados y desconocidos no inventan actividad: quedan en píldoras.
        assertEquals(SubagentSlot.PILL, SubagentPlacement.slot("done"))
        assertEquals(SubagentPlacement.slot(""), SubagentSlot.PILL)
        assertEquals(SubagentSlot.PILL, SubagentPlacement.slot("unknown_state"))
    }

    @Test
    fun `split partitions active and finished subagents`() {
        val all =
            listOf(
                sub("running", taskId = "a"),
                sub("done", taskId = "b"),
                sub("working", taskId = "c"),
            )
        val (activos, terminados) = SubagentPlacement.split(all)
        assertEquals(listOf("a", "c"), activos.map { it.taskId })
        assertEquals(listOf("b"), terminados.map { it.taskId })
        assertEquals(0, SubagentPlacement.split(emptyList()).first.size)
    }

    @Test
    fun `nodeId is deterministic per parent and task and never leaks the raw taskId`() {
        val n1 = SubagentPlacement.nodeId("parent-1", "tarea-secreta")
        val n2 = SubagentPlacement.nodeId("parent-1", "tarea-secreta")
        val n3 = SubagentPlacement.nodeId("parent-2", "tarea-secreta")

        assertEquals(n1, n2)
        assertTrue(n1 != n3)
        // El id interno nunca contiene el taskId, así que no puede filtrarse como texto crudo.
        assertTrue(!n1.contains("tarea-secreta"))
        assertTrue(n1.startsWith("sub:parent-1:"))
    }

    @Test
    fun `displayName prefers the label and falls back to the agent name`() {
        assertEquals("Explorador de华东", SubagentPlacement.displayName(sub("running", label = "Explorador de华东")))
        assertEquals("agy-worker", SubagentPlacement.displayName(sub("running", label = "  ", agent = "agy-worker")))
        assertEquals("pi-worker", SubagentPlacement.displayName(sub("done", agent = "pi-worker")))
    }
}
