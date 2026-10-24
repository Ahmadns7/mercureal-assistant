package com.ahmadns7.mercureal.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.Intent
import com.ahmadns7.mercureal.assistant.ListenIntents
import com.ahmadns7.mercureal.assistant.model.ModelCatalog
import com.ahmadns7.mercureal.assistant.model.ModelRepository
import com.ahmadns7.mercureal.assistant.model.ModelSettings
import com.ahmadns7.mercureal.assistant.translate.Languages
import com.ahmadns7.mercureal.assistant.ui.AssistantState
import com.ahmadns7.mercureal.assistant.ui.HistoryScreen
import com.ahmadns7.mercureal.assistant.ui.WakePhraseEditor
import com.ahmadns7.mercureal.assistant.ui.ModelsScreen
import com.ahmadns7.mercureal.assistant.ui.NotesScreen
import com.ahmadns7.mercureal.assistant.dictate.DictateActivity
import com.ahmadns7.mercureal.assistant.wake.AssistantRegistry
import com.ahmadns7.mercureal.assistant.wake.WakeWordService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var assistant: AssistantController
    private lateinit var repo: ModelRepository

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) assistant.startListening() }

    private val extraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* the user repeats the command after granting */ }

    private fun handleListenIntent(intent: Intent?) {
        if (intent?.action != ListenIntents.ACTION_LISTEN) return
        val hasMic = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) assistant.startListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleListenIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = ModelRepository(applicationContext)
        assistant = AssistantController(applicationContext, repo)
        AssistantRegistry.controller = assistant
        assistant.permissionRequester = { perm -> extraPermission.launch(perm) }
        handleListenIntent(intent)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var screen by remember { mutableStateOf(Screen.Home) }
                    val wakePhrase by repo.settings.wakePhrase.collectAsState(initial = "ahmad")
                    when (screen) {
                        Screen.Home -> AssistantScreen(
                            state = assistant.state,
                            wakePhrase = wakePhrase,
                            onOpenSettings = { screen = Screen.Settings },
                            onOpenWake = { screen = Screen.Settings },
                            onDictate = { startActivity(Intent(this, DictateActivity::class.java)) },
                            repo = repo,
                            onPress = {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    this, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) assistant.startListening()
                                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onRelease = { assistant.stopListeningAndAnswer() },
                        )
                        Screen.Settings -> SettingsScreen(
                            repo = repo,
                            onBack = { screen = Screen.Home },
                            onModels = { screen = Screen.Models },
                            onNotes = { screen = Screen.Notes },
                            onHistory = { screen = Screen.History },
                            onWakeToggle = { on, micOk ->
                                if (on && micOk) WakeWordService.start(applicationContext)
                                else WakeWordService.stop(applicationContext)
                            },
                            onRequestMic = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                        )
                        Screen.History -> HistoryScreen(
                            repo = repo,
                            onBack = { screen = Screen.Settings },
                        )
                        Screen.Models -> ModelsScreen(
                            repo = repo,
                            onBack = { screen = Screen.Settings },
                        )
                        Screen.Notes -> NotesScreen(
                            repo = repo,
                            onBack = { screen = Screen.Settings },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        assistant.release()
        super.onDestroy()
    }

    private enum class Screen { Home, Settings, Models, Notes, History }
}

private data class FeatureOption(val key: String, val title: String, val description: String, val default: Boolean = false)

