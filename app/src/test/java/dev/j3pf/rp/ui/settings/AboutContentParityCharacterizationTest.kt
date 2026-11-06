package dev.j3pf.rp.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.j3pf.rp.ui.theme.RpTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SE-14 (T1a rev3, Gate A): informações de la pantalla Acerca de sin tocar producción.
 * `BuildConfig.VERSION_NAME/VERSION_CODE/FLAVOR` los inyecta SettingsScreens.kt:261-266;
 * esta prueba fija el renderizado exacto para cualquier valor que se pase.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AboutContentParityCharacterizationTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `SE-14 about screen renders version, push flavor and licenses header`() {
        rule.setContent {
            RpTheme {
                AboutContent(
                    versionName = "0.6.1",
                    versionCode = "61",
                    flavor = "direct",
                    onBack = {},
                )
            }
        }

        rule.onNodeWithText("Acerca de").assertIsDisplayed()
        rule.onNodeWithText("Versión").assertIsDisplayed()
        rule.onNodeWithText("0.6.1 (61)").assertIsDisplayed()
        rule.onNodeWithText("Sabor de push").assertIsDisplayed()
        rule.onNodeWithText("direct").assertIsDisplayed()
        rule.onNodeWithText("Licencias").assertIsDisplayed()
    }
}
