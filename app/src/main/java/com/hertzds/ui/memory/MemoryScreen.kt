package com.hertzds.ui.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hertzds.AppContainer
import com.hertzds.ui.chat.WaveformMark
import com.hertzds.ui.theme.hertzSemantic
import kotlinx.coroutines.launch

/** The agent's persistent knowledge — pinned items ride in every prompt. */
@Composable
fun MemoryScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val memories by container.memories.memories.collectAsStateWithLifecycle(initialValue = emptyList())
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val sem = hertzSemantic()

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
            Text("Paměť agenta", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Icon(
                Icons.Filled.Add, "Nová vzpomínka",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(42.dp)
                    .clickable { showAdd = true }
                    .padding(11.dp),
            )
        }

        if (memories.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 40.dp).padding(bottom = 120.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WaveformMark(sizeDp = 26)
                Text(
                    "Agent si zatím nic nepamatuje",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    "Sám si ukládá fakta přes nástroj remember.\nNebo je přidej ručně tlačítkem +.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(memories, key = { it.id }) { memory ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, sem.hairline, RoundedCornerShape(16.dp))
                        .clickable { editTarget = memory.id }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(
                                    if (memory.pinned) MaterialTheme.colorScheme.primary else sem.faintText.copy(alpha = 0.4f),
                                    CircleShape,
                                ),
                        )
                        Text(
                            memory.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 10.dp),
                        )
                        Text(
                            if (memory.pinned) "PIN" else "PIN",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (memory.pinned) MaterialTheme.colorScheme.primary else hertzSemantic().faintText,
                            modifier = Modifier
                                .clickable {
                                    scope.launch { container.memories.update(memory.copy(pinned = !memory.pinned)) }
                                }
                                .padding(4.dp),
                        )
                    }
                    Text(
                        memory.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
        }
    }

    if (showAdd) {
        MemoryDialog(null, null, onDismiss = { showAdd = false }) { title, content ->
            scope.launch { container.memories.remember(title, content, source = "user") }
            showAdd = false
        }
    }

    editTarget?.let { id ->
        val target = memories.firstOrNull { it.id == id }
        if (target != null) {
            MemoryDialog(target.title, target.content, onDismiss = { editTarget = null }) { title, content ->
                scope.launch {
                    val pinned = target.pinned
                    container.memories.update(target.copy(title = title, content = content))
                }
                editTarget = null
            }
        } else editTarget = null
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
    val sem = hertzSemantic()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text(if (initialTitle == null) "Nová vzpomínka" else "Upravit", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    title, { title = it }, singleLine = true,
                    placeholder = { Text("Nadpis") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = sem.hairline,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    content, { content = it }, minLines = 3, maxLines = 10,
                    placeholder = { Text("Obsah…") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = sem.hairline,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && content.isNotBlank(), onClick = { onSave(title, content) }) {
                Text("Uložit", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Zrušit") } },
    )
}
