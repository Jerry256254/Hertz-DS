package com.hertzds.ui.notes

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.hertzds.data.db.NotebookEntity
import com.hertzds.ui.theme.HertzPalette
import com.hertzds.ui.theme.LocalStrings
import com.hertzds.ui.theme.hertzSemantic
import kotlinx.coroutines.launch

/** A personal notepad of "notebooks" — plain notes the user can opt to share into the AI's context. */
@Composable
fun NotesScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val notebooks by container.notebooks.notebooks.collectAsStateWithLifecycle(initialValue = emptyList())
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val sem = hertzSemantic()
    val str = LocalStrings.current

    val editing = notebooks.firstOrNull { it.id == openId }
    if (editing != null) {
        NotebookEditor(
            notebook = editing,
            onBack = { openId = null },
            onSave = { updated -> scope.launch { container.notebooks.save(updated) } },
            onDelete = { scope.launch { container.notebooks.delete(editing.id) }; openId = null },
        )
        return
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(
                Icons.Filled.ArrowBack, str.closeAction,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(42.dp).clickable(onClick = onBack).padding(11.dp),
            )
            Text(str.notes, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Icon(
                Icons.Filled.Add, str.newNotebook,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp).clickable {
                    scope.launch {
                        val created = container.notebooks.create(str.untitledNotebook)
                        openId = created.id
                    }
                }.padding(11.dp),
            )
        }

        if (notebooks.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 40.dp).padding(bottom = 120.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.AutoMirrored.Filled.Notes, null, tint = HertzPalette.Signal, modifier = Modifier.size(34.dp))
                Text(
                    str.noNotebooksYet,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    str.noNotebooksHint,
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
            items(notebooks, key = { it.id }) { notebook ->
                Column(
                    Modifier.fillMaxWidth()
                        .border(1.dp, sem.hairline, RoundedCornerShape(16.dp))
                        .clickable { openId = notebook.id }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            notebook.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (notebook.sharedWithAi) {
                            Text(
                                str.shareWithAi,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (notebook.content.isNotBlank()) {
                        Text(
                            notebook.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotebookEditor(
    notebook: NotebookEntity,
    onBack: () -> Unit,
    onSave: (NotebookEntity) -> Unit,
    onDelete: () -> Unit,
) {
    val sem = hertzSemantic()
    val str = LocalStrings.current
    var title by rememberSaveable(notebook.id) { mutableStateOf(notebook.title) }
    var content by rememberSaveable(notebook.id) { mutableStateOf(notebook.content) }
    var shared by rememberSaveable(notebook.id) { mutableStateOf(notebook.sharedWithAi) }

    LaunchedEffect(title, content, shared) {
        onSave(notebook.copy(title = title.ifBlank { str.untitledNotebook }, content = content, sharedWithAi = shared))
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(
                Icons.Filled.ArrowBack, str.closeAction,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(42.dp).clickable(onClick = onBack).padding(11.dp),
            )
            Box(Modifier.weight(1f))
            Icon(
                Icons.Filled.Close, str.delete,
                tint = HertzPalette.Negative,
                modifier = Modifier.size(42.dp).clickable(onClick = onDelete).padding(11.dp),
            )
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                placeholder = { Text(str.title) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(str.shareWithAi, style = MaterialTheme.typography.bodyLarge)
                    Text(str.shareWithAiHint, style = MaterialTheme.typography.bodySmall, color = sem.faintText)
                }
                Switch(
                    checked = shared,
                    onCheckedChange = { shared = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = HertzPalette.Signal, checkedThumbColor = androidx.compose.ui.graphics.Color.White),
                )
            }

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                placeholder = { Text(str.content) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = sem.hairline,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
