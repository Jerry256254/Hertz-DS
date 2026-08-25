package com.hertzds.ui.chat

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hertzds.R
import com.hertzds.data.db.AttachmentEntity
import com.hertzds.data.db.ChatEntity
import com.hertzds.deepseek.Models
import com.hertzds.ui.theme.LocalStrings

// ── Composer V2 — two rows, gently squared, pure black/white system ──

@Composable
fun ComposerV2(
    enabled: Boolean,
    pendingAttachments: List<AttachmentEntity>,
    currentModel: String,
    onAttach: () -> Unit,
    onModelClick: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSend: (String) -> Unit,
    onStopSend: () -> Unit,
    isDictating: Boolean,
    dictationRms: Float,
    onStartDictation: () -> Unit,
    onStopDictation: (String) -> Unit,
    dictationPartial: String,
    onStartCall: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current
    val str = LocalStrings.current

    // When dictation produces a partial, keep it visible but don't auto-insert yet
    var dictationBuffer by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(dictationPartial, isDictating) {
        if (isDictating && dictationPartial.isNotBlank()) dictationBuffer = dictationPartial
    }

    Column(Modifier.fillMaxWidth()) {
        if (pendingAttachments.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                pendingAttachments.take(4).forEach { attachment ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1C1E22),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2E36)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 6.dp, top = 5.dp, bottom = 5.dp, end = 4.dp),
                        ) {
                            if (attachment.kind == "image") {
                                AsyncImage(model = attachment.uri, contentDescription = attachment.name, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)))
                            } else {
                                Box(
                                    Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f)),
                                    contentAlignment = Alignment.Center
                                ) { Text("TXT", style = MaterialTheme.typography.labelSmall.copy(color = Color.White)) }
                            }
                            Text(attachment.name, style = MaterialTheme.typography.labelSmall.copy(color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 7.dp).width(84.dp))
                            Icon(
                                Icons.Filled.Close, "Remove",
                                tint = Color(0xFF9AA0AE),
                                modifier = Modifier.size(16.dp).clip(RoundedCornerShape(8.dp)).clickable { onRemoveAttachment(attachment.id) }.padding(2.dp),
                            )
                        }
                    }
                }
                if (pendingAttachments.size > 4) {
                    Box(Modifier.align(Alignment.CenterVertically)) { Text("+${pendingAttachments.size - 4}", style = MaterialTheme.typography.labelMedium.copy(color = Color.White)) }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1C1E22),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2E36)),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                // Row 1 — expanding text
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = if (isDictating && dictationBuffer.isNotBlank()) draft + (if (draft.isNotBlank()) " " else "") + dictationBuffer else draft,
                        onValueChange = { if (!isDictating) draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                str.askAnything,
                                style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF6B7280)),
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFECEFF3)),
                        maxLines = 8,
                        minLines = 1,
                        readOnly = isDictating,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = Color.White,
                        ),
                    )
                    if (isDictating) {
                        DictationWaveform(rms = dictationRms, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Row 2 — actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: attachment + model
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Attach
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF25282E),
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onAttach()
                            }
                        ) {
                            Icon(
                                Icons.Filled.Add, "Attach",
                                tint = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).size(18.dp)
                            )
                        }
                        // Model pill — gently squared
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF25282E),
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onModelClick() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Icon(painterResource(R.drawable.ic_model_spark), null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Text(
                                    Models.label(currentModel),
                                    style = MaterialTheme.typography.labelMedium.copy(color = Color.White),
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                    }

                    // Right: dictation / call vs send
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isDictating) {
                            // Stop dictation — inserts into text, does not auto-send
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val inserted = dictationBuffer
                                    draft = (draft + (if (draft.isNotBlank() && inserted.isNotBlank()) " " else "") + inserted).trim()
                                    dictationBuffer = ""
                                    onStopDictation(inserted)
                                }
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Stop, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Text(" " + str.stop, style = MaterialTheme.typography.labelMedium.copy(color = Color.Black))
                                }
                            }
                        } else if (draft.isBlank() && enabled) {
                            // Empty field: show dictation (mic) and call (phone) as separate buttons
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF25282E),
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable { onStartDictation() }
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(painterResource(R.drawable.ic_custom_mic), str.dictate, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onStartCall()
                                }
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Phone, str.call, tint = Color.Black, modifier = Modifier.size(18.dp))
                                }
                            }
                        } else {
                            // Has text: icon-only Send, morphs into Stop while generating
                            val running = !enabled
                            val bg by animateColorAsState(
                                targetValue = if (running) Color(0xFFFF4D4D) else Color.White,
                                label = "sendBg"
                            )
                            val fg = if (running) Color.White else Color.Black
                            val interaction = remember { MutableInteractionSource() }
                            val pressed by interaction.collectIsPressedAsState()
                            val scale by animateFloatAsState(
                                targetValue = if (pressed) 0.88f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                label = "sendScale",
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = bg,
                                modifier = Modifier
                                    .size(40.dp)
                                    .graphicsLayer { scaleX = scale; scaleY = scale }
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(interactionSource = interaction, indication = null) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        when {
                                            running -> onStopSend()
                                            draft.isNotBlank() -> { onSend(draft); draft = "" }
                                        }
                                    }
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Crossfade(targetState = running, label = "sendIcon") { isRunning ->
                                        if (isRunning) Icon(Icons.Filled.Stop, str.stop, tint = fg, modifier = Modifier.size(18.dp))
                                        else Icon(painterResource(R.drawable.ic_send), str.send, tint = fg, modifier = Modifier.size(17.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CallControls(
    isMuted: Boolean,
    isPaused: Boolean,
    onMute: (Boolean) -> Unit,
    onPause: (Boolean) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val str = LocalStrings.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1C1E22),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2E36)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onMute(!isMuted) }) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(if (isMuted) Color.White else Color(0xFF2A2E36)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(if (isMuted) R.drawable.ic_mic_muted else R.drawable.ic_custom_mic),
                        if (isMuted) str.unmute else str.mute,
                        tint = if (isMuted) Color.Black else Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(if (isMuted) str.unmute else str.mute, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF9AA0AE)), modifier = Modifier.padding(top = 6.dp))
            }
            // Pause
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPause(!isPaused) }) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2E36)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (isPaused) Icons.Filled.Mic else Icons.Filled.Pause, if (isPaused) str.resume else str.pause, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Text(if (isPaused) str.resume else str.pause, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF9AA0AE)), modifier = Modifier.padding(top = 6.dp))
            }
            // End — red
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onEnd() }) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFF3B30)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, str.end, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Text(str.end, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF9AA0AE)), modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color = Color.White,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
            .background(if (filled) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp)) }
}

