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
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahmadns7.mercureal.assistant.model.DownloadState
import com.ahmadns7.mercureal.assistant.model.ModelCatalog
import com.ahmadns7.mercureal.assistant.model.ModelKind
import com.ahmadns7.mercureal.assistant.model.ModelRepository
import com.ahmadns7.mercureal.assistant.model.ModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Settings > Models: download, switch and delete the curated on-device models. */
@Composable
fun ModelsScreen(repo: ModelRepository, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val downloads by repo.downloads.collectAsState()
    val installed by repo.installed.collectAsState()
    val activeStt by repo.settings.activeStt.collectAsState(initial = ModelCatalog.DEFAULT_STT)
    val activeLlm by repo.settings.activeLlm.collectAsState(initial = ModelCatalog.DEFAULT_LLM)
    val activeTts by repo.settings.activeTts.collectAsState(initial = ModelCatalog.DEFAULT_TTS)
    val scope = rememberCoroutineScope()
    val usedBytes = remember(installed) { repo.store.bytesUsed() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TextButton(onClick = onBack) { Text("< back") } }
        item { Text("Models", style = MaterialTheme.typography.headlineMedium) }
        item {
            Text(
                "Downloads come from Hugging Face over HTTPS. Each file is checked against a pinned sha256 " +
                    "before it is accepted and is stored only in this app's private storage. " +
                    "Space used by downloaded models: ${formatBytes(usedBytes)}.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        item {
            Text("Speech to text (whisper.cpp)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
        items(ModelCatalog.ofKind(ModelKind.STT), key = { it.id }) { spec ->
            ModelCard(
                spec = spec,
                isInstalled = spec.id in installed,
                isActive = spec.id == activeStt,
                download = downloads[spec.id],
                partialBytes = repo.store.partialBytesFor(spec),
                onDownload = { repo.download(spec) },
                onCancel = { repo.cancel(spec) },
                onUse = { scope.launch { repo.settings.setActiveStt(spec.id) } },
                onDelete = { scope.launch(Dispatchers.IO) { repo.delete(spec) } },
            )
        }

        item {
            Text("Language model (llama.cpp)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
        items(ModelCatalog.ofKind(ModelKind.LLM), key = { it.id }) { spec ->
            ModelCard(
                spec = spec,
                isInstalled = spec.id in installed,
                isActive = spec.id == activeLlm,
                download = downloads[spec.id],
                partialBytes = repo.store.partialBytesFor(spec),
                onDownload = { repo.download(spec) },
                onCancel = { repo.cancel(spec) },
                onUse = { scope.launch { repo.settings.setActiveLlm(spec.id) } },
                onDelete = { scope.launch(Dispatchers.IO) { repo.delete(spec) } },
            )
        }

        item {
            Text("Wake word (sherpa-onnx keyword spotting)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
        items(ModelCatalog.ofKind(ModelKind.KWS), key = { it.id }) { spec ->
            ModelCard(
                spec = spec,
                isInstalled = spec.id in installed,
                isActive = true,
                download = downloads[spec.id],
                partialBytes = repo.store.partialBytesFor(spec),
                onDownload = { repo.download(spec) },
                onCancel = { repo.cancel(spec) },
                onUse = {},
                onDelete = { scope.launch(Dispatchers.IO) { repo.delete(spec) } },
            )
        }

        item {
            Text(
                "Text to speech (sherpa-onnx, offline)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "Replies are spoken only by this on-device voice. If no voice is installed, the app stops with an error instead of using any system voice.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        items(ModelCatalog.ofKind(ModelKind.TTS), key = { it.id }) { spec ->
            ModelCard(
                spec = spec,
                isInstalled = spec.id in installed,
                isActive = spec.id == activeTts,
                download = downloads[spec.id],
                partialBytes = repo.store.partialBytesFor(spec),
                onDownload = { repo.download(spec) },
                onCancel = { repo.cancel(spec) },
                onUse = { scope.launch { repo.settings.setActiveTts(spec.id) } },
                onDelete = { scope.launch(Dispatchers.IO) { repo.delete(spec) } },
            )
        }
    }
}

@Composable
private fun ModelCard(
    spec: ModelSpec,
    isInstalled: Boolean,
    isActive: Boolean,
    download: DownloadState?,
    partialBytes: Long,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onUse: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(spec.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                "size ${formatBytes(spec.sizeBytes)} | license ${spec.license}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("source: ${spec.source}", style = MaterialTheme.typography.bodySmall)

            when {
                download is DownloadState.Running -> {
                    LinearProgressIndicator(
                        progress = download.progress,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    val label = if (download.resumedFrom > 0L) {
                        "resuming from ${formatBytes(download.resumedFrom)}: ${(download.progress * 100).toInt()}%"
                    } else {
                        "downloading ${(download.progress * 100).toInt()}%"
                    }
                    Text(label, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onCancel, modifier = Modifier.padding(top = 4.dp)) { Text("cancel") }
                }
                download is DownloadState.Failed -> {
                    Text(
                        if (download.partKept) {
                            "download interrupted: ${download.message}. progress was saved, retry resumes it."
                        } else {
                            "download failed: ${download.message}. the partial file was removed."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = onDownload, modifier = Modifier.padding(top = 4.dp)) {
                        Text(if (download.partKept) "resume download" else "retry download")
                    }
                }
                isInstalled -> {
                    Text(
                        if (isActive) "downloaded, active" else "downloaded",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!isActive) Button(onClick = onUse) { Text("use") }
                        OutlinedButton(onClick = onDelete, enabled = !isActive) { Text("delete") }
                    }
                    if (isActive) {
                        Text(
                            "switch to another model before deleting this one.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                else -> {
                    val partial = partialBytes
                    if (partial > 0L) {
                        Text(
                            "${formatBytes(partial)} already downloaded, will resume.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Button(onClick = onDownload, modifier = Modifier.padding(top = 4.dp)) {
                        Text(if (partial > 0L) "resume download" else "download")
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1e9)
    bytes >= 1_000_000L -> "%.0f MB".format(bytes / 1e6)
    else -> "$bytes B"
}
