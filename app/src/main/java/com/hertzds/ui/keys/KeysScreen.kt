package com.hertzds.ui.keys

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hertzds.AppContainer
import com.hertzds.data.repo.KeyStatus
import com.hertzds.ui.theme.hertzSemantic
import kotlinx.coroutines.launch

/**
 * API key chain + credits. Live balance from DeepSeek /user/balance,
 * manual top-up as fallback. Hero number up top, flat key rows below.
 */
@Composable
fun KeysScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var statuses by remember { mutableStateOf<List<KeyStatus>>(emptyList()) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var topUpTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    fun reloadNow() {
        scope.launch { statuses = container.keys.statuses() }
    }

    LaunchedEffect(Unit) {
        statuses = container.keys.statuses()
        container.keys.refreshAllBalances()
        statuses = container.keys.statuses()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        // header
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
            Text("Klíče & kredity", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                scope.launch {
                    runCatching { container.keys.refreshAllBalances() }.onFailure { message = it.message }
                    statuses = container.keys.statuses()
                }
            }) { Text("Obnovit", color = MaterialTheme.colorScheme.primary) }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // hero balance
            item {
                val enabled = statuses.filter { it.entity.enabled }
                val total = enabled.mapNotNull { it.remainingUsd }.sum()
                val budget = enabled.mapNotNull { it.budgetUsd }.sum()
                val known = enabled.any { it.remainingUsd != null }

                Column(Modifier.padding(top = 14.dp)) {
                    Text("CELKEM K DISPOZICI", style = MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            if (known) "$%.2f".format(total) else "—",
                            style = MaterialTheme.typography.displaySmall.copy(
                                color = hertzSemantic().positive.takeIf { known } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                        if (budget > 0 && known) {
                            Text(
                                "  / $%.2f".format(budget),
                                style = MaterialTheme.typography.titleMedium,
                                color = hertzSemantic().faintText,
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                        }
                    }
                    if (budget > 0 && known) {
                        val pct = (total / budget).toFloat().coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { pct },
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            color = if (pct < 0.15f) MaterialTheme.colorScheme.error else hertzSemantic().positive,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(4.dp),
                        )
                    }
                    Text(
                        "Klíče se řetězí: když jeden dojde (401/402/429), agent přejde na další.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            // keys
            items(statuses, key = { it.entity.id }) { status ->
                KeyRow(
                    status = status,
                    onToggle = { checked ->
                        scope.launch {
                            container.keys.setEnabled(status.entity.id, checked)
                            statuses = container.keys.statuses()
                        }
                    },
                    onDelete = {
                        scope.launch {
                            container.keys.remove(status.entity.id)
                            statuses = container.keys.statuses()
                        }
                    },
                    onTopUp = { topUpTarget = status.entity.id },
                )
            }

            if (statuses.isEmpty()) {
                item {
                    Text(
                        "Žádné klíče. Vezmi klíč z platform.deepseek.com —\nbez něj agent nemůže startovat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            message?.let { msg ->
                item { Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }

            item {
                Button(
                    onClick = { showAdd = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = androidx.compose.material3.ButtonDefaults.elevatedButtonElevation(0.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
                ) {
                    Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                    Text("  Přidat klíč", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    if (showAdd) {
        AddKeyDialog(
            onAdd = { raw, label ->
                scope.launch {
                    container.keys.add(raw, label)
                        .onFailure { message = when (it.message) {
                            "duplicate" -> "Tento klíč už je uložený."
                            else -> it.message ?: "nepodařilo se přidat"
                        } }
                        .onSuccess { message = null; reloadNow() }
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
                scope.launch { container.keys.setManualTopUp(keyId, amount); reloadNow() }
                topUpTarget = null
            },
            onDismiss = { topUpTarget = null },
        )
    }
}

@Composable
private fun KeyRow(
    status: KeyStatus,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onTopUp: () -> Unit,
) {
    val entity = status.entity
    val sem = hertzSemantic()

    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, sem.hairline, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(9.dp)
                    .background(
                        when {
                            !entity.enabled -> sem.faintText
                            status.inCooldown -> MaterialTheme.colorScheme.error
                            else -> sem.positive
                        },
                        CircleShape,
                    ),
            )
            Text(entity.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 9.dp))
            Spacer(Modifier.weight(1f))
            Switch(
                checked = entity.enabled,
                onCheckedChange = onToggle,
            )
        }

        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
            val remaining = status.remainingUsd
            Text(
                remaining?.let { "$%.4f".format(it) } ?: "—",
                style = MaterialTheme.typography.headlineSmall.copy(color = sem.positive),
            )
            Text(
                "  utraceno $%.4f".format(status.spentUsd),
                style = MaterialTheme.typography.labelSmall,
                color = sem.faintText,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }

        status.remainingPercent?.let { pct ->
            LinearProgressIndicator(
                progress = { pct },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = if (pct < 0.15f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(3.dp),
            )
            Text("%.0f %% zbývá".format(pct * 100), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            entity.maskedKey?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            }
            TextButton(onClick = onTopUp, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                Text("Dobít…", color = MaterialTheme.colorScheme.primary)
            }
            Icon(
                Icons.Filled.Close, "Smazat klíč",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(30.dp)
                    .clickable(onClick = onDelete)
                    .padding(7.dp),
            )
        }

        if (status.inCooldown) {
            Text("⏸ v cooldownu po chybě API", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        entity.lastError?.takeIf { !status.inCooldown }?.let {
            Text("⚠ ${it.take(70)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AddKeyDialog(onAdd: (String, String?) -> Unit, onDismiss: () -> Unit) {
    var raw by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    val sem = hertzSemantic()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Přidat DeepSeek klíč", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField("KLÍČ") {
                    OutlinedTextField(
                        raw, { raw = it }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        placeholder = { Text("sk-…") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = sem.hairline,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                LabeledField("NÁZEV (VOLITELNÉ)") {
                    OutlinedTextField(
                        label, { label = it }, singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = sem.hairline,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "Klíč je šifrovaný přes Android Keystore; plaintext nikdy neuložíme.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(raw, label.ifBlank { null }) },
                enabled = raw.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) { Text("Uložit", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } },
    )
}

@Composable
private fun LabeledField(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

@Composable
private fun TopUpDialog(current: Double?, onSave: (Double?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Manuální dobití", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Kolik $ má klíč celkem? Aplikace odečítá lokální spotřebu, když se nedozví zůstatek od DeepSeek.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    text, { text = it },
                    label = { Text("Částka v USD") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton({ onSave(text.replace(",", ".").toDoubleOrNull()) }) {
                Text(if (text.isBlank()) "Vymazat" else "Uložit", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}
