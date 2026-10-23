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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ahmadns7.mercureal.assistant.model.ModelRepository
import com.ahmadns7.mercureal.assistant.ui.AssistantState
import com.ahmadns7.mercureal.assistant.ui.ModelsScreen
import com.ahmadns7.mercureal.assistant.model.ModelCatalog
import com.ahmadns7.mercureal.assistant.wake.AssistantRegistry
import com.ahmadns7.mercureal.assistant.wake.WakeWordService
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.Switch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var assistant: AssistantController
    private lateinit var repo: ModelRepository

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) assistant.startListening() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = ModelRepository(applicationContext)
        assistant = AssistantController(applicationContext, repo)
        AssistantRegistry.controller = assistant

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
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        assistant.release()
        super.onDestroy()
    }

    private enum class Screen { Home, Settings, Models }
}

@Composable
fun SettingsScreen(
    repo: ModelRepository,
    onBack: () -> Unit,
    onModels: () -> Unit,
    onWakeToggle: (on: Boolean, micOk: Boolean) -> Unit,
    onRequestMic: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val wakeOn by repo.settings.wakeOn.collectAsState(initial = false)
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
        OutlinedButton(onClick = onModels, modifier = Modifier.fillMaxWidth()) { Text("Models") }
    }
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
        Text(text = "status: ${state.label}", modifier = Modifier.padding(top = 16.dp))
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
