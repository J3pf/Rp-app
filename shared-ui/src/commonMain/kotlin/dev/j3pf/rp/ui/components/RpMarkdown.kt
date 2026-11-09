package dev.j3pf.rp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
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
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = language?.takeIf { it.isNotBlank() } ?: "código",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.outline,
                )
                Row(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                clipboard.setText(AnnotatedString(code.trimEnd()))
                                copied = true
                            }.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = "Copiar código",
                        modifier = Modifier.size(12.dp),
                        tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = if (copied) "Copiado" else "Copiar",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Text(
                text = displayedCode,
                style = style,
                softWrap = false,
                modifier = Modifier.horizontalScroll(scrollState).padding(start = 10.dp, end = 10.dp, bottom = 10.dp, top = 4.dp),
            )
        }
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
                    edgeColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                    edgeColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                MarkdownCodeFence(model.content, model.node) { code, language, style ->
                    HighlightedCode(code, language, style, scrollState = scrollState)
                }
            }
        },
    )

/** Markdown con bloques de código resaltados, tipografía adaptada a burbujas de chat y padding dinámico según spacingMode. */
@Composable
fun RpMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    spacingMode: String = "normal",
    textStyleOverride: TextStyle? = null,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val inlineCodeColor = if (isDark) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.75f else 0.55f)
    val markdownState =
        rememberMarkdownState(
            content = text,
            retainState = true,
            immediate = true,
        )
    val bodyLineHeight =
        when (spacingMode) {
            "compact" -> 17.sp
            "spacious" -> 21.sp
            else -> 19.sp
        }
    val baseBody = textStyleOverride ?: MaterialTheme.typography.bodyMedium.copy(lineHeight = bodyLineHeight)
    val blockPadding =
        when (spacingMode) {
            "compact" -> 0.5.dp
            "spacious" -> 3.dp
            else -> 2.dp
        }
    val listPadding =
        when (spacingMode) {
            "compact" -> 0.dp
            "spacious" -> 2.dp
            else -> 1.dp
        }
    val listItemPadding =
        when (spacingMode) {
            "compact" -> 0.dp
            "spacious" -> 1.5.dp
            else -> 1.dp
        }

    Markdown(
        markdownState = markdownState,
        modifier = modifier,
        colors =
            markdownColor(
                text = baseBody.color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface,
                codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
                inlineCodeBackground = inlineCodeBg,
                dividerColor = MaterialTheme.colorScheme.outlineVariant,
                tableBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        padding =
            markdownPadding(
                block = blockPadding,
                list = listPadding,
                listItemTop = listItemPadding,
                listItemBottom = listItemPadding,
            ),
        typography =
            markdownTypography(
                h1 =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize =
                            when (spacingMode) {
                                "compact" -> 15.sp
                                "spacious" -> 18.sp
                                else -> 17.sp
                            },
                        lineHeight =
                            when (spacingMode) {
                                "compact" -> 18.sp
                                "spacious" -> 22.sp
                                else -> 21.sp
                            },
                    ),
                h2 =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize =
                            when (spacingMode) {
                                "compact" -> 14.sp
                                "spacious" -> 16.sp
                                else -> 15.5.sp
                            },
                        lineHeight =
                            when (spacingMode) {
                                "compact" -> 17.sp
                                "spacious" -> 20.sp
                                else -> 19.sp
                            },
                    ),
                h3 =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize =
                            when (spacingMode) {
                                "compact" -> 13.5.sp
                                "spacious" -> 15.sp
                                else -> 14.5.sp
                            },
                        lineHeight =
                            when (spacingMode) {
                                "compact" -> 16.sp
                                "spacious" -> 19.sp
                                else -> 18.sp
                            },
                    ),
                h4 =
                    MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        lineHeight =
                            when (spacingMode) {
                                "compact" -> 15.sp
                                "spacious" -> 18.sp
                                else -> 17.sp
                            },
                    ),
                h5 =
                    MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    ),
                h6 =
                    MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    ),
                text = baseBody,
                paragraph = baseBody,
                bullet = baseBody,
                ordered = baseBody,
                list = baseBody,
                inlineCode =
                    baseBody.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = (baseBody.fontSize.value * 0.9f).sp,
                        color = inlineCodeColor,
                    ),
                quote = baseBody.copy(fontStyle = FontStyle.Italic),
            ),
        components = rememberRpMarkdownComponents(),
    )
}
