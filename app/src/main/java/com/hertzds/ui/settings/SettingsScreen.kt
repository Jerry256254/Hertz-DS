package com.hertzds.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hertzds.AppContainer
import com.hertzds.BuildConfig
import com.hertzds.data.prefs.AppLanguage
import com.hertzds.data.prefs.ThemeMode
import com.hertzds.deepseek.Models
import com.hertzds.ui.theme.LocalStrings
import com.hertzds.voice.DownloadProgress
import com.hertzds.voice.SileroVadModel
import com.hertzds.voice.VoiceModel
import com.hertzds.voice.WhisperModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onOpenKeys: () -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = settings ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val str = LocalStrings.current

    // Expand states — keys expanded by default
    var keysExpanded by rememberSaveable { mutableStateOf(true) }
    var aiExpanded by rememberSaveable { mutableStateOf(false) }
    var voiceExpanded by rememberSaveable { mutableStateOf(false) }
    var aboutExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF000000)).statusBarsPadding()
    ) {
        // Header — gently squared, white on black
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1C1E22)).border(1.dp, Color(0xFF2A2E36), RoundedCornerShape(14.dp))
                    .clickable { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, "Back", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Text(str.settings, style = MaterialTheme.typography.titleLarge.copy(color = Color.White), modifier = Modifier.padding(start = 12.dp))
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Language — always visible, top-level (not collapsible: it's fundamental)
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF111214))
                        .border(1.dp, Color(0xFF2A2E36), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    ChoiceRow(
                        label = str.language,
                        options = listOf(
                            AppLanguage.SYSTEM.tag to str.languageSystem,
                            AppLanguage.CZECH.tag to str.languageCzech,
                            AppLanguage.ENGLISH.tag to str.languageEnglish,
                        ),
                        selected = s.language.tag,
                    ) { tag ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val lang = AppLanguage.entries.first { it.tag == tag }
                        scope.launch { container.settings.setLanguage(lang) }
                    }
                }
            }

            // Keys & Credits — expandable, shows summary + manage button
            item {
                ExpandableSection(
                    title = str.keysAndCredits,
                    subtitle = str.keysAndCreditsSubtitle,
                    expanded = keysExpanded,
                    onToggle = { keysExpanded = !keysExpanded; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Text(str.manageKeysDesc, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF9AA0AE)))
                        OutlinedButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenKeys()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) { Text(str.manageKeys) }
                    }
                }
            }

            // AI Behavior — expandable
            item {
                ExpandableSection(
                    title = str.aiBehavior,
                    subtitle = str.aiBehaviorSubtitle,
                    expanded = aiExpanded,
                    onToggle = { aiExpanded = !aiExpanded; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ValueRow(
                            label = str.defaultModel,
                            value = Models.label(s.defaultModel),
                            options = Models.ALL.map { it to Models.label(it) }
                        ) { id -> scope.launch { container.settings.setDefaultModel(id) } }

                        PromptEditor(s.defaultSystemPrompt) { v -> scope.launch { container.settings.setSystemPrompt(v) } }

                        SliderRow(label = str.creativity, hint = "%.1f".format(s.temperature), value = s.temperature.toFloat(), range = 0f..2f, steps = 7) { v -> scope.launch { container.settings.setTemperature(v.toDouble()) } }
                        SliderRow(label = str.maxToolIterations, hint = "${s.maxToolIterations}", value = s.maxToolIterations.toFloat(), range = 1f..30f, steps = 28) { v -> scope.launch { container.settings.setMaxToolIterations(v.toInt()) } }

                        // Auto-naming is always on — no toggle, just info
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(str.autoNameChats, style = MaterialTheme.typography.bodyLarge.copy(color = Color.White))
                                Text(str.autoNameChatsDesc, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF6B7280)))
                            }
                            Box(Modifier.size(20.dp).background(Color.White, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                                Text("✓", color = Color.Black, style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        ToggleRow(str.webSearch, s.webSearchEnabled) { v -> scope.launch { container.settings.setWebSearchEnabled(v) } }
                        ToggleRow(str.fileTools, s.fileToolsEnabled) { v -> scope.launch { container.settings.setFileToolsEnabled(v) } }
                        ToggleRow(str.longTermMemory, s.memoryEnabled) { v -> scope.launch { container.settings.setMemoryEnabled(v) } }
                    }
                }
            }

            // Voice — expandable
            item {
                ExpandableSection(
                    title = str.voice,
                    subtitle = str.voiceSubtitle,
                    expanded = voiceExpanded,
                    onToggle = { voiceExpanded = !voiceExpanded; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ToggleRow(str.streamSpeech, s.streamingTts) { v -> scope.launch { container.settings.setStreamingTts(v) } }

                        ChoiceRow(label = str.textToSpeech, options = listOf("system" to str.systemEngine, "sherpa" to str.piperOffline), selected = s.ttsEngine) { id -> scope.launch { container.settings.setTtsEngine(id) } }
                        if (s.ttsEngine == "sherpa") {
                            VoiceModelSection(container, s.ttsVoiceId) { id -> scope.launch { container.settings.setTtsVoice(id) } }
                        }
                        SliderRow(label = str.speechSpeed, hint = "%.1fx".format(s.ttsSpeed), value = s.ttsSpeed, range = 0.5f..2f, steps = 5) { v -> scope.launch { container.settings.setTtsSpeed(v) } }

                        ChoiceRow(label = str.speechToText, options = listOf("system" to str.systemEngine, "sherpa" to str.whisperOffline), selected = s.sttEngine) { id -> scope.launch { container.settings.setSttEngine(id) } }
                        if (s.sttEngine == "sherpa") {
                            SttModelSection(container, s.sttModelId) { id -> scope.launch { container.settings.setSttModel(id) } }
                            VadSection(container)
                        }
                        Text(str.voiceModesHint, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF6B7280)), modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            // About / Updates / Legal — expandable footer
            item {
                ExpandableSection(
                    title = str.about,
                    subtitle = str.aboutSubtitle,
                    expanded = aboutExpanded,
                    onToggle = { aboutExpanded = !aboutExpanded; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(str.version, style = MaterialTheme.typography.bodyLarge.copy(color = Color.White))
                                Text(BuildConfig.VERSION_NAME + " (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF9AA0AE)))
                            }
                            UpdateCheckButton(str)
                        }

                        HorizontalDivider(color = Color(0xFF1C1E22))

                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://kuclab.org"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(str.visitWebsite, color = Color.White)
                        }

                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://kuclab.org/legal"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(str.legalDocuments, color = Color.White)
                        }

                        Text(
                            str.openSourceNotice,
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF6B7280)),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compares BuildConfig.VERSION_NAME against the latest GitHub release tag.
 * Only shows a button (straight to GitHub) when a newer version actually exists;
 * otherwise just states that the app is current.
 */
@Composable
private fun UpdateCheckButton(str: com.hertzds.ui.theme.Strings) {
    val context = LocalContext.current
    var latestTag by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        latestTag = runCatching {
            val url = java.net.URL("https://api.github.com/repos/Jerry256254/Hertz-DS/releases/latest")
            (url.openConnection() as java.net.HttpURLConnection).run {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 8000
                readTimeout = 8000
                val body = inputStream.bufferedReader().readText()
                disconnect()
                Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            }
        }.getOrNull()
        checked = true
    }

    val current = BuildConfig.VERSION_NAME.removePrefix("v").substringBefore("-")
    val latest = latestTag?.removePrefix("v")
    val isNewer = latest != null && latest != current

    if (!checked) return
    if (isNewer) {
        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Jerry256254/Hertz-DS/releases/latest"))
                context.startActivity(intent)
            },
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) { Text(str.updateAvailable) }
    } else {
        Text(str.upToDate, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF6B7280)))
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF111214)).border(1.dp, Color(0xFF2A2E36), RoundedCornerShape(16.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(color = Color.White))
                Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF9AA0AE)))
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                content()
            }
        }
    }
}

