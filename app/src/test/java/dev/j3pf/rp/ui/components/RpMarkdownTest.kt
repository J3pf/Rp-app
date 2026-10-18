package dev.j3pf.rp.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.j3pf.rp.ui.theme.RpTheme
import dev.j3pf.rp.ui.theme.RpThemeChoice
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "notnight")
class RpMarkdownTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `dark app theme uses dark syntax palette while system is light`() {
        val code = "val answer = 42"
        val darkPalette = colorHighlights(code, darkMode = true)
        val lightPalette = colorHighlights(code, darkMode = false)

        assertTrue("Expected Kotlin syntax colors", darkPalette.isNotEmpty())
        assertNotEquals("Dark and light syntax palettes must differ", darkPalette, lightPalette)

        rule.setContent {
            CompositionLocalProvider(LocalHighlightDispatcher provides Dispatchers.Unconfined) {
                RpTheme(themeChoice = RpThemeChoice.OMARCHY, darkTheme = true) {
                    RpMarkdown("```kotlin\n$code\n```")
                }
            }
        }
        rule.waitForIdle()

        val renderedCode =
            rule
                .onNodeWithText(code, substring = true)
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .single()
        val renderedColors =
            renderedCode.spanStyles
                .mapNotNull { it.item.color.takeUnless { color -> color == Color.Unspecified } }

        assertEquals(darkPalette, renderedColors)
    }

    private fun colorHighlights(
        code: String,
        darkMode: Boolean,
    ): List<Color> {
        val highlights =
            Highlights
                .Builder()
                .code(code)
                .theme(SyntaxThemes.default(darkMode = darkMode))
                .language(SyntaxLanguage.KOTLIN)
                .build()
                .getHighlights()
        return highlights.filterIsInstance<ColorHighlight>().map { Color(it.rgb) }
    }
}
