package dev.j3pf.rp.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceInputHostStateTest {
    @Test
    fun availableHostForwardsPartialAndFinalText() {
        val host = FakeVoiceInputHost()
        val partial = mutableListOf<String>()
        val final = mutableListOf<String>()
        val state = host.state(true, partial::add, final::add)

        state.startListening()
        host.emitPartial("draft")
        host.emitFinal("complete")

        assertEquals(1, host.startCount)
        assertEquals(listOf("draft"), partial)
        assertEquals(listOf("complete"), final)
        assertFalse(state.isListening)
    }

    @Test
    fun unavailableHostDoesNothingIncludingOnDisposal() {
        val host = FakeVoiceInputHost()
        val state = host.state(false, {}, {})

        state.startListening()
        state.stopListening()
        state.dispose()

        assertEquals(0, host.startCount)
        assertEquals(0, host.stopCount)
        assertFalse(state.isListening)
    }

    @Test
    fun stopAndDisposalStopTheHostAndIgnoreLaterCallbacks() {
        val host = FakeVoiceInputHost()
        val partial = mutableListOf<String>()
        val state = host.state(true, partial::add, {})

        state.startListening()
        state.stopListening()
        assertFalse(state.isListening)
        state.dispose()
        host.emitPartial("after disposal")

        assertEquals(2, host.stopCount)
        assertTrue(partial.isEmpty())
    }

    @Test
    fun errorsResetListeningWithoutForwardingAnErrorToTheComposer() {
        val host = FakeVoiceInputHost()
        val state = host.state(true, {}, {})

        state.startListening()
        assertTrue(state.isListening)
        host.emitError()

        assertFalse(state.isListening)
    }

    private class FakeVoiceInputHost {
        var startCount = 0
        var stopCount = 0
        private var onPartial: ((String) -> Unit)? = null
        private var onFinal: ((String) -> Unit)? = null
        private var onError: (() -> Unit)? = null
        private var currentState: VoiceInputHostState? = null

        fun state(
            isAvailable: Boolean,
            onPartialResult: (String) -> Unit,
            onFinalResult: (String) -> Unit,
        ) = VoiceInputHostState(
            isAvailable = isAvailable,
            start = { partial, final, error ->
                startCount++
                onPartial = partial
                onFinal = final
                onError = error
            },
            stop = { stopCount++ },
            onPartialResult = onPartialResult,
            onFinalResult = onFinalResult,
        ).also { currentState = it }

        fun emitPartial(text: String) {
            onPartial?.invoke(text)
        }

        fun emitFinal(text: String) {
            currentState?.updateListening(false)
            onFinal?.invoke(text)
        }

        fun emitError() {
            currentState?.updateListening(false)
            onError?.invoke()
        }
    }
}
