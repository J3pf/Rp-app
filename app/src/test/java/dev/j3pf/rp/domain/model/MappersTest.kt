package dev.j3pf.rp.domain.model

import dev.j3pf.rp.data.db.MachineEntity
import dev.j3pf.rp.protocol.MachineQuota
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.QuotaWindow
import dev.j3pf.rp.protocol.RpJson
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MappersTest {
    @Test
    fun `machine summary quotas persist and map back`() {
        val quotas =
            listOf(
                MachineQuota(
                    family = "claude",
                    label = "Claude",
                    plan = "pro",
                    windows = listOf(QuotaWindow(id = "weekly", usedPercent = 37.5)),
                ),
            )
        val summary = MachineSummary(machineId = "machine-1", name = "Development", quotas = quotas)

        val entity = summary.toEntity(relayId = "relay-1")
        val quotasJson = assertNotNull(entity.quotasJson)

        assertEquals(quotas, RpJson.decodeFromString(ListSerializer(MachineQuota.serializer()), quotasJson))
        assertEquals("relay-1", entity.relayId)
        assertEquals(summary, entity.toModel())
    }

    @Test
    fun `null or invalid persisted quotas map to null`() {
        assertNull(machineEntity(quotasJson = null).toModel().quotas)
        assertNull(machineEntity(quotasJson = "not valid quota json").toModel().quotas)
        assertNull(machineEntity(quotasJson = "[{}]").toModel().quotas)
    }

    @Test
    fun `agent summary theme and todo fields persist and map back`() {
        val summary =
            dev.j3pf.rp.protocol.AgentSummary(
                agentId = "agent-1",
                machineId = "machine-1",
                machineName = "Development",
                theme = "gentle-sexy",
                todoCompleted = 3,
                todoTotal = 5,
                activeTask = "Implementing feature",
            )

        val entity = summary.toEntity(relayId = "relay-1")
        assertEquals("gentle-sexy", entity.theme)
        assertEquals(3, entity.todoCompleted)
        assertEquals(5, entity.todoTotal)
        assertEquals("Implementing feature", entity.activeTask)

        val mapped = entity.toModel()
        assertEquals(summary, mapped)
    }

    private fun machineEntity(quotasJson: String?) =
        MachineEntity(
            relayId = "relay-1",
            machineId = "machine-1",
            name = "Development",
            context = "personal",
            color = "#6750A4",
            icon = null,
            online = true,
            agentCount = 2,
            openPromptCount = 1,
            hostOnline = true,
            quotasJson = quotasJson,
        )
}
