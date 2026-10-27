package com.example.pettranslator.domain.recognition

import com.example.pettranslator.domain.PetType
import kotlin.math.max

/** Initial experience values copied from the POC; YAMNet scores are not calibrated probabilities. */
data class RecognitionThresholds(
    val eventScore: Float = 0.30f,
    val evidenceMargin: Float = 0.10f,
    val silenceDbfs: Double = -60.0,
    val minimumDurationMs: Long = 975,
    val minimumWindowAgreement: Float = 0.60f,
    val maximumSilentFrameFraction: Double = 0.99,
    val maximumClippedFraction: Double = 0.10,
)

object PetRecognitionPolicy {
    const val RULE_VERSION = "pet-decision-v1-poc-thresholds"
    val thresholds = RecognitionThresholds()

    fun decide(raw: YamnetRawResult, selected: PetType, scene: com.example.pettranslator.domain.PetScene): PetRecognitionResult {
        val t = thresholds
        val quality = raw.quality.copy(
            usable = raw.quality.durationMs >= t.minimumDurationMs &&
                raw.quality.rmsDbfs >= t.silenceDbfs &&
                raw.quality.silentFrameFraction < t.maximumSilentFrameFraction &&
                raw.quality.clippedFraction <= t.maximumClippedFraction && raw.quality.validWindowCount > 0,
        )
        val best: PetType? = when {
            !quality.usable -> null
            raw.catScore >= t.eventScore && raw.catScore - max(raw.dogScore, raw.otherScore) >= t.evidenceMargin -> PetType.Cat
            raw.dogScore >= t.eventScore && raw.dogScore - max(raw.catScore, raw.otherScore) >= t.evidenceMargin -> PetType.Dog
            else -> null
        }
        val status = when {
            best == null -> RecognitionStatus.UNCERTAIN
            best == selected -> if (windowAgreement(raw, best) >= t.minimumWindowAgreement || raw.windowScores.size <= 1)
                RecognitionStatus.SUCCESS else RecognitionStatus.UNCERTAIN
            else -> if (windowAgreement(raw, best) >= t.minimumWindowAgreement || raw.windowScores.size <= 1)
                RecognitionStatus.PET_MISMATCH else RecognitionStatus.UNCERTAIN
        }
        val events = raw.meanScores.indices
            .filter { raw.meanScores[it] >= t.eventScore }
            .sortedByDescending { raw.meanScores[it] }
            .take(8)
            .map { SoundEvent(it, raw.labels.getOrElse(it) { "Unknown" }, raw.meanScores[it]) }
        val windows = raw.windowScores.mapIndexed { index, scores ->
            val catIndex = (76..80).maxBy { scores.getOrElse(it) { 0f } }
            val dogIndex = (69..75).maxBy { scores.getOrElse(it) { 0f } }
            val cat = scores.getOrElse(catIndex) { 0f }
            val dog = scores.getOrElse(dogIndex) { 0f }
            val eventIndex = (69..80).maxBy { scores.getOrElse(it) { 0f } }
            val eventScore = scores.getOrElse(eventIndex) { 0f }
            PetWindowEvidence(index, cat, dog,
                if (eventScore >= t.eventScore) SoundEvent(eventIndex, raw.labels.getOrElse(eventIndex) { "Unknown" }, eventScore) else null)
        }
        return PetRecognitionResult(
            selectedPet = selected, scene = scene,
            detectedPet = best.takeIf { status != RecognitionStatus.UNCERTAIN }, status = status,
            catScore = raw.catScore, dogScore = raw.dogScore, otherScore = raw.otherScore,
            primarySound = raw.primary, soundEvents = events, windowEvidence = windows, quality = quality,
            modelVersion = "google/yamnet/tflite/classification-tflite/1",
            ruleVersion = RULE_VERSION,
        )
    }

    private fun windowAgreement(raw: YamnetRawResult, candidate: PetType): Float {
        val t = thresholds
        val eligible = raw.windowScores.mapNotNull { scores ->
            val cat = (76..80).maxOf { scores[it] }
            val dog = (69..75).maxOf { scores[it] }
            when {
                max(cat, dog) < t.eventScore -> null
                cat - dog >= t.evidenceMargin -> PetType.Cat
                dog - cat >= t.evidenceMargin -> PetType.Dog
                else -> null
            }
        }
        if (eligible.isEmpty()) return if (raw.windowScores.size <= 1) 1f else 0f
        return eligible.count { it == candidate }.toFloat() / eligible.size
    }
}

