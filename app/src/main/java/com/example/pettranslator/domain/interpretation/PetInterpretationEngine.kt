package com.example.pettranslator.domain.interpretation

import com.example.pettranslator.domain.PetScene
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.domain.recognition.PetRecognitionResult
import com.example.pettranslator.domain.recognition.PetWindowEvidence
import com.example.pettranslator.domain.recognition.RecognitionStatus
import java.security.MessageDigest

/** Pure local rules/templates. Only accepted A1.2.2 SUCCESS results can produce an interpretation. */
class PetInterpretationEngine(
    private val now: () -> Long = System::currentTimeMillis,
    private val templates: List<PetInterpretationTemplate> = PetInterpretationTemplateCatalog.entries,
) {
    companion object {
        const val RULE_VERSION = "pet-interpretation-v1.1"
        const val TEMPLATE_VERSION = "pet-copy-v1.0"
    }

    fun interpret(
        recognition: PetRecognitionResult?,
        audioFingerprint: String?,
        scene: PetScene? = recognition?.scene,
    ): PetInterpretationResult? {
        if (recognition == null || recognition.status != RecognitionStatus.SUCCESS ||
            !recognition.quality.usable || recognition.detectedPet != recognition.selectedPet ||
            audioFingerprint.isNullOrBlank() || !audioFingerprint.matches(Regex("[0-9a-fA-F]{64}"))) return null

        val features = summarize(recognition)
        val requestedScene = scene ?: PetScene.Unknown
        val selection = selectTemplate(recognition.selectedPet, requestedScene, audioFingerprint.lowercase())
        val effectiveScene = selection.scene
        val template = selection.template
        val theme = template.coreThemeId
        val tone = toneFor(theme)
        val normalizedFingerprint = audioFingerprint.lowercase()
        val interpretationId = sha256("$normalizedFingerprint|${recognition.selectedPet}|$effectiveScene|${recognition.modelVersion}|${recognition.ruleVersion}|$RULE_VERSION|${template.templateVersion}|${template.templateId}")
        val species = species(recognition.selectedPet)
        val sceneFact = "你选择的「${effectiveScene.title}」场景"
        val categoryFact = features.detectedModelLabels.takeIf { it.isNotEmpty() }
            ?.joinToString("、", prefix = "（YAMNet类别：", postfix = "）").orEmpty()
        val evidence = "录音中检测到了${species}相关声音$categoryFact。"
        val windowsFact = if (features.windowCount > 0)
            "在 ${features.windowCount} 个重叠分析窗口中，有 ${features.windowsWithPetEvidence} 个窗口出现了相关类别证据" else "本次没有可用的逐窗口类别明细"
        return PetInterpretationResult(
            interpretationId = interpretationId,
            recordingId = normalizedFingerprint,
            audioFingerprint = normalizedFingerprint,
            petType = recognition.selectedPet,
            scene = effectiveScene,
            recognitionStatus = recognition.status,
            soundFeatures = features,
            coreThemeId = theme,
            emotionTone = tone,
            title = "${species}的小心声",
            innerMonologue = template.innerMonologue,
            explanation = "$evidence$sceneFact，我们生成了一个关于「${themeTitle(theme)}」的小小趣味猜想。这是场景化趣味表达，不代表 AI 识别了宠物真实心理。",
            interactionSuggestion = suggestion(effectiveScene),
            evidenceSummary = "$evidence$windowsFact。YAMNet 分类分数不是概率，也不表示情绪强弱。",
            modelVersion = recognition.modelVersion,
            recognitionRuleVersion = recognition.ruleVersion,
            interpretationRuleVersion = RULE_VERSION,
            templateVersion = template.templateVersion,
            templateId = template.templateId,
            createdAtEpochMs = now(),
            themeBasis = ThemeBasis.ScenePlayfulAssociation,
            templateFallbackUsed = selection.fallbackUsed,
        )
    }

    private fun summarize(result: PetRecognitionResult): SoundFeatures {
        val labels = result.soundEvents.asSequence()
            .filter { isPetLabel(it.labelIndex, result.selectedPet) }
            .distinctBy { it.labelIndex }
            .map { it.label }
            .toList()
        val category = result.soundEvents.asSequence()
            .filter { isPetLabel(it.labelIndex, result.selectedPet) }
            .maxByOrNull { it.score }
            ?.let { categoryFor(it.labelIndex, result.selectedPet) }
            ?: broadCategory(result.selectedPet)
        val evidenceIndices = result.windowEvidence.asSequence()
            .filter { window -> isPetEvidence(window, result.selectedPet) }
            .map { it.windowIndex }
            .toList()
        val run = longestAdjacentRun(evidenceIndices)
        val count = evidenceIndices.size
        val windowCount = result.windowEvidence.size
        val activity = if (windowCount == 0) VocalActivity.NoWindowBreakdown else when {
            count <= 1 -> VocalActivity.SparseWindowEvidence
            count.toFloat() / windowCount >= .67f -> VocalActivity.FrequentWindowEvidence
            else -> VocalActivity.RepeatedWindowEvidence
        }
        val tier = if (category == broadCategory(result.selectedPet)) TemplateEvidenceTier.BroadClassOnly
            else TemplateEvidenceTier.ModelLabeledSubtype
        return SoundFeatures(
            petType = result.selectedPet,
            soundCategory = category,
            detectedModelLabels = labels,
            windowCount = windowCount,
            windowsWithPetEvidence = count,
            evidenceWindowIndices = evidenceIndices,
            longestAdjacentEvidenceRun = run,
            vocalActivity = activity,
            evidenceStrength = EvidenceStrength.RecognitionAccepted,
            templateEvidenceTier = tier,
            catScore = result.catScore,
            dogScore = result.dogScore,
            otherScore = result.otherScore,
            durationMs = result.quality.durationMs,
            rmsDbfs = result.quality.rmsDbfs,
            validWindowCount = result.quality.validWindowCount,
            silentFrameFraction = result.quality.silentFrameFraction,
            clippedFraction = result.quality.clippedFraction,
            audioQualityUsable = result.quality.usable,
        )
    }

    private fun isPetEvidence(window: PetWindowEvidence, pet: PetType): Boolean = when (pet) {
        PetType.Cat -> window.dominantPetEvent?.labelIndex in 76..80
        PetType.Dog -> window.dominantPetEvent?.labelIndex in 69..75
    }

    private fun isPetLabel(index: Int, pet: PetType) = when (pet) {
        PetType.Cat -> index in 76..80
        PetType.Dog -> index in 69..75
    }

    private fun categoryFor(index: Int, pet: PetType): SoundCategory = when (index) {
        70 -> SoundCategory.DogBark
        71 -> SoundCategory.DogYip
        72 -> SoundCategory.DogHowl
        73 -> SoundCategory.DogBowWow
        74 -> SoundCategory.DogGrowl
        75 -> SoundCategory.DogWhimper
        77 -> SoundCategory.CatPurr
        78 -> SoundCategory.CatMeow
        79 -> SoundCategory.CatHiss
        80 -> SoundCategory.CatCaterwaul
        else -> broadCategory(pet)
    }

    private fun species(pet: PetType) = if (pet == PetType.Cat) "猫咪" else "狗狗"

    private fun broadCategory(pet: PetType) = if (pet == PetType.Cat) SoundCategory.CatBroadCall else SoundCategory.DogBroadCall

    private fun longestAdjacentRun(indices: List<Int>): Int {
        if (indices.isEmpty()) return 0
        var longest = 1
        var current = 1
        for (i in 1 until indices.size) {
            if (indices[i] == indices[i - 1] + 1) current++ else current = 1
            longest = maxOf(longest, current)
        }
        return longest
    }

    private fun themeFor(pet: PetType, scene: PetScene): CoreTheme = when (pet) {
        PetType.Cat -> when (scene) {
            PetScene.Door -> CoreTheme.CatDoorWindow
            PetScene.Meal -> CoreTheme.CatFood
            PetScene.Play -> CoreTheme.CatPlay
            PetScene.Alone -> CoreTheme.CatAlone
            PetScene.Held -> CoreTheme.CatHeld
            PetScene.Unknown -> CoreTheme.CatUnknown
        }
        PetType.Dog -> when (scene) {
            PetScene.Door -> CoreTheme.DogDoorWindow
            PetScene.Meal -> CoreTheme.DogFood
            PetScene.Play -> CoreTheme.DogPlay
            PetScene.Alone -> CoreTheme.DogAlone
            PetScene.Held -> CoreTheme.DogHeld
            PetScene.Unknown -> CoreTheme.DogUnknown
        }
    }

    private fun toneFor(theme: CoreTheme) = when (theme) {
        CoreTheme.Curiosity, CoreTheme.CatDoorWindow, CoreTheme.DogDoorWindow -> EmotionTone.Curious
        CoreTheme.DailyRhythm, CoreTheme.CatFood, CoreTheme.DogFood,
        CoreTheme.Attention, CoreTheme.CatAlone, CoreTheme.DogAlone -> EmotionTone.Gentle
        CoreTheme.Interaction, CoreTheme.CatPlay, CoreTheme.DogPlay -> EmotionTone.Bright
        CoreTheme.Contact, CoreTheme.CatHeld, CoreTheme.DogHeld,
        CoreTheme.EverydayCommunication, CoreTheme.CatUnknown, CoreTheme.DogUnknown -> EmotionTone.Everyday
    }

    private fun themeTitle(theme: CoreTheme) = when (theme) {
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
        CoreTheme.Curiosity -> "好奇地留意周围"
        CoreTheme.DailyRhythm -> "关注熟悉的日常节奏"
        CoreTheme.Interaction -> "互动与陪伴"
        CoreTheme.Attention -> "回应与引起注意"
        CoreTheme.Contact -> "回应当前互动"
        CoreTheme.EverydayCommunication -> "日常交流"
    }

    private data class TemplateSelection(
        val template: PetInterpretationTemplate,
        val scene: PetScene,
        val fallbackUsed: Boolean,
    )

    private fun selectTemplate(pet: PetType, scene: PetScene, fingerprint: String): TemplateSelection {
        val theme = themeFor(pet, scene)
        val exact = templates.filter {
            it.petType == pet && it.scene == scene && it.coreThemeId == theme &&
                it.templateVersion == TEMPLATE_VERSION && it.innerMonologue.isNotBlank()
        }
        val (candidates, fallbackUsed) = if (exact.isNotEmpty()) exact to false else {
            val fallbackTheme = themeFor(pet, PetScene.Unknown)
            templates.filter {
                it.petType == pet && it.scene == PetScene.Unknown && it.coreThemeId == fallbackTheme &&
                    it.templateVersion == TEMPLATE_VERSION && it.innerMonologue.isNotBlank()
            } to true
        }
        check(candidates.isNotEmpty()) { "No approved ${pet.name} interpretation templates available" }
        val selectedScene = if (fallbackUsed) PetScene.Unknown else scene
        val stableInput = "$fingerprint|${pet.name}|$selectedScene|$RULE_VERSION|$TEMPLATE_VERSION"
        val index = (sha256(stableInput).take(8).toLong(16) % candidates.size).toInt()
        return TemplateSelection(candidates[index], selectedScene, fallbackUsed)
    }
    private fun suggestion(scene: PetScene) = when (scene) {
        PetScene.Door -> "看看附近是否有引起它注意的声音，再观察它的动作。"
        PetScene.Meal -> "结合它平时的进食习惯观察，不要只凭这段声音改变喂食安排。"
        PetScene.Play -> "拿出它熟悉的玩具，看看它是否愿意一起互动。"
        PetScene.Alone -> "温柔回应它，观察它是否愿意靠近或继续交流。"
        PetScene.Held -> "留意它的身体姿态和反应；如果它想离开，就轻轻放下。"
        PetScene.Unknown -> "结合它的动作、表情和周围环境继续观察。"
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
