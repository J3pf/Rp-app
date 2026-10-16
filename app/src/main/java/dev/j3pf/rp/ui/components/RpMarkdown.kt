package dev.j3pf.rp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState

private val RpMarkdownComponents =
    markdownComponents(
        codeBlock = highlightedCodeBlock,
        codeFence = highlightedCodeFence,
    )

/** Markdown con bloques de código resaltados y renderizado optimizado para streaming sin bloqueos de hilo principal. */
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
    Markdown(
        markdownState = markdownState,
        modifier = modifier,
        components = RpMarkdownComponents,
    )
}
