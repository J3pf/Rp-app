package dev.j3pf.rp.platform

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.data.ports.VoiceErrorCode
import dev.j3pf.rp.data.ports.VoiceInputHost
import dev.j3pf.rp.data.ports.VoiceListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidVoiceInputHost internal constructor(
    context: Context,
    private val recognitionAvailable: () -> Boolean,
    private val engineFactory: SpeechRecognizerEngineFactory,
) : VoiceInputHost {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        context = context,
        recognitionAvailable = { SpeechRecognizer.isRecognitionAvailable(context) },
        engineFactory = SpeechRecognizerEngineFactory { AndroidSpeechRecognizerEngine(context) },
    )

    private val mutableListening = MutableStateFlow(false)
    private var activeListener: VoiceListener? = null
    private var engine: SpeechRecognizerEngine? = null
    private var generation = 0L

    override val listening: StateFlow<Boolean> = mutableListening.asStateFlow()

    val isAvailable: Boolean
        get() = recognitionAvailable()

    override fun start(listener: VoiceListener) {
        stop()
        if (!isAvailable) {
            listener.onError(VoiceErrorCode.NO_RECOGNIZER)
            return
        }

        val session = ++generation
        activeListener = listener
        val recognizer =
            try {
                engineFactory.create()
            } catch (failure: Exception) {
                failStart(session, failure)
                return
            }
        engine = recognizer
        mutableListening.value = true

        try {
            recognizer.start(
                object : SpeechRecognitionEvents {
                    override fun onPartial(text: String) {
                        if (isCurrent(session)) activeListener?.onPartial(text)
                    }

                    override fun onFinal(text: String) {
                        if (!isCurrent(session)) return
                        val callback = activeListener ?: return
                        activeListener = null
                        mutableListening.value = false
                        callback.onFinal(text)
                    }

                    override fun onError(error: Int) {
                        if (!isCurrent(session)) return
                        val callback = activeListener ?: return
                        activeListener = null
                        mutableListening.value = false
                        callback.onError(mapSpeechError(error))
                    }

                    override fun onListeningChanged(listening: Boolean) {
                        if (isCurrent(session) && activeListener != null) mutableListening.value = listening
                    }
                },
            )
        } catch (failure: Exception) {
            failStart(session, failure)
        }
    }

    override fun stop() {
        generation++
        activeListener = null
        mutableListening.value = false
        val previous = engine
        engine = null
        if (previous != null) {
            try {
                previous.stop()
            } catch (_: Exception) {
            }
            try {
                previous.destroy()
            } catch (_: Exception) {
            }
        }
    }

    private fun failStart(
        session: Long,
        failure: Exception,
    ) {
        if (session != generation) return
        val callback = activeListener
        activeListener = null
        mutableListening.value = false
        val failedEngine = engine
        engine = null
        try {
            failedEngine?.destroy()
        } catch (_: Exception) {
        }
        callback?.onError(
            if (failure is SecurityException) VoiceErrorCode.NO_PERMISSIONS else VoiceErrorCode.CLIENT,
        )
    }

    private fun isCurrent(session: Long): Boolean = session == generation

    private fun mapSpeechError(error: Int): Int =
        when (error) {
            SpeechRecognizer.ERROR_SERVER -> VoiceErrorCode.RECOGNIZER_BANNED
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_NETWORK -> VoiceErrorCode.NETWORK
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceErrorCode.BUSY
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceErrorCode.NO_PERMISSIONS
            else -> VoiceErrorCode.CLIENT
        }
}

internal fun interface SpeechRecognizerEngineFactory {
    fun create(): SpeechRecognizerEngine
}

internal interface SpeechRecognizerEngine {
    fun start(events: SpeechRecognitionEvents)

    fun stop()

    fun destroy()
}

internal interface SpeechRecognitionEvents {
    fun onPartial(text: String)

    fun onFinal(text: String)

    fun onError(error: Int)

    fun onListeningChanged(listening: Boolean) {}
}

private class AndroidSpeechRecognizerEngine(
    context: Context,
) : SpeechRecognizerEngine {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

    override fun start(events: SpeechRecognitionEvents) {
        recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = events.onListeningChanged(true)

                override fun onBeginningOfSpeech() = events.onListeningChanged(true)

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = events.onListeningChanged(false)

                override fun onError(error: Int) = events.onError(error)

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let(events::onFinal)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let(events::onPartial)
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?,
                ) = Unit
            },
        )
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            },
        )
    }

    override fun stop() = recognizer.stopListening()

    override fun destroy() = recognizer.destroy()
}
