package com.example.pettranslator.data.recognition

import android.content.Context
import com.example.pettranslator.domain.PetScene
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.domain.recognition.AudioQuality
import com.example.pettranslator.domain.recognition.PetRecognitionPolicy
import com.example.pettranslator.domain.recognition.PetRecognitionResult
import com.example.pettranslator.domain.recognition.SoundEvent
import com.example.pettranslator.domain.recognition.YamnetRawResult
import com.example.pettranslator.domain.recording.RecordedAudio
import java.io.Closeable
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

interface PetSoundRecognizer {
    suspend fun infer(audio: RecordedAudio): YamnetRawResult
}

/** One interpreter, confined to one executor for process lifetime. No audio leaves app storage. */
class YamnetRecognitionRepository(context: Context) : PetSoundRecognizer, Closeable {
    private val appContext = context.applicationContext
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "pet-yamnet-inference").apply { isDaemon = true }
    }
    private var engine: YamnetEngine? = null // accessed only on executor thread

    override suspend fun infer(audio: RecordedAudio): YamnetRawResult = suspendCancellableCoroutine { continuation ->
        val cancelled = AtomicBoolean(false)
        val future = AtomicReference<Future<*>?>(null)
        continuation.invokeOnCancellation {
            cancelled.set(true)
            future.get()?.cancel(true)
        }
        val submitted = executor.submit {
            try {
                if (cancelled.get()) return@submit
                val model = engine ?: YamnetEngine(appContext).also { it.load(); engine = it }
                val raw = model.classify(
                    File(audio.filePath), audio.sampleRate, audio.sampleCount,
                    cancelled = { cancelled.get() || Thread.currentThread().isInterrupted },
                )
                val quality = AudioQuality(
                    durationMs = raw.durationMs,
                    rmsDbfs = raw.rmsDbfs,
                    clippedFraction = raw.clippedFraction,
                    silentFrameFraction = raw.silentFrameFraction,
                    validWindowCount = raw.windows.size.takeIf { raw.durationMs >= 975 } ?: 0,
                    usable = true,
                )
                val events = raw.means.indices.sortedByDescending { raw.means[it] }.take(8).map {
                    SoundEvent(it, raw.labels[it], raw.means[it])
                }
                val result = YamnetRawResult(raw.means, raw.windows, raw.labels, raw.cat, raw.dog,
                    raw.other, events.firstOrNull(), quality)
                if (!cancelled.get() && continuation.isActive) continuation.resume(result)
            } catch (error: Throwable) {
                if (!cancelled.get() && continuation.isActive) continuation.resumeWithException(error)
            }
        }
        future.set(submitted)
        if (cancelled.get()) submitted.cancel(true)
    }

    suspend fun recognize(audio: RecordedAudio, selected: PetType, scene: PetScene): PetRecognitionResult =
        PetRecognitionPolicy.decide(infer(audio), selected, scene)

    override fun close() {
        executor.execute { engine?.close(); engine = null }
        executor.shutdown()
    }
}

object YamnetRuntime {
    @Volatile private var instance: YamnetRecognitionRepository? = null
    fun get(context: Context): YamnetRecognitionRepository = instance ?: synchronized(this) {
        instance ?: YamnetRecognitionRepository(context.applicationContext).also { instance = it }
    }
}