private val featureOptions = listOf(
    FeatureOption("music_coexistence", "Keep music playing", "Avoid pausing or ducking music while wake listening is active.", true),
    FeatureOption("haptic_feedback", "Haptic feedback", "Vibrate briefly when the wake word is detected."),
    FeatureOption("spoken_replies", "Speak replies aloud", "Read responses with the selected offline voice.", true),
    FeatureOption("save_history", "Save conversation history", "Keep transcripts on this phone for search and follow-up context.", true),
    FeatureOption("privacy_mode", "Privacy mode", "Clear the live transcript from the home screen after each answer."),
    FeatureOption("notification_status", "Persistent status notification", "Show the current listening state while background listening is active.", true),
    FeatureOption("battery_saver", "Battery saver", "Reduce wake-word sampling when the battery is low.", true),
    FeatureOption("screen_off_listening", "Listen with screen off", "Allow the wake word service to continue when the display is off.", true),
    FeatureOption("start_on_boot", "Start after reboot", "Restore wake listening after the phone restarts."),
    FeatureOption("pause_on_call", "Pause during calls", "Temporarily disable listening during phone or video calls.", true),
    FeatureOption("auto_restart", "Auto-restart listener", "Recover automatically if Android stops the audio recorder.", true),
    FeatureOption("confirm_actions", "Confirm sensitive actions", "Ask before calls, messages, or other device-changing actions.", true),
    FeatureOption("concise_replies", "Concise replies", "Prefer shorter answers for faster voice interactions."),
    FeatureOption("include_timestamps", "Show timestamps", "Add timestamps to saved history entries."),
    FeatureOption("auto_delete_audio", "Delete audio immediately", "Never retain captured audio after transcription.", true),
    FeatureOption("wake_sound", "Wake sound", "Play a subtle sound when the wake phrase is recognized."),
    FeatureOption("follow_up_suggestions", "Follow-up suggestions", "Offer a suggested next question after each answer."),
    FeatureOption("network_guard", "Offline-only guard", "Block commands that would require network access.", true),
    FeatureOption("diagnostics", "Local diagnostics", "Record anonymized timing data to help troubleshoot listening issues."),
)

@Composable
private fun FeatureSwitch(option: FeatureOption, repo: ModelRepository) {
    val scope = rememberCoroutineScope()
    val enabled by repo.settings.feature(option.key, option.default).collectAsState(initial = option.default)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(option.title)
            Text(option.description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = enabled, onCheckedChange = { value -> scope.launch { repo.settings.setFeature(option.key, value) } })
    }
}

