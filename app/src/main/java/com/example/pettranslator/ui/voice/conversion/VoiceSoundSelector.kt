package com.example.pettranslator.ui.voice.conversion

import com.example.pettranslator.audio.PetClipType
import com.example.pettranslator.audio.PetSoundAsset
import com.example.pettranslator.audio.PetSoundCatalog
import com.example.pettranslator.domain.PetType
import kotlin.math.abs

/** Deterministic feature-driven selection; replay consumes the resulting sequence without reselecting. */
class VoiceSoundSelector {
    fun select(
        features: VoiceFeatures,
        pet: PetType,
        available: List<PetSoundAsset>,
        previousAudioIds: List<String> = emptyList(),
    ): VoicePlaybackSequence {
        require(features.valid && features.totalDurationMs >= 800 && features.activeDurationMs >= 150 &&
            features.activeDurationMs <= features.totalDurationMs && features.activitySegmentCount > 0 &&
            features.averageEnergy.isFinite() && features.averageEnergy >= 0f &&
            features.energyVariation.isFinite() && features.energyVariation >= 0f &&
            features.pauseDurationsMs.all { it >= 0 }) {
            features.invalidReason ?: "录音没有足够的声音活动，请重新录制"
        }
        val usable = available.filter { candidate ->
            candidate.petType == pet && candidate.available && PetSoundCatalog.byId[candidate.audioId] == candidate
        }
        val longerInput = features.activeDurationMs >= 1_800 ||
            (features.totalDurationMs >= 3_500 && features.activeDurationMs >= 650) ||
            features.activitySegmentCount >= 3 || features.pauseDurationsMs.count { it >= 200 } >= 2
        val variedInput = features.energyVariation >= .8f || features.activitySegmentCount >= 2
        val suitable = if (longerInput) usable else usable.filter {
            it.defaultForShortInput && it.durationMs <= MAX_SHORT_SOUND_MS
        }
        val preferredType = if (longerInput) PetClipType.CONTINUOUS else PetClipType.SINGLE
        val preferred = suitable.filter { it.clipType == preferredType }
        val targetDuration = when {
            longerInput -> features.totalDurationMs.coerceAtLeast(3_800L)
            variedInput || features.activeDurationMs >= 900 -> features.activeDurationMs.coerceAtLeast(1_400L)
            else -> features.activeDurationMs.coerceAtLeast(800L)
        }
        // With no dog SINGLE assets, rank only the short, intact continuous sounds.
        val candidates = preferred.ifEmpty { suitable }.sortedWith(
            compareBy<PetSoundAsset> { abs(it.durationMs - targetDuration) }
                .thenBy { it.durationMs }
                .thenBy { it.audioId }
        )
        check(candidates.isNotEmpty()) { "当前宠物的转换音源尚未就绪，请稍后再试" }
        val selected = candidates.firstOrNull { it.audioId !in previousAudioIds } ?: candidates.first()
        return VoicePlaybackSequence(pet, listOf(VoiceSequenceItem(selected)))
    }

    private companion object {
        const val MAX_SHORT_SOUND_MS = 2_500L
    }
}
