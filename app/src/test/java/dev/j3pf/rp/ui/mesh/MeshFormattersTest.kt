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
        assertEquals("Subagente", formatMeshRole("subagent"))
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

    @Test
    fun `formatMeshDisplayName filters out raw IDs and falls back to cwd basename or Agente`() {
        val uuidNode =
            MeshNode(
                agentId = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                machineId = "m1",
                machineName = "Omarchy",
                name = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                cwd = "/home/drift/Workspace/RP/Rp-app",
                status = "working",
            )
        assertEquals("Rp-app", formatMeshDisplayName(uuidNode))

        val truncatedUuidNode =
            MeshNode(
                agentId = "01a14fbb-44e3-765f-99e1-970655bf47bf",
                machineId = "m1",
                machineName = "Omarchy",
                name = "01a14fbb-44e3-765f-99e1-…",
                cwd = "/home/drift/Workspace/RP/Rp-relay",
                status = "idle",
            )
        assertEquals("Rp-relay", formatMeshDisplayName(truncatedUuidNode))

        val subagentChatNode =
            MeshNode(
                agentId = "subagent-chat-01a14fbb-2812-7332",
                machineId = "m2",
                machineName = "Croma",
                name = "subagent-chat-01a14fbb-2812-7332",
                cwd = "/home/drift/Workspace/RP",
                status = "working",
            )
        assertEquals("RP", formatMeshDisplayName(subagentChatNode))

        val uuidWithSuffixNode =
            MeshNode(
                agentId = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                machineId = "m1",
                machineName = "Omarchy",
                name = "01a14fbb-2812-7332-aae5-d003f17b8fe7 · 4",
                cwd = null,
                status = "working",
            )
        assertEquals("Agente", formatMeshDisplayName(uuidWithSuffixNode))

        val humanNode =
            MeshNode(
                agentId = "agent-123",
                machineId = "m1",
                machineName = "Omarchy",
                name = "Orquestador Principal",
                cwd = "/home/drift/Workspace/RP",
                status = "working",
            )
        assertEquals("Orquestador Principal", formatMeshDisplayName(humanNode))
    }

    @Test
    fun `formatMeshDisplayName disambiguates duplicates`() {
        val node1 =
            MeshNode(
                agentId = "a1",
                machineId = "m1",
                machineName = "Omarchy",
                name = "Worker",
                cwd = "/app",
                status = "idle",
            )
        val node2 =
            MeshNode(
                agentId = "a2",
                machineId = "m2",
                machineName = "Croma",
                name = "Worker",
                cwd = "/app",
                status = "idle",
            )
        val allNodes = listOf(node1, node2)
        assertEquals("Worker (Omarchy)", formatMeshDisplayName(node1, allNodes))
        assertEquals("Worker (Croma)", formatMeshDisplayName(node2, allNodes))

        val node3 =
            MeshNode(
                agentId = "a3",
                machineId = "m1",
                machineName = "Omarchy",
                name = "Worker",
                cwd = "/app",
                status = "idle",
            )
        val sameMachineNodes = listOf(node1, node3)
        assertEquals("Worker (Omarchy · 1)", formatMeshDisplayName(node1, sameMachineNodes))
        assertEquals("Worker (Omarchy · 2)", formatMeshDisplayName(node3, sameMachineNodes))
    }

    @Test
    fun `formatMeshSecondaryLine formats machine and branch or model`() {
        val nodeWithBranch =
            MeshNode(
                agentId = "a1",
                machineId = "m1",
                machineName = "Omarchy",
                name = "Worker",
                gitBranch = "feat/mesh-redesign",
                model = "sonnet",
                status = "idle",
            )
        assertEquals("Omarchy · feat/mesh-redesign", formatMeshSecondaryLine(nodeWithBranch))

        val nodeWithModelOnly =
            MeshNode(
                agentId = "a2",
                machineId = "m2",
                machineName = "Croma",
                name = "Worker",
                model = "gemini-flash",
                status = "idle",
            )
        assertEquals("Croma · gemini-flash", formatMeshSecondaryLine(nodeWithModelOnly))

        val nodeWithNeither =
            MeshNode(
                agentId = "a3",
                machineId = "m1",
                machineName = "Omarchy",
                name = "Worker",
                status = "idle",
            )
        assertEquals("Omarchy", formatMeshSecondaryLine(nodeWithNeither))
    }

    @Test
    fun `formatMeshGroupChipLabel uses orchestrator display name and filters raw IDs`() {
        val orchNode =
            MeshNode(
                agentId = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                machineId = "m1",
                machineName = "Omarchy",
                name = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                cwd = "/home/drift/Workspace/RP/Rp-app",
                status = "working",
            )
        val allNodes = listOf(orchNode)
        val group =
            dev.j3pf.rp.protocol.MeshGroup(
                groupId = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                label = "01a14fbb-2812-7332-aae5-d003f17b8fe7 · 4",
                orchestratorAgentId = orchNode.agentId,
                memberAgentIds = listOf(orchNode.agentId),
            )
        assertEquals("Rp-app", formatMeshGroupChipLabel(group, orchNode, allNodes))
    }

    @Test
    fun `formatMeshDisplayName and group chip handle relay 0_5_3 sentinels and existing numbered suffixes`() {
        val agentSentinelNodeWithCwd =
            MeshNode(
                agentId = "node-1",
                machineId = "m1",
                machineName = "PC",
                name = "Agent",
                cwd = "/home/drift/Workspace/RP/Rp-plugin",
                status = "idle",
            )
        assertEquals("Rp-plugin", formatMeshDisplayName(agentSentinelNodeWithCwd))

        val agentSentinelNodeWithoutCwd =
            MeshNode(
                agentId = "node-2",
                machineId = "m1",
                machineName = "PC",
                name = "Agent",
                cwd = null,
                status = "idle",
            )
        assertEquals("Agente", formatMeshDisplayName(agentSentinelNodeWithoutCwd))

        val alreadyNumbered1 =
            MeshNode(
                agentId = "node-3",
                machineId = "m1",
                machineName = "PC",
                name = "Rp-app (1)",
                cwd = "/home/drift/Workspace/RP/Rp-app",
                status = "idle",
            )
        val alreadyNumbered2 =
            MeshNode(
                agentId = "node-4",
                machineId = "m2",
                machineName = "Mac",
                name = "Rp-app (2)",
                cwd = "/home/drift/Workspace/RP/Rp-app",
                status = "idle",
            )
        val all = listOf(alreadyNumbered1, alreadyNumbered2)
        assertEquals("Rp-app (1)", formatMeshDisplayName(alreadyNumbered1, all))
        assertEquals("Rp-app (2)", formatMeshDisplayName(alreadyNumbered2, all))

        val groupSentinel =
            dev.j3pf.rp.protocol.MeshGroup(
                groupId = "grp-1",
                label = "Mesh Group",
                orchestratorAgentId = null,
                memberAgentIds = listOf("node-1"),
            )
        assertEquals("Grupo de agentes", formatMeshGroupChipLabel(groupSentinel, null, emptyList()))
    }

    @Test
    fun `isIdLooking keeps human names like agent-deploy and agent-docs`() {
        kotlin.test.assertFalse(isIdLooking("agent-deploy"))
        kotlin.test.assertFalse(isIdLooking("agent-docs"))
        kotlin.test.assertFalse(isIdLooking("session-manager"))
        kotlin.test.assertTrue(isIdLooking("agent-01a14fbb-2812"))
        kotlin.test.assertTrue(isIdLooking("subagent-chat-01a14fbb-2812-7332"))
    }

    @Test
    fun `formatMeshMachineName and secondary line sanitize machine IDs`() {
        assertEquals("Omarchy", formatMeshMachineName("Omarchy"))
        assertEquals("Máquina", formatMeshMachineName(null))
        assertEquals("Máquina", formatMeshMachineName(""))
        assertEquals("Máquina", formatMeshMachineName("01a14fbb-2812-7332-aae5-d003f17b8fe7"))

        val nodeWithRawMachine =
            MeshNode(
                agentId = "node-1",
                machineId = "m1",
                machineName = "01a14fbb-2812-7332-aae5-d003f17b8fe7",
                name = "Worker",
                gitBranch = "main",
                status = "idle",
            )
        assertEquals("Máquina · main", formatMeshSecondaryLine(nodeWithRawMachine))
    }
}
