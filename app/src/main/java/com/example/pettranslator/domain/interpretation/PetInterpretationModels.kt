package com.example.pettranslator.domain.interpretation

import com.example.pettranslator.domain.PetScene
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.domain.recognition.RecognitionStatus

enum class SoundCategory(val title: String) {
    CatBroadCall("猫相关声音"), CatPurr("呼噜类别"), CatMeow("喵叫类别"), CatHiss("哈气类别"), CatCaterwaul("长声叫类别"),
    DogBroadCall("狗相关声音"), DogBark("吠叫类别"), DogYip("短促叫声类别"), DogHowl("嚎叫类别"), DogBowWow("汪汪叫类别"), DogGrowl("低吼类别"), DogWhimper("呜咽类别"),
}

/** A scene-led playful theme, never a model-classified emotion or intent. */
enum class CoreTheme {
    // Retained for source and payload compatibility with the A1.3 draft model.
    Curiosity, DailyRhythm, Interaction, Attention, Contact, EverydayCommunication,
    CatDoorWindow, CatFood, CatPlay, CatAlone, CatHeld, CatUnknown,
    DogDoorWindow, DogFood, DogPlay, DogAlone, DogHeld, DogUnknown,
}

val CoreTheme.displayTitle: String get() = when (this) {
    CoreTheme.Curiosity -> "好奇地留意周围"
    CoreTheme.DailyRhythm -> "关注熟悉的日常节奏"
    CoreTheme.Interaction -> "互动与陪伴"
    CoreTheme.Attention -> "回应与引起注意"
    CoreTheme.Contact -> "回应当前互动"
    CoreTheme.EverydayCommunication -> "日常交流"
    CoreTheme.CatDoorWindow -> "好奇・紧盯外面动静"
    CoreTheme.CatFood -> "期待・蹲饭饭信号"
    CoreTheme.CatPlay -> "互动・盼着一起玩"
    CoreTheme.CatAlone -> "软叫・讨要关注"
    CoreTheme.CatHeld -> "反应・感受抱抱"
    CoreTheme.CatUnknown -> "随口喵喵・吸引注意"
    CoreTheme.DogDoorWindow -> "好奇・捕捉外界动静"
    CoreTheme.DogFood -> "期待・等待干饭"
    CoreTheme.DogPlay -> "互动・疯玩邀约"
    CoreTheme.DogAlone -> "轻哼叫唤・想要陪伴"
    CoreTheme.DogHeld -> "反应・感受被抱"
    CoreTheme.DogUnknown -> "汪汪闲聊・吸引目光"
}

/** Copywriting tone only; it is not an inferred pet emotional state. */
enum class EmotionTone { Curious, Gentle, Bright, Everyday }
enum class VocalActivity { SparseWindowEvidence, RepeatedWindowEvidence, FrequentWindowEvidence, NoWindowBreakdown }
enum class EvidenceStrength { RecognitionAccepted }
enum class TemplateEvidenceTier { BroadClassOnly, ModelLabeledSubtype }

/** Facts derived from the existing YAMNet output; window indices are coarse overlapping analysis windows. */
data class SoundFeatures(
    val petType: PetType,
    val soundCategory: SoundCategory,
    val detectedModelLabels: List<String>,
    val windowCount: Int,
    val windowsWithPetEvidence: Int,
    val evidenceWindowIndices: List<Int>,
    val longestAdjacentEvidenceRun: Int,
    val vocalActivity: VocalActivity,
    val evidenceStrength: EvidenceStrength,
    val templateEvidenceTier: TemplateEvidenceTier,
    val catScore: Float,
    val dogScore: Float,
    val otherScore: Float,
    val durationMs: Long,
    val rmsDbfs: Double,
    val validWindowCount: Int,
    val silentFrameFraction: Double? = null,
    val clippedFraction: Double? = null,
    val audioQualityUsable: Boolean = true,
)

/** Persistable, structured result. Facts, scene-based interpretation, and copy are kept separate. */
data class PetInterpretationResult(
    val interpretationId: String,
    val recordingId: String,
    val audioFingerprint: String,
    val petType: PetType,
    val scene: PetScene,
    val recognitionStatus: RecognitionStatus,
    val soundFeatures: SoundFeatures,
    val coreThemeId: CoreTheme,
    val emotionTone: EmotionTone,
    val title: String,
    val innerMonologue: String,
    val explanation: String,
    val interactionSuggestion: String,
    val evidenceSummary: String,
    val modelVersion: String,
    val recognitionRuleVersion: String,
    val interpretationRuleVersion: String,
    val templateVersion: String,
    val templateId: String,
    val createdAtEpochMs: Long,
    val themeBasis: ThemeBasis = ThemeBasis.ScenePlayfulAssociation,
    val templateFallbackUsed: Boolean = false,
)

enum class ThemeBasis { ScenePlayfulAssociation }

data class PetInterpretationTemplate(
    val templateId: String,
    val petType: PetType,
    val scene: PetScene,
    val coreThemeId: CoreTheme,
    val innerMonologue: String,
    val templateVersion: String,
    val styleTag: String? = null,
)
