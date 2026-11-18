package com.example.pettranslator.ui.voice.conversion

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

data class VoiceSoundAsset(
    val id: String,
    val pet: PetType,
    val durationMs: Long,
    val continuous: Boolean,
    val assetPath: String = "voice_conversion/$id.m4a",
) {
    init {
        require(id.isNotBlank() && durationMs > 0)
    }
}

data class VoiceSequenceItem(val asset: VoiceSoundAsset, val pauseAfterMs: Long = 0) {
    init { require(pauseAfterMs in 0L..500L) }
}

data class VoicePlaybackSequence(val pet: PetType, val items: List<VoiceSequenceItem>) {
    init {
        require(items.size in 1..2) { "A conversion uses one or two complete sounds" }
        require(items.all { it.asset.pet == pet }) { "A conversion cannot mix pets" }
        require(items.map { it.asset.id }.distinct().size == items.size) {
            "A conversion cannot repeat the same sound"
        }
    }

    val audioIds: List<String> get() = items.map { it.asset.id }
    val estimatedDurationMs: Long get() = items.sumOf { it.asset.durationMs + it.pauseAfterMs }
}

/** Only the six files expressly auditioned and approved for this Debug development stage. */
object ApprovedVoiceSounds {
    val assets: List<VoiceSoundAsset> = listOf(
        VoiceSoundAsset("cat_fs_362953", PetType.Cat, 734, continuous = false),
        VoiceSoundAsset("cat_fs_618900", PetType.Cat, 1_100, continuous = false),
        VoiceSoundAsset("cat_fs_728095", PetType.Cat, 4_282, continuous = true),
        VoiceSoundAsset("dog_fs_813120", PetType.Dog, 223, continuous = false),
        VoiceSoundAsset("dog_fs_9032", PetType.Dog, 2_831, continuous = true),
        VoiceSoundAsset("dog_fs_807270", PetType.Dog, 3_137, continuous = true),
    )
}
