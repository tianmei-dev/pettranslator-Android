package com.example.pettranslator.domain.recognition

import com.example.pettranslator.domain.PetScene
import com.example.pettranslator.domain.PetType

enum class RecognitionStatus { SUCCESS, UNCERTAIN, PET_MISMATCH }

data class SoundEvent(val labelIndex: Int, val label: String, val score: Float)

data class AudioQuality(
    val durationMs: Long,
    val rmsDbfs: Double,
    val clippedFraction: Double,
    val silentFrameFraction: Double,
    val validWindowCount: Int,
    val usable: Boolean,
)

/** Stable A1.3 input; model scores and product decision are kept separate. */
data class PetRecognitionResult(
    val selectedPet: PetType,
    val scene: PetScene,
    val detectedPet: PetType?,
    val status: RecognitionStatus,
    val catScore: Float,
    val dogScore: Float,
    val otherScore: Float,
    val primarySound: SoundEvent?,
    val soundEvents: List<SoundEvent>,
    val quality: AudioQuality,
    val modelVersion: String,
    val ruleVersion: String,
)

/** Raw, choice-independent YAMNet data, cached so switching a pet never reruns inference. */
data class YamnetRawResult(
    val meanScores: FloatArray,
    val windowScores: List<FloatArray>,
    val labels: List<String>,
    val catScore: Float,
    val dogScore: Float,
    val otherScore: Float,
    val primary: SoundEvent?,
    val quality: AudioQuality,
)

