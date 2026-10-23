package dev.j3pf.rp.ui.components

import dev.j3pf.rp.protocol.MachineQuota
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.QuotaWindow
import kotlin.test.Test
import kotlin.test.assertEquals

class QuotaDynamicIslandTest {
    @Test
    fun testFormatQuotaPercent() {
        assertEquals("0%", formatQuotaPercent(0.0))
        assertEquals("100%", formatQuotaPercent(100.0))
        assertEquals("75.5%", formatQuotaPercent(75.5))
        assertEquals("12.3%", formatQuotaPercent(12.34))
        assertEquals("99.9%", formatQuotaPercent(99.9))
    }

    @Test
    fun testMachineSummaryWithQuotas() {
        val window =
            QuotaWindow(
                id = "5h",
                label = "5 Hours",
                usedPercent = 42.5,
                remainingPercent = 57.5,
                resetsInSec = 3600L,
            )
        val quota =
            MachineQuota(
                family = "claude",
                label = "Claude 3.7",
                ok = true,
                primaryWindowId = "5h",
                windows = listOf(window),
            )
        val machine =
            MachineSummary(
                machineId = "m1",
                name = "Mac Studio",
                color = "#4A90E2",
                online = true,
                quotas = listOf(quota),
            )

        assertEquals("m1", machine.machineId)
        assertEquals(1, machine.quotas?.size)
        val firstQuota = machine.quotas?.firstOrNull()
        val firstWindow = firstQuota?.windows?.firstOrNull()
        assertEquals(42.5, firstWindow?.usedPercent)
    }
}
