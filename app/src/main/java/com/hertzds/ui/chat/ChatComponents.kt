package com.hertzds.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hertzds.data.db.AttachmentEntity
import com.hertzds.data.db.ChatEntity
import com.hertzds.deepseek.Models
import com.hertzds.ui.theme.hertzSemantic

// ─────────────────────────────────────────────────────────────────────────────
// Composer — floating instrument panel: hairline pill, borderless input,
// circular send node that morphs into stop while the agent runs.
// ─────────────────────────────────────────────────────────────────────────────

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
    val sem = hertzSemantic()

    Column(Modifier.fillMaxWidth()) {
        if (pendingAttachments.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
            ) {
                pendingAttachments.take(4).forEach { attachment ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 6.dp, top = 5.dp, bottom = 5.dp, end = 4.dp),
                        ) {
                            if (attachment.kind == "image") {
                                AsyncImage(
                                    model = attachment.uri,
                                    contentDescription = attachment.name,
                                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)),
                                )
                            } else {
                                Box(
                                    Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(sem.signalVeil),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("TXT", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Text(
                                attachment.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 7.dp).width(84.dp),
                            )
                            Icon(
                                Icons.Filled.Close, "Odebrat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .clickable { onRemoveAttachment(attachment.id) }
                                    .padding(2.dp),
                            )
                        }
                    }
                }
                if (pendingAttachments.size > 4) {
                    Box(Modifier.align(Alignment.CenterVertically)) {
                        Text("+${pendingAttachments.size - 4}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, sem.hairline),
        ) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(all = 6.dp)) {
                // attach
                CircleIconButton(
                    icon = Icons.Filled.AttachFile,
                    label = "Připojit soubor",
                    enabled = enabled,
                ) { onAttach() }

                // input
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f).padding(top = 4.dp),
                    placeholder = {
                        Text(
                            "Napiš zprávu…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                )

                // mic
                CircleIconButton(
                    icon = Icons.Filled.Mic,
                    label = "Hands-free",
                    enabled = true,
                    tint = if (handsFreeActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    filled = handsFreeActive,
                ) { onToggleHandsFree() }

                Spacer(Modifier.width(4.dp))

                // send / stop node
                val running = !enabled
                val nodeColor by animateColorAsState(
                    targetValue = when {
                        running -> MaterialTheme.colorScheme.error
                        draft.isNotBlank() -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    label = "node",
                )
                val nodeContent by animateColorAsState(
                    targetValue = when {
                        running || draft.isNotBlank() -> MaterialTheme.colorScheme.background
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "nodeContent",
                )
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(nodeColor)
                        .clickable {
                            when {
                                running -> onStopSend()
                                draft.isNotBlank() -> { onSend(draft); draft = "" }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (running) Icons.Filled.Stop else Icons.Filled.ArrowUpward,
                        contentDescription = if (running) "Zastavit" else "Odeslat",
                        tint = nodeContent,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val sem = hertzSemantic()
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (filled) sem.signalVeil else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ghost drawer — brand header, accent CTA, active-indicator chat list
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun WaveformMark(sizeDp: Int = 22) {
    val bars = listOf(0.45f, 0.85f, 1f, 0.62f, 0.34f)
    Row(
        horizontalArrangement = Arrangement.spacedBy((sizeDp * 0.11f).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bars.forEach { fraction ->
            Box(
                Modifier
                    .width((sizeDp * 0.14f).dp)
                    .height((sizeDp * fraction).dp.coerceAtLeast(2.dp))
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
            )
        }
    }
}

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
    onOpenSettings: () -> Unit,
) {
    val sem = hertzSemantic()
    Column(Modifier.fillMaxWidth()) {
        // brand
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
        ) {
            WaveformMark()
            Text(
                "HERTZ-DS",
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 2.sp),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        // new ghost CTA
        Box(
            Modifier
                .padding(horizontal = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(1.dp, sem.signalVeil, RoundedCornerShape(14.dp))
                .clickable { onNewGhost() }
                .padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                    "Nový ghost chat",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 9.dp),
                )
            }
        }

        Text(
            "KONVERZACE · ${chats.size}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 22.dp, top = 20.dp, bottom = 6.dp),
        )

        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)) {
            items(chats, key = { it.id }) { chat ->
                val active = chat.id == currentId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(chat.id) }
                        .padding(start = 14.dp, end = 12.dp),
                ) {
                    // active rail
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(38.dp)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                                RoundedCornerShape(2.dp),
                            ),
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Text(
                            (if (chat.pinned) "◆ " else "") + chat.title,
                            style = if (active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                            color = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${Models.label(chat.model)} · $%.4f".format(chat.totalCostUsd),
                            style = MaterialTheme.typography.labelSmall,
                            color = hertzSemantic().faintText,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Icon(
                        Icons.Filled.Close,
                        "Smazat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(15.dp)
                            .clip(CircleShape)
                            .clickable { onDelete(chat.id) }
                            .padding(1.dp),
                    )
                }
            }
        }

        // footer: credits + settings
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .clickable { onOpenKeys() }
                .padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("KREDITY", style = MaterialTheme.typography.labelSmall)
                Text(
                    remainingUsd?.let { "$%.2f".format(it) } ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    color = hertzSemantic().positive,
                )
            }
            TextButton(onClick = onOpenKeys) { Text("Spravovat") }
            CircleIconButton(Icons.Filled.Settings, "Nastavení", enabled = true, onClick = onOpenSettings)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dialogs
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGhostDialog(
    models: List<String>,
    onCreate: (title: String?, model: String?, systemPrompt: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf(models.first()) }

    HertzDialog(
        title = "Nový ghost chat",
        dismissLabel = "Zrušit",
        confirmLabel = "Vytvořit",
        onDismiss = onDismiss,
        onConfirm = { onCreate(title, model, prompt) },
    ) {
        DialogField(title, { title = it }, "Název (volitelné)", singleLine = true)
        ModelDropdown(selected = model, models = models, onSelect = { model = it })
        DialogField(prompt, { prompt = it }, "System prompt (volitelné)", minLines = 3, maxLines = 6)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    HertzDialog(
        title = "Model",
        dismissLabel = "Zavřít",
        confirmLabel = "",
        onDismiss = onDismiss,
        onConfirm = {},
    ) {
        Models.ALL.forEach { id ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(id) }
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(selected = id == selected, onClick = { onSelect(id) })
                Column(Modifier.padding(start = 2.dp)) {
                    Text(Models.label(id), style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (id) {
                            Models.PRO -> "nejpřesnější · dražší"
                            Models.VISION -> "rozumí obrázkům"
                            else -> "rychlý · nejlevnější"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(selected: String, models: List<String>, onSelect: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val sem = hertzSemantic()
    Column {
        Text("MODEL", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 6.dp))
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, sem.hairline, RoundedCornerShape(12.dp))
                    .clickable { open = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(Models.label(selected), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                DropdownIcon(open)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                models.forEach { candidate ->
                    DropdownMenuItem(
                        text = { Text(Models.label(candidate)) },
                        onClick = { onSelect(candidate); open = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun DropdownIcon(rotated: Boolean) {
    Text(
        if (rotated) "▲" else "▼",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun RenameDialog(initial: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    HertzDialog(
        title = "Přejmenovat chat",
        dismissLabel = "Zrušit",
        confirmLabel = "Uložit",
        onDismiss = onDismiss,
        onConfirm = { onRename(value) },
    ) {
        DialogField(value, { value = it }, "Název", singleLine = true)
    }
}

/** Shared dialog chrome: flat surface, hairline border, mono section labels. */
@Composable
private fun HertzDialog(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(title, style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        },
        confirmButton = {
            if (confirmLabel.isNotBlank()) {
                TextButton(onClick = onConfirm) {
                    Text(confirmLabel, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
private fun DialogField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 1,
) {
    val sem = hertzSemantic()
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                unfocusedBorderColor = sem.hairline,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
