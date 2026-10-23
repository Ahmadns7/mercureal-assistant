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
import com.ahmadns7.mercureal.assistant.ui.ModelsScreen
import com.ahmadns7.mercureal.assistant.ui.NotesScreen
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
                    when (screen) {
                        Screen.Home -> AssistantScreen(
                            state = assistant.state,
                            onOpenSettings = { screen = Screen.Settings },
                            onOpenWake = { screen = Screen.Settings },
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
                            onWakeToggle = { on, micOk ->
                                if (on && micOk) WakeWordService.start(applicationContext)
                                else WakeWordService.stop(applicationContext)
                            },
                            onRequestMic = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
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

    private enum class Screen { Home, Settings, Models, Notes }
}

@Composable
fun SettingsScreen(
    repo: ModelRepository,
    onBack: () -> Unit,
    onModels: () -> Unit,
    onNotes: () -> Unit,
    onWakeToggle: (on: Boolean, micOk: Boolean) -> Unit,
    onRequestMic: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val wakeOn by repo.settings.wakeOn.collectAsState(initial = false)
    val conversationOn by repo.settings.conversationOn.collectAsState(initial = true)
    val followUp by repo.settings.followUpSeconds.collectAsState(initial = 60)
    val installed by repo.installed.collectAsState()
    val kwsReady = ModelCatalog.KWS_ID in installed
    val context = androidx.compose.ui.platform.LocalContext.current
    val micOk = androidx.core.content.ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("< back") }
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Wake word \"ahmad\"")
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
        OutlinedButton(onClick = onNotes, modifier = Modifier.fillMaxWidth()) { Text("Notes") }
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
    onOpenSettings: () -> Unit,
    onOpenWake: () -> Unit,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Mercureal", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "offline. on-device. nothing leaves this phone.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(text = "status: ${stateLabel(state)}", modifier = Modifier.padding(top = 16.dp))
        Text(text = "say \"ahmad\" when wake word is on in Settings", style = MaterialTheme.typography.bodySmall)
        if (state.transcript.isNotBlank()) {
            Text(text = "you: ${state.transcript}", modifier = Modifier.padding(top = 12.dp))
        }
        if (state.reply.isNotBlank()) {
            Text(text = "assistant: ${state.reply}", modifier = Modifier.padding(top = 12.dp))
        }
        Button(
            onClick = {},
            modifier = Modifier
                .padding(top = 32.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onPress()
                            tryAwaitRelease()
                            onRelease()
                        }
                    )
                },
        ) {
            Text("hold to talk")
        }
        TextButton(onClick = onOpenSettings, modifier = Modifier.padding(top = 16.dp)) {
            Text("Settings")
        }
    }
}
