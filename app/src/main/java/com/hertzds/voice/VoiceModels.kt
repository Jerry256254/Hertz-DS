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
        /**
         * Verified against the sherpa-onnx "tts-models" GitHub release on 2026-08-26 —
         * every archiveUrl below was actually downloaded and its contents inspected.
         * "Jirka" is the ONLY Czech Piper voice sherpa-onnx has published; a previous
         * version of this catalog listed two more ("Hana", "Lukáš") that were never
         * real — those release assets don't exist (404), so they could never finish
         * downloading. The second Czech voice here is a Coqui-trained VITS model
         * (single Czech speaker, no espeak phonemization) from the same release —
         * its file layout (a *.onnx plus tokens.txt, no espeak-ng-data) already fits
         * [SherpaTts.load]'s generic file discovery without any loader changes.
         */
        val PIPER_VOICES = listOf(
            Piper(
                id = "piper-cs-jirka",
                displayName = "Jirka (čeština)",
                approxSizeMb = 64,
                language = "cs",
                releaseAsset = "vits-piper-cs_CZ-jirka-medium.tar.bz2",
                extractedDirName = "vits-piper-cs_CZ-jirka-medium",
            ),
            Piper(
                id = "coqui-cs-female",
                displayName = "Coqui (čeština, žena)",
                approxSizeMb = 64,
                language = "cs",
                releaseAsset = "vits-coqui-cs-cv.tar.bz2",
                extractedDirName = "vits-coqui-cs-cv",
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
