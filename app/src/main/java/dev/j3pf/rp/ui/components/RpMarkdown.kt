package dev.j3pf.rp.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.model.rememberMarkdownState
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalHighlightDispatcher = compositionLocalOf<CoroutineDispatcher> { Dispatchers.Default }

@Composable
fun HighlightedCode(
    code: String,
    language: String?,
    style: TextStyle,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val dispatcher = LocalHighlightDispatcher.current
    val annotatedString by
        produceState(
            initialValue = buildAnnotatedString { append(code) },
            code,
            language,
            isDark,
            dispatcher,
        ) {
            value =
                withContext(dispatcher) {
                    val syntaxLanguage = language?.let { SyntaxLanguage.getByName(it) }
                    val highlights =
                        Highlights
                            .Builder()
                            .code(code)
                            .theme(SyntaxThemes.default(darkMode = isDark))
                            .apply { if (syntaxLanguage != null) language(syntaxLanguage) }
                            .build()
                            .getHighlights()

                    buildAnnotatedString {
                        append(code)
                        highlights.forEach { highlight ->
                            when (highlight) {
                                is ColorHighlight -> {
                                    addStyle(
                                        SpanStyle(
                                            color = Color(0xFF000000.toInt() or (highlight.rgb and 0x00FFFFFF)),
                                        ),
                                        highlight.location.start,
                                        highlight.location.end,
                                    )
                                }

                                is BoldHighlight -> {
                                    addStyle(
                                        SpanStyle(fontWeight = FontWeight.Bold),
                                        highlight.location.start,
                                        highlight.location.end,
                                    )
                                }
                            }
                        }
                    }
                }
        }

    val displayedCode = if (annotatedString.text == code) annotatedString else AnnotatedString(code)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = displayedCode,
            style = style,
            softWrap = false,
            modifier = Modifier.horizontalScroll(scrollState).padding(10.dp),
        )
    }
}

@Composable
private fun rememberRpMarkdownComponents() =
    markdownComponents(
        codeBlock = { model ->
            val scrollState = rememberScrollState()
            Box(
                Modifier.horizontalEdgeFade(
                    scrollState,
                    edgeColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                MarkdownCodeBlock(model.content, model.node) { code, language, style ->
                    HighlightedCode(code, language, style, scrollState = scrollState)
                }
            }
        },
        codeFence = { model ->
            val scrollState = rememberScrollState()
            Box(
                Modifier.horizontalEdgeFade(
                    scrollState,
                    edgeColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                MarkdownCodeFence(model.content, model.node) { code, language, style ->
                    HighlightedCode(code, language, style, scrollState = scrollState)
                }
            }
        },
    )

/** Markdown con bloques de código resaltados, interlineado amplio y renderizado optimizado para streaming. */
@Composable
fun RpMarkdown(
    text: String,
    modifier: Modifier = Modifier,
) {
    val markdownState =
        rememberMarkdownState(
            content = text,
            retainState = true,
            immediate = true,
        )
    val baseBody = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
    Markdown(
        markdownState = markdownState,
        modifier = modifier,
        padding =
            markdownPadding(
                block = 8.dp,
                list = 6.dp,
                listItemTop = 3.dp,
                listItemBottom = 3.dp,
            ),
        typography =
            markdownTypography(
                text = baseBody,
                paragraph = baseBody,
                bullet = baseBody,
                ordered = baseBody,
                list = baseBody,
            ),
        components = rememberRpMarkdownComponents(),
    )
}
