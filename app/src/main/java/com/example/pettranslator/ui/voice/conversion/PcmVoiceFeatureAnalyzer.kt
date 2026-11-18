package com.example.pettranslator.ui.voice.conversion

import com.example.pettranslator.domain.recording.RecordedAudio
import java.io.EOFException
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Reads the current recording lease without retaining PCM samples or writing any files. */
class PcmVoiceFeatureAnalyzer : VoiceFeatureAnalyzer {
    override suspend fun analyze(audio: RecordedAudio): VoiceFeatures = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val duration = audio.sampleCount * 1_000 / SAMPLE_RATE
        fun invalid(reason: String) = VoiceFeatures(
            totalDurationMs = duration.coerceIn(0L, MAX_DURATION_MS),
            activeDurationMs = 0, averageEnergy = 0f, energyVariation = 0f,
            activitySegmentCount = 0, pauseDurationsMs = emptyList(),
            valid = false, invalidReason = reason,
        )
        if (audio.sampleRate != SAMPLE_RATE || audio.channels != 1 || audio.format != "PCM_S16LE" ||
            audio.sampleCount !in 1L..MAX_SAMPLES || audio.durationMs !in 1L..MAX_DURATION_MS ||
            abs(audio.durationMs - duration) > 1L) {
            return@withContext invalid("录音格式或时长信息不完整，请重新录音")
        }
        if (duration < MIN_DURATION_MS) {
            return@withContext invalid("录音时间太短啦，请至少录制0.8秒后再试")
        }
        val file = File(audio.filePath)
        try {
            if (!file.isFile || !file.canRead() || file.length() != audio.sampleCount * 2) {
                return@withContext invalid("录音文件不完整，请重新录制")
            }
            val windows = ArrayList<Window>()
            val buffer = ByteArray(WINDOW_SAMPLES * 2)
            var totalSquares = 0.0
            var offset = 0L
            file.inputStream().buffered().use { input ->
                while (offset < audio.sampleCount) {
                    currentCoroutineContext().ensureActive()
                    val sampleCount = min(WINDOW_SAMPLES.toLong(), audio.sampleCount - offset).toInt()
                    val bytesNeeded = sampleCount * 2
                    var bytesRead = 0
                    while (bytesRead < bytesNeeded) {
                        val count = input.read(buffer, bytesRead, bytesNeeded - bytesRead)
                        if (count <= 0) throw EOFException("Truncated PCM")
                        bytesRead += count
                    }
                    var squares = 0.0
                    for (index in 0 until sampleCount) {
                        val signed = ((buffer[index * 2].toInt() and 0xff) or
                            (buffer[index * 2 + 1].toInt() shl 8)).toShort().toInt()
                        val normalised = signed / 32768.0
                        squares += normalised * normalised
                    }
                    totalSquares += squares
                    windows += Window(offset * 1_000 / SAMPLE_RATE,
                        (offset + sampleCount) * 1_000 / SAMPLE_RATE, sqrt(squares / sampleCount))
                    offset += sampleCount
                }
                if (input.read() != -1 || file.length() != audio.sampleCount * 2) {
                    throw IOException("PCM changed during analysis")
                }
            }
            currentCoroutineContext().ensureActive()
            val sortedEnergy = windows.map { it.rms }.sorted()
            val lowEnergyReference = sortedEnergy[((sortedEnergy.size - 1) * .2).toInt()]
            val strongestWindow = sortedEnergy.last()
            // A lower-percentile window is only a noise reference, not a measured noise floor.
            // Cap the relative threshold so a sustained, clearly audible utterance can remain active.
            val threshold = max(ACTIVITY_FLOOR, min(lowEnergyReference * 2.8, strongestWindow * .25))
            val marked = windows.map { it.rms >= threshold }
            val spans = ArrayList<Span>()
            for (index in windows.indices) {
                if (!marked[index]) continue
                val window = windows[index]
                val previous = spans.lastOrNull()
                if (previous != null && window.startMs - previous.endMs <= MERGE_PAUSE_MS) {
                    previous.endMs = window.endMs
                    previous.activeMs += window.endMs - window.startMs
                } else {
                    spans += Span(window.startMs, window.endMs, window.endMs - window.startMs)
                }
            }
            // Isolated clicks cannot make a result; retain only substantial activity groups.
            val retained = spans.filter { it.activeMs >= MIN_SEGMENT_ACTIVE_MS }
            val activeDuration = retained.sumOf { it.activeMs }
            val averageRms = windows.map { it.rms }.average()
            val variance = windows.sumOf { (it.rms - averageRms) * (it.rms - averageRms) } / windows.size
            val variation = (sqrt(variance) / max(averageRms, 1e-12)).coerceIn(0.0, 10.0)
            val contrast = strongestWindow / max(lowEnergyReference, .0001)
            // Very quiet, near-constant room tone does not provide enough evidence.
            // This still cannot distinguish loud environmental noise from a person's voice.
            val energyEvidence = strongestWindow >= MIN_EVIDENCE_RMS &&
                (strongestWindow >= STEADY_EVIDENCE_RMS || contrast >= 2.2)
            val valid = activeDuration >= MIN_ACTIVE_MS && retained.isNotEmpty() && energyEvidence
            currentCoroutineContext().ensureActive()
            VoiceFeatures(
                totalDurationMs = duration,
                activeDurationMs = activeDuration,
                averageEnergy = sqrt(totalSquares / audio.sampleCount).toFloat(),
                energyVariation = variation.toFloat(),
                activitySegmentCount = retained.size,
                pauseDurationsMs = retained.zipWithNext { first, second -> second.startMs - first.endMs },
                valid = valid,
                invalidReason = if (valid) null else "没有检测到足够的声音活动，请靠近麦克风再说一次",
            )
        } catch (_: IOException) {
            invalid("录音文件暂时无法读取，请重新录制")
        } catch (_: SecurityException) {
            invalid("录音文件暂时无法读取，请重新录制")
        }
    }

    private data class Window(val startMs: Long, val endMs: Long, val rms: Double)
    private data class Span(val startMs: Long, var endMs: Long, var activeMs: Long)

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val WINDOW_SAMPLES = SAMPLE_RATE / 20 // 50ms, matching the capture sample rate.
        const val MIN_DURATION_MS = 800L
        const val MAX_DURATION_MS = 10_000L
        const val MAX_SAMPLES = 160_000L
        const val MERGE_PAUSE_MS = 100L
        const val MIN_SEGMENT_ACTIVE_MS = 100L
        const val MIN_ACTIVE_MS = 150L
        const val ACTIVITY_FLOOR = .0006
        const val MIN_EVIDENCE_RMS = .0012
        const val STEADY_EVIDENCE_RMS = .006
    }
}
