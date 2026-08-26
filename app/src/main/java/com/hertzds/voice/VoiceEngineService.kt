package com.hertzds.voice

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.io.File

/**
 * Hosts the offline Piper (sherpa-onnx) TTS engine in the `:voice` process (see
 * the manifest). This is third-party JNI code; if it ever segfaults, only this
 * disposable process dies — the client (VoiceEngineClient, running in the main
 * process) sees the binding drop and treats it as a normal speak() failure.
 *
 * Protocol is a plain Messenger, not AIDL: one request type (speak text with a
 * given voice), one client-supplied replyTo for DONE/ERROR. Deliberately no
 * streaming of PCM across the IPC boundary — this process also owns the
 * AudioTrack and plays the audio itself, so nothing but text crosses processes.
 */
class VoiceEngineService : Service() {

    companion object {
        const val MSG_SPEAK = 1
        const val MSG_STOP = 2
        const val MSG_DONE = 10
        const val MSG_ERROR = 11

        const val ARG_VOICE_ID = "voiceId"
        const val ARG_VOICE_DIR = "voiceDir"
        const val ARG_TEXT = "text"
        const val ARG_SPEED = "speed"
        const val ARG_ERROR = "error"
    }

    private val thread = HandlerThread("voice-engine").apply { start() }
    private val scope = CoroutineScope(SupervisorJob() + Handler(thread.looper).asCoroutineDispatcher())

    private var loadedTts: SherpaTts? = null
    private var loadedVoiceId: String? = null
    private var player: StreamingPcmPlayer? = null
    private var speakJob: Job? = null

    private val messenger = Messenger(
        Handler(thread.looper) { msg ->
            when (msg.what) {
                MSG_SPEAK -> handleSpeak(msg.data, msg.replyTo)
                MSG_STOP -> stopCurrent()
            }
            true
        },
    )

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun stopCurrent() {
        speakJob?.cancel()
        speakJob = null
        runCatching { player?.stop(); player?.release() }
        player = null
    }

    private fun handleSpeak(data: Bundle, replyTo: android.os.Messenger?) {
        stopCurrent()
        val voiceId = data.getString(ARG_VOICE_ID)
        val voiceDir = data.getString(ARG_VOICE_DIR)
        val text = data.getString(ARG_TEXT)
        val speed = data.getFloat(ARG_SPEED, 1.0f)
        if (voiceId == null || voiceDir == null || text == null) {
            reply(replyTo, MSG_ERROR, "missing arguments")
            return
        }

        speakJob = scope.launch {
            try {
                if (loadedVoiceId != voiceId) {
                    loadedTts?.release()
                    loadedTts = SherpaTts.load(File(voiceDir)) ?: error("could not load voice model")
                    loadedVoiceId = voiceId
                }
                val tts = loadedTts ?: error("voice not loaded")
                val sentences = text.split(Regex("(?<=[.!?…])\\s+")).filter { it.isNotBlank() }
                val activePlayer = StreamingPcmPlayer(tts.sampleRate())
                player = activePlayer
                activePlayer.start()
                for (sentence in sentences) {
                    tts.speak(sentence, speed) { chunk -> activePlayer.write(chunk) }
                }
                activePlayer.stop()
                activePlayer.release()
                player = null
                reply(replyTo, MSG_DONE, null)
            } catch (e: Exception) {
                reply(replyTo, MSG_ERROR, e.message ?: "speak failed")
            }
        }
    }

    private fun reply(replyTo: android.os.Messenger?, what: Int, error: String?) {
        val message = Message.obtain(null, what)
        if (error != null) message.data = Bundle().apply { putString(ARG_ERROR, error) }
        runCatching { replyTo?.send(message) }
    }

    override fun onDestroy() {
        stopCurrent()
        loadedTts?.release()
        thread.quitSafely()
        super.onDestroy()
    }
}
