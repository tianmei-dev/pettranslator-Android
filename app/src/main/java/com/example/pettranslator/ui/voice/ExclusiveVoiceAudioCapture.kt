package com.example.pettranslator.ui.voice

import com.example.pettranslator.domain.recording.AudioCapture
import com.example.pettranslator.domain.recording.CaptureException
import com.example.pettranslator.domain.recording.CaptureFrame
import com.example.pettranslator.domain.recording.RecordedAudio
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex

/** Each voice destination supplies its own delegate; the token covers capture and file cleanup. */
internal class ExclusiveVoiceAudioCapture(
    private val delegate: AudioCapture,
    private val audit: (String) -> Unit,
) : AudioCapture {
    private val commandLock = Any()
    private var owner: Any? = null

    override suspend fun record(onFrame: (CaptureFrame) -> Unit): RecordedAudio =
        recordAndUse(onFrame) { it }

    /** The delegate has released AudioRecord; retain only this file until its consumer finishes. */
    suspend fun <T> recordAndUse(
        onFrame: (CaptureFrame) -> Unit,
        consume: suspend (RecordedAudio) -> T,
    ): T {
        val token = Any()
        if (!microphone.tryLock(token)) {
            throw CaptureException("麦克风正被其他录音占用，请稍后重试")
        }
        synchronized(commandLock) { owner = token }
        var invoked = false
        var completed: RecordedAudio? = null
        var frameCount = 0L
        var lastElapsedMs = 0L
        var lastSampleCount = 0L
        var rmsMin = Float.POSITIVE_INFINITY
        var rmsMax = Float.NEGATIVE_INFINITY
        var firstFrame = true
        try {
            currentCoroutineContext().ensureActive()
            invoked = true
            return delegate.record { frame ->
                frameCount++
                lastElapsedMs = frame.elapsedMs
                lastSampleCount = frame.sampleCount
                if (frame.rms.isFinite()) {
                    rmsMin = minOf(rmsMin, frame.rms)
                    rmsMax = maxOf(rmsMax, frame.rms)
                }
                if (firstFrame && frame.sampleCount > 0 && frame.elapsedMs > 0) {
                    firstFrame = false
                    report("first_frame elapsedMs=${frame.elapsedMs} sampleCount=${frame.sampleCount} rms=${frame.rms}")
                }
                onFrame(frame)
            }.let { audio ->
                completed = audio
                consume(audio)
            }
        } finally {
            try {
                if (invoked) {
                    val minimum = rmsMin.takeIf { it.isFinite() }?.toString() ?: "none"
                    val maximum = rmsMax.takeIf { it.isFinite() }?.toString() ?: "none"
                    report("ended completed=${completed != null} durationMs=${completed?.durationMs ?: lastElapsedMs} " +
                        "sampleCount=${completed?.sampleCount ?: lastSampleCount} frameCount=$frameCount " +
                        "frameElapsedMs=$lastElapsedMs rmsMin=$minimum rmsMax=$maximum")
                }
                // AudioCapture.record has already released AudioRecord before this deletion.
                synchronized(commandLock) {
                    if (owner === token) {
                        try {
                            if (invoked) {
                                delegate.discard()
                                report("cleanup ownSession=true delegateDiscardCompleted=true")
                            }
                        }
                        finally { owner = null }
                    }
                }
            } finally {
                microphone.unlock(token)
            }
        }
    }

    override fun stop() {
        synchronized(commandLock) { if (owner != null) delegate.stop() }
    }

    override fun discard() {
        synchronized(commandLock) { if (owner != null) delegate.discard() }
    }

    private fun report(message: String) {
        // Diagnostics must never change microphone ownership or cleanup behavior.
        runCatching { audit(message) }
    }

    private companion object {
        val microphone = Mutex()
    }
}
