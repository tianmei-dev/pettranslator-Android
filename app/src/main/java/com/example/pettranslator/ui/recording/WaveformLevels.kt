package com.example.pettranslator.ui.recording

import kotlin.math.log10
import kotlin.math.pow

/** Display-only dBFS mapping. Never normalize each clip or modify captured PCM. */
internal fun rmsToWaveLevel(rms: Float): Float {
    if (!rms.isFinite() || rms <= 0f) return 0f
    val db = 20f * log10(rms.coerceAtMost(1f))
    if (db <= -48f) return 0f
    return ((db + 48f) / 38f).coerceIn(0f, 1f).pow(.85f)
}

/** Display envelope from real 50 ms PCM frames; peak transients add shape without altering audio. */
internal fun pcmFrameToWaveLevel(rms: Float, peak: Float): Float {
    val rmsLevel = rmsToWaveLevel(rms)
    val peakLevel = rmsToWaveLevel(peak)
    return (rmsLevel * .65f + peakLevel * .35f).coerceIn(0f, 1f)
}
