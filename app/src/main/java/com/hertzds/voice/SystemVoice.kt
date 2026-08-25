package com.hertzds.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/** Wraps android.speech.tts.TextToSpeech with sentence-by-sentence streaming. */
class SystemTts(context: Context) {

    private var engine: TextToSpeech? = null
    private var ready = false

    private val appContext = context.applicationContext

    suspend fun init(): Boolean {
        val done = kotlinx.coroutines.CompletableDeferred<Boolean>()
        engine = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            done.complete(ready)
        }
        return done.await()
    }

    fun availableVoices(): List<Voice> = engine?.voices?.toList().orEmpty()

    fun setVoice(voiceName: String?) {
        val engine = engine ?: return
        val voice = voiceName?.let { name -> engine.voices?.firstOrNull { it.name == name } }
        if (voice != null) engine.voice = voice
    }

    fun setSpeed(rate: Float) {
        engine?.setSpeechRate(rate)
    }

    fun setLanguage(locale: Locale) {
        engine?.language = locale
    }

    /** Speaks one utterance and completes when playback finishes (or is stopped). */
    fun speak(text: String, onDone: () -> Unit) {
        val engine = engine ?: run { onDone(); return }
        if (!ready || text.isBlank()) {
            onDone()
            return
        }
        val id = UUID.randomUUID().toString()
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { if (utteranceId == id) onDone() }
                @Deprecated("required override")
                override fun onError(utteranceId: String?) { if (utteranceId == id) onDone() }
            },
        )
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, id)
    }

    fun stop() {
        engine?.stop()
    }

    fun release() {
        engine?.shutdown()
        engine = null
    }
}

sealed interface SttEvent {
    data class Partial(val text: String) : SttEvent
    data class Final(val text: String) : SttEvent
    data class Error(val message: String) : SttEvent
    data object EndOfSpeech : SttEvent
}

/** Wraps android.speech.SpeechRecognizer as a Flow so it composes with the hands-free loop. */
class SystemStt(private val context: Context) {

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun listen(languageTag: String, partialResults: Boolean = true): Flow<SttEvent> = callbackFlow {
        if (!isAvailable()) {
            trySend(SttEvent.Error("speech_recognition_unavailable"))
            close()
            return@callbackFlow
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partialResults)
        }

        recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { trySend(SttEvent.EndOfSpeech) }

                override fun onError(error: Int) {
                    trySend(SttEvent.Error("recognizer_error_$error"))
                    close()
                }

                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!text.isNullOrBlank()) trySend(SttEvent.Final(text))
                    close()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!text.isNullOrBlank()) trySend(SttEvent.Partial(text))
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            },
        )

        recognizer.startListening(intent)
        awaitClose { recognizer.destroy() }
    }
}
