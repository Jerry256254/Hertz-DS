package com.hertzds.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hertzds.AppContainer
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Cron-like recurring agent runs, created by the schedule_task tool. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val tasks by container.database.scheduledTaskDao().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val formatter = DateTimeFormatter.ofPattern("d.M. HH:mm")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Naplánované úlohy") },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Zpět") } },
            )
        },
    ) { padding ->
        if (tasks.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Žádné úlohy. Řekni agentovi: „každé ráno v 7:30 mi shrň novinky“ — " +
                        "sám si to naplánuje přes nástroj schedule_task.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(tasks, key = { it.id }) { task ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(task.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "každých ${task.intervalMinutes} min · příští běh " +
                                        formatter.format(Instant.ofEpochMilli(task.nextRunAt).atZone(ZoneId.systemDefault())),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = task.enabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        container.database.scheduledTaskDao().upsert(task.copy(enabled = enabled))
                                    }
                                },
                            )
                            IconButton(onClick = {
                                scope.launch { container.database.scheduledTaskDao().delete(task.id) }
                            }) {
                                Icon(Icons.Filled.Delete, "Smazat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            task.prompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        task.lastResult?.let {
                            Text(
                                "naposledy: ${it.take(120)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
