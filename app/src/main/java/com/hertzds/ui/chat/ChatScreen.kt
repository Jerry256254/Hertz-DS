package com.hertzds.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.hertzds.data.repo.MessageRole
import com.hertzds.deepseek.Models
import com.hertzds.ui.AppVm
import com.hertzds.ui.HandsFreeUi
import com.hertzds.ui.TurnState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: AppVm,
    onOpenKeys: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenTasks: () -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val settings by vm.settings.collectAsStateWithLifecycle()
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

    // --- permissions & pickers -----------------------------------------------
    var wantHandsFree by rememberSaveable { mutableStateOf(false) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setHandsFree(true)
        else vm.showSnackbar("Mikrofon je potřeba pro hands-free režim.")
    }

    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.addAttachment(context, uri)
        }
    }

    // --- snackbar wiring -------------------------------------------------------
    LaunchedEffect(snackbarMessage) {
        val message = snackbarMessage ?: return@LaunchedEffect
        when {
            message.startsWith("LOW_CREDITS:") -> {
                val left = message.removePrefix("LOW_CREDITS:")
                snackbarHost.showSnackbar(
                    "Docházejí kredity: zbývá $left $. Dobijte klíč na DeepSeek.",
                    actionLabel = "Klíče",
                    duration = SnackbarDuration.Long,
                )
                vm.consumeSnackbar()
            }

            else -> {
                snackbarHost.showSnackbar(message)
                vm.consumeSnackbar()
            }
        }
    }

    // --- auto-scroll -----------------------------------------------------------
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                GhostChatDrawer(
                    chats = chatList,
                    currentId = currentChat?.id,
                    onSelect = {
                        vm.openChat(it)
                        scope.launch { drawerState.close() }
                    },
                    onNewGhost = { showNewGhostDialog = true },
                    onDelete = vm::deleteChat,
                    onPin = vm::pinChat,
                    remainingUsd = remainingUsd,
                    onOpenKeys = {
                        scope.launch { drawerState.close() }
                        onOpenKeys()
                    },
                )
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHost) },
            topBar = {
                Column {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, "Ghost chaty")
                            }
                        },
                        title = {
                            Column {
                                Text(currentChat?.title ?: "Hertz-DS", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    Models.label(currentChat?.model ?: "") +
                                        if (currentChat?.toolsEnabled == true) "" else " · nástroje vypnuty",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        actions = {
                            TextButton(onClick = onOpenKeys) {
                                Text(
                                    text = remainingUsd?.let { "$%.2f".format(it) } ?: "$—",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = when {
                                        remainingUsd == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                        remainingUsd!! < 1.0 -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.tertiary
                                    },
                                )
                            }
                            var menuOpen by rememberSaveable { mutableStateOf(false) }
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Nabídka") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Přepnout model") }, onClick = { menuOpen = false; showModelPicker = true })
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (currentChat?.toolsEnabled == true) "Vypnout nástroje" else "Zapnout nástroje"
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        currentChat?.let { vm.setChatTools(it.id, !it.toolsEnabled) }
                                    },
                                )
                                DropdownMenuItem(text = { Text("Přejmenovat") }, onClick = {
                                    menuOpen = false
                                    renameTarget = currentChat?.id
                                })
                                DropdownMenuItem(text = { Text("Pin do horní části") }, onClick = {
                                    menuOpen = false
                                    currentChat?.let { vm.pinChat(it.id, !(it.pinned)) }
                                }, leadingIcon = { Icon(Icons.Filled.PushPin, null) })
                                DropdownMenuItem(text = { Text("Smazat historii") }, onClick = {
                                    menuOpen = false
                                    currentChat?.let { vm.clearChatHistory(it.id) }
                                }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
                                DropdownMenuItem(text = { Text("Paměť agenta") }, onClick = { menuOpen = false; onOpenMemory() })
                                DropdownMenuItem(text = { Text("Naplánované úlohy") }, onClick = { menuOpen = false; onOpenTasks() })
                                DropdownMenuItem(text = { Text("Nastavení") }, onClick = { menuOpen = false; onOpenSettings() })
                            }
                        },
                    )
                    HandsFreeBar(handsFree, onStop = { vm.setHandsFree(false) })
                }
            },
            bottomBar = {
                Composer(
                    enabled = turn is TurnState.Idle,
                    pendingAttachments = pending,
                    onAttach = { attachLauncher.launch(arrayOf("image/*", "text/*", "application/pdf", "application/json")) },
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
                        if (handsFree != HandsFreeUi.Off && handsFree !is HandsFreeUi.Failed) {
                            vm.setHandsFree(false)
                        } else if (granted) {
                            vm.setHandsFree(true)
                        } else {
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageRow(message)
                }
                item {
                    when (val t = turn) {
                        is TurnState.Running -> ToolTicker(t)
                        else -> Unit
                    }
                }
            }
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

    renameTarget?.let { chatId ->
        RenameDialog(
            initial = chatList.firstOrNull { it.id == chatId }?.title.orEmpty(),
            onDismiss = { renameTarget = null },
            onRename = { vm.renameChat(chatId, it); renameTarget = null },
        )
    }
}

/** Slim status strip under the app bar while the hands-free loop runs. */
@Composable
private fun HandsFreeBar(state: HandsFreeUi, onStop: () -> Unit) {
    when (state) {
        HandsFreeUi.Off -> Unit
        is HandsFreeUi.Failed -> Text(
            "Hands-free se nepodařilo spustit (${state.message})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        else -> Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                TextButton(onClick = onStop) { Text("stop") }
            }
        }
    }
}

@Composable
private fun ToolTicker(state: TurnState.Running) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text = buildString {
                append("agent pracuje")
                state.toolName?.let { append(" · $it") }
                state.toolDetail?.let { append(" · $it") }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
