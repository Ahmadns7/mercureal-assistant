package com.ahmadns7.mercureal.assistant.dictate

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ahmadns7.mercureal.assistant.audio.MicRecorder
import com.ahmadns7.mercureal.assistant.engine.SttEngine
import com.ahmadns7.mercureal.assistant.model.ModelCatalog
import com.ahmadns7.mercureal.assistant.model.ModelRepository
import com.ahmadns7.mercureal.assistant.wake.AssistantRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Offline dictation. Records on this phone, transcribes with the installed whisper model,
 * then lets the user copy the text or send it to any app through the system share sheet.
 * No network: this screen declares nothing new and calls no server.
 *
 * Reached from the home screen "dictate" button and from the app icon long-press shortcut.
 */
class DictateActivity : ComponentActivity() {

    private lateinit var repo: ModelRepository
    private val stt = SttEngine()
    private val recorder = MicRecorder()

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* the user taps record again after granting */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = ModelRepository(applicationContext)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DictateScreen(
                        repo = repo,
                        stt = stt,
                        recorder = recorder,
                        onNeedMic = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                        onShare = ::share,
                        onCopy = ::copy,
                        onClose = { finish() },
                    )
                }
            }
        }
    }

    private fun copy(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("MercuReal dictation", text))
    }

    private fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, "send dictation to"))
    }

    override fun onDestroy() {
        recorder.stop()
        stt.close()
        super.onDestroy()
    }
}

private enum class Phase { LOADING, READY, RECORDING, TRANSCRIBING, DONE, ERROR }

@Composable
private fun DictateScreen(
    repo: ModelRepository,
    stt: SttEngine,
    recorder: MicRecorder,
    onNeedMic: () -> Unit,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(Phase.LOADING) }
    var message by remember { mutableStateOf("loading the speech model") }
    var text by remember { mutableStateOf("") }
    var seconds by remember { mutableLongStateOf(0L) }
    var copied by remember { mutableStateOf(false) }

    // Load the whisper model once, off the main thread.
    LaunchedEffect(Unit) {
        val sttId = repo.settings.activeStt.first()
        val spec = ModelCatalog.byId(sttId)
        when {
            spec == null || !repo.store.isInstalled(spec) -> {
                phase = Phase.ERROR
                message = "no speech model installed. get one in Settings > Models."
            }
            AssistantRegistry.controller?.micInUse == true || repo.settings.wakeOn.first() -> {
                phase = Phase.ERROR
                message = "the wake word is listening and holds the mic. turn it off in Settings, then dictate."
            }
            else -> {
                try {
                    withContext(Dispatchers.Default) { stt.load(repo.store.fileFor(spec)) }
                    phase = Phase.READY
                    message = "tap record, speak, tap stop. up to 30 seconds."
                } catch (e: Exception) {
                    phase = Phase.ERROR
                    message = "couldn't load the speech model: ${e.message}"
                }
            }
        }
    }

    // Elapsed counter while recording, plus the 30 s cap that MicRecorder enforces.
    LaunchedEffect(phase) {
        if (phase == Phase.RECORDING) {
            seconds = 0
            while (phase == Phase.RECORDING && seconds < 30) {
                delay(1000)
                seconds += 1
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Dictate", style = MaterialTheme.typography.headlineMedium)
        Text("offline. your voice is transcribed on this phone only.", style = MaterialTheme.typography.bodySmall)
        Text("status: ${statusLine(phase, seconds, message)}. $message")

        if (phase == Phase.READY || phase == Phase.DONE) {
            Button(onClick = {
                val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                if (!hasMic) {
                    onNeedMic()
                } else {
                    recorder.start()
                    copied = false
                    phase = Phase.RECORDING
                    message = "recording. tap stop when done."
                }
            }) { Text("record") }
        }

        if (phase == Phase.RECORDING) {
            Button(onClick = {
                val pcm = recorder.stop()
                phase = Phase.TRANSCRIBING
                message = "transcribing on this phone"
                scope.launch {
                    val result = withContext(Dispatchers.Default) {
                        val lang = if (ModelCatalog.byId(repo.settings.activeStt.first())?.multilingual == true) {
                            repo.settings.sttLanguage.first()
                        } else "en"
                        stt.transcribe(pcm, lang)
                    }
                    if (result.isBlank()) {
                        phase = Phase.READY
                        message = "didn't catch that. try again."
                    } else {
                        text = result
                        phase = Phase.DONE
                        message = "done. edit if you like, then copy or send."
                    }
                }
            }) { Text("stop") }
        }

        if (phase == Phase.DONE) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("dictated text") },
                minLines = 3,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    onCopy(text)
                    copied = true
                }) { Text(if (copied) "copied" else "copy") }
                OutlinedButton(onClick = { onShare(text) }) { Text("send to app") }
            }
        }

        OutlinedButton(onClick = onClose) { Text("close") }
    }
}

private fun statusLine(phase: Phase, seconds: Long, message: String): String = when (phase) {
    Phase.LOADING -> "loading the speech model"
    Phase.READY -> "ready"
    Phase.RECORDING -> "recording ${seconds}s of 30s"
    Phase.TRANSCRIBING -> "transcribing"
    Phase.DONE -> "done"
    Phase.ERROR -> "error"
}
