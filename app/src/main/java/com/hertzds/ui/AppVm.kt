package com.hertzds.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hertzds.AppContainer
import com.hertzds.agent.AgentEngine
import com.hertzds.agent.AgentEvent
import com.hertzds.data.db.AttachmentEntity
import com.hertzds.data.db.ChatEntity
import com.hertzds.data.prefs.Settings
import com.hertzds.data.repo.MessageRole
import com.hertzds.data.repo.MessageStatus
import com.hertzds.deepseek.ApiMessage
import com.hertzds.deepseek.ChatRequest
import com.hertzds.deepseek.Models
import com.hertzds.tools.OcrBridge
import com.hertzds.voice.HandsFreeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Everything above the message list: what the agent is doing right now. */
sealed interface TurnState {
    data object Idle : TurnState
    data class Running(
        val toolName: String? = null,
        val toolDetail: String? = null,
    ) : TurnState
}

sealed interface HandsFreeUi {
    data object Off : HandsFreeUi
    data object Listening : HandsFreeUi
    data class Heard(val partial: String) : HandsFreeUi
    data object Thinking : HandsFreeUi
    data object Speaking : HandsFreeUi
    data class Failed(val message: String) : HandsFreeUi
}

class AppVm(private val container: AppContainer) : ViewModel() {

    companion object {
        const val DEFAULT_CHAT_TITLE = "Nový chat"
    }

    private val chats = container.chats
    private val keys = container.keys
    private val voice get() = container.voiceManager

    val settings: StateFlow<Settings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val chatList: StateFlow<List<ChatEntity>> = chats.chats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId.asStateFlow()

