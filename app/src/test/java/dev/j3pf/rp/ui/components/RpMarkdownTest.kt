package dev.j3pf.rp.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.j3pf.rp.ui.theme.RpTheme
import dev.j3pf.rp.ui.theme.RpThemeChoice
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.coroutines.CoroutineContext

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

    @Test
    fun `latest streamed code is displayed while its highlight work is pending`() {
        val currentCode = mutableStateOf("previous code")
        val latestCode = "latest streamed code"
        val pausedDispatcher = PausedDispatcher()

        rule.setContent {
            CompositionLocalProvider(LocalHighlightDispatcher provides pausedDispatcher) {
                RpTheme {
                    HighlightedCode(currentCode.value, "kotlin", TextStyle.Default)
                }
            }
        }
        rule.waitForIdle()

        rule.runOnIdle { currentCode.value = latestCode }
        rule.waitForIdle()

        val displayedText =
            rule
                .onNodeWithText(latestCode)
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .single()
                .text
        assertEquals(latestCode, displayedText)
    }

    @Test
    fun `long code lines stay on one line in a horizontally scrollable viewport`() {
        val code = "long_line_".repeat(24)
        rule.setContent {
            CompositionLocalProvider(LocalHighlightDispatcher provides Dispatchers.Unconfined) {
                RpTheme {
                    HighlightedCode(
                        code = code,
                        language = null,
                        style = TextStyle(fontSize = 16.sp),
                        modifier = Modifier.width(160.dp),
                    )
                }
            }
        }
        rule.waitForIdle()

        val textNode = rule.onNodeWithText(code).fetchSemanticsNode()
        val textLayoutResults = mutableListOf<TextLayoutResult>()
        textNode.config
            .getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(textLayoutResults)
        assertEquals(1, textLayoutResults.single().lineCount)
        assertTrue(textNode.config.contains(SemanticsProperties.HorizontalScrollAxisRange))
    }

    private class PausedDispatcher : CoroutineDispatcher() {
        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) = Unit
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
