package com.example.pettranslator.ui.voice.conversion

import com.example.pettranslator.domain.PetType

/** Deterministic feature-driven selection; replay consumes the resulting sequence without reselecting. */
class VoiceSoundSelector {
    fun select(
        features: VoiceFeatures,
        pet: PetType,
        available: List<VoiceSoundAsset>,
        previousAudioIds: List<String> = emptyList(),
    ): VoicePlaybackSequence {
        require(features.valid && features.totalDurationMs >= 800 && features.activeDurationMs >= 150 &&
            features.activeDurationMs <= features.totalDurationMs && features.activitySegmentCount > 0 &&
            features.averageEnergy.isFinite() && features.averageEnergy >= 0f &&
            features.energyVariation.isFinite() && features.energyVariation >= 0f &&
            features.pauseDurationsMs.all { it >= 0 }) {
            features.invalidReason ?: "录音没有足够的声音活动，请重新录制"
        }
        val approved = ApprovedVoiceSounds.assets.filter { it.pet == pet }.associateBy { it.id }
        val usable = available.filter { candidate ->
            val reference = approved[candidate.id]
            candidate.pet == pet && reference != null && candidate.continuous == reference.continuous &&
                candidate.assetPath == reference.assetPath
        }.associateBy { it.id }
        val longerInput = features.activeDurationMs >= 1_800 ||
            (features.totalDurationMs >= 3_500 && features.activeDurationMs >= 650) ||
            features.activitySegmentCount >= 3 || features.pauseDurationsMs.count { it >= 200 } >= 2
        val variedInput = features.energyVariation >= .8f || features.activitySegmentCount >= 2
        val preferredIds = when {
            pet == PetType.Cat && longerInput -> listOf("cat_fs_728095")
            pet == PetType.Cat && (features.activeDurationMs >= 900 || variedInput) ->
                listOf("cat_fs_618900", "cat_fs_362953")
            pet == PetType.Cat -> listOf("cat_fs_362953", "cat_fs_618900")
            !longerInput && features.totalDurationMs <= 1_600 &&
                features.activeDurationMs <= 350 && features.activitySegmentCount == 1 ->
                listOf("dog_fs_813120")
            variedInput || features.activeDurationMs >= 2_500 ->
                listOf("dog_fs_807270", "dog_fs_9032")
            else -> listOf("dog_fs_9032", "dog_fs_807270")
        }
        // Rotation is limited to this feature-appropriate class; a long cat clip is never
        // replaced with short calls, and a tiny dog bark is never looped to fill time.
        val candidates = preferredIds.mapNotNull { usable[it] }
        check(candidates.isNotEmpty()) { "当前宠物的转换音源尚未就绪，请稍后再试" }
        val selected = candidates.firstOrNull { it.id !in previousAudioIds } ?: candidates.first()
        return VoicePlaybackSequence(pet, listOf(VoiceSequenceItem(selected)))
    }
}