// — Reused row primitives, updated to white/black squared style —

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge.copy(color = Color.White), modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Color.White,
                checkedThumbColor = Color.Black,
                checkedBorderColor = Color.White,
                uncheckedThumbColor = Color(0xFF9AA0AE),
                uncheckedTrackColor = Color(0xFF2A2E36),
                uncheckedBorderColor = Color(0xFF2A2E36),
            ),
        )
    }
}

@Composable
private fun ValueRow(label: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge.copy(color = Color.White), modifier = Modifier.weight(1f))
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { open = true }.padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(value, style = MaterialTheme.typography.titleMedium.copy(color = Color.White))
                Text(" ▾", style = MaterialTheme.typography.labelMedium.copy(color = Color.White))
            }
            DropdownMenu(
                expanded = open, onDismissRequest = { open = false },
                modifier = Modifier.background(Color(0xFF1C1E22)).border(1.dp, Color(0xFF2A2E36), RoundedCornerShape(12.dp))
            ) {
                options.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = { Text(name, color = Color.White) },
                        onClick = { onSelect(id); open = false },
                        colors = MenuDefaults.itemColors(textColor = Color.White)
                    )
                }
            }
        }
    }
}

@Composable
fun ChoiceRow(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 7.dp)) {
            options.forEach { (value, displayLabel) ->
                val active = value == selected
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (active) Color.White else Color.Transparent)
                        .border(1.dp, if (active) Color.White else Color(0xFF2A2E36), RoundedCornerShape(10.dp))
                        .clickable { onSelect(value) }.padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(displayLabel, style = MaterialTheme.typography.labelMedium.copy(color = if (active) Color.Black else Color(0xFF9AA0AE)))
                }
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, hint: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onCommit: (Float) -> Unit) {
    var local by rememberSaveable(value) { mutableStateOf(value) }
    Column(Modifier.padding(vertical = 8.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(color = Color.White), modifier = Modifier.weight(1f))
            Text(hint, style = MaterialTheme.typography.labelMedium.copy(color = Color.White))
        }
        Slider(
            value = local, onValueChange = { local = it }, onValueChangeFinished = { onCommit(local) },
            valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0xFF2A2E36)),
        )
    }
}

