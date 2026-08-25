package com.hertzds.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hertzds.data.db.AttachmentEntity
import com.hertzds.data.db.ChatEntity
import com.hertzds.deepseek.Models

// ---- composer ------------------------------------------------------------------

@Composable
fun Composer(
    enabled: Boolean,
    pendingAttachments: List<AttachmentEntity>,
    onAttach: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSend: (String) -> Unit,
    onStopSend: () -> Unit,
    handsFreeActive: Boolean,
    onToggleHandsFree: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }

    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (pendingAttachments.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    pendingAttachments.forEach { attachment ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 2.dp),
                            ) {
                                if (attachment.kind == "image") {
                                    AsyncImage(
                                        model = attachment.uri,
                                        contentDescription = attachment.name,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.InsertDriveFile,
                                        null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    attachment.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp).size(width = 90.dp, height = 16.dp),
                                )
                                IconButton(onClick = { onRemoveAttachment(attachment.id) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Filled.Close, "Odebrat přílohu", modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onAttach, enabled = enabled) {
                    Icon(Icons.Filled.AttachFile, "Připojit soubor")
                }

                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Zeptej se na cokoli…") },
                    maxLines = 6,
                    shape = RoundedCornerShape(24.dp),
                )

                Spacer(Modifier.size(6.dp))

                IconButton(onClick = onToggleHandsFree) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = if (handsFreeActive) "Zastavit hands-free" else "Hands-free",
                        tint = if (handsFreeActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.size(2.dp))

                FilledIconButton(
                    onClick = {
                        if (enabled && draft.isNotBlank()) {
                            onSend(draft)
                            draft = ""
                        } else if (!enabled) {
                            onStopSend()
                        }
                    },
                    modifier = Modifier.size(46.dp),
                ) {
                    Icon(
                        if (enabled) Icons.Filled.ArrowUpward else Icons.Filled.Stop,
                        contentDescription = if (enabled) "Odeslat" else "Zastavit",
                    )
                }
            }
        }
    }
}

// ---- ghost chats drawer ----------------------------------------------------------

@Composable
fun GhostChatDrawer(
    chats: List<ChatEntity>,
    currentId: String?,
    onSelect: (String) -> Unit,
    onNewGhost: () -> Unit,
    onDelete: (String) -> Unit,
    onPin: (String, Boolean) -> Unit,
    remainingUsd: Double?,
    onOpenKeys: () -> Unit,
) {
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
        item {
            TextButton(onClick = onNewGhost, modifier = Modifier.padding(horizontal = 12.dp)) {
                Icon(Icons.Filled.Add, null)
                Text("Nový ghost chat", modifier = Modifier.padding(start = 6.dp))
            }
        }
        items(chats, key = { it.id }) { chat ->
            Surface(
                color = if (chat.id == currentId) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                onClick = { onSelect(chat.id) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (chat.pinned) "📌 " else "") + chat.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            Models.label(chat.model) + " · $%.4f".format(chat.totalCostUsd),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onPin(chat.id, !chat.pinned) }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Filled.PushPin,
                            "Pin",
                            modifier = Modifier.size(15.dp),
                            tint = if (chat.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onDelete(chat.id) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.Delete, "Smazat", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Surface(
                onClick = onOpenKeys,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Kredity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        remainingUsd?.let { "$%.2f".format(it) } ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

// ---- dialogs ----------------------------------------------------------------------

@Composable
fun NewGhostDialog(
    models: List<String>,
    onCreate: (title: String?, model: String?, systemPrompt: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf(models.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nový ghost chat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Název (volitelné)") }, singleLine = true)
                ModelDropdown(selected = model, models = models, onSelect = { model = it })
                OutlinedTextField(
                    prompt,
                    { prompt = it },
                    label = { Text("System prompt (volitelné)") },
                    minLines = 3,
                    maxLines = 6,
                )
            }
        },
        confirmButton = { TextButton({ onCreate(title, model, prompt) }) { Text("Vytvořit") } },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}

@Composable
fun ModelPickerDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Model") },
        text = {
            Column {
                Models.ALL.forEach { id ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(selected = id == selected, onClick = { onSelect(id) })
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(Models.label(id), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                when (id) {
                                    Models.PRO -> "nejpřesnější · dražší"
                                    Models.VISION -> "umí obrázky"
                                    else -> "rychlý a levný"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text("Zavřít") } },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(selected: String, models: List<String>, onSelect: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = Models.label(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text("Model") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            models.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(Models.label(candidate)) },
                    onClick = { onSelect(candidate); open = false },
                )
            }
        }
    }
}

@Composable
fun RenameDialog(initial: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Přejmenovat chat") },
        text = { OutlinedTextField(value, { value = it }, singleLine = true) },
        confirmButton = { TextButton({ onRename(value) }) { Text("Uložit") } },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}
