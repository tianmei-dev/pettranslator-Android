package com.example.pettranslator.domain.interpretation

import com.example.pettranslator.domain.PetScene
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.domain.recognition.RecognitionStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/** Versioned, deterministic value codec for persisting/restoring a result without a new runtime dependency. */
object PetInterpretationCodec {
    private const val MAGIC = 0x50544952 // PTIR
    private const val VERSION = 3

    fun encode(value: PetInterpretationResult): String {
        val bytes = ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { out ->
            out.writeInt(MAGIC); out.writeInt(VERSION)
            out.writeUTF(value.interpretationId); out.writeUTF(value.recordingId); out.writeUTF(value.audioFingerprint)
            out.writeUTF(value.petType.name); out.writeUTF(value.scene.name); out.writeUTF(value.recognitionStatus.name)
            val sound = value.soundFeatures
            out.writeUTF(sound.petType.name); out.writeUTF(sound.soundCategory.name)
            out.writeInt(sound.detectedModelLabels.size); sound.detectedModelLabels.forEach(out::writeUTF)
            out.writeInt(sound.windowCount); out.writeInt(sound.windowsWithPetEvidence)
            out.writeInt(sound.evidenceWindowIndices.size); sound.evidenceWindowIndices.forEach(out::writeInt)
            out.writeInt(sound.longestAdjacentEvidenceRun); out.writeUTF(sound.vocalActivity.name)
            out.writeUTF(sound.evidenceStrength.name); out.writeUTF(sound.templateEvidenceTier.name)
            out.writeFloat(sound.catScore); out.writeFloat(sound.dogScore)
            out.writeFloat(sound.otherScore); out.writeLong(sound.durationMs); out.writeDouble(sound.rmsDbfs)
            out.writeInt(sound.validWindowCount)
            out.writeNullableDouble(sound.silentFrameFraction); out.writeNullableDouble(sound.clippedFraction)
            out.writeBoolean(sound.audioQualityUsable)
            out.writeUTF(value.coreThemeId.name); out.writeUTF(value.emotionTone.name)
            out.writeUTF(value.title); out.writeUTF(value.innerMonologue); out.writeUTF(value.explanation)
            out.writeUTF(value.interactionSuggestion); out.writeUTF(value.evidenceSummary)
            out.writeUTF(value.modelVersion); out.writeUTF(value.recognitionRuleVersion)
            out.writeUTF(value.interpretationRuleVersion); out.writeUTF(value.templateVersion)
            out.writeUTF(value.templateId); out.writeLong(value.createdAtEpochMs)
            out.writeUTF(value.themeBasis.name); out.writeBoolean(value.templateFallbackUsed)
        } }.toByteArray()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun decode(encoded: String): PetInterpretationResult {
        val bytes = Base64.getUrlDecoder().decode(encoded)
        require(bytes.size <= 256 * 1024) { "Interpretation payload is too large" }
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC) { "Invalid interpretation payload" }
            val version = input.readInt()
            require(version in 1..VERSION) { "Unsupported interpretation payload version" }
            val id = input.readUTF(); val recordingId = input.readUTF(); val fingerprint = input.readUTF()
            val pet = enumValue<PetType>(input.readUTF()); val scene = enumValue<PetScene>(input.readUTF())
            val status = enumValue<RecognitionStatus>(input.readUTF())
            val soundPet = enumValue<PetType>(input.readUTF()); val category = enumValue<SoundCategory>(input.readUTF())
            val labels = List(input.readCount(64)) { input.readUTF() }
            val windowCount = input.readInt(); val windowEvidence = input.readInt()
            val indices = List(input.readCount(1000)) { input.readInt() }
            val longest = input.readInt(); val activity = enumValue<VocalActivity>(input.readUTF())
            val strength = enumValue<EvidenceStrength>(input.readUTF())
            val tier = enumValue<TemplateEvidenceTier>(input.readUTF())
            val cat = input.readFloat(); val dog = input.readFloat(); val other = input.readFloat()
            val duration = input.readLong(); val rms = input.readDouble(); val validWindows = input.readInt()
            val silentFrameFraction = if (version >= 3) input.readNullableDouble() else null
            val clippedFraction = if (version >= 3) input.readNullableDouble() else null
            val qualityUsable = if (version >= 3) input.readBoolean() else true
            val features = SoundFeatures(soundPet, category, labels, windowCount, windowEvidence, indices, longest,
                activity, strength, tier, cat, dog, other, duration, rms, validWindows,
                silentFrameFraction, clippedFraction, qualityUsable)
            val theme = enumValue<CoreTheme>(input.readUTF()); val tone = enumValue<EmotionTone>(input.readUTF())
            val title = input.readUTF(); val monologue = input.readUTF(); val explanation = input.readUTF()
            val suggestion = input.readUTF(); val evidence = input.readUTF(); val modelVersion = input.readUTF()
            val recognitionRuleVersion = input.readUTF(); val interpretationRuleVersion = input.readUTF()
            val templateVersion = input.readUTF(); val templateId = input.readUTF(); val createdAt = input.readLong()
            val themeBasis = if (version >= 2) enumValue<ThemeBasis>(input.readUTF()) else ThemeBasis.ScenePlayfulAssociation
            val templateFallback = if (version >= 2) input.readBoolean() else false
            val result = PetInterpretationResult(id, recordingId, fingerprint, pet, scene, status, features, theme, tone,
                title, monologue, explanation, suggestion, evidence, modelVersion, recognitionRuleVersion,
                interpretationRuleVersion, templateVersion, templateId, createdAt, themeBasis, templateFallback)
            require(input.available() == 0) { "Trailing interpretation payload data" }
            return result
        }
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String): T = enumValueOf(value)
    private fun DataInputStream.readCount(max: Int): Int = readInt().also { require(it in 0..max) { "Invalid collection size" } }
    private fun DataOutputStream.writeNullableDouble(value: Double?) {
        if (value == null) writeBoolean(false) else {
            writeBoolean(true)
            writeDouble(value)
        }
    }
    private fun DataInputStream.readNullableDouble(): Double? = if (readBoolean()) readDouble() else null
}