@Composable
private fun PromptEditor(current: String, onCommit: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    val str = LocalStrings.current
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(str.systemPrompt, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)), modifier = Modifier.weight(1f))
            TextButton(onClick = {
                if (editing) onCommit(draft) else draft = current
                editing = !editing
            }) { Text(if (editing) str.save else str.edit, color = Color.White) }
        }
        if (editing) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it }, minLines = 4, maxLines = 12,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFF2A2E36), focusedBorderColor = Color.White, cursorColor = Color.White,
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color(0xFF1C1E22)
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(current.take(140) + if (current.length > 140) "…" else "", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF9AA0AE)))
        }
    }
}

// Voice sections — kept but restyled to white/black

@Composable
private fun VoiceModelSection(container: AppContainer, selectedVoiceId: String?, onSelectVoice: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    val progress = remember { mutableStateMapOf<String, DownloadProgress>() }
    var downloaded by remember { mutableStateOf(setOf<String>()) }
    fun refresh() { downloaded = VoiceModel.PIPER_VOICES.filter { container.modelDownloader.isDownloaded(it.id, it.extractedDirName) }.map { it.id }.toSet() }
    remember { refresh(); true }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        VoiceModel.PIPER_VOICES.forEach { voice ->
            ModelDownloadRow(
                name = voice.displayName, meta = "~${voice.approxSizeMb} MB",
                isDownloaded = voice.id in downloaded, isSelected = voice.id == selectedVoiceId,
                busy = progress.containsKey(voice.id), progressState = progress[voice.id],
                onDownload = {
                    scope.launch {
                        container.modelDownloader.download(voice.id, voice.archiveUrl, voice.extractedDirName).collect {
                            progress[voice.id] = it
                            if (it is DownloadProgress.Done || it is DownloadProgress.Failed) refresh()
                        }
                        progress.remove(voice.id)
                    }
                },
                onSelect = { onSelectVoice(if (voice.id == selectedVoiceId) null else voice.id) },
            )
        }
    }
}

