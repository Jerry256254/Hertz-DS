package com.hertzds.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Main-process side of [VoiceEngineService]. Binds lazily, rebinds after the
 * service's process dies (a native crash there shows up here as
 * onServiceDisconnected, never as an exception in this process), and turns the
 * whole exchange into one suspend call the rest of the app treats like any other
 * speak() — success or a caught failure, never a crash.
 */
class VoiceEngineClient(private val appContext: Context) {

    private var messenger: Messenger? = null
    private var binding = false
    private var pendingBinds = mutableListOf<CompletableDeferred<Boolean>>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            messenger = Messenger(binder)
            binding = false
            pendingBinds.forEach { it.complete(true) }
            pendingBinds.clear()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // The :voice process died (most likely a native crash in sherpa-onnx).
            // Drop the stale messenger; the next speak() call rebinds fresh.
            messenger = null
        }

        override fun onBindingDied(name: ComponentName?) {
            messenger = null
        }
    }

    private suspend fun ensureBound(): Messenger? {
        messenger?.let { return it }
        if (binding) {
            val deferred = CompletableDeferred<Boolean>()
            pendingBinds += deferred
            deferred.await()
            return messenger
        }
        binding = true
        val deferred = CompletableDeferred<Boolean>()
        pendingBinds += deferred
        val bound = runCatching {
            appContext.bindService(
                Intent(appContext, VoiceEngineService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
        }.getOrDefault(false)
        if (!bound) {
            binding = false
            pendingBinds.remove(deferred)
            deferred.complete(false)
            return null
        }
        return try {
            withTimeout(4_000) { deferred.await() }
            messenger
        } catch (e: TimeoutCancellationException) {
            null
        }
    }

    /** Speaks [text] with the given voice; returns true on success, false on any failure. */
    suspend fun speak(voiceId: String, voiceDirPath: String, text: String, speed: Float): Boolean {
        val target = ensureBound() ?: return false
        val result = CompletableDeferred<Boolean>()
        val replyMessenger = Messenger(
            android.os.Handler(android.os.Looper.getMainLooper()) { msg ->
                when (msg.what) {
                    VoiceEngineService.MSG_DONE -> result.complete(true)
                    VoiceEngineService.MSG_ERROR -> result.complete(false)
                }
                true
            },
        )
        val message = Message.obtain(null, VoiceEngineService.MSG_SPEAK).apply {
            data = android.os.Bundle().apply {
                putString(VoiceEngineService.ARG_VOICE_ID, voiceId)
                putString(VoiceEngineService.ARG_VOICE_DIR, voiceDirPath)
                putString(VoiceEngineService.ARG_TEXT, text)
                putFloat(VoiceEngineService.ARG_SPEED, speed)
            }
            replyTo = replyMessenger
        }
        return try {
            target.send(message)
            // No hard timeout: TTS of a long answer can legitimately take a while.
            // If the service process dies mid-speak, onServiceDisconnected fires and
            // we fall back below — but result may never complete in that exact race,
            // so bound it generously rather than hang forever.
            withTimeout(120_000) { result.await() }
        } catch (e: RemoteException) {
            messenger = null
            false
        } catch (e: TimeoutCancellationException) {
            false
        }
    }

    fun stop() {
        val target = messenger ?: return
        runCatching { target.send(Message.obtain(null, VoiceEngineService.MSG_STOP)) }
    }

    fun release() {
        if (messenger != null || binding) {
            runCatching { appContext.unbindService(connection) }
        }
        messenger = null
        binding = false
    }
}
