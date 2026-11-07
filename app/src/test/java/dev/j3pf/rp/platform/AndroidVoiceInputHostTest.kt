package dev.j3pf.rp.platform

import android.content.Context
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.data.ports.VoiceErrorCode
import dev.j3pf.rp.data.ports.VoiceListener
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidVoiceInputHostTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun unavailableRecognizerReportsNoRecognizerWithoutCreatingAnEngine() {
        var createCount = 0
        val host =
            AndroidVoiceInputHost(
                context,
                { false },
                SpeechRecognizerEngineFactory {
                    createCount++
                    FakeSpeechRecognizerEngine()
                },
            )
        val errors = mutableListOf<Int>()

        host.start(listener(onError = errors::add))

        assertEquals(listOf(VoiceErrorCode.NO_RECOGNIZER), errors)
        assertEquals(0, createCount)
        assertFalse(host.listening.value)
    }

    @Test
    fun startForwardsPartialAndFinalAndTracksListening() {
        val engine = FakeSpeechRecognizerEngine()
        val host = host(engine)
        val partial = mutableListOf<String>()
        val final = mutableListOf<String>()
        val errors = mutableListOf<Int>()

        host.start(listener(partial::add, final::add, errors::add))
        assertTrue(host.listening.value)
        engine.events.onPartial("part")
        engine.events.onFinal("done")

        assertEquals(listOf("part"), partial)
        assertEquals(listOf("done"), final)
        assertTrue(errors.isEmpty())
        assertFalse(host.listening.value)
    }

    @Test
    fun stopDelegatesToRecognizerAndClearsListening() {
        val engine = FakeSpeechRecognizerEngine()
        val host = host(engine)
        host.start(listener())

        host.stop()

        assertEquals(1, engine.stopCount)
        assertFalse(host.listening.value)
    }

    @Test
    fun startStopsAndReleasesThePreviousRecognizerBeforeReplacingIt() {
        val previous = FakeSpeechRecognizerEngine()
        val next = FakeSpeechRecognizerEngine()
        val engines = ArrayDeque(listOf(previous, next))
        val host =
            AndroidVoiceInputHost(
                context,
                { true },
                SpeechRecognizerEngineFactory { engines.removeFirst() },
            )

        host.start(listener())
        host.start(listener())

        assertEquals(1, previous.stopCount)
        assertEquals(1, previous.destroyCount)
        assertEquals(1, next.startCount)
    }

    @Test
    fun speechRecognizerErrorsMapToTheFrozenPortCodes() {
        val cases =
            listOf(
                SpeechRecognizer.ERROR_SERVER to VoiceErrorCode.RECOGNIZER_BANNED,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT to VoiceErrorCode.NETWORK,
                SpeechRecognizer.ERROR_CLIENT to VoiceErrorCode.CLIENT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY to VoiceErrorCode.BUSY,
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS to VoiceErrorCode.NO_PERMISSIONS,
            )

        cases.forEach { (platformError, expectedCode) ->
            val engine = FakeSpeechRecognizerEngine()
            val host = host(engine)
            val errors = mutableListOf<Int>()
            host.start(listener(onError = errors::add))

            engine.events.onError(platformError)

            assertEquals(listOf(expectedCode), errors, "SpeechRecognizer error $platformError")
            assertFalse(host.listening.value)
        }
    }

    @Test
    fun startFailuresMapSecurityExceptionsToNoPermissionsAndOthersToClient() {
        listOf(
            SecurityException("permission") to VoiceErrorCode.NO_PERMISSIONS,
            IllegalStateException("recognizer") to VoiceErrorCode.CLIENT,
        ).forEach { (failure, expectedCode) ->
            val host =
                AndroidVoiceInputHost(
                    context,
                    { true },
                    SpeechRecognizerEngineFactory { throw failure },
                )
            val errors = mutableListOf<Int>()

            host.start(listener(onError = errors::add))

            assertEquals(listOf(expectedCode), errors)
            assertFalse(host.listening.value)
        }
    }

    private fun host(engine: FakeSpeechRecognizerEngine) =
        AndroidVoiceInputHost(context, { true }, SpeechRecognizerEngineFactory { engine })

    private fun listener(
        onPartial: (String) -> Unit = {},
        onFinal: (String) -> Unit = {},
        onError: (Int) -> Unit = {},
    ) =
        object : VoiceListener {
            override fun onPartial(text: String) = onPartial.invoke(text)

            override fun onFinal(text: String) = onFinal.invoke(text)

            override fun onError(code: Int) = onError.invoke(code)
        }

    private class FakeSpeechRecognizerEngine : SpeechRecognizerEngine {
        lateinit var events: SpeechRecognitionEvents
        var startCount = 0
        var stopCount = 0
        var destroyCount = 0

        override fun start(events: SpeechRecognitionEvents) {
            this.events = events
            startCount++
        }

        override fun stop() {
            stopCount++
        }

        override fun destroy() {
            destroyCount++
        }
    }
}
