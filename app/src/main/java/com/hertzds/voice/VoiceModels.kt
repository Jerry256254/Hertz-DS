package com.hertzds.voice

/**
 * Catalog of downloadable Piper (VITS) TTS voices and a Whisper STT model, all
 * pulled from k2-fsa/sherpa-onnx's official GitHub release assets at tap time —
 * nothing is bundled in the APK. Sizes are approximate, shown to the user before
 * they confirm the download.
 */
sealed class VoiceModel(
    val id: String,
    val displayName: String,
    val approxSizeMb: Int,
    val language: String,
) {
    abstract val archiveUrl: String
    /** Folder name the archive extracts into, and where we look for its files afterwards. */
    abstract val extractedDirName: String

    class Piper(
        id: String,
        displayName: String,
        approxSizeMb: Int,
        language: String,
        private val releaseAsset: String,
        override val extractedDirName: String,
    ) : VoiceModel(id, displayName, approxSizeMb, language) {
        override val archiveUrl =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$releaseAsset"
    }

    companion object {
        val PIPER_VOICES = listOf(
            Piper(
                id = "piper-cs-jirka",
                displayName = "Jirka (čeština)",
                approxSizeMb = 63,
                language = "cs",
                releaseAsset = "vits-piper-cs_CZ-jirka-medium.tar.bz2",
                extractedDirName = "vits-piper-cs_CZ-jirka-medium",
            ),
            Piper(
                id = "piper-en-amy",
                displayName = "Amy (English)",
                approxSizeMb = 63,
                language = "en",
                releaseAsset = "vits-piper-en_US-amy-medium.tar.bz2",
                extractedDirName = "vits-piper-en_US-amy-medium",
            ),
            Piper(
                id = "piper-en-ryan",
                displayName = "Ryan (English)",
                approxSizeMb = 63,
                language = "en",
                releaseAsset = "vits-piper-en_US-ryan-medium.tar.bz2",
                extractedDirName = "vits-piper-en_US-ryan-medium",
            ),
        )
    }
}

data class WhisperModel(
    val id: String,
    val displayName: String,
    val approxSizeMb: Int,
    private val releaseAsset: String,
) {
    val archiveUrl =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$releaseAsset"
    val extractedDirName = releaseAsset.removeSuffix(".tar.bz2")

    companion object {
        val OPTIONS = listOf(
            WhisperModel(
                id = "whisper-tiny",
                displayName = "Whisper Tiny (rychlý, ~75 MB)",
                approxSizeMb = 75,
                releaseAsset = "sherpa-onnx-whisper-tiny.tar.bz2",
            ),
            WhisperModel(
                id = "whisper-base",
                displayName = "Whisper Base (přesnější, ~140 MB)",
                approxSizeMb = 140,
                releaseAsset = "sherpa-onnx-whisper-base.tar.bz2",
            ),
        )
    }
}
