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

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var screen by remember { mutableStateOf(Screen.Home) }
                    when (screen) {
                        Screen.Home -> AssistantScreen(
                            state = assistant.state,
                            onOpenSettings = { screen = Screen.Settings },
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
                            onBack = { screen = Screen.Home },
                            onModels = { screen = Screen.Models },
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
fun SettingsScreen(onBack: () -> Unit, onModels: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("< back") }
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        OutlinedButton(onClick = onModels, modifier = Modifier.fillMaxWidth()) { Text("Models") }
    }
}

@Composable
fun AssistantScreen(
    state: AssistantState,
    onOpenSettings: () -> Unit,
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