@Composable
private fun SttModelSection(container: AppContainer, selectedId: String?, onSelectModel: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    val progress = remember { mutableStateMapOf<String, DownloadProgress>() }
    var downloaded by remember { mutableStateOf(setOf<String>()) }
    fun refresh() { downloaded = WhisperModel.OPTIONS.filter { container.modelDownloader.isDownloaded(it.id, it.extractedDirName) }.map { it.id }.toSet() }
    remember { refresh(); true }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        WhisperModel.OPTIONS.forEach { model ->
            ModelDownloadRow(
                name = model.displayName.substringBefore(" ("), meta = "~${model.approxSizeMb} MB",
                isDownloaded = model.id in downloaded, isSelected = model.id == selectedId,
                busy = progress.containsKey(model.id), progressState = progress[model.id],
                onDownload = {
                    scope.launch {
                        container.modelDownloader.download(model.id, model.archiveUrl, model.extractedDirName).collect {
                            progress[model.id] = it
                            if (it is DownloadProgress.Done || it is DownloadProgress.Failed) refresh()
                        }
                        progress.remove(model.id)
                    }
                },
                onSelect = { onSelectModel(if (model.id == selectedId) null else model.id) },
            )
        }
    }
}

@Composable
private fun ModelDownloadRow(
    name: String, meta: String, isDownloaded: Boolean, isSelected: Boolean, busy: Boolean,
    progressState: DownloadProgress?, onDownload: () -> Unit, onSelect: () -> Unit,
) {
    val str = LocalStrings.current
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge.copy(color = Color.White))
                Text("$meta · ${if (isDownloaded) str.downloaded else str.notDownloaded}", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)))
            }
            when {
                isDownloaded && isSelected -> TextButton(onClick = onSelect) { Text(str.active, color = Color.White) }
                isDownloaded -> TextButton(onClick = onSelect) { Text(str.select, color = Color(0xFF9AA0AE)) }
                !busy -> TextButton(onClick = onDownload) { Text(str.download, color = Color.White) }
            }
        }
        when (val p = progressState) {
            is DownloadProgress.Downloading -> DownloadBar(p.bytesRead, p.totalBytes)
            is DownloadProgress.Extracting -> LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = Color.White, trackColor = Color(0xFF2A2E36))
            is DownloadProgress.Failed -> Text("${str.downloadError}: ${p.message}", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFFF6B6B)))
            else -> Unit
        }
    }
}

@Composable
private fun VadSection(container: AppContainer) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val str = LocalStrings.current
    var downloaded by remember { mutableStateOf(SileroVadModel.isDownloaded(context)) }
    var progress by remember { mutableStateOf<DownloadProgress?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Column(Modifier.weight(1f)) {
            Text(str.sileroVad, style = MaterialTheme.typography.bodyLarge.copy(color = Color.White))
            Text(if (downloaded) str.sileroVadDownloaded else str.sileroVadHint, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF9AA0AE)))
        }
        if (!downloaded) {
            TextButton(
                enabled = progress == null || progress is DownloadProgress.Failed,
                onClick = {
                    scope.launch {
                        SileroVadModel.download(context, container.http).collect {
                            progress = it
                            if (it is DownloadProgress.Done) downloaded = true
                        }
                    }
                },
            ) { Text(str.download, color = Color.White) }
        }
    }
    when (val p = progress) {
        is DownloadProgress.Downloading -> DownloadBar(p.bytesRead, p.totalBytes)
        is DownloadProgress.Failed -> Text("${str.downloadError}: ${p.message}", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFFF6B6B)))
        else -> Unit
    }
}

@Composable
private fun DownloadBar(bytesRead: Long, totalBytes: Long) {
    if (totalBytes > 0) {
        Column {
            LinearProgressIndicator(progress = { (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) }, trackColor = Color(0xFF2A2E36), color = Color.White, modifier = Modifier.fillMaxWidth().height(3.dp))
            Text("${bytesRead / 1024 / 1024} / ${totalBytes / 1024 / 1024} MB", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6B7280)), modifier = Modifier.padding(top = 3.dp))
        }
    } else LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = Color.White)
}
