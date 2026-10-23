package com.ahmadns7.mercureal.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ahmadns7.mercureal.assistant.model.ModelRepository
import com.ahmadns7.mercureal.assistant.wake.WakePhrase
import kotlinx.coroutines.launch

/**
 * Wake phrase picker: two hand-checked presets plus a free-text custom phrase. Saving builds the
 * keyword line, persists phrase and keyword line in DataStore, and restarts the listener if it is on.
 */
@Composable
fun WakePhraseEditor(repo: ModelRepository, wakeOn: Boolean, onRestart: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val saved by repo.settings.wakePhrase.collectAsState(initial = WakePhrase.DEFAULT_PHRASE)
    var draft by remember(saved) { mutableStateOf(saved) }
    var message by remember { mutableStateOf<String?>(null) }

    fun applyPhrase(input: String) {
        scope.launch {
            try {
                val lexicon = WakePhrase.lexiconFor(context, WakePhrase.wordsNeeded(input))
                val line = WakePhrase.build(input, lexicon)
                repo.settings.setWakePhrase(line.phrase, line.keywords)
                draft = line.phrase
                val note = when {
                    line.fromPreset -> "saved \"${line.phrase}\"."
                    line.oovWords.isNotEmpty() ->
                        "saved \"${line.phrase}\". ${line.oovWords.joinToString(", ")} is not in the " +
                            "pronunciation dictionary, so it uses rough spelling sounds and may not trigger."
                    else -> "saved \"${line.phrase}\" from the pronunciation lookup. detection quality varies."
                }
                message = if (wakeOn) "$note restarting the listener." else note
                if (wakeOn) onRestart()
            } catch (e: IllegalArgumentException) {
                message = e.message ?: "that phrase can't be used"
            }
        }
    }

    Text("Wake phrase", style = MaterialTheme.typography.titleSmall)
    Text(
        "the phrase that wakes the assistant. the presets are hand-checked. any other phrase (1 to 3 " +
            "letters-only words) uses an english pronunciation lookup, so names and made-up words may " +
            "not trigger. short, distinct words work best.",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WakePhrase.PRESET_PHRASES.forEach { p ->
            OutlinedButton(onClick = {
                draft = p
                applyPhrase(p)
            }) { Text(if (p == saved) "${p.uppercase()} *" else p.uppercase()) }
        }
    }
    OutlinedTextField(
        value = draft,
        onValueChange = {
            draft = it
            message = null
        },
        singleLine = true,
        label = { Text("custom phrase") },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { applyPhrase(draft) }, enabled = draft.isNotBlank()) { Text("save phrase") }
        if (draft != saved) {
            TextButton(onClick = {
                draft = saved
                message = null
            }) { Text("reset") }
        }
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}
