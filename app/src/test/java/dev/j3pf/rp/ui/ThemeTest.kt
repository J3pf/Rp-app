package dev.j3pf.rp.ui

import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.ui.theme.RpThemeChoice
import dev.j3pf.rp.ui.theme.resolveThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {
    @Test
    fun `ajuste explicito prevalece sobre auto`() {
        assertEquals(RpThemeChoice.GENTLE_SEXY, resolveThemeChoice("gentle-sexy", emptyList(), emptyList()))
        assertEquals(RpThemeChoice.GENTLE_CUTE, resolveThemeChoice("gentle-cute", emptyList(), emptyList()))
        assertEquals(RpThemeChoice.OMARCHY, resolveThemeChoice("omarchy-system", emptyList(), emptyList()))
        assertEquals(RpThemeChoice.SYSTEM, resolveThemeChoice("system", emptyList(), emptyList()))
    }

    @Test
    fun `auto con 2 o mas pcs prioriza la pc personal`() {
        val workPc =
            MachineSummary(
                machineId = "m-work",
                name = "Work PC",
                context = "work",
                color = "#7AA2F7",
                online = true,
            )
        val personalPc =
            MachineSummary(
                machineId = "m-personal",
                name = "Personal Laptop",
                context = "personal",
                color = "#E91E63", // Hint hacia sexy
                online = true,
            )
        val workAgent =
            AgentSummary(
                agentId = "a1",
                machineId = "m-work",
                sessionName = "omarchy-task",
                launcher = "omarchy",
            )
        val personalAgent =
            AgentSummary(
                agentId = "a2",
                machineId = "m-personal",
                theme = "Gentleman-Sexy",
                sessionName = "gentle-sexy-dev",
            )

        val resolved =
            resolveThemeChoice(
                themeSetting = "auto",
                machines = listOf(workPc, personalPc),
                agents = listOf(workAgent, personalAgent),
            )
        assertEquals(RpThemeChoice.GENTLE_SEXY, resolved)
    }

    @Test
    fun `auto sin pc personal toma el tema mas repetido`() {
        val pc1 = MachineSummary(machineId = "m1", name = "PC 1", context = "work", color = "#7AA2F7", online = true)
        val pc2 = MachineSummary(machineId = "m2", name = "PC 2", context = "work", color = "#7AA2F7", online = true)
        val pc3 = MachineSummary(machineId = "m3", name = "PC 3", context = "server", color = "#E91E63", online = true)

        val agent1 = AgentSummary(agentId = "a1", machineId = "m1", theme = "omarchy-system")
        val agent2 = AgentSummary(agentId = "a2", machineId = "m2", theme = "omarchy-system")
        val agent3 = AgentSummary(agentId = "a3", machineId = "m3", theme = "gentle-sexy")

        val resolved =
            resolveThemeChoice(
                themeSetting = "auto",
                machines = listOf(pc1, pc2, pc3),
                agents = listOf(agent1, agent2, agent3),
            )
        assertEquals(RpThemeChoice.OMARCHY, resolved)
    }

    @Test
    fun `auto sin datos cae a gentle cute por defecto`() {
        assertEquals(RpThemeChoice.GENTLE_CUTE, resolveThemeChoice("auto", emptyList(), emptyList()))
    }
}
