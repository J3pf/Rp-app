package dev.j3pf.rp.ui

import androidx.compose.ui.graphics.Color
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.ui.theme.CuteDarkColors
import dev.j3pf.rp.ui.theme.CuteLightColors
import dev.j3pf.rp.ui.theme.DefaultDarkColors
import dev.j3pf.rp.ui.theme.DefaultLightColors
import dev.j3pf.rp.ui.theme.OmarchyDarkColors
import dev.j3pf.rp.ui.theme.OmarchyLightColors
import dev.j3pf.rp.ui.theme.RpColors
import dev.j3pf.rp.ui.theme.RpThemeChoice
import dev.j3pf.rp.ui.theme.SexyDarkColors
import dev.j3pf.rp.ui.theme.SexyLightColors
import dev.j3pf.rp.ui.theme.paletteFor
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

    @Test
    fun `paleta cute preserva colores primary background surface exactos`() {
        assertEquals(Color(0xFFD81B60), CuteLightColors.primary)
        assertEquals(Color(0xFFFFF7F9), CuteLightColors.background)
        assertEquals(Color(0xFFFFF0F5), CuteLightColors.surface)

        assertEquals(Color(0xFFFF80AB), CuteDarkColors.primary)
        assertEquals(Color(0xFF1E171E), CuteDarkColors.background)
        assertEquals(Color(0xFF281E29), CuteDarkColors.surface)
    }

    @Test
    fun `paleta sexy preserva colores primary background surface exactos`() {
        assertEquals(Color(0xFFC2185B), SexyLightColors.primary)
        assertEquals(Color(0xFFFDF2F8), SexyLightColors.background)
        assertEquals(Color(0xFFFCE7F3), SexyLightColors.surface)

        assertEquals(Color(0xFFFF2A85), SexyDarkColors.primary)
        assertEquals(Color(0xFF120815), SexyDarkColors.background)
        assertEquals(Color(0xFF1B0D20), SexyDarkColors.surface)
    }

    @Test
    fun `paleta omarchy preserva colores primary background surface exactos`() {
        assertEquals(Color(0xFF3D59A1), OmarchyLightColors.primary)
        assertEquals(Color(0xFFF5F6FA), OmarchyLightColors.background)
        assertEquals(Color(0xFFE8EBF5), OmarchyLightColors.surface)

        assertEquals(Color(0xFF7AA2F7), OmarchyDarkColors.primary)
        assertEquals(Color(0xFF1A1B26), OmarchyDarkColors.background)
        assertEquals(Color(0xFF232431), OmarchyDarkColors.surface)
    }

    @Test
    fun `paleta default preserva colores primary background surface exactos`() {
        assertEquals(Color(0xFF4F378B), DefaultLightColors.primary)
        assertEquals(Color(0xFFFEF7FF), DefaultLightColors.background)
        assertEquals(Color(0xFFFEF7FF), DefaultLightColors.surface)

        assertEquals(Color(0xFFCFBCFF), DefaultDarkColors.primary)
        assertEquals(DefaultDarkColors.background, paletteFor(RpThemeChoice.SYSTEM, darkTheme = true).background)
        assertEquals(DefaultDarkColors.surface, paletteFor(RpThemeChoice.SYSTEM, darkTheme = true).surface)
    }

    @Test
    fun `colores semanticos RpColors preservados`() {
        assertEquals(Color(0xFFB3261E), RpColors.danger)
        assertEquals(Color(0xFFB26A00), RpColors.warning)
        assertEquals(Color(0xFF2E7D32), RpColors.success)
    }

    @Test
    fun `paletteFor entrega la paleta correspondiente segun eleccion y modo oscuro`() {
        assertEquals(CuteLightColors, paletteFor(RpThemeChoice.GENTLE_CUTE, darkTheme = false))
        assertEquals(CuteDarkColors, paletteFor(RpThemeChoice.GENTLE_CUTE, darkTheme = true))
        assertEquals(SexyLightColors, paletteFor(RpThemeChoice.GENTLE_SEXY, darkTheme = false))
        assertEquals(SexyDarkColors, paletteFor(RpThemeChoice.GENTLE_SEXY, darkTheme = true))
        assertEquals(OmarchyLightColors, paletteFor(RpThemeChoice.OMARCHY, darkTheme = false))
        assertEquals(OmarchyDarkColors, paletteFor(RpThemeChoice.OMARCHY, darkTheme = true))
        assertEquals(DefaultLightColors, paletteFor(RpThemeChoice.SYSTEM, darkTheme = false))
        assertEquals(DefaultDarkColors, paletteFor(RpThemeChoice.SYSTEM, darkTheme = true))
    }

    @Test
    fun `tokens de skeleton ofrecen contraste adecuado en temas oscuros`() {
        val darkPalettes = listOf(CuteDarkColors, SexyDarkColors, OmarchyDarkColors, DefaultDarkColors)
        darkPalettes.forEach { palette ->
            val base =
                androidx.compose.ui.graphics
                    .lerp(palette.surfaceVariant, palette.onSurface, 0.14f)
            val highlight =
                androidx.compose.ui.graphics
                    .lerp(palette.surfaceVariant, palette.onSurface, 0.28f)
            org.junit.Assert.assertNotEquals("Base de skeleton no debe coincidir con surfaceVariant", palette.surfaceVariant, base)
            org.junit.Assert.assertNotEquals("Highlight de skeleton debe distinguirse de la base", base, highlight)
        }
    }
}