@Composable
fun SettingsScreen(
    repo: ModelRepository,
    onBack: () -> Unit,
    onModels: () -> Unit,
    onNotes: () -> Unit,
    onHistory: () -> Unit,
    onWakeToggle: (on: Boolean, micOk: Boolean) -> Unit,
    onRequestMic: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val wakeOn by repo.settings.wakeOn.collectAsState(initial = false)
    val conversationOn by repo.settings.conversationOn.collectAsState(initial = true)
    val retentionDays by repo.settings.retentionDays.collectAsState(initial = 30)
    val retentionTurns by repo.settings.retentionTurns.collectAsState(initial = 500)
    val wakePhrase by repo.settings.wakePhrase.collectAsState(initial = "ahmad")
    val followUp by repo.settings.followUpSeconds.collectAsState(initial = 60)
    val installed by repo.installed.collectAsState()
    val kwsReady = ModelCatalog.KWS_ID in installed
    val context = androidx.compose.ui.platform.LocalContext.current
    val micOk = androidx.core.content.ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("< back") }
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Wake word \"$wakePhrase\"")
                Text(
                    when {
                        !kwsReady -> "needs the wake word model: download it in Models."
                        !micOk -> "needs microphone permission."
                        else -> "listens on this phone only. a persistent notification shows while it runs."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = wakeOn,
                enabled = kwsReady,
                onCheckedChange = { on ->
                    scope.launch { repo.settings.setWakeOn(on) }
                    if (on && !micOk) onRequestMic() else onWakeToggle(on, micOk)
                },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Conversation mode")
                Text(
                    "after a reply, keep listening for your next question without saying \"ahmad\" again. say \"stop listening\" or \"bye\" to end it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = conversationOn,
                onCheckedChange = { on -> scope.launch { repo.settings.setConversationOn(on) } },
            )
        }
        WakePhraseEditor(
            repo = repo,
            wakeOn = wakeOn,
            onRestart = { onWakeToggle(true, micOk) },
        )
        Text("History keeps", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModelSettings.RETENTION_DAYS.forEach { d ->
                OutlinedButton(onClick = { scope.launch { repo.settings.setRetentionDays(d) } }) {
                    Text(if (d == retentionDays) "${d}d *" else "${d}d")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModelSettings.RETENTION_TURNS.forEach { t ->
                OutlinedButton(onClick = { scope.launch { repo.settings.setRetentionTurns(t) } }) {
                    Text(if (t == retentionTurns) "$t turns *" else "$t turns")
                }
            }
        }
        if (conversationOn) {
            Text("Follow-up window: ends after ${followUp}s of silence", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModelSettings.FOLLOW_UP_OPTIONS.forEach { s ->
                    OutlinedButton(onClick = { scope.launch { repo.settings.setFollowUpSeconds(s) } }) {
                        Text(if (s == followUp) "${s}s *" else "${s}s")
                    }
                }
            }
        }
        val sttLang by repo.settings.sttLanguage.collectAsState(initial = "auto")
        val translationOn by repo.settings.translationOn.collectAsState(initial = false)
        val targetCode by repo.settings.translationTarget.collectAsState(initial = Languages.DEFAULT_TARGET)
        val activeStt = repo.settings.activeStt.collectAsState(initial = ModelCatalog.DEFAULT_STT).value
        val sttMultilingual = ModelCatalog.byId(activeStt)?.multilingual == true
        Text("Speech language", style = MaterialTheme.typography.titleSmall)
        Text(
            if (sttMultilingual) "auto detects what you speak. pick one to lock it."
            else "the active speech model is English-only (.en). switch to a multilingual model in Models to use this.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (sttMultilingual) {
            OutlinedButton(onClick = {
                val codes = Languages.sttCodes
                val next = codes[(codes.indexOf(sttLang).coerceAtLeast(0) + 1) % codes.size]
                scope.launch { repo.settings.setSttLanguage(next) }
            }) { Text("language: $sttLang (tap to change)") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Translation")
                Text(
                    "say \"say X in french\" or \"translate X to spanish\". the reply is translated on this phone. the voice only speaks English, so other languages show on screen.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = translationOn,
                onCheckedChange = { on -> scope.launch { repo.settings.setTranslationOn(on) } },
            )
        }
        if (translationOn) {
            Text("Default target language", style = MaterialTheme.typography.bodySmall)
            val targets = Languages.targets
            val current = targets.firstOrNull { it.code == targetCode } ?: targets.first()
            OutlinedButton(onClick = {
                val next = targets[(targets.indexOf(current) + 1) % targets.size]
                scope.launch { repo.settings.setTranslationTarget(next.code) }
            }) { Text("target: ${current.name} (tap to change)") }
        }
        Text("Assistant features (19)", style = MaterialTheme.typography.titleSmall)
        Text("These controls are saved on this phone and take effect the next time listening starts.", style = MaterialTheme.typography.bodySmall)
        featureOptions.forEach { FeatureSwitch(option = it, repo = repo) }
        OutlinedButton(onClick = onNotes, modifier = Modifier.fillMaxWidth()) { Text("Notes") }
        OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("History") }
        OutlinedButton(onClick = onModels, modifier = Modifier.fillMaxWidth()) { Text("Models") }
    }
}

/** Human label for the controller state. */
fun stateLabel(state: AssistantState): String = when {
    state.session && state.label == "listening" -> "listening for your next question"
    state.label == "listening" -> "listening"
    state.label == "transcribing" -> "thinking (hearing you)"
    state.label == "thinking" -> "thinking"
    state.label == "speaking" -> "speaking"
    state.label == "idle" && state.session -> "conversation open"
    else -> state.label
}

@Composable
fun AssistantScreen(
    state: AssistantState,
    wakePhrase: String,
    onOpenSettings: () -> Unit,
    onOpenWake: () -> Unit,
    onDictate: () -> Unit,
    repo: ModelRepository,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(text = "Mercureal", style = MaterialTheme.typography.headlineMedium)
                Text(text = "private voice assistant", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onOpenSettings) { Text("Settings") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${stateLabel(state).replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (state.session) "Conversation is open. You can ask a follow-up without repeating the wake phrase."
                    else "Say \"$wakePhrase\" when wake word listening is enabled.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (state.transcript.isNotBlank() || state.reply.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.transcript.isNotBlank()) Text("You", style = MaterialTheme.typography.labelMedium)
                    if (state.transcript.isNotBlank()) Text(state.transcript)
                    if (state.reply.isNotBlank()) {
                        HorizontalDivider()
                        Text("Mercureal", style = MaterialTheme.typography.labelMedium)
                        Text(state.reply)
                    }
                }
            }
        }
        Button(
            onClick = {},
            modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onPress()
                    tryAwaitRelease()
                    onRelease()
                })
            },
        ) { Text("Hold to talk") }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onDictate, modifier = Modifier.weight(1f)) { Text("Dictate") }
            OutlinedButton(onClick = onOpenWake, modifier = Modifier.weight(1f)) { Text("Wake word") }
        }
        Text("Offline processing enabled. Audio and history stay on this phone.", style = MaterialTheme.typography.bodySmall)
    }
}
