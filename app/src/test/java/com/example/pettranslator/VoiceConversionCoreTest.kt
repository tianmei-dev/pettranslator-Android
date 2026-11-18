package com.example.pettranslator

import com.example.pettranslator.domain.PetType
import com.example.pettranslator.domain.recording.RecordedAudio
import com.example.pettranslator.ui.voice.conversion.ApprovedVoiceSounds
import com.example.pettranslator.ui.voice.conversion.PcmVoiceFeatureAnalyzer
import com.example.pettranslator.ui.voice.conversion.VoiceFeatures
import com.example.pettranslator.ui.voice.conversion.VoicePlaybackSequence
import com.example.pettranslator.ui.voice.conversion.VoiceSequenceItem
import com.example.pettranslator.ui.voice.conversion.VoiceSoundAsset
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
    private val assets = ApprovedVoiceSounds.assets
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
        assertEquals(listOf("cat_fs_362953"), ordinary.audioIds)
        assertEquals(listOf("cat_fs_618900"), varied.audioIds)
        assertEquals(listOf("cat_fs_618900"),
            selector.select(features(active = 1_000), PetType.Cat, assets).audioIds)
    }

    @Test fun longOrRhythmicCatUsesCompleteContinuousSoundDespitePreviousResult() {
        val long = features(total = 6_000, active = 3_000)
        val rhythmic = features(total = 2_500, active = 900, segments = 3, pauses = listOf(300, 300))
        for (input in listOf(long, rhythmic)) {
            val result = selector.select(input, PetType.Cat, assets, listOf("cat_fs_728095"))
            assertEquals(listOf("cat_fs_728095"), result.audioIds)
            assertEquals(4_282L, result.estimatedDurationMs)
            assertEquals(1, result.items.size)
        }
    }

    @Test fun catRotationStaysWithinTheShortClassAndDoesNotChangeExistingResult() {
        val original = selector.select(features(), PetType.Cat, assets)
        val next = selector.select(features(), PetType.Cat, assets, original.audioIds)
        assertEquals(listOf("cat_fs_362953"), original.audioIds)
        assertEquals(listOf("cat_fs_618900"), next.audioIds)
        assertNotEquals(original.audioIds, next.audioIds)
        assertEquals(original, selector.select(features(), PetType.Cat, assets.reversed()))
    }

    @Test fun dogTinyAccentIsOneSoundWhileAnOrdinarySentenceUsesACompleteSequence() {
        val accent = selector.select(features(total = 1_000, active = 250), PetType.Dog, assets)
        assertEquals(listOf("dog_fs_813120"), accent.audioIds)
        assertEquals(223L, accent.estimatedDurationMs)
        assertEquals(1, accent.items.size)
        assertEquals(listOf("dog_fs_9032"),
            selector.select(features(total = 2_000, active = 900), PetType.Dog, assets).audioIds)
    }

    @Test fun longDogFeatureRuleAndRotationUseOnlyTheTwoContinuousSounds() {
        val long = features(total = 6_000, active = 3_000, variation = 1.1f)
        val first = selector.select(long, PetType.Dog, assets)
        val next = selector.select(long, PetType.Dog, assets, first.audioIds)
        assertEquals(listOf("dog_fs_807270"), first.audioIds)
        assertEquals(listOf("dog_fs_9032"), next.audioIds)
        assertTrue(listOf(first, next).all { result ->
            result.items.size == 1 && result.items.all { it.asset.pet == PetType.Dog && it.asset.continuous }
        })
    }

    @Test fun excludesEveryUnapprovedSourceAndNeverCrossesPetBoundaries() {
        val excluded = listOf(
            VoiceSoundAsset("cat_fs_412017", PetType.Cat, 1_750, false),
            VoiceSoundAsset("cat_fs_668817", PetType.Cat, 665, false),
            VoiceSoundAsset("dog_fs_350593", PetType.Dog, 850, false),
            VoiceSoundAsset("dog_fs_351876", PetType.Dog, 3_568, true),
        )
        assertThrows(IllegalStateException::class.java) { selector.select(features(), PetType.Cat, excluded) }
        assertThrows(IllegalStateException::class.java) { selector.select(features(), PetType.Dog, excluded) }
        assertThrows(IllegalStateException::class.java) {
            selector.select(features(), PetType.Cat, listOf(assets.first().copy(pet = PetType.Dog)))
        }
        for (pet in PetType.entries) {
            val result = selector.select(features(), pet, assets + excluded)
            assertTrue(result.items.all { it.asset.pet == pet })
            assertTrue(result.audioIds.all { id -> assets.any { it.id == id } })
        }
    }

    @Test fun invalidEvidenceMissingSourcesAndWrongAssetPathsDoNotMakeAResult() {
        assertThrows(IllegalArgumentException::class.java) {
            selector.select(features().copy(valid = false), PetType.Cat, assets)
        }
        assertThrows(IllegalStateException::class.java) { selector.select(features(), PetType.Cat, emptyList()) }
        assertThrows(IllegalStateException::class.java) {
            selector.select(features(), PetType.Cat,
                assets.filter { it.pet == PetType.Cat }.map { it.copy(assetPath = "unapproved/${it.id}.m4a") })
        }
    }

    @Test fun sequenceEnforcesDistinctSamePetSoundsAndReportsFixedDuration() {
        val cat = assets.filter { it.pet == PetType.Cat }
        val sequence = VoicePlaybackSequence(PetType.Cat,
            listOf(VoiceSequenceItem(cat[0], 150), VoiceSequenceItem(cat[1])))
        assertEquals(listOf("cat_fs_362953", "cat_fs_618900"), sequence.audioIds)
        assertEquals(1_984L, sequence.estimatedDurationMs)
        assertThrows(IllegalArgumentException::class.java) {
            VoicePlaybackSequence(PetType.Cat, listOf(VoiceSequenceItem(cat[0]), VoiceSequenceItem(cat[0])))
        }
        assertThrows(IllegalArgumentException::class.java) {
            VoicePlaybackSequence(PetType.Cat, listOf(VoiceSequenceItem(assets.last())))
        }
        assertThrows(IllegalArgumentException::class.java) {
            VoicePlaybackSequence(PetType.Cat, cat.map { VoiceSequenceItem(it) })
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
