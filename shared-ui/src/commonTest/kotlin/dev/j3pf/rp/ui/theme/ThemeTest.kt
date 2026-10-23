package dev.j3pf.rp.ui.theme

import dev.j3pf.rp.protocol.MachineSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ThemeTest {
    @Test
    fun testDefaultTokensAreValid() {
        assertNotNull(RpSpacing.screenPadding)
        assertNotNull(RpShapes.card)
        assertNotNull(RpColors.danger)
    }

    @Test
    fun testResolveThemeChoiceExplicit() {
        val choice = resolveThemeChoice("gentle-sexy", emptyList(), emptyList())
        assertEquals(RpThemeChoice.GENTLE_SEXY, choice)
    }

    @Test
    fun testResolveThemeChoicePersonalMachinePrecedence() {
        val m1 =
            MachineSummary(
                machineId = "m1",
                name = "Work Machine",
                context = "work",
                online = true,
                color = "0xFF3D59A1",
            )
        val m2 =
            MachineSummary(
                machineId = "m2",
                name = "Personal Machine",
                context = "personal",
                online = true,
                color = "0xFFE91E63",
            )
        val choice = resolveThemeChoice("auto", listOf(m1, m2), emptyList())
        assertEquals(RpThemeChoice.GENTLE_SEXY, choice)
    }
}
