package dev.j3pf.rp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown

/** Markdown con bloques de código resaltados (módulo `multiplatform-markdown-renderer-code`). */
@Composable
fun RpMarkdown(text: String, modifier: Modifier = Modifier) {
    Markdown(
        content = text,
        modifier = modifier,
        components = markdownComponents(
            codeBlock = highlightedCodeBlock,
            codeFence = highlightedCodeFence,
        ),
    )
}
