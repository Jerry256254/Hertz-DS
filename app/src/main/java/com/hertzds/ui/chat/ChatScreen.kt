package com.hertzds.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hertzds.R
import com.hertzds.data.repo.MessageRole
import com.hertzds.data.repo.MessageStatus
import com.hertzds.deepseek.Models
import com.hertzds.ui.AppVm
import com.hertzds.ui.HandsFreeUi
import com.hertzds.ui.TurnState
import com.hertzds.ui.theme.HertzPalette
import com.hertzds.ui.theme.LocalStrings
import com.hertzds.util.Haptics
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    vm: AppVm,
    onOpenKeys: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenNotes: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val vibe = remember(context) { Haptics(context) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val str = LocalStrings.current

    val settings by vm.settings.collectAsStateWithLifecycle()
    val hapticsEnabled = settings?.hapticsEnabled ?: true
    val chatList by vm.chatList.collectAsStateWithLifecycle()
    val currentChat by vm.currentChat.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val pending by vm.pendingAttachments.collectAsStateWithLifecycle()
    val turn by vm.turn.collectAsStateWithLifecycle()
    val handsFree by vm.handsFree.collectAsStateWithLifecycle()
    val remainingUsd by vm.remainingUsd.collectAsStateWithLifecycle()
    val snackbarMessage by vm.snackbar.collectAsStateWithLifecycle()
    val ghostMode by vm.ghostMode.collectAsStateWithLifecycle()
    val isDictating by vm.isDictating.collectAsStateWithLifecycle()
    val dictationRms by vm.dictationRms.collectAsStateWithLifecycle()
    val isInCall by vm.isInCall.collectAsStateWithLifecycle()
    val callRms by vm.callRms.collectAsStateWithLifecycle()
    val isCallUserSpeaking by vm.isCallUserSpeaking.collectAsStateWithLifecycle()
    val readingMessageId by vm.readingMessageId.collectAsStateWithLifecycle()

    val snackbarHost = remember { SnackbarHostState() }
    var showNewGhostDialog by rememberSaveable { mutableStateOf(false) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.showSnackbar(str.micPermissionRequired)
    }
    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.addAttachment(context, uri)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(snackbarMessage) {
        val message = snackbarMessage ?: return@LaunchedEffect
        if (message.startsWith("LOW_CREDITS:")) {
            snackbarHost.showSnackbar("${str.lowCreditsPrefix}${message.removePrefix("LOW_CREDITS:")}${str.lowCreditsSuffix}")
            vm.consumeSnackbar()
        } else if (message.startsWith("TTS_FALLBACK:")) {
            snackbarHost.showSnackbar(str.ttsFallbackWarning)
            vm.consumeSnackbar()
        } else {
            snackbarHost.showSnackbar(message)
            vm.consumeSnackbar()
        }
    }

    val listState = rememberLazyListState()
    val visibleCount = messages.count { it.role != MessageRole.TOOL } + (if (turn is TurnState.Running) 1 else 0)
    LaunchedEffect(visibleCount, messages.lastOrNull()?.content?.length) {
        if (visibleCount > 0) listState.animateScrollToItem(visibleCount - 1)
    }

    // A firm pulse when generation starts/lands, plus a light tick for every
    // chunk of text the AI streams in — driven off the real Vibrator, not
    // Compose's HapticFeedbackType, which several OEMs silently swallow.
    var wasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(turn) {
        if (!hapticsEnabled) { wasRunning = turn is TurnState.Running; return@LaunchedEffect }
        val running = turn is TurnState.Running
        if (running != wasRunning) vibe.strong()
        wasRunning = running
    }
    LaunchedEffect(messages.lastOrNull()?.content?.length, messages.lastOrNull()?.status) {
        if (!hapticsEnabled) return@LaunchedEffect
        val last = messages.lastOrNull() ?: return@LaunchedEffect
        if (last.role != MessageRole.ASSISTANT) return@LaunchedEffect
        if (last.status == MessageStatus.STREAMING && last.content.isNotEmpty()) vibe.tick()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF0A0A0A),
                drawerContentColor = Color(0xFFF2F2F2),
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                GhostChatDrawer(
                    chats = chatList,
                    currentId = currentChat?.id,
                    onSelect = { vm.openChat(it); scope.launch { drawerState.close() } },
                    onNewGhost = {
                        scope.launch { drawerState.close() }
                        showNewGhostDialog = true
                    },
                    onDelete = vm::deleteChat,
                    onPin = { id, pinned -> vm.pinChat(id, pinned) },
                    onRename = { id, title -> vm.renameChat(id, title) },
                    onOpenNotes = { scope.launch { drawerState.close() }; onOpenNotes() },
                    remainingUsd = remainingUsd,
                    onOpenKeys = { scope.launch { drawerState.close() }; onOpenKeys() },
                    onOpenSettings = { scope.launch { drawerState.close() }; onOpenSettings() },
                )
            }
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0A))
        ) {
            // Fog overlays for call mode — bottom for user, top for assistant
            if (isInCall) {
                if (isCallUserSpeaking) {
                    VoiceFog(
                        amplitude = callRms,
                        fromBottom = true,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                } else if (turn is TurnState.Running) {
                    VoiceFog(
                        amplitude = callRms,
                        fromBottom = false,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }

            // Content fills the whole screen and scrolls BEHIND the glass bars
            // above and below it, instead of being boxed between them.
            if (isInCall) {
                CallScreenBody(
                    isUserSpeaking = isCallUserSpeaking,
                    isThinking = turn is TurnState.Running,
                    amplitude = callRms,
                    lastAssistantText = messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.content.orEmpty(),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 118.dp, bottom = 190.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(messages.filter { it.role != MessageRole.TOOL }, key = { it.id }) { message ->
                        MessageRow(
                            message,
                            isCallMode = false,
                            isReading = readingMessageId == message.id,
                            onToggleReadAloud = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                vm.toggleReadAloud(message.id, message.content)
                            },
                        )
                    }
                    when (val t = turn) {
                        is TurnState.Running -> item { ToolTicker(t) }
                        else -> Unit
                    }
                }
                if (messages.isEmpty() && turn is TurnState.Idle && !isDictating) {
                    EmptyHero(
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                    )
                }
            }

            // Glass top bar — floats over the scrolling content, doesn't reserve space for it
            FloatingIslands(
                hasChat = currentChat != null && messages.isNotEmpty(),
                ghostEnabled = ghostMode,
                onMenuClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch { drawerState.open() }
                },
                onNewChat = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vm.newChat()
                },
                onGhostToggle = { enabled ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.toggleGhostMode(enabled)
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(GlassBrush(fromTop = true))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

            // Glass composer / call controls — floats over the scrolling content
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(GlassBrush(fromTop = false))) {
                if (isInCall) {
                    CallControls(
                        isMuted = false, // vm.callMuted.collectAsState not needed for stub
                        isPaused = false,
                        onMute = { vm.toggleCallMute(!it) },
                        onPause = { vm.toggleCallPause(!it) },
                        onEnd = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.endCall()
                        },
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                } else {
                    Column(
                        Modifier
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp, bottom = 4.dp)
                    ) {
                        ComposerV2(
                            enabled = turn is TurnState.Idle,
                            pendingAttachments = pending,
                            currentModel = currentChat?.model ?: settings?.defaultModel ?: Models.FLASH,
                            onAttach = {
                                attachLauncher.launch(arrayOf("image/*", "text/*", "application/pdf", "application/json"))
                            },
                            onSelectModel = { id ->
                                val chat = currentChat
                                if (chat != null) vm.setChatModel(chat.id, id) else vm.setDefaultModel(id)
                            },
                            onRemoveAttachment = vm::removePendingAttachment,
                            onSend = { text ->
                                if (hapticsEnabled) vibe.strong()
                                vm.stopSpeakingIfIdle()
                                vm.send(text)
                            },
                            onStopSend = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.stop()
                            },
                            isDictating = isDictating,
                            dictationRms = dictationRms,
                            onStartDictation = {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (!granted) micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                else {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.startDictation()
                                }
                            },
                            onStopDictation = { partial ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val text = vm.stopDictation()
                                // Insert into field is handled by ComposerV2 via callback
                            },
                            dictationPartial = vm.dictationPartial.collectAsStateWithLifecycle().value,
                            onStartCall = {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (!granted) micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                else {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.startCall()
                                }
                            },
                        )
                        Text(
                            str.disclaimer,
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6E6E6E)),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHost,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp)
            )
        }
    }

    if (showNewGhostDialog) {
        NewGhostDialog(
            models = Models.ALL,
            onDismiss = { showNewGhostDialog = false },
            onCreate = { title, model, prompt ->
                vm.newGhostChat(model, prompt, title)
                showNewGhostDialog = false
            },
        )
    }
}

