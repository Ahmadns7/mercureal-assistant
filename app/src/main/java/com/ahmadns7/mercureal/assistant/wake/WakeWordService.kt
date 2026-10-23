package com.ahmadns7.mercureal.assistant.wake

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ahmadns7.mercureal.assistant.AssistantController
import com.ahmadns7.mercureal.assistant.MainActivity
import com.ahmadns7.mercureal.assistant.model.ModelCatalog
import com.ahmadns7.mercureal.assistant.model.ModelRepository
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

/**
 * Always-on keyword listening. Runs as a foreground service with foregroundServiceType=microphone
 * and a persistent notification, so Android keeps the mic and the user always sees it is listening.
 *
 * Battery: only the small KWS model runs continuously (int8, one thread). Whisper, llama and TTS
 * are NOT loaded until a keyword fires: the utterance captured after the hit is handed to
 * AssistantController, which runs the existing STT -> LLM -> TTS path.
 *
 * Fail closed: if the wake model or keywords cannot be loaded, the service stops itself and the
 * toggle in the app is switched back off.
 */
class WakeWordService : Service() {

    @Volatile private var running = false
    private var worker: Thread? = null
    private var detector: WakeWordDetector? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        val notification = buildNotification("listening for \"ahmad\"")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        if (!running) {
            val repo = ModelRepository(applicationContext)
            val det = try {
                WakeWordDetector.create(applicationContext, repo.store)
            } catch (e: Exception) {
                Log.w(TAG, "wake word unavailable: ${e.message}")
                failClosed(repo, "wake word unavailable: ${e.message}")
                return START_NOT_STICKY
            }
            detector = det
            running = true
            worker = thread(name = "wake-kws") { listen(det, repo) }
        }
        return START_NOT_STICKY
    }

    private fun listen(det: WakeWordDetector, repo: ModelRepository) {
        val minBuf = AudioRecord.getMinBufferSize(
            WakeWordDetector.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            failClosed(repo, "microphone permission missing")
            return
        }
        @Suppress("MissingPermission")
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            WakeWordDetector.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2,
        )
        val chunk = ShortArray(minBuf / 2)
        val floats = FloatArray(chunk.size)
        rec.startRecording()
        try {
            while (running) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n <= 0) continue
                for (i in 0 until n) floats[i] = chunk[i] / 32768f
                if (det.accept(floats.copyOf(n))) onKeyword(rec)
            }
        } finally {
            rec.stop()
            rec.release()
        }
    }

    /**
     * Keyword fired: capture the next utterance (up to ~6 s, or until ~1 s of silence), then hand the
     * PCM to the existing whisper pipeline. Runs on the worker thread, so it never blocks the KWS loop
     * for longer than the capture window.
     */
    private fun onKeyword(rec: AudioRecord) {
        updateNotification("heard \"ahmad\", listening")
        val buf = ByteArrayOutputStream()
        val minBuf = AudioRecord.getMinBufferSize(WakeWordDetector.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val chunk = ByteArray(minBuf)
        val maxBytes = WakeWordDetector.SAMPLE_RATE * 2 * MAX_UTTERANCE_SECONDS
        var quiet = 0
        val quietLimit = WakeWordDetector.SAMPLE_RATE * 2 / minBuf * SILENCE_END_MS / 1000
        while (buf.size() < maxBytes && running) {
            val n = rec.read(chunk, 0, chunk.size)
            if (n <= 0) continue
            buf.write(chunk, 0, n)
            if (rms(chunk, n) < SILENCE_RMS) quiet++ else quiet = 0
            if (quiet >= quietLimit && buf.size() > WakeWordDetector.SAMPLE_RATE / 2) break
        }
        val bytes = buf.toByteArray()
        val pcm = FloatArray(bytes.size / 2)
        for (i in pcm.indices) {
            val lo = bytes[2 * i].toInt() and 0xff
            val hi = bytes[2 * i + 1].toInt()
            pcm[i] = ((hi shl 8) or lo) / 32768f
        }
        updateNotification("listening for \"ahmad\"")
        AssistantRegistry.controller?.answerUtterance(pcm)
    }

    private fun rms(bytes: ByteArray, n: Int): Double {
        var sum = 0.0
        var i = 0
        while (i + 1 < n) {
            val s = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xff)).toShort().toDouble()
            sum += s * s
            i += 2
        }
        return kotlin.math.sqrt(sum / (n / 2).coerceAtLeast(1))
    }

    private fun failClosed(repo: ModelRepository, why: String) {
        Log.w(TAG, "failing closed: $why")
        running = false
        // Switch the toggle back off so the UI does not claim it is listening.
        kotlinx.coroutines.runBlocking { repo.settings.setWakeOn(false) }
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        worker?.join(1000)
        detector?.release()
        detector = null
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Wake word", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Shown while Mercureal is listening for its wake word" }
            )
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Mercureal wake word on")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "stop", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        private const val TAG = "WakeWordService"
        private const val CHANNEL_ID = "wake_word"
        private const val NOTIF_ID = 4217
        private const val ACTION_STOP = "com.ahmadns7.mercureal.assistant.wake.STOP"
        private const val MAX_UTTERANCE_SECONDS = 6
        private const val SILENCE_END_MS = 900
        private const val SILENCE_RMS = 500.0

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WakeWordService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, WakeWordService::class.java).setAction(ACTION_STOP))
        }
    }
}

/** Process-wide handle to the controller so the service can hand off an utterance. */
object AssistantRegistry {
    @Volatile var controller: AssistantController? = null
}
