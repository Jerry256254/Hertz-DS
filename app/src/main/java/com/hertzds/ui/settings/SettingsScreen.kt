package com.hertzds.ui.settings

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hertzds.AppContainer
import com.hertzds.data.prefs.AppLanguage
import com.hertzds.data.prefs.ThemeMode
import com.hertzds.deepseek.Models
import com.hertzds.ui.theme.hertzSemantic
import com.hertzds.voice.DownloadProgress
import com.hertzds.voice.SileroVadModel
import com.hertzds.voice.VoiceModel
import com.hertzds.voice.WhisperModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = settings ?: return
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(
                Icons.Filled.Close, "Zavřít",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(42.dp)
                    .clickable(onClick = onBack)
                    .padding(11.dp),
            )
            Text("Nastavení", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("OBECNÉ") {
                SegmentedRow(
                    label = "Jazyk",
                    options = listOf(AppLanguage.SYSTEM to "Systém", AppLanguage.CZECH to "Čeština", AppLanguage.ENGLISH to "English"),
                    selected = s.language,
                ) { v -> launchSet(scope) { container.settings.setLanguage(v) } }
                SegmentedRow(
                    label = "Téma",
                    options = listOf(ThemeMode.SYSTEM to "Auto", ThemeMode.DARK to "Tmavé", ThemeMode.LIGHT to "Světlé"),
                    selected = s.themeMode,
                ) { v -> launchSet(scope) { container.settings.setTheme(v) } }
            }

            Section("AGENT") {
                ValueRow(
                    label = "Výchozí model",
                    value = Models.label(s.defaultModel),
                    options = Models.ALL.map { it to Models.label(it) },
                ) { id -> launchSet(scope) { container.settings.setDefaultModel(id) } }
                PromptEditor(s.defaultSystemPrompt) { value -> launchSet(scope) { container.settings.setSystemPrompt(value) } }
                SliderRow(
                    label = "Kreativita", hint = "%.1f".format(s.temperature),
                    value = s.temperature.toFloat(), range = 0f..2f, steps = 7,
                ) { v -> launchSet(scope) { container.settings.setTemperature(v.toDouble()) } }
                SliderRow(
                    label = "Max. iterací nástrojů", hint = "${s.maxToolIterations}",
                    value = s.maxToolIterations.toFloat(), range = 1f..30f, steps = 28,
                ) { v -> launchSet(scope) { container.settings.setMaxToolIterations(v.toInt()) } }
                ToggleRow("Automaticky pojmenovávat chaty", s.autoNameChats) { v ->
                    launchSet(scope) { container.settings.setAutoNameChats(v) }
                }
            }

            Section("NÁSTROJE") {
                ToggleRow("Vyhledávání na webu", s.webSearchEnabled) { v ->
                    launchSet(scope) { container.settings.setWebSearchEnabled(v) }
                }
                ToggleRow("Práce se soubory", s.fileToolsEnabled) { v ->
                    launchSet(scope) { container.settings.setFileToolsEnabled(v) }
                }
                ToggleRow("Dlouhodobá paměť", s.memoryEnabled) { v ->
                    launchSet(scope) { container.settings.setMemoryEnabled(v) }
                }
                MistralKeyRow(container, s)
            }

            Section("HLAS") {
                ToggleRow("Streamovat řeč po větách", s.streamingTts) { v ->
                    launchSet(scope) { container.settings.setStreamingTts(v) }
                }
                ChoiceRow(
                    label = "Syntéza řeči",
                    options = listOf("system" to "Systémová", "sherpa" to "Piper · offline"),
                    selected = s.ttsEngine,
                ) { id -> launchSet(scope) { container.settings.setTtsEngine(id) } }
                if (s.ttsEngine == "sherpa") {
                    VoiceModelSection(container, s.ttsVoiceId) { id ->
                        launchSet(scope) { container.settings.setTtsVoice(id) }
                    }
                }
                SliderRow(
                    label = "Rychlost řeči", hint = "%.1fx".format(s.ttsSpeed),
                    value = s.ttsSpeed, range = 0.5f..2f, steps = 5,
                ) { v -> launchSet(scope) { container.settings.setTtsSpeed(v) } }
                ChoiceRow(
                    label = "Rozpoznávání řeči",
                    options = listOf("system" to "Systémové", "sherpa" to "Whisper · offline"),
                    selected = s.sttEngine,
                ) { id -> launchSet(scope) { container.settings.setSttEngine(id) } }
                if (s.sttEngine == "sherpa") {
                    SttModelSection(container, s.sttModelId) { id ->
                        launchSet(scope) { container.settings.setSttModel(id) }
                    }
                    VadSection(container)
                }
                ToggleRow("Hands-free jako výchozí", s.handsFree) { v ->
                    launchSet(scope) { container.settings.setHandsFree(v) }
                }
            }

            Section("KREDITY") {
                SliderRow(
                    label = "Upozornit pod zůstatkem", hint = "$%.1f".format(s.creditAlertUsd),
                    value = s.creditAlertUsd.toFloat(), range = 0.5f..20f, steps = 38,
                ) { v -> launchSet(scope) { container.settings.setCreditAlert(v.toDouble()) } }
                Text(
                    "Peak sazby DeepSeek platí 01–04 a 06–10 UTC v pracovní dny (×2). " +
                        "Mimo špičku je cena přesně poloviční.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun launchSet(scope: CoroutineScope, block: suspend () -> Unit) {
    scope.launch { block() }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section & row primitives — hairline cards, 52dp rows, mono labels
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Section(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val sem = hertzSemantic()
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, sem.hairline, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
private fun RowShell(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        content = content,
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowShell {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = hertzSemantic().hairline,
            ),
        )
    }
}

@Composable
private fun <T> SegmentedRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val sem = hertzSemantic()
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 7.dp)) {
            options.forEach { (value, displayLabel) ->
                val active = value == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .border(
                            1.dp,
                            if (active) hertzSemantic().signalVeil else sem.hairline,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { onSelect(value) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        displayLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Dropdown-style value row for longer option lists (models). */
@Composable
private fun ValueRow(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val sem = hertzSemantic()
    RowShell {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { open = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(" ▾", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (id, name) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(name) },
                        onClick = { onSelect(id); open = false },
                    )
                }
            }
        }
    }
}

/** Two-option inline choice rendered as text buttons. */
@Composable
private fun ChoiceRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 7.dp)) {
            options.forEach { (value, displayLabel) ->
                val active = value == selected
                val sem = hertzSemantic()
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .border(1.dp, if (active) sem.signalVeil else sem.hairline, RoundedCornerShape(10.dp))
                        .clickable { onSelect(value) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        displayLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    hint: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onCommit: (Float) -> Unit,
) {
    var local by rememberSaveable(value) { mutableStateOf(value) }
    Column(Modifier.padding(vertical = 8.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

@Composable
private fun PromptEditor(current: String, onCommit: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    val sem = hertzSemantic()

    Column(Modifier.padding(vertical = 10.dp)) {
        Row {
            Text("SYSTEM PROMPT", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                if (editing) onCommit(draft) else draft = current
                editing = !editing
            }) { Text(if (editing) "Uložit" else "Upravit", color = MaterialTheme.colorScheme.primary) }
        }
        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                minLines = 4,
                maxLines = 12,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = sem.hairline,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                current.take(140) + if (current.length > 140) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MistralKeyRow(container: AppContainer, s: com.hertzds.data.prefs.Settings) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    RowShell {
        Column(Modifier.weight(1f)) {
            Text("Mistral OCR klíč", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (s.mistralOcrKey != null) "uložen ✓" else "volitelný · pro složitější PDF sken",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = {
            if (editing) scope.launch { container.settings.setMistralOcrKey(draft.ifBlank { null }) }
            editing = !editing
        }) { Text(if (editing) "Uložit" else "Změnit", color = MaterialTheme.colorScheme.primary) }
    }
    if (editing) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text("klíč · prázdné smaže") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = hertzSemantic().hairline,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Voice model management (Piper / Whisper / VAD downloads)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VoiceModelSection(container: AppContainer, selectedVoiceId: String?, onSelectVoice: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    val progress = remember { mutableStateMapOf<String, DownloadProgress>() }
    var downloaded by remember { mutableStateOf(setOf<String>()) }

    fun refresh() {
        downloaded = VoiceModel.PIPER_VOICES
            .filter { container.modelDownloader.isDownloaded(it.id, it.extractedDirName) }
            .map { it.id }.toSet()
    }
    remember { refresh(); true }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        VoiceModel.PIPER_VOICES.forEach { voice ->
            ModelDownloadRow(
                name = voice.displayName,
                meta = "~${voice.approxSizeMb} MB",
                isDownloaded = voice.id in downloaded,
                isSelected = voice.id == selectedVoiceId,
                busy = progress.containsKey(voice.id),
                progressState = progress[voice.id],
                onDownload = {
                    scope.launch {
                        container.modelDownloader.download(voice.id, voice.archiveUrl, voice.extractedDirName)
                            .collect {
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

    fun refresh() {
        downloaded = WhisperModel.OPTIONS
            .filter { container.modelDownloader.isDownloaded(it.id, it.extractedDirName) }
            .map { it.id }.toSet()
    }
    remember { refresh(); true }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        WhisperModel.OPTIONS.forEach { model ->
            ModelDownloadRow(
                name = model.displayName.substringBefore(" ("),
                meta = "~${model.approxSizeMb} MB",
                isDownloaded = model.id in downloaded,
                isSelected = model.id == selectedId,
                busy = progress.containsKey(model.id),
                progressState = progress[model.id],
                onDownload = {
                    scope.launch {
                        container.modelDownloader.download(model.id, model.archiveUrl, model.extractedDirName)
                            .collect {
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
    name: String,
    meta: String,
    isDownloaded: Boolean,
    isSelected: Boolean,
    busy: Boolean,
    progressState: DownloadProgress?,
    onDownload: () -> Unit,
    onSelect: () -> Unit,
) {
    val sem = hertzSemantic()
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    buildString {
                        append(meta)
                        append(" · ")
                        append(if (isDownloaded) "staženo" else "není staženo")
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            when {
                isDownloaded && isSelected -> TextButton(onClick = onSelect) {
                    Text("aktivní ✓", color = MaterialTheme.colorScheme.primary)
                }
                isDownloaded -> TextButton(onClick = onSelect) {
                    Text("Vybrat", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                !busy -> TextButton(onClick = onDownload) {
                    Text("Stáhnout", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        when (val p = progressState) {
            is DownloadProgress.Downloading -> DownloadBar(p.bytesRead, p.totalBytes)
            is DownloadProgress.Extracting -> LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
            is DownloadProgress.Failed -> Text(
                "chyba: ${p.message}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
            )
            else -> Unit
        }
    }
}

@Composable
private fun VadSection(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloaded by remember { mutableStateOf(SileroVadModel.isDownloaded(context)) }
    var progress by remember { mutableStateOf<DownloadProgress?>(null) }

    RowShell {
        Column(Modifier.weight(1f)) {
            Text("Silero VAD", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (downloaded) "staženo ✓" else "doporučeno pro hands-free · ~2 MB",
                style = MaterialTheme.typography.bodySmall,
            )
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
            ) { Text("Stáhnout", color = MaterialTheme.colorScheme.primary) }
        }
    }
    when (val p = progress) {
        is DownloadProgress.Downloading -> DownloadBar(p.bytesRead, p.totalBytes)
        is DownloadProgress.Failed -> Text(
            "chyba: ${p.message}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
        )
        else -> Unit
    }
}

@Composable
private fun DownloadBar(bytesRead: Long, totalBytes: Long) {
    if (totalBytes > 0) {
        Column {
            LinearProgressIndicator(
                progress = { (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )
            Text(
                "${bytesRead / 1024 / 1024} / ${totalBytes / 1024 / 1024} MB",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    } else {
        LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
    }
}