// ── Ghost drawer — pure black, white accents, squared ──

@Composable
fun WaveformMark(sizeDp: Int = 22) {
    val bars = listOf(0.45f, 0.85f, 1f, 0.62f, 0.34f)
    Row(horizontalArrangement = Arrangement.spacedBy((sizeDp * 0.11f).dp), verticalAlignment = Alignment.CenterVertically) {
        bars.forEach { fraction ->
            Box(
                Modifier.width((sizeDp * 0.14f).dp).height((sizeDp * fraction).dp.coerceAtLeast(2.dp))
                    .background(Color.White, RoundedCornerShape(4.dp)),
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
    val haptics = LocalHapticFeedback.current
    val str = LocalStrings.current
    Column(Modifier.fillMaxWidth().background(Color(0xFF000000))) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp)) {
            WaveformMark()
            Text("HERTZ-DS", style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 2.sp, color = Color.White), modifier = Modifier.padding(start = 10.dp))
        }
        Box(
            Modifier.padding(horizontal = 14.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Color.White).clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNewGhost()
                }.padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Text(str.newGhostChat, style = MaterialTheme.typography.titleMedium.copy(color = Color.Black), modifier = Modifier.padding(start = 9.dp))
            }
        }
        Text("${str.conversations} · ${chats.size}", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)), modifier = Modifier.padding(start = 22.dp, top = 20.dp, bottom = 6.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(chats, key = { it.id }) { chat ->
                val active = chat.id == currentId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(chat.id)
                    }.padding(start = 14.dp, end = 12.dp),
                ) {
                    Box(
                        Modifier.width(3.dp).height(38.dp)
                            .background(if (active) Color.White else Color.Transparent, RoundedCornerShape(2.dp)),
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (chat.pinned) {
                                Icon(painterResource(R.drawable.ic_pin), null, tint = Color.White, modifier = Modifier.size(11.dp))
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(
                                chat.title,
                                style = if (active) MaterialTheme.typography.titleMedium.copy(color = Color.White) else MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF9AA0AE)),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text("${Models.label(chat.model)} · $%.4f".format(chat.totalCostUsd), style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)), modifier = Modifier.padding(top = 2.dp))
                    }
                    Icon(
                        Icons.Filled.Close, "Delete",
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(18.dp).clip(RoundedCornerShape(8.dp)).clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDelete(chat.id)
                        }.padding(3.dp),
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1C1E22)).border(1.dp, Color(0xFF2A2E36), RoundedCornerShape(14.dp))
                .clickable { onOpenKeys() }.padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(str.credits, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)))
                Text(remainingUsd?.let { "$%.2f".format(it) } ?: "—", style = MaterialTheme.typography.titleLarge.copy(color = Color.White))
            }
            TextButton(onClick = onOpenKeys) { Text(str.manage, color = Color.White) }
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2E36)).clickable { onOpenSettings() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Settings, "Settings", tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
    }
}

