package com.hertzds.ui.tasks

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hertzds.AppContainer
import com.hertzds.ui.chat.WaveformMark
import com.hertzds.ui.theme.LocalStrings
import com.hertzds.ui.theme.hertzSemantic
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Recurring agent runs created by the schedule_task tool. */
@Composable
fun TasksScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val tasks by container.database.scheduledTaskDao().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val sem = hertzSemantic()
    val str = LocalStrings.current
    val formatter = DateTimeFormatter.ofPattern("d.M. HH:mm")

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
                Icons.Filled.Close, str.closeAction,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(42.dp)
                    .clickable(onClick = onBack)
                    .padding(11.dp),
            )
            Text(str.scheduledTasks, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }

        if (tasks.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 40.dp).padding(bottom = 120.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WaveformMark(sizeDp = 26)
                Text(
                    str.noTasks,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    str.noTasksHint,
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
            items(tasks, key = { it.id }) { task ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (task.enabled) sem.hairline else sem.hairline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .background(if (task.enabled) MaterialTheme.colorScheme.primary else sem.faintText.copy(alpha = 0.4f), CircleShape),
                        )
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                str.everyMinutesNext.format(
                                    task.intervalMinutes,
                                    formatter.format(Instant.ofEpochMilli(task.nextRunAt).atZone(ZoneId.systemDefault())),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = hertzSemantic().faintText,
                                modifier = Modifier.padding(top = 2.dp),
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
                    }
                    Text(
                        task.prompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    task.lastResult?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            "→ ${it.take(140)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = hertzSemantic().positive,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
        }
    }
}
