package com.hertzds.ui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hertzds.AppContainer
import kotlinx.coroutines.launch

/**
 * The agent's persistent knowledge. Pinned memories ride along in every prompt;
 * the rest are retrieved by keyword match against the current question.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val memories by container.memories.memories.collectAsStateWithLifecycle(initialValue = emptyList())
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editTarget by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paměť agenta") },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Zpět") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, "Nová vzpomínka") }
        },
    ) { padding ->
        if (memories.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Agent si zatím nic nepamatuje.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Sám si ukládá fakta přes nástroj remember — nebo je přidej ručně.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(memories, key = { it.id }) { memory ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (if (memory.pinned) "📌 " else "") + memory.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                scope.launch { container.memories.update(memory.copy(pinned = !memory.pinned)) }
                            }) {
                                Icon(
                                    Icons.Filled.PushPin, "Pin",
                                    tint = if (memory.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { scope.launch { container.memories.forget(memory.id) } }) {
                                Icon(Icons.Filled.Delete, "Smazat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            memory.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        MemoryDialog(null, null, onDismiss = { showAdd = false }) { title, content ->
            scope.launch {
                container.memories.remember(title, content, source = "user")
            }
            showAdd = false
        }
    }

    editTarget?.let { id ->
        val target = memories.firstOrNull { it.id == id }
        if (target != null) {
            MemoryDialog(target.title, target.content, onDismiss = { editTarget = null }) { title, content ->
                scope.launch {
                    container.memories.update(target.copy(title = title, content = content))
                }
                editTarget = null
            }
        } else {
            editTarget = null
        }
    }
}

@Composable
private fun MemoryDialog(
    initialTitle: String?,
    initialContent: String?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initialTitle.orEmpty()) }
    var content by rememberSaveable { mutableStateOf(initialContent.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialTitle == null) "Nová vzpomínka" else "Upravit vzpomínku") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Nadpis") }, singleLine = true)
                OutlinedTextField(content, { content = it }, label = { Text("Obsah") }, minLines = 3, maxLines = 10)
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && content.isNotBlank(), onClick = { onSave(title, content) }) {
                Text("Uložit")
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}
