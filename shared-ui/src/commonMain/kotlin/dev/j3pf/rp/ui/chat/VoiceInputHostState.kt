package dev.j3pf.rp.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.StateFlow

/** Composer-facing state that keeps platform voice input behind [VoiceInputHost]. */
class VoiceInputHostState(
    private val isAvailable: Boolean,
    private val start: (onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: () -> Unit) -> Unit,
    private val stop: () -> Unit,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
) {
    var isListening by mutableStateOf(false)
        private set

    private var active = false
    private var disposed = false

    fun startListening() {
        if (!isAvailable || disposed) return
        active = true
        isListening = true
        try {
            start(
                { text -> if (active) onPartialResult(text) },
                { text ->
                    if (active) {
                        active = false
                        updateListening(false)
                        onFinalResult(text)
                    }
                },
                {
                    if (active) {
                        active = false
                        updateListening(false)
                    }
                },
            )
        } catch (_: Exception) {
            active = false
            updateListening(false)
        }
    }

    fun stopListening() {
        if (!isAvailable || disposed) return
        active = false
        updateListening(false)
        stop()
    }

    /** Mirrors the host's StateFlow when platform recognition changes outside a composer action. */
    fun updateListening(listening: Boolean) {
        if (!disposed) isListening = listening
    }

    fun dispose() {
        if (!isAvailable || disposed) return
        active = false
        disposed = true
        isListening = false
        stop()
    }
}

/** Remembers the port consumer, forwarding callbacks and stopping recognition when it leaves UI. */
@Composable
fun rememberVoiceInputHostState(
    hostKey: Any?,
    isAvailable: Boolean,
    listeningFlow: StateFlow<Boolean>?,
    start: (onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: () -> Unit) -> Unit,
    stop: () -> Unit,
    onPartialResult: (String) -> Unit,
    onFinalResult: (String) -> Unit,
): VoiceInputHostState {
    val currentOnPartial by rememberUpdatedState(onPartialResult)
    val currentOnFinal by rememberUpdatedState(onFinalResult)
    val state =
        remember(hostKey, isAvailable) {
            VoiceInputHostState(
                isAvailable = isAvailable,
                start = start,
                stop = stop,
                onPartialResult = { currentOnPartial(it) },
                onFinalResult = { currentOnFinal(it) },
            )
        }

    if (listeningFlow != null) {
        val listening by listeningFlow.collectAsState()
        LaunchedEffect(state, listening) {
            state.updateListening(listening)
        }
    }

    DisposableEffect(state) {
        onDispose { state.dispose() }
    }

    return state
}
