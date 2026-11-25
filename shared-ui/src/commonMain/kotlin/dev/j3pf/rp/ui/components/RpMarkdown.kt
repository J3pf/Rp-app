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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
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
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.model.rememberMarkdownState
import dev.j3pf.rp.ui.util.currentTimeMillis
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

val LocalHighlightDispatcher = compositionLocalOf<CoroutineDispatcher> { Dispatchers.Default }

internal const val MAX_HIGHLIGHT_CODE_LENGTH = 32 * 1024
internal const val MAX_IMMEDIATE_MARKDOWN_LENGTH = 2_000
internal const val STREAMING_MARKDOWN_DEBOUNCE_MS = 150L

internal fun isAllowedMarkdownUri(uri: String): Boolean {
    val trimmed = uri.trim()
    return trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("http://", ignoreCase = true)
}

internal fun buildHighlightedAnnotatedString(
    code: String,
    language: String?,
    isDark: Boolean,
): AnnotatedString {
    if (code.length > MAX_HIGHLIGHT_CODE_LENGTH) {
        return AnnotatedString(code)
    }
    return try {
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
            val len = code.length
            highlights.forEach { highlight ->
                val start = highlight.location.start.coerceIn(0, len)
                val end = highlight.location.end.coerceIn(0, len)
                if (start >= end) return@forEach
                when (highlight) {
                    is ColorHighlight -> {
                        addStyle(
                            SpanStyle(
                                color = Color(0xFF000000.toInt() or (highlight.rgb and 0x00FFFFFF)),
                            ),
                            start,
                            end,
                        )
                    }

                    is BoldHighlight -> {
                        addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold),
                            start,
                            end,
                        )
                    }
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        AnnotatedString(code)
    }
}

@Composable
fun HighlightedCode(
    code: String,
    language: String?,
    style: TextStyle,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    isStreaming: Boolean = false,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val dispatcher = LocalHighlightDispatcher.current
    val skipHighlight = isStreaming && dispatcher !== Dispatchers.Unconfined
    val plainCode = remember(code) { AnnotatedString(code) }
    val highlightPassCount =
        remember {
            object {
                var count = 0
            }
        }
    val annotatedString by
        produceState(
            initialValue = plainCode,
            code,
            language,
            isDark,
            dispatcher,
            skipHighlight,
        ) {
            if (skipHighlight || code.length > MAX_HIGHLIGHT_CODE_LENGTH) {
                value = plainCode
                return@produceState
            }
            if (dispatcher === Dispatchers.Default && highlightPassCount.count > 0) {
                delay(120L)
            }
            highlightPassCount.count++
            value =
                withContext(dispatcher) {
                    buildHighlightedAnnotatedString(
                        code = code,
                        language = language,
                        isDark = isDark,
                    )
                }
        }

    val displayedCode = if (annotatedString.text == code) annotatedString else plainCode
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

private class MarkdownStyleCache {
    var colorsScheme: ColorScheme? = null
    var colorsText: Color = Color.Unspecified
    var colorsIsDark: Boolean = false
    var colors: MarkdownColors? = null

    var paddingSpacingMode: String? = null
    var padding: MarkdownPadding? = null

    var typoMaterial: Typography? = null
    var typoSpacingMode: String? = null
    var typoOverride: TextStyle? = null
    var typoInlineCodeColor: Color = Color.Unspecified
    var typography: MarkdownTypography? = null
}

private class BooleanHolder(
    var value: Boolean = false,
)

@Composable
private fun rememberRpMarkdownComponents(isStreaming: Boolean) =
    remember(isStreaming) {
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
                        HighlightedCode(
                            code = code,
                            language = language,
                            style = style,
                            scrollState = scrollState,
                            isStreaming = isStreaming,
                        )
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
                        HighlightedCode(
                            code = code,
                            language = language,
                            style = style,
                            scrollState = scrollState,
                            isStreaming = isStreaming,
                        )
                    }
                }
            },
        )
    }

