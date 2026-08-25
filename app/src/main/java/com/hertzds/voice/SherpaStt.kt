package com.hertzds.voice

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Whisper speech-to-text via sherpa-onnx, running fully offline on-device. */
class SherpaStt private constructor(private val recognizer: OfflineRecognizer) {

    suspend fun transcribe(samples: FloatArray, sampleRate: Int = 16_000): String =
        withContext(Dispatchers.Default) {
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(samples, sampleRate)
                recognizer.decode(stream)
                recognizer.getResult(stream).text.trim()
            } finally {
                stream.release()
            }
        }

    fun release() = recognizer.release()

    companion object {
        fun load(modelDir: File): SherpaStt? {
            val encoder = modelDir.listFiles { f -> f.name.contains("encoder") && f.extension == "onnx" }
                ?.firstOrNull() ?: return null
            val decoder = modelDir.listFiles { f -> f.name.contains("decoder") && f.extension == "onnx" }
                ?.firstOrNull() ?: return null
            val tokens = modelDir.listFiles { f -> f.name.contains("tokens") && f.extension == "txt" }
                ?.firstOrNull() ?: return null

            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80, dither = 0.0f),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = encoder.path,
                        decoder = decoder.path,
                        language = "",
                        task = "transcribe",
                        tailPaddings = -1,
                        enableTokenTimestamps = false,
                        enableSegmentTimestamps = false,
                    ),
                    tokens = tokens.path,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                ),
                decodingMethod = "greedy_search",
            )
            val recognizer = runCatching {
                OfflineRecognizer(assetManager = null, config = config)
            }.getOrNull() ?: return null
            return SherpaStt(recognizer)
        }
    }
}
