package dev.j3pf.rp.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.j3pf.rp.protocol.DeviceInfo
import dev.j3pf.rp.ui.theme.RpTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class DevicesScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `devices list renders device names and este badge for current device`() {
        var revokedId: String? = null
        val devices =
            listOf(
                DeviceInfo(
                    deviceId = "dev-current",
                    name = "Pixel 8 Pro",
                    createdAt = 1000L,
                    lastSeenAt = 2000L,
                    current = true,
                ),
                DeviceInfo(
                    deviceId = "dev-other",
                    name = "Galaxy Tab S9",
                    createdAt = 1000L,
                    lastSeenAt = 1500L,
                    current = false,
                ),
            )

        rule.setContent {
            RpTheme {
                DevicesContent(
                    devices = devices,
                    onBack = {},
                    onRevoke = { revokedId = it },
                )
            }
        }

        // El dispositivo actual debe mostrar su nombre con " (este)" y NO tener botón de revocar
        rule.onNodeWithText("Pixel 8 Pro (este)").assertIsDisplayed()

        // El otro dispositivo debe mostrar su nombre y el botón "Revocar"
        rule.onNodeWithText("Galaxy Tab S9").assertIsDisplayed()
        rule.onNodeWithText("Revocar").assertIsDisplayed().performClick()
        rule.waitForIdle()

        // El diálogo de confirmación debe abrirse con opción para revocar
        rule.onNodeWithText("Revocar dispositivo").assertIsDisplayed()
        rule.onAllNodes(hasText("Revocar"))[1].performClick()
        rule.waitForIdle()
        assertEquals("dev-other", revokedId)
    }
}
