package dev.j3pf.rp.ui.settings

import dev.j3pf.rp.data.repo.NotifyCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsModelsTest {
    @Test
    fun healthStateDefaults() {
        val defaultHealth = HealthState()
        assertTrue(defaultHealth.notificationsEnabled)
        assertFalse(defaultHealth.approvalsMuted)
        assertFalse(defaultHealth.batteryExcluded)
    }

    @Test
    fun settingsUiStateDefaults() {
        val state = SettingsUiState()
        assertNull(state.session)
        assertTrue(state.machines.isEmpty())
        assertTrue(state.agents.isEmpty())
        assertEquals("auto", state.settings.appTheme)
    }

    @Test
    fun themeLabelFormatting() {
        assertEquals("Sincronizado con Pi (automático)", themeLabel("auto"))
        assertEquals("Gentle-Cute", themeLabel("gentle-cute"))
        assertEquals("Material You / Sistema", themeLabel("system"))
        assertEquals("Sincronizado con Pi (automático)", themeLabel("unknown"))
    }

    @Test
    fun fontScaleLabelFormatting() {
        assertEquals("Pequeño (85%)", fontScaleLabel(0.85f))
        assertEquals("Normal (100%)", fontScaleLabel(1.00f))
        assertEquals("Grande (115%)", fontScaleLabel(1.15f))
        assertEquals("Extra grande (130%)", fontScaleLabel(1.30f))
    }

    @Test
    fun messageSpacingLabelFormatting() {
        assertEquals("Compacto", messageSpacingLabel("compact"))
        assertEquals("Amplio", messageSpacingLabel("spacious"))
        assertEquals("Normal", messageSpacingLabel("normal"))
        assertEquals("Normal", messageSpacingLabel("unknown"))
    }

    @Test
    fun healthSummaryMessages() {
        val disabled = HealthState(notificationsEnabled = false)
        assertEquals("⚠ Notificaciones desactivadas", healthSummary(disabled))

        val muted = HealthState(notificationsEnabled = true, approvalsMuted = true)
        assertEquals("⚠ Canal de autorizaciones silenciado", healthSummary(muted))

        val ok = HealthState(notificationsEnabled = true, approvalsMuted = false)
        assertEquals("Categorías, PCs, horas silenciosas, prueba", healthSummary(ok))
    }

    @Test
    fun categoryLabelTranslations() {
        assertEquals("Autorizaciones", categoryLabel(NotifyCategory.APPROVALS))
        assertEquals("Preguntas", categoryLabel(NotifyCategory.QUESTIONS))
        assertEquals("Terminal", categoryLabel(NotifyCategory.TERMINAL))
        assertEquals("Terminó", categoryLabel(NotifyCategory.FINISHED))
        assertEquals("Errores", categoryLabel(NotifyCategory.ERRORS))
        assertEquals("Errores", categoryLabel("custom"))
    }
}
