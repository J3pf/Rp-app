package dev.j3pf.rp.ui.mesh

import dev.j3pf.rp.protocol.CommAction
import dev.j3pf.rp.protocol.MeshNode
import dev.j3pf.rp.protocol.MeshTodos
import kotlin.test.Test
import kotlin.test.assertEquals

class MeshFormattersTest {
    @Test
    fun `formatMeshRole translates roles to Spanish`() {
        assertEquals("Orquestador", formatMeshRole("orchestrator"))
        assertEquals("Subordinado", formatMeshRole("subordinate"))
        assertEquals("Individual", formatMeshRole("solo"))
        assertEquals("Agente", formatMeshRole("custom_role"))
    }

    @Test
    fun `formatMeshStatus translates statuses to Spanish`() {
        assertEquals("Trabajando", formatMeshStatus("working"))
        assertEquals("Esperando respuesta", formatMeshStatus("waiting_input"))
        assertEquals("Inactivo", formatMeshStatus("idle"))
        assertEquals("Desconectado", formatMeshStatus("offline"))
        assertEquals("Error", formatMeshStatus("error"))
        assertEquals("Compactando", formatMeshStatus("compacting"))
    }

    @Test
    fun `formatSubagentStatus translates subagent execution status`() {
        assertEquals("En ejecución", formatMeshSubagentStatus("running"))
        assertEquals("Trabajando", formatMeshSubagentStatus("working"))
        assertEquals("Terminado", formatMeshSubagentStatus("done"))
        assertEquals("Completado", formatMeshSubagentStatus("completed"))
        assertEquals("Error", formatMeshSubagentStatus("error"))
        assertEquals("Detenido", formatMeshSubagentStatus("stopped"))
    }

    @Test
    fun `formatCommAction translates actions to Spanish`() {
        assertEquals("Pregunta", formatMeshCommAction(CommAction.ASK.raw))
        assertEquals("Respuesta", formatMeshCommAction(CommAction.REPLY.raw))
        assertEquals("Pase", formatMeshCommAction(CommAction.HANDOVER.raw))
        assertEquals("Envío", formatMeshCommAction(CommAction.SEND.raw))
        assertEquals("Mensaje", formatMeshCommAction(CommAction.MESSAGE.raw))
        assertEquals("Mensaje", formatMeshCommAction(CommAction.UNKNOWN.raw))
    }

    @Test
    fun `formatMeshCommText handles null as hidden content`() {
        assertEquals("Contenido oculto", formatMeshCommText(null))
        assertEquals("Mensaje real", formatMeshCommText("Mensaje real"))
    }

    @Test
    fun `formatNodeAccessibility builds localized screen reader description`() {
        val nodeWithTodos =
            MeshNode(
                agentId = "agent-1",
                machineId = "m1",
                machineName = "PC",
                name = "Builder",
                status = "working",
                role = "orchestrator",
                todos = MeshTodos(total = 7, done = 3, inProgress = 1, blocked = 0, pending = 3),
            )
        assertEquals(
            "Orquestador Builder, Trabajando, 3 de 7 tareas",
            formatMeshNodeContentDescription(nodeWithTodos),
        )

        val nodeWithoutTodos =
            MeshNode(
                agentId = "agent-2",
                machineId = "m1",
                machineName = "PC",
                name = "Worker",
                status = "idle",
                role = "subordinate",
            )
        assertEquals(
            "Subordinado Worker, Inactivo",
            formatMeshNodeContentDescription(nodeWithoutTodos),
        )
    }
}
