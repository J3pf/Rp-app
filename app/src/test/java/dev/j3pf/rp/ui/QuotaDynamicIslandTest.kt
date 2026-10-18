package dev.j3pf.rp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import dev.j3pf.rp.protocol.MachineQuota
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.QuotaWindow
import dev.j3pf.rp.ui.components.QuotaDynamicIsland
import dev.j3pf.rp.ui.theme.RpTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuotaDynamicIslandTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `isla no se renderiza cuando ninguna maquina tiene cuotas (quirurgico - tolerancia)`() {
        val machines =
            listOf(
                machine("sin-cuotas", "Sin cuotas", quotas = null),
                machine("lista-vacia", "Lista vacía", quotas = emptyList()),
            )

        rule.setContent { RpTheme { QuotaDynamicIsland(machines) } }

        rule.onAllNodesWithText("Claude", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("%", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Sin cuotas").assertCountEquals(0)
        rule.onAllNodesWithText("Lista vacía").assertCountEquals(0)
    }

    @Test
    fun `isla no se renderiza cuando los proveedores estan marcados como notConfigured`() {
        val machine =
            machine(
                "sin-proveedor-configurado",
                "Máquina sin proveedor configurado",
                quotas = listOf(claudeQuota().copy(notConfigured = true)),
            )

        rule.setContent { RpTheme { QuotaDynamicIsland(listOf(machine)) } }

        rule.onAllNodesWithText("Claude", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("%", substring = true).assertCountEquals(0)
    }

    @Test
    fun `isla renderiza pills con nombre de proveedor y porcentaje cuando hay cuotas`() {
        val machine = machine("claude-machine", "Máquina Claude", quotas = listOf(claudeQuota(usedPercent = 15.0)))

        rule.setContent { RpTheme { QuotaDynamicIsland(listOf(machine)) } }

        rule.onNodeWithText("Claude").assertIsDisplayed()
        rule.onNodeWithText("15%").assertIsDisplayed()
    }

    @Test
    fun `pulsar una pill abre el modal con desglose de ventanas y tiempo de reseteo`() {
        val machine = machine("claude-machine", "Máquina Claude", quotas = listOf(claudeQuota()))

        rule.setContent { RpTheme { QuotaDynamicIsland(listOf(machine)) } }
        rule.onNodeWithText("Claude").performClick()

        rule.onAllNodesWithText("Máquina Claude").assertCountEquals(2)
        rule.onNodeWithText("5h window").assertIsDisplayed()
        rule.onNodeWithText("Usado 14.5% · Disponible 85.5%").assertIsDisplayed()
        rule.onNodeWithText("Resetea en 1h 30m").assertIsDisplayed()
    }

    @Test
    fun `soporta multiples maquinas con cuotas`() {
        val machines =
            listOf(
                machine("machine-one", "Máquina Uno", quotas = listOf(claudeQuota())),
                machine("machine-two", "Máquina Dos", quotas = listOf(claudeQuota(label = "Claude Dos"))),
            )

        val selectedMachineId = mutableStateOf<String?>(null)
        rule.setContent { RpTheme { QuotaDynamicIsland(machines, selectedMachineId = selectedMachineId.value) } }

        rule.onNodeWithText("Máquina Uno").assertIsDisplayed()
        rule.onNodeWithText("Claude").assertIsDisplayed()
        rule.runOnIdle { selectedMachineId.value = "machine-two" }
        rule.waitForIdle()
        rule.onNodeWithText("Máquina Dos").assertIsDisplayed()
        rule.onNodeWithText("Claude Dos").assertIsDisplayed()
    }

    @Test
    fun `deslizar hacia la izquierda cambia a la siguiente maquina con cuotas (S4)`() {
        val machines =
            listOf(
                machine("machine-one", "Máquina Uno", quotas = listOf(claudeQuota())),
                machine("machine-two", "Máquina Dos", quotas = listOf(claudeQuota(label = "Claude Dos"))),
            )

        rule.setContent { RpTheme { QuotaDynamicIsland(machines) } }

        rule.onNodeWithText("Máquina Uno").assertIsDisplayed()
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText("Máquina Dos").assertIsDisplayed()
        rule.onNodeWithText("Claude Dos").assertIsDisplayed()
    }

    private fun machine(
        id: String,
        name: String,
        quotas: List<MachineQuota>?,
    ) = MachineSummary(
        machineId = id,
        name = name,
        online = true,
        quotas = quotas,
    )

    private fun claudeQuota(
        label: String = "Claude",
        usedPercent: Double = 14.5,
    ) = MachineQuota(
        family = "claude",
        label = label,
        primaryWindowId = "five-hour-window",
        windows =
            listOf(
                QuotaWindow(
                    id = "five-hour-window",
                    label = "5h window",
                    usedPercent = usedPercent,
                    remainingPercent = 100.0 - usedPercent,
                    resetsInSec = 5_400,
                ),
            ),
    )
}
