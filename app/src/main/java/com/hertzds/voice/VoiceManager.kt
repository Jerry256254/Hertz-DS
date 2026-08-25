package com.hertzds.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.hertzds.data.prefs.AppLanguage
import com.hertzds.data.prefs.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.Locale
import kotlin.coroutines.resume

sealed interface HandsFreeState {
    data object Idle : HandsFreeState
    data object Listening : HandsFreeState
    data class Heard(val text: String) : HandsFreeState
    data object Thinking : HandsFreeState
    data object Speaking : HandsFreeState
    data class Error(val message: String) : HandsFreeState
}

/**
 * Picks system TTS/STT or the downloaded sherpa-onnx models depending on settings,
 * and drives the hands-free listen/think/speak loop. Falls back to the system
 * engine whenever the chosen sherpa model has not been downloaded yet.
 */
class VoiceManager(
    private val context: Context,
    private val http: OkHttpClient,
    private val downloader: ModelDownloader,
) {
    private val systemTts by lazy { SystemTts(context) }
    private val systemStt by lazy { SystemStt(context) }

    private var sherpaTts: SherpaTts? = null
    private var sherpaTtsVoiceId: String? = null
    private var sherpaStt: SherpaStt? = null
    private var sherpaSttModelId: String? = null
    private var sherpaVad: SherpaVad? = null

    private var systemTtsReady = false

    suspend fun ensureSystemTtsReady() {
        if (!systemTtsReady) systemTtsReady = systemTts.init()
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Speaks [text], splitting on sentence boundaries so playback starts before generation finishes. */
    suspend fun speak(text: String, settings: Settings) {
        if (text.isBlank()) return
        val sentences = text.split(Regex("(?<=[.!?…])\\s+")).filter { it.isNotBlank() }

        if (settings.ttsEngine == "sherpa") {
            val voice = settings.ttsVoiceId?.let { voiceId ->
                VoiceModel.PIPER_VOICES.firstOrNull { it.id == voiceId }
            }
            if (voice != null && ensureSherpaTts(voice)) {
                speakWithSherpa(sentences, settings.ttsSpeed)
                return
            }
        }
        speakWithSystem(sentences, settings)
    }

    private suspend fun speakWithSherpa(sentences: List<String>, speed: Float) {
        val tts = sherpaTts ?: return
        val player = StreamingPcmPlayer(tts.sampleRate())
        player.start()
        try {
            for (sentence in sentences) {
                tts.speak(sentence, speed) { chunk -> player.write(chunk) }
            }
        } finally {
            player.stop()
            player.release()
        }
    }

    private suspend fun speakWithSystem(sentences: List<String>, settings: Settings) {
        ensureSystemTtsReady()
        systemTts.setSpeed(settings.ttsSpeed)
        systemTts.setVoice(settings.ttsVoiceId)
        for (sentence in sentences) {
            withContext(Dispatchers.Main) { suspendSpeak(sentence) }
        }
    }

    private suspend fun suspendSpeak(sentence: String) {
        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
        systemTts.speak(sentence) { done.complete(Unit) }
        try {
            done.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            systemTts.stop()
            throw e
        }
    }

    fun stopSpeaking() {
        systemTts.stop()
    }

    private fun ensureSherpaTts(voice: VoiceModel.Piper): Boolean {
        if (sherpaTts != null && sherpaTtsVoiceId == voice.id) return true
        if (!downloader.isDownloaded(voice.id, voice.extractedDirName)) return false
        val dir = downloader.directoryFor(voice.id, voice.extractedDirName)
        val loaded = SherpaTts.load(dir) ?: return false
        sherpaTts?.release()
        sherpaTts = loaded
        sherpaTtsVoiceId = voice.id
        return true
    }

    private fun ensureSherpaStt(model: WhisperModel): Boolean {
        if (sherpaStt != null && sherpaSttModelId == model.id) return true
        if (!downloader.isDownloaded(model.id, model.extractedDirName)) return false
        val dir = downloader.directoryFor(model.id, model.extractedDirName)
        val loaded = SherpaStt.load(dir) ?: return false
        sherpaStt?.release()
        sherpaStt = loaded
        sherpaSttModelId = model.id
        return true
    }

    private fun ensureVad(): SherpaVad? {
        sherpaVad?.let { return it }
        if (!SileroVadModel.isDownloaded(context)) return null
        val loaded = SherpaVad.load(SileroVadModel.file(context)) ?: return null
        sherpaVad = loaded
        return loaded
    }

    /**
     * One hands-free turn: records from the mic, uses VAD (if downloaded) to detect
     * end of speech, then transcribes. Falls back to the system recognizer, which
     * does its own end-pointing, when sherpa STT/VAD are not ready.
     */
    fun listenOnce(settings: Settings): Flow<HandsFreeState> = callbackFlow {
        if (!hasMicPermission()) {
            trySend(HandsFreeState.Error("mic_permission_denied"))
            close()
            return@callbackFlow
        }

        if (settings.sttEngine == "sherpa") {
            val model = WhisperModel.OPTIONS.firstOrNull { it.id == settings.sttModelId }
            val vad = ensureVad()
            if (model != null && vad != null && ensureSherpaStt(model)) {
                launchSherpaListen(vad, sherpaStt!!)
                return@callbackFlow
            }
        }
        launchSystemListen(settings)
    }.flowOn(Dispatchers.Default)

    private suspend fun ProducerScope<HandsFreeState>.launchSystemListen(settings: Settings) {
        trySend(HandsFreeState.Listening)
        val locale = when (settings.language) {
            AppLanguage.CZECH -> Locale("cs", "CZ")
            AppLanguage.ENGLISH -> Locale.US
            AppLanguage.SYSTEM -> Locale.getDefault()
        }
        val job = launch(Dispatchers.Main) {
            systemStt.listen(locale.toLanguageTag()).collect { event ->
                when (event) {
                    is SttEvent.Partial -> trySend(HandsFreeState.Heard(event.text))
                    is SttEvent.Final -> {
                        trySend(HandsFreeState.Heard(event.text))
                        close()
                    }
                    is SttEvent.Error -> {
                        trySend(HandsFreeState.Error(event.message))
                        close()
                    }
                    SttEvent.EndOfSpeech -> {}
                }
            }
        }
        awaitClose { job.cancel() }
    }

    @Suppress("MissingPermission")
    private suspend fun ProducerScope<HandsFreeState>.launchSherpaListen(vad: SherpaVad, stt: SherpaStt) {
        trySend(HandsFreeState.Listening)
        val sampleRate = SherpaVad.SAMPLE_RATE
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(SherpaVad.WINDOW_SIZE * 4)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer,
        )

        val job = launch(Dispatchers.Default) {
            vad.reset()
            recorder.startRecording()
            val shortBuffer = ShortArray(SherpaVad.WINDOW_SIZE)
            val speechBuffer = mutableListOf<Float>()
            var hadSpeech = false

            try {
                while (isActive) {
                    val read = recorder.read(shortBuffer, 0, shortBuffer.size)
                    if (read <= 0) continue
                    val floatChunk = FloatArray(read) { shortBuffer[it] / 32768.0f }
                    vad.accept(floatChunk)
                    if (vad.isSpeechDetected()) hadSpeech = true
                    speechBuffer.addAll(floatChunk.toList())

                    var segment = vad.popSegment()
                    while (segment != null) {
                        val text = stt.transcribe(segment)
                        if (text.isNotBlank()) {
                            trySend(HandsFreeState.Heard(text))
                            close()
                            return@launch
                        }
                        segment = vad.popSegment()
                    }

                    if (hadSpeech && speechBuffer.size > sampleRate * 20) {
                        val text = stt.transcribe(speechBuffer.toFloatArray())
                        trySend(HandsFreeState.Heard(text))
                        close()
                        return@launch
                    }
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }
        }
        awaitClose { job.cancel() }
    }

    fun release() {
        systemTts.release()
        sherpaTts?.release()
        sherpaStt?.release()
        sherpaVad?.release()
    }
}
