package com.hertzds.ui.chat

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hertzds.R
import com.hertzds.data.db.AttachmentEntity
import com.hertzds.data.db.ChatEntity
import com.hertzds.ui.theme.HertzPalette
import com.hertzds.ui.theme.LocalStrings

/**
 * A vertical fade from the app background color toward transparent — used behind
 * the floating top/bottom bars so scrolled chat content is genuinely visible
 * sliding past underneath them, glass-style, rather than being hidden by a
 * solid bar. (A true gaussian blur-through needs capturing the layer beneath,
 * which Compose has no simple built-in for; this fade is the achievable part.)
 */
fun GlassBrush(fromTop: Boolean): androidx.compose.ui.graphics.Brush {
    val base = Color(0xFF0A0A0A)
    // Matte, not see-through: content is still perceptibly there behind it,
    // but the bar reads as a frosted surface, not clear glass.
    val edge = base.copy(alpha = 0.97f)
    val middle = base.copy(alpha = 0.82f)
    return androidx.compose.ui.graphics.Brush.verticalGradient(
        if (fromTop) listOf(edge, edge, middle) else listOf(middle, edge, edge),
    )
}

// ── Composer V2 — two rows, gently squared, pure black/white system ──

@Composable
fun ComposerV2(
    enabled: Boolean,
    pendingAttachments: List<AttachmentEntity>,
    currentModel: String,
    modelOptions: List<String>,
    onAttach: () -> Unit,
    onSelectModel: (String) -> Unit,
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
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
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
                        color = Color(0xFF1E1E1E),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2A2A)),
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
                                tint = Color(0xFFA8A8A8),
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
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF1E1E1E).copy(alpha = 0.92f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2A2A)),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                // Row 1 — the text field, or a full-width live waveform while dictating
                if (isDictating) {
                    Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                        DictationWaveform(rms = dictationRms, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                str.askAnything,
                                style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF6E6E6E)),
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFF2F2F2)),
                        maxLines = 8,
                        minLines = 1,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = Color.White,
                        ),
                    )
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
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF272727),
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onAttach()
                            }
                        ) {
                            Icon(
                                Icons.Filled.Add, "Attach",
                                tint = Color.White,
                                modifier = Modifier.padding(10.dp).size(18.dp)
                            )
                        }
                        // Model pill — anchors its own dropdown right where it sits,
                        // not a full-screen dialog floating in the middle of the app.
                        var modelMenuOpen by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF272727),
                                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    modelMenuOpen = true
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Icon(painterResource(R.drawable.ic_model_spark), null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Text(
                                    currentModel.ifBlank { "—" },
                                    style = MaterialTheme.typography.labelMedium.copy(color = Color.White),
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                                    Text(
                                        if (modelMenuOpen) "▲" else "▼",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFA8A8A8)),
                                        modifier = Modifier.padding(start = 5.dp),
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = modelMenuOpen,
                                onDismissRequest = { modelMenuOpen = false },
                                modifier = Modifier.background(Color(0xFF1E1E1E)),
                            ) {
                                modelOptions.forEach { id ->
                                    DropdownMenuItem(
                                        text = { Text(id.ifBlank { "—" }, color = if (id == currentModel) HertzPalette.Signal else Color.White) },
                                        onClick = { onSelectModel(id); modelMenuOpen = false },
                                    )
                                }
                            }
                        }
                    }

                    // Right: dictation / call vs send
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isDictating) {
                            // Stop dictation — inserts into text, does not auto-send
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = HertzPalette.Signal,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(16.dp)).clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val inserted = dictationBuffer
                                    draft = (draft + (if (draft.isNotBlank() && inserted.isNotBlank()) " " else "") + inserted).trim()
                                    dictationBuffer = ""
                                    onStopDictation(inserted)
                                }
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Stop, str.stop, tint = HertzPalette.OnSignal, modifier = Modifier.size(16.dp))
                                }
                            }
                        } else if (draft.isBlank() && enabled) {
                            // Empty field: show dictation (mic) and call (phone) as separate buttons
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF272727),
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(16.dp)).clickable { onStartDictation() }
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(painterResource(R.drawable.ic_custom_mic), str.dictate, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = HertzPalette.Signal,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(16.dp)).clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onStartCall()
                                }
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Phone, str.call, tint = HertzPalette.OnSignal, modifier = Modifier.size(18.dp))
                                }
                            }
                        } else {
                            // Has text: icon-only Send, morphs into Stop while generating
                            val running = !enabled
                            val bg by animateColorAsState(
                                targetValue = if (running) Color(0xFFFF4D4D) else HertzPalette.Signal,
                                label = "sendBg"
                            )
                            val fg = if (running) Color.White else HertzPalette.OnSignal
                            val interaction = remember { MutableInteractionSource() }
                            val pressed by interaction.collectIsPressedAsState()
                            val scale by animateFloatAsState(
                                targetValue = if (pressed) 0.88f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                label = "sendScale",
                            )
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = bg,
                                modifier = Modifier
                                    .size(40.dp)
                                    .graphicsLayer { scaleX = scale; scaleY = scale }
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(interactionSource = interaction, indication = null) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        when {
                                            running -> onStopSend()
                                            draft.isNotBlank() -> {
                                                onSend(draft)
                                                draft = ""
                                                keyboard?.hide()
                                                focusManager.clearFocus(force = true)
                                            }
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

/**
 * Full-screen call presence — a breathing orb + one live status line, replacing
 * the text chat entirely while a voice call is active. No wall of transcript to
 * read; the point of a call is that you're not looking at your screen.
 */
@Composable
fun CallScreenBody(
    isUserSpeaking: Boolean,
    isThinking: Boolean,
    amplitude: Float,
    lastAssistantText: String,
) {
    val str = LocalStrings.current
    val transition = rememberInfiniteTransition(label = "orb")
    val breathe by transition.animateFloat(
        initialValue = 0.94f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "breathe",
    )
    val orbScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isUserSpeaking) 1f + (amplitude * 0.28f) else breathe,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "orbScale",
    )

    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(180.dp)
                .graphicsLayer { scaleX = orbScale; scaleY = orbScale }
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(HertzPalette.Signal.copy(alpha = 0.35f), HertzPalette.Signal.copy(alpha = 0.05f)),
                    ),
                )
                .border(1.dp, HertzPalette.Signal.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            BrandMark(sizeDp = 56)
        }

        Spacer(Modifier.height(28.dp))

        Text(
            when {
                isUserSpeaking -> str.callListening
                isThinking -> str.callThinking
                else -> str.callReady
            },
            style = MaterialTheme.typography.titleLarge.copy(color = Color.White),
        )

        Spacer(Modifier.height(18.dp))
        DictationWaveform(rms = if (isUserSpeaking) amplitude else 0f, modifier = Modifier.fillMaxWidth(0.7f))

        if (!isUserSpeaking && !isThinking && lastAssistantText.isNotBlank()) {
            Spacer(Modifier.height(22.dp))
            Text(
                lastAssistantText,
                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFA8A8A8)),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
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
    // No card behind these — they float directly on the call screen, like a
    // real phone call's control row, not a chat-composer-shaped box.
    Row(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Mute — icon only; the glyph itself changes (mic vs. mic-muted), not just its tint
        Box(
            Modifier.size(56.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (isMuted) HertzPalette.Signal else Color(0xFF1E1E1E))
                .border(1.dp, Color(0xFF2A2A2A), androidx.compose.foundation.shape.CircleShape)
                .clickable { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onMute(!isMuted) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (isMuted) R.drawable.ic_mic_muted else R.drawable.ic_custom_mic),
                if (isMuted) str.unmute else str.mute,
                tint = if (isMuted) HertzPalette.OnSignal else Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        // End — the primary action: larger, centered, red
        Box(
            Modifier.size(72.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color(0xFFFF3B30))
                .clickable { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onEnd() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, str.end, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        // Pause
        Box(
            Modifier.size(56.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (isPaused) HertzPalette.Signal else Color(0xFF1E1E1E))
                .border(1.dp, Color(0xFF2A2A2A), androidx.compose.foundation.shape.CircleShape)
                .clickable { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPause(!isPaused) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (isPaused) Icons.Filled.Mic else Icons.Filled.Pause, if (isPaused) str.resume else str.pause, tint = if (isPaused) HertzPalette.OnSignal else Color.White, modifier = Modifier.size(22.dp))
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

/** The shark mark — the app's actual logo/icon asset, not a placeholder glyph. */
@Composable
fun BrandMark(sizeDp: Int = 22) {
    Box(Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size((sizeDp * 1.7f).dp),
        )
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
    onRename: (String, String) -> Unit,
    onOpenNotes: () -> Unit,
    remainingUsd: Double?,
    onOpenKeys: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val str = LocalStrings.current
    var query by rememberSaveable { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ChatEntity?>(null) }

    Column(
        Modifier.fillMaxWidth().fillMaxHeight().background(Color(0xFF0A0A0A))
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark()
                Text("HERTZ-DS", style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 2.sp, color = Color.White), modifier = Modifier.padding(start = 10.dp))
            }
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(HertzPalette.Signal)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNewGhost()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, str.newGhostChat, tint = HertzPalette.OnSignal, modifier = Modifier.size(19.dp))
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E1E)).border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(12.dp)),
        ) {
            Icon(Icons.Filled.Search, null, tint = Color(0xFF6E6E6E), modifier = Modifier.padding(start = 12.dp).size(16.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 11.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text(str.searchChats, style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF6E6E6E)))
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close, null, tint = Color(0xFF6E6E6E),
                    modifier = Modifier.padding(end = 12.dp).size(15.dp).clickable { query = "" },
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).clip(RoundedCornerShape(12.dp))
                .clickable { onOpenNotes() }.padding(vertical = 6.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Notes, null, tint = HertzPalette.Signal, modifier = Modifier.size(18.dp))
            Text(str.notes, style = MaterialTheme.typography.titleMedium.copy(color = Color.White), modifier = Modifier.padding(start = 10.dp))
        }

        val filtered = if (query.isBlank()) chats else chats.filter { it.title.contains(query, ignoreCase = true) }
        Text("${str.conversations} · ${filtered.size}", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6E6E6E)), modifier = Modifier.padding(start = 22.dp, top = 4.dp, bottom = 6.dp))
        val pinned = filtered.filter { it.pinned }
        val unpinned = filtered.filter { !it.pinned }
        val grouped = groupChatsByDate(unpinned, str)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
            if (pinned.isNotEmpty()) {
                items(pinned, key = { it.id }) { chat ->
                    ChatRow(chat, chat.id == currentId, haptics, onSelect, onDelete, onPin, onRenameRequest = { renameTarget = chat })
                }
            }
            grouped.forEach { (section, sectionChats) ->
                item(key = "header_$section") {
                    Text(
                        section,
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6E6E6E)),
                        modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 4.dp),
                    )
                }
                items(sectionChats, key = { it.id }) { chat ->
                    ChatRow(chat, chat.id == currentId, haptics, onSelect, onDelete, onPin, onRenameRequest = { renameTarget = chat })
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E1E1E)).border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(14.dp))
                .clickable { onOpenKeys() }.padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(str.credits, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6E6E6E)))
                Text(remainingUsd?.let { "$%.2f".format(it) } ?: "—", style = MaterialTheme.typography.titleLarge.copy(color = Color.White))
            }
            TextButton(onClick = onOpenKeys) { Text(str.manage, color = Color.White) }
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2A2A)).clickable { onOpenSettings() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Settings, "Settings", tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
    }

    renameTarget?.let { chat ->
        RenameDialog(
            initial = chat.title,
            onRename = { newTitle -> onRename(chat.id, newTitle); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }
}

/** Buckets chats by [ChatEntity.updatedAt] into DeepSeek-style sidebar sections, newest-first within each. */
private fun groupChatsByDate(chats: List<ChatEntity>, str: com.hertzds.ui.theme.Strings): List<Pair<String, List<ChatEntity>>> {
    val sorted = chats.sortedByDescending { it.updatedAt }
    val now = java.util.Calendar.getInstance()
    val startOfToday = (now.clone() as java.util.Calendar).apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val startOfYesterday = startOfToday - 86_400_000L
    val start7d = startOfToday - 7 * 86_400_000L
    val start30d = startOfToday - 30 * 86_400_000L
    val buckets = linkedMapOf<String, MutableList<ChatEntity>>()
    for (chat in sorted) {
        val section = when {
            chat.updatedAt >= startOfToday -> str.dateToday
            chat.updatedAt >= startOfYesterday -> str.dateYesterday
            chat.updatedAt >= start7d -> str.date7Days
            chat.updatedAt >= start30d -> str.date30Days
            else -> str.dateOlder
        }
        buckets.getOrPut(section) { mutableListOf() }.add(chat)
    }
    val order = listOf(str.dateToday, str.dateYesterday, str.date7Days, str.date30Days, str.dateOlder)
    return order.mapNotNull { key -> buckets[key]?.let { key to it } }
}

@Composable
private fun ChatRow(
    chat: ChatEntity,
    active: Boolean,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPin: (String, Boolean) -> Unit,
    onRenameRequest: () -> Unit,
) {
    val str = LocalStrings.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelect(chat.id)
        }.padding(start = 14.dp, end = 6.dp),
    ) {
        Box(
            Modifier.width(3.dp).height(38.dp)
                .background(if (active) HertzPalette.Signal else Color.Transparent, RoundedCornerShape(2.dp)),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chat.pinned) {
                    Icon(painterResource(R.drawable.ic_pin), null, tint = Color.White, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    chat.title,
                    style = if (active) MaterialTheme.typography.titleMedium.copy(color = Color.White) else MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFA8A8A8)),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text("${chat.model.ifBlank { "—" }} · $%.4f".format(chat.totalCostUsd), style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6E6E6E)), modifier = Modifier.padding(top = 2.dp))
        }
        Box {
            Icon(
                Icons.Filled.MoreVert, str.chatOptions,
                tint = Color(0xFF6E6E6E),
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    menuOpen = true
                }.padding(5.dp),
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.background(Color(0xFF1E1E1E)),
            ) {
                DropdownMenuItem(
                    text = { Text(str.renameChat, color = Color.White) },
                    leadingIcon = { Icon(Icons.Filled.Edit, null, tint = Color.White) },
                    onClick = { menuOpen = false; onRenameRequest() },
                )
                DropdownMenuItem(
                    text = { Text(if (chat.pinned) str.unpin else str.pin, color = Color.White) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_pin), null, tint = Color.White) },
                    onClick = { menuOpen = false; onPin(chat.id, !chat.pinned) },
                )
                DropdownMenuItem(
                    text = { Text(str.delete, color = Color(0xFFFF5A6E)) },
                    leadingIcon = { Icon(Icons.Filled.Close, null, tint = Color(0xFFFF5A6E)) },
                    onClick = { menuOpen = false; confirmDelete = true },
                )
            }
        }
    }

    if (confirmDelete) {
        HertzDialog(
            title = str.delete,
            confirmLabel = str.delete,
            dismissLabel = str.cancel,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete(chat.id)
                confirmDelete = false
            },
        ) {
            Text(chat.title, style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFA8A8A8)))
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
    var model by rememberSaveable { mutableStateOf(models.firstOrNull().orEmpty()) }
    val str = LocalStrings.current
    HertzDialog(title = str.newGhostChat, dismissLabel = str.cancel, confirmLabel = str.create, onDismiss = onDismiss, onConfirm = { onCreate(title, model, prompt) }) {
        DialogField(title, { title = it }, str.nameOptional, singleLine = true)
        ModelDropdown(selected = model, models = models, onSelect = { model = it })
        DialogField(prompt, { prompt = it }, str.systemPromptOptional, minLines = 3, maxLines = 6)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(selected: String, models: List<String>, onSelect: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        Text("MODEL", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6E6E6E)), modifier = Modifier.padding(bottom = 6.dp))
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E1E)).border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(12.dp))
                    .clickable { open = true }.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(selected.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium.copy(color = Color.White), modifier = Modifier.weight(1f))
                Text(if (open) "▲" else "▼", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFA8A8A8)))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(Color(0xFF1E1E1E))) {
                models.forEach { candidate ->
                    DropdownMenuItem(text = { Text(candidate.ifBlank { "—" }, color = Color.White) }, onClick = { onSelect(candidate); open = false })
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
        containerColor = Color(0xFF141414),
        shape = RoundedCornerShape(16.dp),
        title = { Text(title, style = MaterialTheme.typography.headlineSmall.copy(color = Color.White)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content) },
        confirmButton = {
            if (confirmLabel.isNotBlank()) TextButton(onClick = onConfirm) { Text(confirmLabel, color = Color.White) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel, color = Color(0xFFA8A8A8)) } },
    )
}

@Composable
private fun DialogField(value: String, onChange: (String) -> Unit, label: String, singleLine: Boolean = false, minLines: Int = 1, maxLines: Int = 1) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6E6E6E)), modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange, singleLine = singleLine, minLines = minLines, maxLines = maxLines,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White.copy(alpha = 0.6f),
                unfocusedBorderColor = Color(0xFF2A2A2A),
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color(0xFF1E1E1E),
                cursorColor = Color.White,
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFF2F2F2)),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