@Composable
private fun FloatingIslands(
    hasChat: Boolean,
    ghostEnabled: Boolean,
    onMenuClick: () -> Unit,
    onNewChat: () -> Unit,
    onGhostToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val str = LocalStrings.current
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left island — drawer
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1E1E1E).copy(alpha = 0.94f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2A2A)),
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onMenuClick)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Menu, "Menu",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        // Right island — New chat when in a chat, else Ghost toggle. Icon-only:
        // the glyph plus the accent-blue "on" fill carries the meaning.
        if (hasChat) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1E1E1E).copy(alpha = 0.94f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2A2A)),
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onNewChat)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, str.newChat, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        } else {
            val ghostOn = ghostEnabled
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (ghostOn) HertzPalette.Signal else Color(0xFF1E1E1E),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (ghostOn) HertzPalette.Signal else Color(0xFF2A2A2A)),
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).clickable { onGhostToggle(!ghostOn) }
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_ghost),
                        if (ghostOn) str.ghostOn else str.ghostOff,
                        tint = if (ghostOn) HertzPalette.OnSignal else Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolTicker(state: TurnState.Running) {
    val str = LocalStrings.current
    val transition = rememberInfiniteTransition(label = "tick")
    val pulse by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "tickPulse",
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 2.dp)) {
        Box(
            Modifier
                .size(7.dp)
                .alpha(pulse)
                .background(Color.White, androidx.compose.foundation.shape.CircleShape),
        )
        Text(
            buildString {
                append(str.working)
                state.toolName?.let { append(" · $it") }
                state.toolDetail?.let { append(" · $it") }
            },
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFA8A8A8),
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun EmptyHero(modifier: Modifier = Modifier) {
    val str = LocalStrings.current
    Column(modifier.padding(horizontal = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BrandMark(sizeDp = 34)
        Spacer(Modifier.height(18.dp))
        Text(str.heroTitle, style = MaterialTheme.typography.displaySmall.copy(color = Color.White), textAlign = TextAlign.Center)
        Text(
            str.heroSubtitle,
            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFA8A8A8)),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