    val currentChat: StateFlow<ChatEntity?> =
        combine(_currentChatId, chats.chats) { id, list -> list.firstOrNull { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = MutableStateFlow<List<com.hertzds.data.db.MessageEntity>>(emptyList())
    val messages: StateFlow<List<com.hertzds.data.db.MessageEntity>> = _messages.asStateFlow()

    private val _pendingAttachments = MutableStateFlow<List<AttachmentEntity>>(emptyList())
    val pendingAttachments: StateFlow<List<AttachmentEntity>> = _pendingAttachments.asStateFlow()

    private val _turn = MutableStateFlow<TurnState>(TurnState.Idle)
    val turn: StateFlow<TurnState> = _turn.asStateFlow()

    private val _handsFree = MutableStateFlow<HandsFreeUi>(HandsFreeUi.Off)
    val handsFree: StateFlow<HandsFreeUi> = _handsFree.asStateFlow()

    /** Total USD left across enabled keys, refreshed after every billed round trip. */
    private val _remainingUsd = MutableStateFlow<Double?>(null)
    val remainingUsd: StateFlow<Double?> = _remainingUsd.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    private var turnJob: Job? = null
    private var handsFreeJob: Job? = null
    private var messagesJob: Job? = null
    private val sendMutex = Mutex()
    private var lastAlertAt = 0L

    init {
        viewModelScope.launch {
            settings.filterNotNull().first { it.eulaAccepted } // gate: nothing loads before consent
            openMostRecentChat()
            refreshCredits()
        }
    }

    fun consumeSnackbar() { _snackbar.value = null }

    fun showSnackbar(message: String) { _snackbar.value = message }

    // ---- chat lifecycle -------------------------------------------------------

    fun openMostRecentChat() {
        viewModelScope.launch {
            val s = settings.value ?: return@launch
            val existing = _currentChatId.value?.let { chats.getChat(it) }
            val chat = existing ?: chats.chats.first().firstOrNull() ?: chats.ensureChat(
                model = s.defaultModel,
                systemPrompt = null,
                fallbackTitle = DEFAULT_CHAT_TITLE,
            )
            openChat(chat.id)
        }
    }

    fun openChat(chatId: String) {
        _currentChatId.value = chatId
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chats.observeMessages(chatId)
                .catch { _snackbar.value = it.message }
                .collect { _messages.value = it }
        }
    }

    fun newGhostChat(model: String?, systemPrompt: String?, title: String?) {
        viewModelScope.launch {
            val s = settings.value ?: return@launch
            val chat = chats.createChat(
                title = title?.takeIf { it.isNotBlank() } ?: "Ghost ${chatList.value.size + 1}",
                model = model ?: s.defaultModel,
                systemPrompt = systemPrompt?.takeIf { it.isNotBlank() },
            )
            openChat(chat.id)
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            chats.deleteChat(chatId)
            if (_currentChatId.value == chatId) {
                _currentChatId.value = null
                openMostRecentChat()
            }
        }
    }

    fun clearChatHistory(chatId: String) = viewModelScope.launch { chats.clearMessages(chatId) }

    fun renameChat(chatId: String, title: String) =
        viewModelScope.launch { chats.rename(chatId, title.trim(), auto = false) }

    fun pinChat(chatId: String, pinned: Boolean) =
        viewModelScope.launch { chats.setPinned(chatId, pinned) }

    fun setChatModel(chatId: String, model: String) {
        viewModelScope.launch {
            chats.getChat(chatId)?.let { chats.updateChat(it.copy(model = model)) }
        }
    }

    fun setChatTools(chatId: String, enabled: Boolean) {
        viewModelScope.launch {
            chats.getChat(chatId)?.let { chats.updateChat(it.copy(toolsEnabled = enabled)) }
        }
    }

    fun setChatTemperature(chatId: String, temperature: Double?) {
        viewModelScope.launch {
            chats.getChat(chatId)?.let { chats.updateChat(it.copy(temperature = temperature)) }
        }
    }

    // ---- attachments ----------------------------------------------------------

    fun addAttachment(context: Context, uri: Uri) {
        viewModelScope.launch {
            val chatId = _currentChatId.value ?: return@launch
            val resolver = context.contentResolver
            val (name, size) = queryMeta(resolver, uri)
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val kind = when {
                mime.startsWith("image/") -> "image"
                mime.contains("pdf") -> "pdf"
                mime.startsWith("text/") || mime.contains("json") ||
                    mime.contains("csv") || mime.contains("xml") -> "text"
                else -> "other"
            }
            val attachment = AttachmentEntity(
                id = java.util.UUID.randomUUID().toString(),
                messageId = null,
                chatId = chatId,
                uri = uri.toString(),
                name = name,
                mimeType = mime,
                sizeBytes = size,
                kind = kind,
                createdAt = System.currentTimeMillis(),
            )
            chats.addAttachment(attachment)
            _pendingAttachments.value += attachment

            // Extract text now so the model can use it even without vision support.
            viewModelScope.launch {
                val s = settings.value ?: return@launch
                val extracted = OcrBridge.extract(container.http, context, uri, attachment, s.mistralOcrKey)
                if (extracted != null) {
                    chats.setExtractedText(attachment.id, extracted)
                    _pendingAttachments.value = _pendingAttachments.value.map {
                        if (it.id == attachment.id) it.copy(extractedText = extracted.takeIf { t -> t.isNotBlank() }) else it
                    }
                }
            }
        }
    }

    fun removePendingAttachment(id: String) {
        viewModelScope.launch {
            chats.deleteAttachment(id)
            _pendingAttachments.value = _pendingAttachments.value.filterNot { it.id == id }
        }
    }

    private fun queryMeta(resolver: android.content.ContentResolver, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "file"
        var size = 0L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0) ?: name
                    size = if (cursor.isNull(1)) 0L else cursor.getLong(1)
                }
            }
        }
        return name to size
    }

    // ---- the main send pipeline ----------------------------------------------

    fun send(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch { runTurn(text) }
    }

    fun stop() {
        turnJob?.cancel()
        turnJob = null
        voice.stopSpeaking()
        _turn.value = TurnState.Idle
    }

    /** Used by the composer: kill leftover TTS from a previous answer before sending. */
    fun stopSpeakingIfIdle() {
        if (_turn.value is TurnState.Idle) voice.stopSpeaking()
    }

    /**
     * One full exchange: persist the user message, drive the agent loop, speak the
     * answer sentence-by-sentence while it streams. Returns the final text.
     */
    private suspend fun runTurn(userText: String): String = sendMutex.withLock {
        val s = settings.value ?: return ""
        var chatId = _currentChatId.value
        if (chatId == null) {
            openMostRecentChat()
            chatId = _currentChatId.value ?: return ""
        }

        val userMessage = chats.newMessage(chatId, MessageRole.USER, userText)
        chats.addMessage(userMessage)
        val pendings = _pendingAttachments.value
        if (pendings.isNotEmpty()) {
            pendings.forEach { chats.attachToMessage(it.id, userMessage.id) }
            _pendingAttachments.value = emptyList()
        }

        _turn.value = TurnState.Running()

        // Sentence queue: deltas are chopped into sentences and spoken immediately.
        val ttsEnabled = s.streamingTts
        val ttsQueue = Channel<String>(Channel.UNLIMITED)
        val ttsJob = if (ttsEnabled) launchSpeaker(ttsQueue, s) else null
        val splitter = SentenceSplitter()

        var finalText = ""
        var failure: String? = null
        try {
            val job = viewModelScope.launch {
                try {
                    container.agentEngine.runTurn(chatId, s).collect { event ->
                        when (event) {
                            is AgentEvent.Delta -> {
                                finalText += event.text
                                if (ttsEnabled) {
                                    splitter.feed(event.text).forEach { ttsQueue.trySend(it) }
                                }
                            }

                            is AgentEvent.ToolStarted ->
                                _turn.value = TurnState.Running(event.toolName, event.detail.ifBlank { null })

                            is AgentEvent.Spent -> {
                                refreshCredits()
                                maybeCreditAlert(s)
                            }

                            is AgentEvent.Failed -> failure = event.message
                            else -> Unit
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failure = failure ?: e.message ?: "error"
                }
            }
            turnJob = job
            job.join()
        } finally {
            if (ttsEnabled) {
                val remainder = splitter.flush()
                if (remainder.isNotBlank()) ttsQueue.trySend(remainder)
                ttsQueue.close()
                ttsJob?.join()
            }
        }

        _turn.value = TurnState.Idle
        if (failure != null) _snackbar.value = failure
        if (finalText.isBlank() && ttsEnabled) voice.stopSpeaking()

        maybeAutoName(chatId)
        return finalText
    }

    private fun launchSpeaker(queue: Channel<String>, s: Settings) = viewModelScope.launch {
        for (sentence in queue) {
            runCatching { voice.speak(sentence, s) }
                .onFailure { _snackbar.value = "TTS: ${it.message}" }
        }
    }

    private suspend fun refreshCredits() {
        _remainingUsd.value = runCatching { keys.totalRemainingUsd() }.getOrNull()
    }

    private suspend fun maybeCreditAlert(s: Settings) {
        val remaining = _remainingUsd.value ?: return
        if (remaining < s.creditAlertUsd && System.currentTimeMillis() - lastAlertAt > 10 * 60_000L) {
            lastAlertAt = System.currentTimeMillis()
            _snackbar.value = "LOW_CREDITS:$remaining"
        }
    }

    /** Cheap one-shot completion that names a chat after its first exchange. */
    private fun maybeAutoName(chatId: String) {
        viewModelScope.launch {
            val s = settings.value ?: return@launch
            if (!s.autoNameChats) return@launch
            val chat = chats.getChat(chatId) ?: return@launch
            if (!chat.titleIsAuto || chats.userMessageCount(chatId) < 1) return@launch

            val key = keys.nextKey() ?: return@launch
            val transcript = chats.messages(chatId)
                .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
                .takeLast(4)
                .joinToString("\n") { "${it.role}: ${it.content.take(400)}" }

            runCatching {
                val response = container.deepSeekClient.complete(
                    key.secret,
                    ChatRequest(
                        model = Models.FLASH,
                        messages = listOf(
                            ApiMessage(
                                role = MessageRole.SYSTEM,
                                content = kotlinx.serialization.json.JsonPrimitive(
                                    "Invent a chat title (max 5 words, same language as the conversation). " +
                                        "Reply with the title only, no quotes.",
                                ),
                            ),
                            ApiMessage(role = "user", content = kotlinx.serialization.json.JsonPrimitive(transcript)),
                        ),
                        temperature = 0.3,
                        maxTokens = 24,
                    ),
                )
                val title = response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
                    .removeSurrounding("\"").take(60)
                if (title.isNotBlank()) chats.rename(chatId, title, auto = false)
            }
        }
    }

    // ---- hands-free -----------------------------------------------------------

    fun setHandsFree(on: Boolean) {
        if (on) startHandsFree() else stopHandsFree()
    }

    private fun startHandsFree() {
        if (handsFreeJob?.isActive == true) return
        val s = settings.value ?: return
        if (!voice.hasMicPermission()) {
            _handsFree.value = HandsFreeUi.Failed("mic_permission_denied")
            return
        }
        handsFreeJob = viewModelScope.launch {
            var currentSettings = s
            try {
                while (isActive) {
                    currentSettings = settings.value ?: break
                    _handsFree.value = HandsFreeUi.Listening
                    val outcome = runCatching { voice.listenOnce(currentSettings).last() }
                    when (val state = outcome.getOrNull()) {
                        is HandsFreeState.Heard -> {
                            if (state.text.isBlank()) continue
                            _handsFree.value = HandsFreeUi.Thinking
                            runTurn(state.text)
                            _handsFree.value = HandsFreeUi.Speaking
                        }

                        is HandsFreeState.Error -> {
                            _handsFree.value = HandsFreeUi.Failed(state.message)
                            break
                        }

                        else -> {
                            _handsFree.value = HandsFreeUi.Failed("no_speech")
                            break
                        }
                    }
                    if (!(settings.value?.handsFree ?: false)) break
                }
            } finally {
                _handsFree.value = HandsFreeUi.Off
            }
        }
    }

    fun stopHandsFree() {
        handsFreeJob?.cancel()
        handsFreeJob = null
        voice.stopSpeaking()
        _handsFree.value = HandsFreeUi.Off
    }

    fun releaseVoice() {
        stopHandsFree()
        voice.release()
    }
}

/** Accumulates streamed text and yields complete sentences as soon as they end. */
class SentenceSplitter {
    private val builder = StringBuilder()
    private val boundary = Regex("(?<=[.!?…])\\s+")

    fun feed(text: String): List<String> {
        builder.append(text)
        val out = mutableListOf<String>()
        while (true) {
            val match = boundary.find(builder) ?: break
            val end = match.range.last + 1
            val sentence = builder.substring(0, end).trim()
            if (sentence.isNotEmpty()) out += sentence
            builder.delete(0, end)
        }
        return out
    }

    fun flush(): String = builder.toString().trim().also { builder.clear() }
}
