package com.hertzds.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Speech end-of-turn detection (Silero VAD via sherpa-onnx) for hands-free mode.
 * Unlike TTS/STT voices this is one small ~2 MB onnx file, not a tar.bz2 archive.
 */
object SileroVadModel {
    private const val URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
    const val ID = "silero-vad"

    fun file(context: Context): File = File(File(context.filesDir, "voice_models"), "silero_vad.onnx")

    const val MIN_MODEL_BYTES = 500_000L

    fun isDownloaded(context: Context): Boolean = file(context).exists() && file(context).length() >= MIN_MODEL_BYTES

    /**
     * Downloads to a `.partial` sibling and only renames it onto the final path
     * once fully written — a model file [isDownloaded] can see is therefore
     * guaranteed complete. An interrupted download previously left a truncated
     * file that still passed the old, weaker size check; loading that into the
     * native VAD engine is a native-layer crash no Kotlin catch can prevent.
     */
    fun download(context: Context, http: OkHttpClient): Flow<DownloadProgress> = flow {
        val target = file(context).apply { parentFile?.mkdirs() }
        val partial = File(target.parentFile, "${target.name}.partial")
        emit(DownloadProgress.Downloading(0, 0))
        try {
            val request = Request.Builder().url(URL).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(DownloadProgress.Failed("HTTP ${response.code}"))
                    return@flow
                }
                val body = response.body ?: run { emit(DownloadProgress.Failed("empty body")); return@flow }
                val total = body.contentLength()
                var read = 0L
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            read += n
                            emit(DownloadProgress.Downloading(read, total))
                        }
                    }
                }
                if (total > 0 && read != total) error("incomplete download: got $read of $total bytes")
            }
            if (partial.length() < MIN_MODEL_BYTES) error("downloaded file looks too small (${partial.length()} bytes)")
            target.delete()
            if (!partial.renameTo(target)) error("could not finalize downloaded model")
            emit(DownloadProgress.Done(target.parentFile!!))
        } catch (e: Exception) {
            partial.delete()
            emit(DownloadProgress.Failed(e.message ?: "download failed"))
        }
    }.flowOn(Dispatchers.IO)
}

class SherpaVad private constructor(private val vad: Vad) {

    /** True once enough trailing silence has been seen to consider the utterance finished. */
    fun accept(chunk: FloatArray): Boolean {
        vad.acceptWaveform(chunk)
        return !vad.empty()
    }

    fun popSegment(): FloatArray? {
        if (vad.empty()) return null
        val segment = vad.front().samples
        vad.pop()
        return segment
    }

    fun isSpeechDetected(): Boolean = vad.isSpeechDetected()

    fun reset() = vad.reset()

    fun release() = vad.release()

    companion object {
        const val SAMPLE_RATE = 16_000
        const val WINDOW_SIZE = 512

        fun load(modelFile: File): SherpaVad? {
            if (!modelFile.exists() || modelFile.length() < SileroVadModel.MIN_MODEL_BYTES) return null
            val config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = modelFile.path,
                    threshold = 0.5f,
                    minSilenceDuration = 0.6f,
                    minSpeechDuration = 0.25f,
                    windowSize = WINDOW_SIZE,
                    maxSpeechDuration = 20.0f,
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu",
                debug = false,
            )
            val vad = runCatching { Vad(assetManager = null, config = config) }.getOrNull() ?: return null
            return SherpaVad(vad)
        }
    }
}
