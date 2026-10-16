package dev.j3pf.rp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState

@Composable
private fun rememberRpMarkdownComponents() =
    markdownComponents(
        codeBlock = { model ->
            Box(Modifier.horizontalEdgeFade(edgeColor = MaterialTheme.colorScheme.surfaceVariant)) {
                highlightedCodeBlock(model)
            }
        },
        codeFence = { model ->
            Box(Modifier.horizontalEdgeFade(edgeColor = MaterialTheme.colorScheme.surfaceVariant)) {
                highlightedCodeFence(model)
            }
        },
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
        components = rememberRpMarkdownComponents(),
    )
}
