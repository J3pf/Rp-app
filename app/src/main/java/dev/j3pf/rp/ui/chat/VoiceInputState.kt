package dev.j3pf.rp.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

class VoiceInputState(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
) {
    var isListening by mutableStateOf(false)
        private set

    var rmsDb by mutableFloatStateOf(0f)
        private set

    private var speechRecognizer: SpeechRecognizer? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        if (!isAvailable) return
        stopListening()

        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

        try {
            val recognizer =
                SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(
                        object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                isListening = true
                            }

                            override fun onBeginningOfSpeech() {}

                            override fun onRmsChanged(rmsdB: Float) {
                                rmsDb = rmsdB
                            }

                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {
                                isListening = false
                            }

                            override fun onError(error: Int) {
                                isListening = false
                                rmsDb = 0f
                            }

                            override fun onResults(results: Bundle?) {
                                isListening = false
                                rmsDb = 0f
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                matches?.firstOrNull()?.let { onFinal(it) }
                            }

                            override fun onPartialResults(partialResults: Bundle?) {
                                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                matches?.firstOrNull()?.let { onPartial(it) }
                            }

                            override fun onEvent(
                                eventType: Int,
                                params: Bundle?,
                            ) {}
                        },
                    )
                }
            speechRecognizer = recognizer
            recognizer.startListening(intent)
            isListening = true
        } catch (_: Exception) {
            isListening = false
            destroy()
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
        isListening = false
        rmsDb = 0f
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        isListening = false
        rmsDb = 0f
    }
}

@Composable
fun rememberVoiceInputState(
    onPartialResult: (String) -> Unit,
    onFinalResult: (String) -> Unit,
): VoiceInputState {
    val context = LocalContext.current
    val currentOnPartial by rememberUpdatedState(onPartialResult)
    val currentOnFinal by rememberUpdatedState(onFinalResult)

    val state =
        remember(context) {
            VoiceInputState(
                context = context,
                onPartial = { currentOnPartial(it) },
                onFinal = { currentOnFinal(it) },
            )
        }

    DisposableEffect(state) {
        onDispose {
            state.destroy()
        }
    }

    return state
}
