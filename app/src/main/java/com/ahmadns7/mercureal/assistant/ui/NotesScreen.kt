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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahmadns7.mercureal.assistant.model.ModelRepository
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/** Settings > Notes: view and delete the on-device notes saved with "remember that ...". */
@Composable
fun NotesScreen(repo: ModelRepository, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val notes by repo.notes.notes.collectAsState(initial = emptyList())
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text("< back") }
        Text("Notes", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Say \"remember that ...\" to save one. Notes stay on this phone and are only shared with the assistant when you ask about them.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (notes.isNotEmpty()) {
            OutlinedButton(onClick = { scope.launch { repo.notes.deleteAll() } }, modifier = Modifier.fillMaxWidth()) {
                Text("delete all notes")
            }
        }
        if (notes.isEmpty()) {
            Text("No notes yet.", modifier = Modifier.padding(top = 12.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(notes, key = { it.id }) { note ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(note.text)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(note.createdAt)),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { scope.launch { repo.notes.delete(note.id) } }) { Text("delete") }
                    }
                }
            }
        }
    }
}