// ── Dialogs — custom to match pure black/white, squared ──

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
    val str = LocalStrings.current
    HertzDialog(title = str.newGhostChat, dismissLabel = str.cancel, confirmLabel = str.create, onDismiss = onDismiss, onConfirm = { onCreate(title, model, prompt) }) {
        DialogField(title, { title = it }, str.nameOptional, singleLine = true)
        ModelDropdown(selected = model, models = models, onSelect = { model = it })
        DialogField(prompt, { prompt = it }, str.systemPromptOptional, minLines = 3, maxLines = 6)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val str = LocalStrings.current
    HertzDialog(title = str.model, dismissLabel = str.close, confirmLabel = "", onDismiss = onDismiss, onConfirm = {}) {
        Models.ALL.forEach { id ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(id) }.padding(vertical = 4.dp),
            ) {
                RadioButton(selected = id == selected, onClick = { onSelect(id) }, colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = Color(0xFF6B7280)))
                Column(Modifier.padding(start = 2.dp)) {
                    Text(Models.label(id), style = MaterialTheme.typography.titleMedium.copy(color = Color.White))
                    Text(
                        when (id) {
                            Models.PRO -> str.modelProDesc
                            Models.VISION -> str.modelVisionDesc
                            else -> str.modelFlashDesc
                        }, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF9AA0AE))
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
    Column {
        Text("MODEL", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)), modifier = Modifier.padding(bottom = 6.dp))
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1C1E22)).border(1.dp, Color(0xFF2A2E36), RoundedCornerShape(12.dp))
                    .clickable { open = true }.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(Models.label(selected), style = MaterialTheme.typography.titleMedium.copy(color = Color.White), modifier = Modifier.weight(1f))
                Text(if (open) "▲" else "▼", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF9AA0AE)))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(Color(0xFF1C1E22))) {
                models.forEach { candidate ->
                    DropdownMenuItem(text = { Text(Models.label(candidate), color = Color.White) }, onClick = { onSelect(candidate); open = false })
                }
            }
        }
    }
}

@Composable
fun RenameDialog(initial: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val str = LocalStrings.current
    HertzDialog(title = str.renameChat, dismissLabel = str.cancel, confirmLabel = str.save, onDismiss = onDismiss, onConfirm = { onRename(value) }) {
        DialogField(value, { value = it }, str.name, singleLine = true)
    }
}

@Composable
private fun HertzDialog(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111214),
        shape = RoundedCornerShape(16.dp),
        title = { Text(title, style = MaterialTheme.typography.headlineSmall.copy(color = Color.White)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content) },
        confirmButton = {
            if (confirmLabel.isNotBlank()) TextButton(onClick = onConfirm) { Text(confirmLabel, color = Color.White) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel, color = Color(0xFF9AA0AE)) } },
    )
}

@Composable
private fun DialogField(value: String, onChange: (String) -> Unit, label: String, singleLine: Boolean = false, minLines: Int = 1, maxLines: Int = 1) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)), modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange, singleLine = singleLine, minLines = minLines, maxLines = maxLines,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White.copy(alpha = 0.6f),
                unfocusedBorderColor = Color(0xFF2A2E36),
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color(0xFF1C1E22),
                cursorColor = Color.White,
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFECEFF3)),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
