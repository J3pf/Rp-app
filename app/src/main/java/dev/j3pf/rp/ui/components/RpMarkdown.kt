package dev.j3pf.rp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.ReferenceLinkHandlerImpl
import com.mikepenz.markdown.model.State
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

private val RpMarkdownComponents =
    markdownComponents(
        codeBlock = highlightedCodeBlock,
        codeFence = highlightedCodeFence,
    )

private val flavour = CommonMarkFlavourDescriptor()

/** Markdown con bloques de código resaltados y renderizado síncrono para evitar parpadeos y retrasos. */
@Composable
fun RpMarkdown(
    text: String,
    modifier: Modifier = Modifier,
) {
    val state =
        remember(text) {
            val parser = MarkdownParser(flavour)
            val tree = parser.buildMarkdownTreeFromString(text)
            State.Success(tree, text, true, ReferenceLinkHandlerImpl())
        }
    Markdown(
        state = state,
        modifier = modifier,
        components = RpMarkdownComponents,
    )
}