/** Markdown con bloques de código resaltados, tipografía adaptada a burbujas de chat y padding dinámico según spacingMode. */
@Composable
fun RpMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    spacingMode: String = "normal",
    textStyleOverride: TextStyle? = null,
    isStreaming: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val m3Typography = MaterialTheme.typography
    val isDark = remember(colorScheme.surface) { colorScheme.surface.luminance() < 0.5f }
    val inlineCodeColor = if (isDark) colorScheme.tertiary else colorScheme.primary
    val inlineCodeBg =
        remember(colorScheme.surfaceVariant, isDark) {
            colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.75f else 0.55f)
        }
    val dispatcher = LocalHighlightDispatcher.current
    val renderedInitialStreaming = remember { BooleanHolder() }

    val effectiveText =
        if (!isStreaming || dispatcher === Dispatchers.Unconfined) {
            text
        } else {
            val latestText by rememberUpdatedState(text)
            var debouncedText by remember { mutableStateOf(text) }
            var lastEmitMs by remember { mutableLongStateOf(if (text.isNotEmpty()) currentTimeMillis() else 0L) }
            LaunchedEffect(text, isStreaming) {
                if (debouncedText.isEmpty() && latestText.isNotEmpty()) {
                    debouncedText = latestText
                    lastEmitMs = currentTimeMillis()
                    return@LaunchedEffect
                }
                val elapsed = currentTimeMillis() - lastEmitMs
                val remaining = STREAMING_MARKDOWN_DEBOUNCE_MS - elapsed
                if (remaining > 0L) {
                    delay(remaining)
                }
                debouncedText = latestText
                lastEmitMs = currentTimeMillis()
            }
            debouncedText
        }

    val parseImmediate =
        dispatcher === Dispatchers.Unconfined ||
            (!isStreaming && effectiveText.length <= MAX_IMMEDIATE_MARKDOWN_LENGTH) ||
            (isStreaming && !renderedInitialStreaming.value)

    SideEffect {
        renderedInitialStreaming.value = isStreaming && effectiveText.isNotEmpty()
    }

    val markdownState =
        rememberMarkdownState(
            content = effectiveText,
            retainState = true,
            immediate = parseImmediate,
        )
    val bodyLineHeight =
        when (spacingMode) {
            "compact" -> 17.sp
            "spacious" -> 21.sp
            else -> 19.sp
        }
    val baseBody =
        remember(m3Typography.bodyMedium, bodyLineHeight, textStyleOverride) {
            textStyleOverride ?: m3Typography.bodyMedium.copy(lineHeight = bodyLineHeight)
        }
    val styleCache = remember { MarkdownStyleCache() }
    val resolvedTextColor = baseBody.color.takeIf { it != Color.Unspecified } ?: colorScheme.onSurface
    val colors =
        if (styleCache.colors != null &&
            styleCache.colorsScheme == colorScheme &&
            styleCache.colorsText == resolvedTextColor &&
            styleCache.colorsIsDark == isDark
        ) {
            styleCache.colors!!
        } else {
            markdownColor(
                text = resolvedTextColor,
                codeBackground = colorScheme.surfaceContainerHigh,
                inlineCodeBackground = inlineCodeBg,
                dividerColor = colorScheme.outlineVariant,
                tableBackground = colorScheme.surfaceContainerHigh,
            ).also {
                styleCache.colorsScheme = colorScheme
                styleCache.colorsText = resolvedTextColor
                styleCache.colorsIsDark = isDark
                styleCache.colors = it
            }
        }
    val padding =
        if (styleCache.padding != null && styleCache.paddingSpacingMode == spacingMode) {
            styleCache.padding!!
        } else {
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
            markdownPadding(
                block = blockPadding,
                list = listPadding,
                listItemTop = listItemPadding,
                listItemBottom = listItemPadding,
            ).also {
                styleCache.paddingSpacingMode = spacingMode
                styleCache.padding = it
            }
        }
    val typography =
        if (styleCache.typography != null &&
            styleCache.typoMaterial == m3Typography &&
            styleCache.typoSpacingMode == spacingMode &&
            styleCache.typoOverride == textStyleOverride &&
            styleCache.typoInlineCodeColor == inlineCodeColor
        ) {
            styleCache.typography!!
        } else {
            markdownTypography(
                h1 =
                    m3Typography.titleMedium.copy(
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
                    m3Typography.titleMedium.copy(
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
                    m3Typography.titleSmall.copy(
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
                    m3Typography.labelLarge.copy(
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
                    m3Typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    ),
                h6 =
                    m3Typography.labelLarge.copy(
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
            ).also {
                styleCache.typoMaterial = m3Typography
                styleCache.typoSpacingMode = spacingMode
                styleCache.typoOverride = textStyleOverride
                styleCache.typoInlineCodeColor = inlineCodeColor
                styleCache.typography = it
            }
        }
    val components = rememberRpMarkdownComponents(isStreaming = isStreaming)

    val delegateUriHandler = LocalUriHandler.current
    val safeUriHandler =
        remember(delegateUriHandler) {
            object : UriHandler {
                override fun openUri(uri: String) {
                    if (isAllowedMarkdownUri(uri)) {
                        delegateUriHandler.openUri(uri.trim())
                    }
                }
            }
        }

    CompositionLocalProvider(LocalUriHandler provides safeUriHandler) {
        Markdown(
            markdownState = markdownState,
            modifier = modifier,
            colors = colors,
            padding = padding,
            typography = typography,
            components = components,
        )
    }
}
