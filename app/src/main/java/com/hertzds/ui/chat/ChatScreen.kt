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
import com.hertzds.deepseek.Models
import com.hertzds.ui.AppVm
import com.hertzds.ui.HandsFreeUi
import com.hertzds.ui.TurnState
import com.hertzds.ui.theme.HertzPalette
import com.hertzds.ui.theme.LocalStrings
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    vm: AppVm,
    onOpenKeys: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenTasks: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val str = LocalStrings.current

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
    var showModelPicker by rememberSaveable { mutableStateOf(false) }

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
        } else {
            snackbarHost.showSnackbar(message)
            vm.consumeSnackbar()
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF0A0E1C),
                drawerContentColor = Color(0xFFE7EAFB),
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
                .background(Color(0xFF080B14))
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

            Column(
                Modifier.fillMaxSize()
            ) {
                // Floating islands — properly inset below status bar
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
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )

                // Messages
                Box(Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(messages, key = { it.id }) { message ->
                            // In call mode, don't show full markdown — just plain
                            MessageRow(
                                message,
                                isCallMode = isInCall,
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
                    if (messages.isEmpty() && turn is TurnState.Idle && !isInCall && !isDictating) {
                        EmptyHero(
                            onSuggestion = { text ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                vm.send(text)
                            },
                            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                        )
                    }
                }

                // Composer or Call controls
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
                            currentModel = currentChat?.model ?: Models.FLASH,
                            onAttach = {
                                attachLauncher.launch(arrayOf("image/*", "text/*", "application/pdf", "application/json"))
                            },
                            onModelClick = { showModelPicker = true },
                            onRemoveAttachment = vm::removePendingAttachment,
                            onSend = { text ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6C74A0)),
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
    if (showModelPicker) {
        currentChat?.let { chat ->
            ModelPickerDialog(
                selected = chat.model,
                onSelect = { vm.setChatModel(chat.id, it); showModelPicker = false },
                onDismiss = { showModelPicker = false },
            )
        }
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
            color = Color(0xFF1A2140),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C355C)),
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
                color = Color(0xFF1A2140),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C355C)),
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
                color = if (ghostOn) HertzPalette.Signal else Color(0xFF1A2140),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (ghostOn) HertzPalette.Signal else Color(0xFF2C355C)),
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).clickable { onGhostToggle(!ghostOn) }
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_ghost),
                        if (ghostOn) str.ghostOn else str.ghostOff,
                        tint = Color.White,
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
            color = Color(0xFFA3ABD1),
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun EmptyHero(onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    val str = LocalStrings.current
    val suggestions = listOf(
        str.suggestion1Prompt to str.suggestion1Hint,
        str.suggestion2Prompt to str.suggestion2Hint,
        str.suggestion3Prompt to str.suggestion3Hint,
    )
    Column(modifier.padding(horizontal = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        WaveformMark(sizeDp = 34)
        Spacer(Modifier.height(18.dp))
        Text(str.heroTitle, style = MaterialTheme.typography.displaySmall.copy(color = Color.White), textAlign = TextAlign.Center)
        Text(
            str.heroSubtitle,
            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFA3ABD1)),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(28.dp))
        suggestions.forEach { (prompt, hint) ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1A2140),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C355C)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clickable { onSuggestion(prompt) },
            ) {
                Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
                    Text(prompt, style = MaterialTheme.typography.titleMedium.copy(color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(hint, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFA3ABD1)), modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}
