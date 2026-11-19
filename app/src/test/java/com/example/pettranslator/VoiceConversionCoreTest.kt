package com.example.pettranslator

import com.example.pettranslator.audio.PetClipType
import com.example.pettranslator.audio.PetSoundCatalog
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.domain.recording.RecordedAudio
import com.example.pettranslator.ui.voice.conversion.PcmVoiceFeatureAnalyzer
import com.example.pettranslator.ui.voice.conversion.VoiceFeatures
import com.example.pettranslator.ui.voice.conversion.VoicePlaybackSequence
import com.example.pettranslator.ui.voice.conversion.VoiceSequenceItem
import com.example.pettranslator.ui.voice.conversion.VoiceSoundSelector
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VoiceConversionCoreTest {
    @get:Rule val files = TemporaryFolder()
    private val analyzer = PcmVoiceFeatureAnalyzer()
    private val selector = VoiceSoundSelector()
    private val assets = PetSoundCatalog.assets
    private var clipNumber = 0

    @Test fun readsPcmInsteadOfTrustingCaptureEnergyMetadata() = runTest {
        val audio = clip(1_000, listOf(Burst(150, 850, .1)))
        val result = analyzer.analyze(audio)
        assertTrue(result.valid)
        assertEquals(1_000L, result.totalDurationMs)
        assertEquals(700L, result.activeDurationMs)
        assertEquals(1, result.activitySegmentCount)
        assertTrue(result.pauseDurationsMs.isEmpty())
        assertTrue(result.averageEnergy in .05f.. .08f)
        assertTrue(result.energyVariation > .4f)
        assertTrue(File(audio.filePath).exists()) // Reader does not own file deletion.
    }

    @Test fun findsPausesAtDifferentMicrophoneLevels() = runTest {
        val timing = listOf(Burst(100, 400, .006), Burst(750, 1_050, .006), Burst(1_400, 1_800, .006))
        val quiet = analyzer.analyze(clip(2_100, timing, .0001))
        val loud = analyzer.analyze(clip(2_100, timing.map { it.copy(amplitude = .2) }, .0001))
        for (result in listOf(quiet, loud)) {
            assertTrue(result.valid)
            assertEquals(1_000L, result.activeDurationMs)
            assertEquals(3, result.activitySegmentCount)
            assertEquals(listOf(350L, 350L), result.pauseDurationsMs)
        }
        assertTrue(loud.averageEnergy > quiet.averageEnergy * 10)
    }

    @Test fun silenceLowSteadyRoomToneAndSingleClickDoNotGenerateEvidence() = runTest {
        val silence = clip(1_000, emptyList()).copy(rms = .5f, peak = 1f)
        val roomTone = clip(1_000, emptyList(), .004)
        val click = clip(1_000, listOf(Burst(300, 350, .4)))
        for (audio in listOf(silence, roomTone, click)) {
            val result = analyzer.analyze(audio)
            assertFalse(result.valid)
            assertTrue(!result.invalidReason.isNullOrBlank())
        }
    }

    @Test fun rejectsShortTruncatedUnsupportedAndInconsistentRecordings() = runTest {
        val short = clip(600, listOf(Burst(0, 600, .2)))
        val valid = clip(1_000, listOf(Burst(100, 900, .2)))
        val truncated = clip(1_000, listOf(Burst(100, 900, .2)))
        File(truncated.filePath).appendBytes(byteArrayOf(1))
        val missing = valid.copy(filePath = File(files.root, "absent.pcm").absolutePath)
        for (audio in listOf(short, truncated, missing,
            valid.copy(sampleRate = 8_000), valid.copy(channels = 2),
            valid.copy(format = "WAV"), valid.copy(durationMs = 2_000))) {
            assertFalse(analyzer.analyze(audio).valid)
        }
    }

    @Test fun cancelledAnalysisCannotReturnAValidResult() = runTest {
        val audio = clip(1_000, listOf(Burst(100, 900, .2)))
        val cancelled = Job().apply { cancel() }
        val error = runCatching {
            withContext(cancelled) { analyzer.analyze(audio) }
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertTrue(File(audio.filePath).exists())
    }

    @Test fun shortCatChoiceUsesActivityAndEnergyVariation() {
        val ordinary = selector.select(features(), PetType.Cat, assets)
        val varied = selector.select(features(variation = 1.2f), PetType.Cat, assets)
        assertEquals(listOf("cat_single_01"), ordinary.audioIds)
        assertEquals(listOf("cat_single_04"), varied.audioIds)
        assertEquals(listOf("cat_single_04"),
            selector.select(features(active = 1_000), PetType.Cat, assets).audioIds)
    }

    @Test fun longOrRhythmicCatUsesOneCompleteContinuousSound() {
        val long = features(total = 6_000, active = 3_000)
        val rhythmic = features(total = 2_500, active = 900, segments = 3, pauses = listOf(300, 300))
        val first = selector.select(long, PetType.Cat, assets)
        assertEquals(listOf("cat_continuous_02"), first.audioIds)
        assertEquals(6_008L, first.estimatedDurationMs)
        for (input in listOf(long, rhythmic)) {
            val result = selector.select(input, PetType.Cat, assets, first.audioIds)
            assertNotEquals(first.audioIds, result.audioIds)
            assertEquals(PetClipType.CONTINUOUS, result.items.single().asset.clipType)
        }
    }

    @Test fun catRotationStaysWithinTheShortClassAndDoesNotChangeExistingResult() {
        val original = selector.select(features(), PetType.Cat, assets)
        val next = selector.select(features(), PetType.Cat, assets, original.audioIds)
        assertEquals(listOf("cat_single_01"), original.audioIds)
        assertEquals(listOf("cat_single_02"), next.audioIds)
        assertNotEquals(original.audioIds, next.audioIds)
        assertEquals(original, selector.select(features(), PetType.Cat, assets.reversed()))
    }

    @Test fun shortDogFallsBackToOneShortContinuousSoundWhenSinglesAreAbsent() {
        val accent = selector.select(features(total = 1_000, active = 250), PetType.Dog, assets)
        assertEquals(listOf("dog_continuous_03"), accent.audioIds)
        assertEquals(1_210L, accent.estimatedDurationMs)
        assertEquals(PetClipType.CONTINUOUS, accent.items.single().asset.clipType)
        assertEquals(listOf("dog_continuous_01"),
            selector.select(features(total = 2_000, active = 900), PetType.Dog, assets).audioIds)
    }

    @Test fun longDogMatchesDurationAndRotatesWithoutCombiningSounds() {
        val long = features(total = 6_000, active = 3_000, variation = 1.1f)
        val first = selector.select(long, PetType.Dog, assets)
        val next = selector.select(long, PetType.Dog, assets, first.audioIds)
        assertEquals(listOf("dog_continuous_08"), first.audioIds)
        assertEquals(listOf("dog_continuous_07"), next.audioIds)
        assertTrue(listOf(first, next).all { result ->
            result.items.single().asset.let { it.petType == PetType.Dog && it.clipType == PetClipType.CONTINUOUS }
        })
    }

    @Test fun excludesEveryUnapprovedSourceAndNeverCrossesPetBoundaries() {
        val excluded = listOf(
            PetSoundCatalog.byId.getValue("cat_single_01").copy(audioId = "cat_single_99"),
            PetSoundCatalog.byId.getValue("dog_continuous_03").copy(audioId = "dog_continuous_99"),
            PetSoundCatalog.byId.getValue("cat_single_02").copy(available = false),
        )
        assertThrows(IllegalStateException::class.java) { selector.select(features(), PetType.Cat, excluded) }
        assertThrows(IllegalStateException::class.java) { selector.select(features(), PetType.Dog, excluded) }
        assertThrows(IllegalStateException::class.java) {
            selector.select(features(), PetType.Cat, assets.filter { it.petType == PetType.Dog })
        }
        for (pet in PetType.entries) {
            val result = selector.select(features(), pet, assets + excluded)
            assertTrue(result.items.all { it.asset.petType == pet })
            assertTrue(result.items.all { PetSoundCatalog.byId[it.asset.audioId] == it.asset })
        }
    }

    @Test fun invalidEvidenceMissingSourcesAndWrongAssetPathsDoNotMakeAResult() {
        assertThrows(IllegalArgumentException::class.java) {
            selector.select(features().copy(valid = false), PetType.Cat, assets)
        }
        assertThrows(IllegalStateException::class.java) { selector.select(features(), PetType.Cat, emptyList()) }
        assertThrows(IllegalStateException::class.java) {
            selector.select(features(), PetType.Cat,
                assets.filter { it.petType == PetType.Cat }.map { it.copy(assetPath = "unapproved/${it.audioId}.m4a") })
        }
    }

    @Test fun disabledLongCatDoesNotBecomeAShortFallback() {
        val long = PetSoundCatalog.byId.getValue("cat_continuous_05")
        assertFalse(long.defaultForShortInput)
        assertThrows(IllegalStateException::class.java) {
            selector.select(features(), PetType.Cat, listOf(long))
        }
    }

    @Test fun sequenceEnforcesDistinctSamePetNonOverlappingSoundsAndReportsFixedDuration() {
        val first = PetSoundCatalog.byId.getValue("cat_single_01")
        val second = PetSoundCatalog.byId.getValue("cat_single_02")
        val parent = PetSoundCatalog.byId.getValue("cat_continuous_02")
        val dog = PetSoundCatalog.byId.getValue("dog_continuous_03")
        val sequence = VoicePlaybackSequence(PetType.Cat,
            listOf(VoiceSequenceItem(first, 150), VoiceSequenceItem(second)))
        assertEquals(listOf("cat_single_01", "cat_single_02"), sequence.audioIds)
        assertEquals(1_850L, sequence.estimatedDurationMs)
        assertThrows(IllegalArgumentException::class.java) {
            VoicePlaybackSequence(PetType.Cat, listOf(VoiceSequenceItem(first), VoiceSequenceItem(first)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            VoicePlaybackSequence(PetType.Cat, listOf(VoiceSequenceItem(dog)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            VoicePlaybackSequence(PetType.Cat, listOf(first, second, parent).map { VoiceSequenceItem(it) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            VoicePlaybackSequence(PetType.Cat, listOf(VoiceSequenceItem(parent), VoiceSequenceItem(first)))
        }
    }

    private fun features(
        total: Long = 1_500,
        active: Long = 700,
        segments: Int = 1,
        variation: Float = .25f,
        pauses: List<Long> = emptyList(),
    ) = VoiceFeatures(total, active, .04f, variation, segments, pauses, valid = true)

    private data class Burst(val startMs: Int, val endMs: Int, val amplitude: Double)

    /** Deterministic offline PCM fixtures; these are never installed as pet sound resources. */
    private fun clip(durationMs: Int, bursts: List<Burst>, backgroundAmplitude: Double = 0.0): RecordedAudio {
        val rate = 16_000
        val count = durationMs * rate / 1_000
        val bytes = ByteArray(count * 2)
        repeat(count) { index ->
            val timeMs = index * 1_000 / rate
            val amplitude = bursts.firstOrNull { timeMs in it.startMs until it.endMs }?.amplitude ?: 0.0
            val value = ((sin(2 * PI * 440 * index / rate) * amplitude +
                sin(2 * PI * 100 * index / rate) * backgroundAmplitude) * 32767).toInt().toShort().toInt()
            bytes[index * 2] = (value and 0xff).toByte()
            bytes[index * 2 + 1] = (value shr 8).toByte()
        }
        val file = files.newFile("voice_${clipNumber++}.pcm").apply { writeBytes(bytes) }
        return RecordedAudio(file.absolutePath, rate, count.toLong(), durationMs.toLong(), rms = 0f, peak = 0f)
    }
}
