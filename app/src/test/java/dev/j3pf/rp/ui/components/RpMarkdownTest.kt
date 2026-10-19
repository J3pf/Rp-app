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
    fun `highlighted annotated string uses opaque colors for every colored span`() {
        val code = "suspend fun answer() { return 42 }"
        rule.setContent {
            CompositionLocalProvider(LocalHighlightDispatcher provides Dispatchers.Unconfined) {
                RpTheme {
                    HighlightedCode(code, "kotlin", TextStyle.Default)
                }
            }
        }
        rule.waitForIdle()

        val annotatedCode =
            rule
                .onNodeWithText(code, substring = true)
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .single()
        val coloredSpans = annotatedCode.spanStyles.filter { it.item.color != Color.Unspecified }

        assertTrue("Expected Kotlin syntax-highlighted spans", coloredSpans.isNotEmpty())
        coloredSpans.forEach { span ->
            assertEquals("Span ${span.start}..${span.end} should be opaque", 1f, span.item.color.alpha, 0f)
        }
    }

    @Test
    fun `Kotlin keywords render with opaque syntax colors in markdown`() {
        val code = "suspend fun answer() {\n    return 42\n}"
        rule.setContent {
            CompositionLocalProvider(LocalHighlightDispatcher provides Dispatchers.Unconfined) {
                RpTheme {
                    RpMarkdown("```kotlin\n$code\n```")
                }
            }
        }
        rule.waitForIdle()

        val renderedCode =
            rule
                .onNodeWithText("suspend", substring = true)
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .single()
        listOf("suspend", "fun", "return").forEach { keyword ->
            val keywordSpans =
                renderedCode.spanStyles.filter { span ->
                    renderedCode.text.substring(span.start, span.end) == keyword &&
                        span.item.color != Color.Unspecified
                }
            assertTrue("Expected a colored span for '$keyword'", keywordSpans.isNotEmpty())
            keywordSpans.forEach { span ->
                assertEquals("'$keyword' should have an opaque color", 1f, span.item.color.alpha, 0f)
                assertNotEquals("'$keyword' should not be transparent", Color.Transparent, span.item.color)
            }
        }
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

    @Test
    fun `markdown renders headings and paragraphs across compact and spacious spacing modes`() {
        val markdownText = "# Encabezado 1\n## Encabezado 2\nTexto del párrafo normal con detalles."

        rule.setContent {
            CompositionLocalProvider(LocalHighlightDispatcher provides Dispatchers.Unconfined) {
                RpTheme {
                    RpMarkdown(markdownText, spacingMode = "compact")
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("Encabezado 1", substring = true).fetchSemanticsNode()
        rule.onNodeWithText("Encabezado 2", substring = true).fetchSemanticsNode()
        rule.onNodeWithText("Texto del párrafo normal", substring = true).fetchSemanticsNode()
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
        return highlights
            .filterIsInstance<ColorHighlight>()
            .map { Color(0xFF000000.toInt() or (it.rgb and 0x00FFFFFF)) }
    }
}
