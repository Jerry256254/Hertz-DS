package com.hertzds.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hertzds.deepseek.Models
import com.hertzds.ui.AppVm
import com.hertzds.ui.HandsFreeUi
import com.hertzds.ui.TurnState
import com.hertzds.ui.theme.hertzSemantic
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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val sem = hertzSemantic()

    val chatList by vm.chatList.collectAsStateWithLifecycle()
    val currentChat by vm.currentChat.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val pending by vm.pendingAttachments.collectAsStateWithLifecycle()
    val turn by vm.turn.collectAsStateWithLifecycle()
    val handsFree by vm.handsFree.collectAsStateWithLifecycle()
    val remainingUsd by vm.remainingUsd.collectAsStateWithLifecycle()
    val snackbarMessage by vm.snackbar.collectAsStateWithLifecycle()

    val snackbarHost = remember { SnackbarHostState() }
    var showNewGhostDialog by rememberSaveable { mutableStateOf(false) }
    var showModelPicker by rememberSaveable { mutableStateOf(false) }
    var renameTarget by rememberSaveable { mutableStateOf<String?>(null) }

    // permissions & pickers -----------------------------------------------------
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setHandsFree(true)
        else vm.showSnackbar("Mikrofon je potřeba pro hands-free režim.")
    }
    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.addAttachment(context, uri)
        }
    }

    // snackbar ------------------------------------------------------------------
    LaunchedEffect(snackbarMessage) {
        val message = snackbarMessage ?: return@LaunchedEffect
        if (message.startsWith("LOW_CREDITS:")) {
            snackbarHost.showSnackbar("Docházejí kredity: zbývá ${message.removePrefix("LOW_CREDITS:")}$ · dobijte klíč")
            vm.consumeSnackbar()
        } else {
            snackbarHost.showSnackbar(message)
            vm.consumeSnackbar()
        }
    }

    // auto-scroll -----------------------------------------------------------------
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
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
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding(),
        ) {
            TopBar(
                chatTitle = currentChat?.title,
                chatModel = currentChat?.model,
                toolsEnabled = currentChat?.toolsEnabled != false,
                remainingUsd = remainingUsd,
                onMenu = { scope.launch { drawerState.open() } },
                onModelClick = { showModelPicker = true },
                onCreditClick = onOpenKeys,
                onRename = { renameTarget = currentChat?.id },
                onToggleTools = { currentChat?.let { vm.setChatTools(it.id, !it.toolsEnabled) } },
                onPinChat = { currentChat?.let { vm.pinChat(it.id, !it.pinned) } },
                onClearHistory = { currentChat?.let { vm.clearChatHistory(it.id) } },
                onMemory = onOpenMemory,
                onTasks = onOpenTasks,
                onSettings = onOpenSettings,
            )

            HandsFreeStrip(handsFree, onStop = { vm.setHandsFree(false) })

            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(messages, key = { it.id }) { message -> MessageRow(message) }
                    when (val t = turn) {
                        is TurnState.Running -> item { ToolTicker(t) }
                        else -> Unit
                    }
                }
                if (messages.isEmpty() && turn is TurnState.Idle && handsFree == HandsFreeUi.Off) {
                    EmptyHero(
                        onSuggestion = { text -> vm.send(text) },
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                    )
                }
            }

            // composer zone
            Column(
                Modifier
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Composer(
                    enabled = turn is TurnState.Idle,
                    pendingAttachments = pending,
                    onAttach = {
                        attachLauncher.launch(arrayOf("image/*", "text/*", "application/pdf", "application/json"))
                    },
                    onRemoveAttachment = vm::removePendingAttachment,
                    onSend = { text ->
                        vm.stopSpeakingIfIdle()
                        vm.send(text)
                    },
                    onStopSend = vm::stop,
                    handsFreeActive = handsFree != HandsFreeUi.Off && handsFree !is HandsFreeUi.Failed,
                    onToggleHandsFree = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        val activeNow = handsFree != HandsFreeUi.Off && handsFree !is HandsFreeUi.Failed
                        when {
                            activeNow -> vm.setHandsFree(false)
                            granted -> vm.setHandsFree(true)
                            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                )
            }
        }
    }

    // dialogs -------------------------------------------------------------------
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
    renameTarget?.let { chatId ->
        RenameDialog(
            initial = chatList.firstOrNull { it.id == chatId }?.title.orEmpty(),
            onDismiss = { renameTarget = null },
            onRename = { vm.renameChat(chatId, it); renameTarget = null },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar — transparent instrument header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    chatTitle: String?,
    chatModel: String?,
    toolsEnabled: Boolean,
    remainingUsd: Double?,
    onMenu: () -> Unit,
    onModelClick: () -> Unit,
    onCreditClick: () -> Unit,
    onRename: () -> Unit,
    onToggleTools: () -> Unit,
    onPinChat: () -> Unit,
    onClearHistory: () -> Unit,
    onMemory: () -> Unit,
    onTasks: () -> Unit,
    onSettings: () -> Unit,
) {
    val sem = hertzSemantic()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(
            Icons.Filled.Menu, "Ghost chaty",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable(onClick = onMenu)
                .padding(11.dp),
        )

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                chatTitle ?: "Hertz-DS",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Models.label(chatModel ?: "").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onModelClick),
                )
                if (!toolsEnabled) {
                    Text(
                        "· nástroje off",
                        style = MaterialTheme.typography.labelSmall,
                        color = hertzSemantic().warning,
                    )
                }
            }
        }

        // credit pill
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(1.dp, sem.hairline),
            onClick = onCreditClick,
        ) {
            Text(
                remainingUsd?.let { "$%.2f".format(it) } ?: "$—",
                style = MaterialTheme.typography.labelMedium,
                color = hertzSemantic().positive,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }

        var menuOpen by rememberSaveable { mutableStateOf(false) }
        Box {
            Icon(
                Icons.Filled.MoreHoriz, "Nabídka",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable { menuOpen = true }
                    .padding(11.dp),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Přejmenovat") }, onClick = { menuOpen = false; onRename() })
                DropdownMenuItem(text = { Text(if (toolsEnabled) "Vypnout nástroje" else "Zapnout nástroje") }, onClick = { menuOpen = false; onToggleTools() })
                DropdownMenuItem(text = { Text("Připíchat nahoru") }, onClick = { menuOpen = false; onPinChat() })
                DropdownMenuItem(text = { Text("Smazat historii") }, onClick = { menuOpen = false; onClearHistory() })
                DropdownMenuItem(text = { Text("Paměť agenta") }, onClick = { menuOpen = false; onMemory() })
                DropdownMenuItem(text = { Text("Naplánované úlohy") }, onClick = { menuOpen = false; onTasks() })
                DropdownMenuItem(text = { Text("Nastavení") }, onClick = { menuOpen = false; onSettings() })
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hands-free strip + tool ticker
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HandsFreeStrip(state: HandsFreeUi, onStop: () -> Unit) {
    val sem = hertzSemantic()
    when (state) {
        HandsFreeUi.Off -> Unit
        is HandsFreeUi.Failed -> Text(
            "Hands-free se nepodařilo spustit (${state.message})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 22.dp),
        )

        else -> {
            val transition = rememberInfiniteTransition(label = "hf")
            val pulse by transition.animateFloat(
                initialValue = 0.35f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "pulse",
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .alpha(pulse)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                    val label = when (state) {
                        HandsFreeUi.Listening -> "poslouchám…"
                        is HandsFreeUi.Heard -> state.partial.ifBlank { "…"}
                        HandsFreeUi.Thinking -> "přemýšlím…"
                        HandsFreeUi.Speaking -> "mluvím…"
                        else -> ""
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                    )
                    Icon(
                        Icons.Filled.Stop, "Zastavit hands-free",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onStop)
                            .padding(7.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolTicker(state: TurnState.Running) {
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
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Text(
            buildString {
                append("pracuji")
                state.toolName?.let { append(" · $it") }
                state.toolDetail?.let { append(" · $it") }
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyHero(onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    val suggestions = listOf(
        "Co je dnes v zahraničním tisku?" to "Prohledám web a shrnu události.",
        "Ulož si: heslo na Wi-Fi je hertz2026" to "Zapíše do dlouhodobé paměti.",
        "Každý den v 8:00 mi shrň počasí" to "Naplánuje opakovanou úlohu.",
    )
    Column(modifier.padding(horizontal = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        WaveformMark(sizeDp = 34)
        Spacer(Modifier.height(18.dp))
        Text("Jak můžu pomoct?", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Text(
            "Agent s přístupem k webu, souborům a paměti.\nOdpovědi čte nahlas, pracuje i na pozadí.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(28.dp))
        suggestions.forEach { (prompt, hint) ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clickable { onSuggestion(prompt) },
            ) {
                Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
                    Text(prompt, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(hint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}
