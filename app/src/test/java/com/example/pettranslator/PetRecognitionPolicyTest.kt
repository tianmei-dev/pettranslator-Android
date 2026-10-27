package com.example.pettranslator

import com.example.pettranslator.domain.PetScene
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.domain.recognition.*
import org.junit.Assert.assertEquals
import org.junit.Test

class PetRecognitionPolicyTest {
    private val labels = List(521) { "class-$it" }.toMutableList().apply {
        this[69] = "Dog"; this[70] = "Bark"; this[76] = "Cat"; this[78] = "Meow"
    }

    private fun raw(cat: Float, dog: Float, other: Float = .05f, quality: AudioQuality = goodQuality()): YamnetRawResult {
        val means = FloatArray(521)
        means[76] = cat
        means[70] = dog
        means[0] = other
        return YamnetRawResult(means, listOf(means.copyOf()), labels, cat, dog, other,
            SoundEvent(76, "Cat", cat), quality)
    }

    private fun goodQuality() = AudioQuality(3000, -24.0, 0.0, .02, 5, true)

    @Test fun selectedPetCanSucceedOrMismatchOnlyWithClearEvidence() {
        assertEquals(RecognitionStatus.SUCCESS, PetRecognitionPolicy.decide(raw(.8f, .1f), PetType.Cat, PetScene.Play).status)
        assertEquals(RecognitionStatus.PET_MISMATCH, PetRecognitionPolicy.decide(raw(.1f, .8f), PetType.Cat, PetScene.Play).status)
        assertEquals(RecognitionStatus.PET_MISMATCH, PetRecognitionPolicy.decide(raw(.8f, .1f), PetType.Dog, PetScene.Play).status)
    }

    @Test fun nonPetAmbiguousQuietAndShortAudioAreUncertain() {
        assertEquals(RecognitionStatus.UNCERTAIN, PetRecognitionPolicy.decide(raw(.05f, .03f, .8f), PetType.Cat, PetScene.Unknown).status)
        assertEquals(RecognitionStatus.UNCERTAIN, PetRecognitionPolicy.decide(raw(.8f, .72f), PetType.Cat, PetScene.Unknown).status)
        assertEquals(RecognitionStatus.UNCERTAIN, PetRecognitionPolicy.decide(raw(.8f, .1f, quality = goodQuality().copy(rmsDbfs = -80.0)), PetType.Cat, PetScene.Unknown).status)
        assertEquals(RecognitionStatus.UNCERTAIN, PetRecognitionPolicy.decide(raw(.8f, .1f, quality = goodQuality().copy(durationMs = 600)), PetType.Cat, PetScene.Unknown).status)
        assertEquals(RecognitionStatus.UNCERTAIN, PetRecognitionPolicy.decide(raw(.8f, .1f, quality = goodQuality().copy(silentFrameFraction = .995)), PetType.Cat, PetScene.Unknown).status)
        assertEquals(RecognitionStatus.UNCERTAIN, PetRecognitionPolicy.decide(raw(.8f, .1f, quality = goodQuality().copy(clippedFraction = .5)), PetType.Cat, PetScene.Unknown).status)
    }

    @Test fun inconsistentWindowEvidenceIsUncertain() {
        val cat = FloatArray(521).apply { this[76] = .31f; this[70] = .02f; this[0] = .01f }
        val dog = FloatArray(521).apply { this[76] = .02f; this[70] = 1f; this[0] = .01f }
        val aggregate = FloatArray(521) { (cat[it] + cat[it] + dog[it] + dog[it]) / 4f }
        val mixed = YamnetRawResult(aggregate, listOf(cat, cat.copyOf(), dog, dog.copyOf()), labels,
            aggregate[76], aggregate[70], .02f, SoundEvent(70, "Bark", aggregate[70]), goodQuality())
        assertEquals(RecognitionStatus.UNCERTAIN, PetRecognitionPolicy.decide(mixed, PetType.Dog, PetScene.Play).status)
    }

    @Test fun decisionKeepsOrdinalWindowEvidenceFromRawYamnetScores() {
        val catWindow = FloatArray(521).apply { this[78] = .8f; this[70] = .03f }
        val secondCatWindow = FloatArray(521).apply { this[78] = .65f; this[70] = .02f }
        val mean = FloatArray(521) { (catWindow[it] + secondCatWindow[it]) / 2f }
        val raw = YamnetRawResult(mean, listOf(catWindow, secondCatWindow), labels,
            .8f, .03f, .01f, SoundEvent(78, "Meow", .8f), goodQuality())
        val result = PetRecognitionPolicy.decide(raw, PetType.Cat, PetScene.Unknown)
        assertEquals(RecognitionStatus.SUCCESS, result.status)
        assertEquals(listOf(0, 1), result.windowEvidence.map { it.windowIndex })
        assertEquals(listOf("Meow", "Meow"), result.windowEvidence.map { it.dominantPetEvent?.label })
    }
}
