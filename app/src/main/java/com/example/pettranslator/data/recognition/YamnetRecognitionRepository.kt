package com.example.pettranslator.data.recognition

import android.content.Context
import android.util.Log
import com.example.pettranslator.domain.PetScene
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.domain.recognition.AudioQuality
import com.example.pettranslator.domain.recognition.PetRecognitionPolicy
import com.example.pettranslator.domain.recognition.PetRecognitionResult
import com.example.pettranslator.domain.recognition.SoundEvent
import com.example.pettranslator.domain.recognition.YamnetRawResult
import com.example.pettranslator.domain.recording.RecordedAudio
import com.example.pettranslator.privacy.PrivacyGate
import java.io.Closeable
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

interface PetSoundRecognizer {
    suspend fun infer(audio: RecordedAudio): YamnetRawResult
}

/** One interpreter, confined to one executor for process lifetime. No audio leaves app storage. */
class YamnetRecognitionRepository(context: Context) : PetSoundRecognizer, Closeable {
    init { PrivacyGate.requireConsent() }

    private val appContext = context.applicationContext
    private val worker = AtomicReference<Thread?>(null)
    private val executor: ExecutorService = PrivacyGate.withConsent {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "pet-yamnet-inference").apply { isDaemon = true; worker.set(this) }
        }.also { Log.i("PrivacyConsent", "yamnet_runtime_created") }
    }
    private val lifecycleLock = Any()
    @Volatile private var closing = false
    private val activeJobs = mutableSetOf<Job>()
    private val closeResult = CompletableFuture<Unit>()
    private val stoppedLogged = AtomicBoolean(false)
    private var engine: YamnetEngine? = null // accessed only on executor thread
    private var initializationCloseFailure: Throwable? = null // accessed only on executor thread

    override suspend fun infer(audio: RecordedAudio): YamnetRawResult {
        PrivacyGate.requireConsent()
        val job = currentCoroutineContext().job
        PrivacyGate.trackCurrentJob(job).use {
            PrivacyGate.withConsent {
                synchronized(lifecycleLock) {
                    check(!closing) { "YAMNet 已关闭" }
                    activeJobs += job
                }
            }
            try {
                return inferOnExecutor(audio, job)
            } finally {
                synchronized(lifecycleLock) { activeJobs -= job }
            }
        }
    }

    private suspend fun inferOnExecutor(audio: RecordedAudio, job: Job): YamnetRawResult = suspendCancellableCoroutine { continuation ->
        val cancelled = AtomicBoolean(false)
        val future = AtomicReference<Future<*>?>(null)
        continuation.invokeOnCancellation {
            cancelled.set(true)
            future.get()?.cancel(true)
        }
        fun resumeFailure(error: Throwable) {
            if (cancelled.get() || !continuation.isActive) return
            try {
                PrivacyGate.withConsent {
                    if (!cancelled.get() && continuation.isActive) continuation.resumeWithException(error)
                }
            } catch (denied: Throwable) {
                continuation.cancel(CancellationException("Privacy consent revoked").apply { initCause(denied) })
            }
        }
        val task = Runnable {
            try {
                PrivacyGate.requireConsent()
                if (cancelled.get() || !job.isActive || closing) return@Runnable
                val model = engine ?: run {
                    PrivacyGate.requireConsent()
                    val candidate = YamnetEngine(appContext)
                    try {
                        // Native initialization may finish after cancellation; it must never start classification then.
                        candidate.load()
                        PrivacyGate.withConsent {
                            if (cancelled.get() || !job.isActive || closing) throw CancellationException("分析已取消")
                            engine = candidate
                            Log.i("PrivacyConsent", "yamnet_model_loaded")
                        }
                        candidate
                    } catch (error: Throwable) {
                        try { candidate.close() }
                        catch (closeFailure: Throwable) {
                            initializationCloseFailure = closeFailure
                            error.addSuppressed(closeFailure)
                        }
                        throw error
                    }
                }
                PrivacyGate.requireConsent()
                if (cancelled.get() || !job.isActive || closing) throw CancellationException("分析已取消")
                val raw = model.classify(
                    File(audio.filePath), audio.sampleRate, audio.sampleCount,
                    cancelled = { cancelled.get() || !job.isActive || closing ||
                        !PrivacyGate.isGranted() || Thread.currentThread().isInterrupted },
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
                PrivacyGate.withConsent {
                    if (!cancelled.get() && !closing && continuation.isActive) continuation.resume(result)
                }
            } catch (error: Throwable) {
                resumeFailure(error)
            }
        }
        val submitted = try {
            PrivacyGate.withConsent {
                synchronized(lifecycleLock) {
                    check(!closing) { "YAMNet 已关闭" }
                    executor.submit(task)
                }
            }
        } catch (error: Throwable) {
            resumeFailure(error)
            return@suspendCancellableCoroutine
        }
        future.set(submitted)
        if (cancelled.get()) submitted.cancel(true)
    }

    suspend fun recognize(audio: RecordedAudio, selected: PetType, scene: PetScene): PetRecognitionResult =
        PetRecognitionPolicy.decide(infer(audio), selected, scene)

    override fun close() {
        check(Thread.currentThread() !== worker.get()) { "YAMNet 不能在推理线程上同步关闭" }
        val result = beginShutdown()
        var interrupted = false
        var failure: Throwable? = null
        try {
            while (true) {
                try { result.get(); break }
                catch (_: InterruptedException) { interrupted = true }
                catch (error: ExecutionException) { failure = error.cause ?: error; break }
            }
            while (!executor.isTerminated) {
                try { executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS) }
                catch (_: InterruptedException) { interrupted = true }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
        failure?.let { throw it }
        if (stoppedLogged.compareAndSet(false, true)) Log.i("PrivacyConsent", "yamnet_runtime_stopped")
    }

    private fun beginShutdown(): CompletableFuture<Unit> {
        val jobs = synchronized(lifecycleLock) {
            if (closing) return closeResult
            closing = true
            activeJobs.toList()
        }
        jobs.forEach { it.cancel(CancellationException("YAMNet 已关闭")) }
        try {
            executor.execute {
                var failure = initializationCloseFailure
                try { engine?.close() }
                catch (error: Throwable) {
                    if (failure == null) failure = error else failure.addSuppressed(error)
                }
                finally { engine = null; executor.shutdown() }
                if (failure == null) closeResult.complete(Unit)
                else closeResult.completeExceptionally(failure)
            }
        } catch (error: Throwable) {
            executor.shutdown()
            closeResult.completeExceptionally(error)
        }
        return closeResult
    }
}

object YamnetRuntime {
    @Volatile private var instance: YamnetRecognitionRepository? = null
    private var shuttingDown = false
    fun get(context: Context): YamnetRecognitionRepository = PrivacyGate.withConsent {
        synchronized(this) {
            check(!shuttingDown) { "YAMNet 正在关闭" }
            instance ?: YamnetRecognitionRepository(context.applicationContext).also { instance = it }
        }
    }

    suspend fun shutdownForPrivacyRevocation() {
        val current = synchronized(this) {
            shuttingDown = true
            instance
        }
        withContext(NonCancellable + Dispatchers.IO) { current?.close() }
        synchronized(this) {
            if (instance === current) instance = null
            shuttingDown = false
        }
    }
}
