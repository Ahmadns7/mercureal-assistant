package com.ahmadns7.mercureal.assistant.model

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DownloadState {
    /** [resumedFrom] is the byte offset the transfer started from (0 when it is a fresh download). */
    data class Running(val progress: Float, val resumedFrom: Long = 0L) : DownloadState
    /** [partKept] is true when the partial file is still on disk and a retry resumes it. */
    data class Failed(val message: String, val partKept: Boolean) : DownloadState
}

/** Download queue and installed-state for the Models screen. */
class ModelRepository(context: Context) {

    val store = ModelStore(context)
    val settings = ModelSettings(context)
    val entitlements = com.ahmadns7.mercureal.assistant.model.Entitlements(context)
    val notes = com.ahmadns7.mercureal.assistant.memory.NoteStore(context)
    val history = com.ahmadns7.mercureal.assistant.history.HistoryStore(context, settings)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = HashMap<String, Job>()

    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads.asStateFlow()

    private val _installed = MutableStateFlow(computeInstalled())
    val installed: StateFlow<Set<String>> = _installed.asStateFlow()

    fun download(spec: ModelSpec) {
        if (jobs[spec.id]?.isActive == true) return
        jobs[spec.id] = scope.launch {
            _downloads.update { it + (spec.id to DownloadState.Running(0f)) }
            try {
                ModelDownloader.download(
                    spec = spec,
                    store = store,
                    onStart = { from ->
                        _downloads.update { it + (spec.id to DownloadState.Running(0f, from)) }
                    },
                    onProgress = { p ->
                        _downloads.update {
                            val resumed = (it[spec.id] as? DownloadState.Running)?.resumedFrom ?: 0L
                            it + (spec.id to DownloadState.Running(p, resumed))
                        }
                    },
                )
                _downloads.update { it - spec.id }
            } catch (e: CancellationException) {
                // Cancel keeps the .part for a later resume.
                _downloads.update { it - spec.id }
                throw e
            } catch (e: Exception) {
                val kept = e !is ModelVerificationException && store.partFileFor(spec).isFile
                _downloads.update {
                    it + (spec.id to DownloadState.Failed(e.message ?: "download failed", kept))
                }
            } finally {
                _installed.value = computeInstalled()
            }
        }
    }

    fun cancel(spec: ModelSpec) {
        jobs.remove(spec.id)?.cancel()
    }

    /** Deletes the downloaded file and any partial download. The UI disables delete for the active model. */
    fun delete(spec: ModelSpec) {
        store.delete(spec)
        _downloads.update { it - spec.id }
        _installed.value = computeInstalled()
    }

    fun release() {
        scope.cancel()
    }

    private fun computeInstalled(): Set<String> =
        ModelCatalog.all.filter { store.isInstalled(it) }.map { it.id }.toSet()
}
