package com.hertzds.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Piper (VITS) text-to-speech via sherpa-onnx, loaded from a downloaded model
 * folder on disk (not from Android assets, so it can be user-installed later).
 */
class SherpaTts private constructor(private val engine: OfflineTts) {

    fun sampleRate(): Int = engine.sampleRate()

    /** Streams PCM chunks to [onChunk] as they're generated so playback can start early. */
    suspend fun speak(text: String, speed: Float, onChunk: (FloatArray) -> Unit): Unit =
        withContext(Dispatchers.Default) {
            engine.generateWithCallback(text = text, sid = 0, speed = speed) { samples ->
                onChunk(samples)
                1 // keep generating
            }
            Unit
        }

    fun release() = engine.release()

    companion object {
        /** Below this, an .onnx file is a truncated/failed download, not a real model. */
        private const val MIN_MODEL_BYTES = 1_000_000L

        /** Locates the model files inside an extracted Piper voice folder. */
        fun load(voiceDir: File): SherpaTts? {
            val onnx = voiceDir.listFiles { f -> f.extension == "onnx" }?.firstOrNull() ?: return null
            if (onnx.length() < MIN_MODEL_BYTES) return null
            val tokens = File(voiceDir, "tokens.txt").takeIf { it.exists() } ?: return null
            val dataDir = File(voiceDir, "espeak-ng-data").takeIf { it.exists() }?.path.orEmpty()

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = onnx.path,
                        lexicon = "",
                        tokens = tokens.path,
                        dataDir = dataDir,
                        dictDir = "",
                        noiseScale = 0.667f,
                        noiseScaleW = 0.8f,
                        lengthScale = 1.0f,
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                ),
                ruleFsts = "",
                ruleFars = "",
                maxNumSentences = 1,
                silenceScale = 0.2f,
            )
            val tts = runCatching { OfflineTts(assetManager = null, config = config) }.getOrNull() ?: return null
            return SherpaTts(tts)
        }
    }
}

/** Plays 16-bit PCM float samples as they arrive, for true streamed speech. */
class StreamingPcmPlayer(sampleRate: Int) {

    companion object {
        /** Bytes per frame for mono 32-bit float PCM — AudioTrack rejects sizes not aligned to this. */
        private const val FRAME_SIZE_BYTES = 4

        /**
         * getMinBufferSize can return ERROR (-1) or ERROR_BAD_VALUE (-2) on some OEM audio
         * HALs for PCM_FLOAT at unusual sample rates; falling back to a fixed 1-second
         * buffer keeps AudioTrack.Builder from ever seeing a negative or misaligned size
         * (the concrete cause of the "invalid audio buffer size" crash).
         */
        private fun bufferSizeBytes(sampleRate: Int): Int {
            val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            val base = if (minBuffer > 0) minBuffer * 2 else sampleRate * FRAME_SIZE_BYTES
            val remainder = base % FRAME_SIZE_BYTES
            return if (remainder == 0) base else base + (FRAME_SIZE_BYTES - remainder)
        }
    }

    private val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(bufferSizeBytes(sampleRate))
        .build()

    fun start() = track.play()

    fun write(samples: FloatArray) {
        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
    }

    fun stop() {
        runCatching { track.stop() }
        track.flush()
    }

    fun release() {
        runCatching { track.stop() }
        track.release()
    }
}
