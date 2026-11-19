package com.example.pettranslator.ui.voice.conversion

import com.example.pettranslator.audio.PetSoundAsset
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.domain.recording.RecordedAudio

/** Energy/activity evidence only: these fields do not identify speech, words, or emotion. */
data class VoiceFeatures(
    val totalDurationMs: Long,
    val activeDurationMs: Long,
    /** Normalised RMS calculated from the PCM, rather than the recording metadata. */
    val averageEnergy: Float,
    /** Coefficient of variation of window RMS, including natural pauses. */
    val energyVariation: Float,
    val activitySegmentCount: Int,
    val pauseDurationsMs: List<Long>,
    val valid: Boolean,
    val invalidReason: String? = null,
)

fun interface VoiceFeatureAnalyzer {
    suspend fun analyze(audio: RecordedAudio): VoiceFeatures
}

data class VoiceSequenceItem(val asset: PetSoundAsset, val pauseAfterMs: Long = 0) {
    init { require(pauseAfterMs in 0L..500L) }
}

data class VoicePlaybackSequence(val pet: PetType, val items: List<VoiceSequenceItem>) {
    init {
        require(items.size in 1..2) { "A conversion uses one or two complete sounds" }
        require(items.all { it.asset.petType == pet }) { "A conversion cannot mix pets" }
        require(audioIds.distinct().size == items.size) {
            "A conversion cannot repeat the same sound"
        }
        require(!hasOverlappingSounds()) { "A conversion cannot combine overlapping source intervals" }
    }

    val audioIds: List<String> get() = items.map { it.asset.audioId }
    val estimatedDurationMs: Long get() = items.sumOf { it.asset.durationMs + it.pauseAfterMs }

    internal fun hasOverlappingSounds(): Boolean {
        if (items.size != 2) return false
        val first = items[0].asset
        val second = items[1].asset
        return second.audioId in first.overlaps || first.audioId in second.overlaps
    }
}
