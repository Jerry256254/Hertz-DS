package com.hertzds.ui.keys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hertzds.AppContainer
import com.hertzds.data.repo.KeyStatus
import kotlinx.coroutines.launch

/**
 * API key chain + credit overview. Live balances come from DeepSeek /user/balance;
 * manual top-ups are the fallback when that endpoint is unavailable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = LocalContext.current
    var statuses by remember { mutableStateOf<List<KeyStatus>>(emptyList()) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var topUpTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    suspend fun reload() {
        statuses = container.keys.statuses()
    }

    LaunchedEffect(Unit) {
        reload()
        container.keys.refreshAllBalances()
        reload()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API klíče a kredity") },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Zpět") } },
                actions = {
                    IconButton(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            runCatching { container.keys.refreshAllBalances() }
                                .onFailure { message = it.message ?: "refresh selhal" }
                            reload()
                            busy = false
                        }
                    }) { Icon(Icons.Filled.Refresh, "Obnovit zůstatky") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, "Přidat klíč") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val enabled = statuses.filter { it.entity.enabled }
                val total = enabled.mapNotNull { it.remainingUsd }.sum()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Celkový zůstatek", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (enabled.any { it.remainingUsd != null }) "$%.2f".format(total) else "neznámý",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        val budget = enabled.mapNotNull { it.budgetUsd }.sum()
                        if (budget > 0 && enabled.any { it.remainingUsd != null }) {
                            LinearProgressIndicator(
                                progress = { (total / budget).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                            Text(
                                "z $%.2f rozpočtu · %.0f %%".format(budget, total / budget * 100),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Klíče se řetězí automaticky: když jeden dojde (401/402/429), agent přejde na další.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (statuses.isEmpty()) {
                item {
                    Text(
                        "Zatím žádné klíče. Vezmi si klíč z platform.deepseek.com — bez něj agent nemůže startovat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(statuses, key = { it.entity.id }) { status ->
                KeyCard(
                    status = status,
                    onToggle = {
                        scope.launch {
                            container.keys.setEnabled(status.entity.id, !status.entity.enabled)
                            reload()
                        }
                    },
                    onDelete = {
                        scope.launch {
                            container.keys.remove(status.entity.id)
                            reload()
                        }
                    },
                    onTopUp = { topUpTarget = status.entity.id },
                )
            }

            message?.let { msg ->
                item {
                    Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showAdd) {
        AddKeyDialog(
            onAdd = { raw, label ->
                scope.launch {
                    busy = true
                    container.keys.add(raw, label)
                        .onFailure { message = when (it.message) {
                            "duplicate" -> "Tento klíč už je uložený."
                            else -> it.message ?: "nepodařilo se přidat"
                        } }
                        .onSuccess { message = null }
                    reload()
                    busy = false
                }
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }

    topUpTarget?.let { keyId ->
        TopUpDialog(
            current = statuses.firstOrNull { it.entity.id == keyId }?.entity?.manualToppedUpUsd,
            onSave = { amount ->
                scope.launch {
                    container.keys.setManualTopUp(keyId, amount)
                    reload()
                }
                topUpTarget = null
            },
            onDismiss = { topUpTarget = null },
        )
    }
}

@Composable
private fun KeyCard(status: KeyStatus, onToggle: () -> Unit, onDelete: () -> Unit, onTopUp: () -> Unit) {
    val entity = status.entity
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entity.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        entity.maskedKey + if (status.inCooldown) "  · ⏸ cooldown" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (status.inCooldown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = entity.enabled, onCheckedChange = { onToggle() })
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val remaining = status.remainingUsd
                    Text(
                        remaining?.let { "$%.4f".format(it) } ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    status.remainingPercent?.let { pct ->
                        LinearProgressIndicator(
                            progress = { pct },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                        Text(
                            "%.0f %% zbývá".format(pct * 100),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    entity.lastError?.let {
                        Text(
                            "⚠ ${it.take(80)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = onTopUp) { Text("Dobít…") }
                    Text(
                        "utraceno $%.4f".format(status.spentUsd),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, "Smazat klíč", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AddKeyDialog(onAdd: (String, String?) -> Unit, onDismiss: () -> Unit) {
    var raw by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Přidat DeepSeek klíč") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    raw, { raw = it },
                    label = { Text("sk-…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(label, { label = it }, label = { Text("Název (volitelné)") }, singleLine = true)
                Text(
                    "Klíč je zašifrovaný přes Android Keystore a na disku nikdy není v plaintextu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button({ onAdd(raw, label.ifBlank { null }) }, enabled = raw.isNotBlank()) { Text("Uložit") } },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}

@Composable
private fun TopUpDialog(current: Double?, onSave: (Double?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manuální dobití") },
        text = {
            Column {
                Text(
                    "Kolik $ má klíč celkem k dispozici? Aplikace z toho odečítá lokální spotřebu, " +
                        "když se nedozví zůstatek od DeepSeek.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    text, { text = it },
                    label = { Text("Částka v USD") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = {
            TextButton({
                val value = text.replace(",", ".").toDoubleOrNull()
                onSave(value)
            }) { Text(if (text.isBlank()) "Vymazat" else "Uložit") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}
