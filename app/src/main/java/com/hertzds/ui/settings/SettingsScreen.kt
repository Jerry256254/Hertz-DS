package com.hertzds.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hertzds.AppContainer
import com.hertzds.data.prefs.AppLanguage
import com.hertzds.data.prefs.ThemeMode
import com.hertzds.deepseek.Models
import com.hertzds.voice.DownloadProgress
import com.hertzds.voice.SileroVadModel
import com.hertzds.voice.VoiceModel
import com.hertzds.voice.WhisperModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = settings ?: return
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nastavení") },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Zpět") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Section("Obecné") {
                ChoiceRow(
                    label = "Jazyk",
                    options = AppLanguage.entries.toList().map { it.name },
                    display = { when (it) {
                        "SYSTEM" -> "Podle systému"
                        "CZECH" -> "Čeština"
                        else -> "English"
                    } },
                    selected = s.language.name,
                    onSelect = { name -> launchSet(scope, container) { container.settings.setLanguage(AppLanguage.valueOf(name)) } },
                )
                ChoiceRow(
                    label = "Téma",
                    options = ThemeMode.entries.toList().map { it.name },
                    display = { when (it) {
                        "SYSTEM" -> "Podle systému"
                        "DARK" -> "Tmavé"
                        else -> "Světlé"
                    } },
                    selected = s.themeMode.name,
                    onSelect = { name -> launchSet(scope, container) { container.settings.setTheme(ThemeMode.valueOf(name)) } },
                )
            }

            Section("Agent") {
                ChoiceRow(
                    label = "Výchozí model",
                    options = Models.ALL,
                    display = Models::label,
                    selected = s.defaultModel,
                    onSelect = { id -> launchSet(scope, container) { container.settings.setDefaultModel(id) } },
                )
                ExpandableTextEditor(
                    label = "System prompt",
                    value = s.defaultSystemPrompt,
                ) { value -> launchSet(scope, container) { container.settings.setSystemPrompt(value) } }
                SliderRow(
                    label = "Kreativita (temperature)",
                    value = s.temperature.toFloat(),
                    range = 0f..2f,
                    steps = 7,
                    format = { "%.1f".format(it) },
                ) { v -> launchSet(scope, container) { container.settings.setTemperature(v.toDouble()) } }
                SliderRow(
                    label = "Max. iterací nástrojů",
                    value = s.maxToolIterations.toFloat(),
                    range = 1f..30f,
                    steps = 28,
                    format = { "%.0f".format(it) },
                ) { v -> launchSet(scope, container) { container.settings.setMaxToolIterations(v.toInt()) } }
                SwitchRow("Automaticky pojmenovávat chaty", s.autoNameChats) { v ->
                    launchSet(scope, container) { container.settings.setAutoNameChats(v) }
                }
            }

            Section("Nástroje agenta") {
                SwitchRow("Vyhledávání na webu", s.webSearchEnabled) { v ->
                    launchSet(scope, container) { container.settings.setWebSearchEnabled(v) }
                }
                SwitchRow("Práce se soubory", s.fileToolsEnabled) { v ->
                    launchSet(scope, container) { container.settings.setFileToolsEnabled(v) }
                }
                SwitchRow("Dlouhodobá paměť", s.memoryEnabled) { v ->
                    launchSet(scope, container) { container.settings.setMemoryEnabled(v) }
                }
                MistralKeyField(container, s)
            }

            Section("Hlas") {
                SwitchRow("Streamovat řeč po větách", s.streamingTts) { v ->
                    launchSet(scope, container) { container.settings.setStreamingTts(v) }
                }
                ChoiceRow(
                    label = "Syntéza řeči (TTS)",
                    options = listOf("system", "sherpa"),
                    display = { if (it == "system") "Systémový engine" else "Piper (offline, lepší)" },
                    selected = s.ttsEngine,
                    onSelect = { id -> launchSet(scope, container) { container.settings.setTtsEngine(id) } },
                )
                if (s.ttsEngine == "sherpa") {
                    VoiceModelSection(container, s.ttsVoiceId) { id ->
                        launchSet(scope, container) { container.settings.setTtsVoice(id) }
                    }
                }
                SliderRow(
                    label = "Rychlost řeči",
                    value = s.ttsSpeed,
                    range = 0.5f..2f,
                    steps = 5,
                    format = { "%.1fx".format(it) },
                ) { v -> launchSet(scope, container) { container.settings.setTtsSpeed(v) } }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                ChoiceRow(
                    label = "Rozpoznávání řeči (STT)",
                    options = listOf("system", "sherpa"),
                    display = { if (it == "system") "Systémové rozpoznávání" else "Whisper (offline)" },
                    selected = s.sttEngine,
                    onSelect = { id -> launchSet(scope, container) { container.settings.setSttEngine(id) } },
                )
                if (s.sttEngine == "sherpa") {
                    SttModelSection(container, s.sttModelId) { id ->
                        launchSet(scope, container) { container.settings.setSttModel(id) }
                    }
                    VadSection(container)
                }
                SwitchRow("Hands-free režim jako výchozí", s.handsFree) { v ->
                    launchSet(scope, container) { container.settings.setHandsFree(v) }
                }
            }

            Section("Kredity a ceny") {
                SliderRow(
                    label = "Upozornit, když klesnou pod",
                    value = s.creditAlertUsd.toFloat(),
                    range = 0.5f..20f,
                    steps = 38,
                    format = { "$%.1f".format(it) },
                ) { v -> launchSet(scope, container) { container.settings.setCreditAlert(v.toDouble()) } }
                Text(
                    "Peak sazby DeepSeek platí 01–04 a 06–10 UTC v pracovní dny (cena ×2). " +
                        "Mimo špičku je přesně polovina.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun launchSet(scope: CoroutineScope, container: AppContainer, block: suspend AppContainer.() -> Unit) {
    scope.launch { container.block() }
}

// ---- sections ------------------------------------------------------------------

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    options: List<String>,
    display: (String) -> String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(display(selected), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { option ->
                val active = option == selected
                TextButton(onClick = { onSelect(option) }) {
                    Text(
                        display(option),
                        style = MaterialTheme.typography.labelSmall,
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
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    format: (Float) -> String,
    onCommit: (Float) -> Unit,
) {
    var local by rememberSaveable(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth()) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(format(local), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
private fun ExpandableTextEditor(label: String, value: String, onCommit: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                if (editing) {
                    onCommit(draft)
                } else {
                    draft = value
                }
                editing = !editing
            }) { Text(if (editing) "Uložit" else "Upravit") }
        }
        if (editing) {
            androidx.compose.material3.OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                minLines = 4,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                value.take(160) + if (value.length > 160) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MistralKeyField(container: AppContainer, s: com.hertzds.data.prefs.Settings) {
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("Mistral OCR klíč (volitelný)", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (s.mistralOcrKey != null) "uložen ✓" else "jen pro složitější PDF sken",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = {
            if (editing) {
                scope.launch { container.settings.setMistralOcrKey(draft.ifBlank { null }) }
            }
            editing = !editing
        }) { Text(if (editing) "Uložit" else "Změnit") }
    }
    if (editing) {
        androidx.compose.material3.OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("klíč, nebo prázdné pro smazání") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---- voice model management ------------------------------------------------------

@Composable
private fun VoiceModelSection(container: AppContainer, selectedVoiceId: String?, onSelectVoice: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    val progress = remember { mutableStateMapOf<String, DownloadProgress>() }
    var downloaded by remember { mutableStateOf(setOf<String>()) }

    fun refreshDownloaded() {
        downloaded = VoiceModel.PIPER_VOICES
            .filter { container.modelDownloader.isDownloaded(it.id, it.extractedDirName) }
            .map { it.id }
            .toSet()
    }
    remember { refreshDownloaded(); true }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VoiceModel.PIPER_VOICES.forEach { voice ->
            val isDownloaded = voice.id in downloaded
            val isSelected = voice.id == selectedVoiceId
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(voice.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            buildString {
                                append("~${voice.approxSizeMb} MB · ")
                                append(if (isDownloaded) "staženo" else "klepni pro stažení")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isDownloaded) {
                        TextButton(onClick = { onSelectVoice(if (isSelected) null else voice.id) }) {
                            Text(if (isSelected) "vybrán ✓" else "vybrat")
                        }
                    } else {
                        TextButton(enabled = voice.id !in progress, onClick = {
                            scope.launch {
                                container.modelDownloader.download(voice.id, voice.archiveUrl, voice.extractedDirName)
                                    .collect {
                                        progress[voice.id] = it
                                        if (it is DownloadProgress.Done || it is DownloadProgress.Failed) {
                                            refreshDownloaded()
                                        }
                                    }
                                progress.remove(voice.id)
                            }
                        }) { Text("stáhnout") }
                    }
                }
                when (val p = progress[voice.id]) {
                    is DownloadProgress.Downloading -> LinearProgress(p.bytesRead, p.totalBytes)
                    DownloadProgress.Extracting -> Text("rozbaluji…", style = MaterialTheme.typography.labelSmall)
                    is DownloadProgress.Failed -> Text(
                        "chyba: ${p.message}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun SttModelSection(container: AppContainer, selectedId: String?, onSelectModel: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    val progress = remember { mutableStateMapOf<String, DownloadProgress>() }
    var downloaded by remember { mutableStateOf(setOf<String>()) }

    fun refreshDownloaded() {
        downloaded = WhisperModel.OPTIONS
            .filter { container.modelDownloader.isDownloaded(it.id, it.extractedDirName) }
            .map { it.id }
            .toSet()
    }
    remember { refreshDownloaded(); true }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WhisperModel.OPTIONS.forEach { model ->
            val isDownloaded = model.id in downloaded
            val isSelected = model.id == selectedId
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (isDownloaded) "staženo" else "klepni pro stažení",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isDownloaded) {
                        TextButton(onClick = { onSelectModel(if (isSelected) null else model.id) }) {
                            Text(if (isSelected) "vybrán ✓" else "vybrat")
                        }
                    } else {
                        TextButton(enabled = model.id !in progress, onClick = {
                            scope.launch {
                                container.modelDownloader.download(model.id, model.archiveUrl, model.extractedDirName)
                                    .collect {
                                        progress[model.id] = it
                                        if (it is DownloadProgress.Done || it is DownloadProgress.Failed) {
                                            refreshDownloaded()
                                        }
                                    }
                                progress.remove(model.id)
                            }
                        }) { Text("stáhnout") }
                    }
                }
                when (val p = progress[model.id]) {
                    is DownloadProgress.Downloading -> LinearProgress(p.bytesRead, p.totalBytes)
                    DownloadProgress.Extracting -> Text("rozbaluji…", style = MaterialTheme.typography.labelSmall)
                    is DownloadProgress.Failed -> Text(
                        "chyba: ${p.message}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun VadSection(container: AppContainer) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var downloaded by remember { mutableStateOf(SileroVadModel.isDownloaded(context)) }
    var progress by remember { mutableStateOf<DownloadProgress?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("Silero VAD (konec řeči, ~2 MB)", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (downloaded) "staženo ✓" else "doporučeno pro hands-free",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!downloaded) {
            TextButton(onClick = {
                scope.launch {
                    SileroVadModel.download(context, container.http).collect {
                        progress = it
                        if (it is DownloadProgress.Done) downloaded = true
                    }
                }
            }, enabled = progress == null || progress is DownloadProgress.Failed) { Text("stáhnout") }
        }
    }
    progress?.let { p ->
        when (p) {
            is DownloadProgress.Downloading -> LinearProgress(p.bytesRead, p.totalBytes)
            DownloadProgress.Extracting -> Text("rozbaluji…")
            is DownloadProgress.Failed -> Text(
                "chyba: ${p.message}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
            else -> Unit
        }
    }
}

@Composable
private fun LinearProgress(bytesRead: Long, totalBytes: Long) {
    if (totalBytes > 0) {
        Column {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${bytesRead / 1024 / 1024} MB / ${totalBytes / 1024 / 1024} MB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
