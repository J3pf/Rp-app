package dev.j3pf.rp.ui.newagent

import dev.j3pf.rp.protocol.HostBackends
import dev.j3pf.rp.protocol.HostInfo
import dev.j3pf.rp.protocol.MachineSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NewAgentModelsTest {
    @Test
    fun backendLabels() {
        assertEquals("herdr", backendLabel("herdr"))
        assertEquals("tmux", backendLabel("tmux"))
        assertEquals("Windows Terminal", backendLabel("wt"))
        assertEquals("Consola", backendLabel("console"))
        assertEquals("custom", backendLabel("custom"))
    }

    @Test
    fun newAgentUiHasHerdr() {
        val noHost = NewAgentUi()
        assertFalse(noHost.hasHerdr)

        val hostWithoutHerdr =
            NewAgentUi(
                host =
                    HostInfo(
                        machineId = "m1",
                        backends = HostBackends(wt = true),
                    ),
            )
        assertFalse(hostWithoutHerdr.hasHerdr)

        val hostWithHerdr =
            NewAgentUi(
                host =
                    HostInfo(
                        machineId = "m1",
                        backends = HostBackends(herdr = "1.0.0"),
                    ),
            )
        assertTrue(hostWithHerdr.hasHerdr)
    }

    @Test
    fun newAgentUiBackendsList() {
        val backends =
            HostBackends(
                herdr = "1.0.0",
                tmux = "3.2",
                wt = true,
                console = false,
            )
        val ui =
            NewAgentUi(
                host =
                    HostInfo(
                        machineId = "m1",
                        backends = backends,
                    ),
            )
        assertEquals(listOf("herdr", "tmux", "wt"), ui.backends)
    }

    @Test
    fun newAgentUiEffectiveBackendFallback() {
        val ui =
            NewAgentUi(
                host =
                    HostInfo(
                        machineId = "m1",
                        defaultBackend = "wt",
                        backends = HostBackends(wt = true, console = true),
                    ),
                form = NewAgentForm(backend = null),
            )
        assertEquals("wt", ui.effectiveBackend)

        val overridden = ui.copy(form = ui.form.copy(backend = "console"))
        assertEquals("console", overridden.effectiveBackend)
    }

    @Test
    fun newAgentUiCanCreateValidation() {
        val baseForm =
            NewAgentForm(
                machineId = "m1",
                path = "/home/user/project",
                spawning = false,
            )
        val ui =
            NewAgentUi(
                machines = listOf(MachineSummary(machineId = "m1", name = "PC")),
                host =
                    HostInfo(
                        machineId = "m1",
                        defaultBackend = "tmux",
                        backends = HostBackends(tmux = "3.2"),
                    ),
                form = baseForm,
            )

        assertTrue(ui.canCreate)

        val noMachine = ui.copy(form = baseForm.copy(machineId = null))
        assertFalse(noMachine.canCreate)

        val noPath = ui.copy(form = baseForm.copy(path = ""))
        assertFalse(noPath.canCreate)

        val spawning = ui.copy(form = baseForm.copy(spawning = true))
        assertFalse(spawning.canCreate)

        // Herdr validations
        val herdrUi =
            ui.copy(
                host =
                    HostInfo(
                        machineId = "m1",
                        backends = HostBackends(herdr = "1.0.0"),
                    ),
                form = baseForm.copy(backend = "herdr"),
            )
        val newWs = herdrUi.copy(form = herdrUi.form.copy(placement = HerdrPlacement.NEW_WORKSPACE))
        assertTrue(newWs.canCreate)

        val tabNoWs = herdrUi.copy(form = herdrUi.form.copy(placement = HerdrPlacement.TAB, workspaceId = null))
        assertFalse(tabNoWs.canCreate)

        val tabWithWs = herdrUi.copy(form = herdrUi.form.copy(placement = HerdrPlacement.TAB, workspaceId = "ws-1"))
        assertTrue(tabWithWs.canCreate)

        val splitNoPane = herdrUi.copy(form = herdrUi.form.copy(placement = HerdrPlacement.SPLIT, paneId = null))
        assertFalse(splitNoPane.canCreate)

        val splitWithPane = herdrUi.copy(form = herdrUi.form.copy(placement = HerdrPlacement.SPLIT, paneId = "p-1"))
        assertTrue(splitWithPane.canCreate)
    }
}
