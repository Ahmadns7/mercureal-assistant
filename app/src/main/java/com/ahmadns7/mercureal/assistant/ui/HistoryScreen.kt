package com.ahmadns7.mercureal.assistant.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahmadns7.mercureal.assistant.history.Turn
import com.ahmadns7.mercureal.assistant.model.ModelRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saved conversation turns, grouped by session, with full-text search and delete controls. */
@Composable
fun HistoryScreen(repo: ModelRepository, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val turns by repo.history.observe().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Turn>?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(query, turns) {
        hits = if (query.isBlank()) null else repo.history.search(query)
    }
    val shown = hits ?: turns
    val groups = shown.groupBy { it.sessionId }.values.toList()
    val fmt = remember { SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("< back") }
        Text("History", style = MaterialTheme.typography.headlineMedium)
        Text(
            "${turns.size} turns kept on this phone, newest first. older turns and anything past the cap are removed automatically (set in Settings).",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("search questions and answers") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!confirmClear) {
                OutlinedButton(onClick = { confirmClear = true }, enabled = turns.isNotEmpty()) {
                    Text("clear all history")
                }
            } else {
                Button(onClick = {
                    scope.launch {
                        repo.history.clearAll()
                        confirmClear = false
                    }
                }) { Text("yes, delete everything") }
                TextButton(onClick = { confirmClear = false }) { Text("cancel") }
            }
        }
        if (shown.isEmpty()) {
            Text(
                if (query.isBlank()) "no conversations yet. talk to it and they show up here."
                else "no matches for that search.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(groups, key = { it.first().sessionId }) { group ->
                val sid = group.first().sessionId
                val started = group.minOf { it.timestamp }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "${fmt.format(Date(started))} | ${group.size} turn(s)",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        TextButton(onClick = { scope.launch { repo.history.deleteSession(sid) } }) {
                            Text("delete session")
                        }
                    }
                    group.sortedBy { it.timestamp }.forEach { t ->
                        Text("you: ${t.question}", style = MaterialTheme.typography.bodyMedium)
                        Text("Mercureal: ${t.answer}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${fmt.format(Date(t.timestamp))} | ${t.mode}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
