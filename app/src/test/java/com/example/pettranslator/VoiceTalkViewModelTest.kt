package com.example.pettranslator

import androidx.lifecycle.ViewModelStore
import com.example.pettranslator.core.Feature
import com.example.pettranslator.core.FeatureAccessManager
import com.example.pettranslator.domain.PetPreferences
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.domain.recording.AudioCapture
import com.example.pettranslator.domain.recording.CaptureFrame
import com.example.pettranslator.domain.recording.RecordedAudio
import com.example.pettranslator.privacy.PrivacyGate
import com.example.pettranslator.ui.voice.VoiceTalkPhase
import com.example.pettranslator.ui.voice.VoiceTalkViewModel
import com.example.pettranslator.ui.voice.conversion.ApprovedVoiceSounds
import com.example.pettranslator.ui.voice.conversion.VoiceFeatureAnalyzer
import com.example.pettranslator.ui.voice.conversion.VoiceFeatures
import com.example.pettranslator.ui.voice.conversion.VoicePlaybackProgress
import com.example.pettranslator.ui.voice.conversion.VoicePlaybackSequence
import com.example.pettranslator.ui.voice.conversion.VoiceSequencePlayer
import com.example.pettranslator.ui.voice.conversion.VoiceSoundAsset
import com.example.pettranslator.ui.voice.conversion.VoiceSoundLibrary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceTalkViewModelTest {
    @Before fun blockPrivacyBeforeTest() = PrivacyGate.block()
    @After fun blockPrivacyAfterTest() = PrivacyGate.block()

    @Test fun loadedPetAndForegroundAreRequiredAndOnlyFramesStartRecording() = runTest {
        withVoice { fixture ->
            val model = fixture.model
            assertNull(model.selectedPet.value)
            model.foreground(true)
            model.start()
            runCurrent()
            assertEquals(PetType.Dog, model.selectedPet.value)
            assertEquals(0, fixture.capture.starts)

            model.foreground(false)
            model.start()
            runCurrent()
            assertEquals(0, fixture.capture.starts)

            model.foreground(true)
            model.start()
            runCurrent()
            assertEquals(1, fixture.capture.starts)
            assertEquals(VoiceTalkPhase.STARTING, model.uiState.value.phase)
            assertEquals(0L, model.uiState.value.elapsedMs)
            assertTrue(model.uiState.value.levels.isEmpty())

            fixture.capture.emit(CaptureFrame(2_000L, 125L, .35f, .8f))
            assertEquals(VoiceTalkPhase.RECORDING, model.uiState.value.phase)
            assertEquals(125L, model.uiState.value.elapsedMs)
            assertEquals(listOf(.35f), model.uiState.value.levels)
            fixture.capture.emit(CaptureFrame(4_800L, 300L, .62f, .9f))
            assertEquals(300L, model.uiState.value.elapsedMs)
            assertEquals(listOf(.35f, .62f), model.uiState.value.levels)
        }
    }

    @Test fun manualStopAnalyzesThenPublishesResultAndAutomaticallyPlays() = runTest {
        withVoice { fixture ->
            startRecording(fixture, elapsedMs = 1_000L)
            fixture.model.stop()
            fixture.model.stop()
            assertEquals(VoiceTalkPhase.STOPPING, fixture.model.uiState.value.phase)
            assertEquals(1, fixture.capture.stops)

            fixture.capture.result.complete(captureMetadata(1_000L))
            advanceUntilIdle()

            val state = fixture.model.uiState.value
            assertEquals(VoiceTalkPhase.RESULT, state.phase)
            assertEquals(1_000L, state.elapsedMs)
            assertNotNull(state.result)
            assertTrue(fixture.phases.contains(VoiceTalkPhase.CONVERTING))
            assertTrue(fixture.phases.contains(VoiceTalkPhase.RESULT))
            assertEquals(1, fixture.analyzer.calls)
            assertEquals(listOf(state.result), fixture.player.played)
            assertEquals(state.playbackDurationMs, state.playbackPositionMs)
            assertFalse(state.isPlaying)
            assertEquals(0, fixture.capture.active)
            assertEquals(1, fixture.capture.releases)
            assertEquals(1, fixture.capture.discards)
        }
    }

    @Test fun captureShorterThanEightHundredMillisIsAnError() = runTest {
        withVoice { fixture ->
            startRecording(fixture, elapsedMs = 799L)
            fixture.model.stop()
            fixture.capture.result.complete(captureMetadata(799L))
            runCurrent()

            assertEquals(VoiceTalkPhase.ERROR, fixture.model.uiState.value.phase)
            assertTrue(fixture.model.uiState.value.message.orEmpty().contains("太短"))
            assertFalse(fixture.phases.contains(VoiceTalkPhase.RECORDED))
            assertFalse(fixture.phases.contains(VoiceTalkPhase.CONVERTING))
            assertFalse(fixture.phases.contains(VoiceTalkPhase.RESULT))
            assertEquals(0, fixture.capture.active)
            assertEquals(1, fixture.capture.releases)
        }
    }

    @Test fun repeatedStartOwnsOnlyOneCaptureInstance() = runTest {
        withVoice { fixture ->
            runCurrent()
            fixture.model.foreground(true)
            repeat(4) { fixture.model.start() }
            runCurrent()
            assertEquals(1, fixture.capture.starts)
            assertEquals(1, fixture.capture.active)

            fixture.capture.emit(CaptureFrame(8_000L, 500L, .4f))
            fixture.model.start()
            runCurrent()
            assertEquals(1, fixture.capture.starts)
            assertEquals(VoiceTalkPhase.RECORDING, fixture.model.uiState.value.phase)
        }
    }

    @Test fun backgroundInterruptionReleasesCaptureWithoutAutomaticResume() = runTest {
        withVoice { fixture ->
            startRecording(fixture)

            fixture.model.foreground(false)
            runCurrent()

            assertEquals(VoiceTalkPhase.ERROR, fixture.model.uiState.value.phase)
            assertTrue(fixture.model.uiState.value.message.orEmpty().contains("中断"))
            assertEquals(0, fixture.capture.active)
            assertEquals(1, fixture.capture.releases)
            assertEquals(1, fixture.capture.discards)

            fixture.model.foreground(true)
            runCurrent()
            assertEquals(1, fixture.capture.starts)
            assertEquals(VoiceTalkPhase.ERROR, fixture.model.uiState.value.phase)
        }
    }

    @Test fun permissionDenialReportsAnErrorWithoutStartingCapture() = runTest {
        for (permanent in listOf(false, true)) {
            withVoice { fixture ->
                runCurrent()
                fixture.model.foreground(true)
                fixture.model.permissionDenied(permanent)
                runCurrent()

                assertEquals(VoiceTalkPhase.ERROR, fixture.model.uiState.value.phase)
                assertTrue(fixture.model.uiState.value.message.orEmpty().contains("麦克风"))
                if (permanent) assertTrue(fixture.model.uiState.value.message.orEmpty().contains("系统设置"))
                assertEquals(0, fixture.capture.starts)
                fixture.model.foreground(false)
                fixture.model.foreground(true)
                runCurrent()
                assertEquals(0, fixture.capture.starts)
            }
        }
    }

    @Test fun privacyDenialDoesNotOpenCaptureOrResumeWhenConsentChanges() = runTest {
        withVoice(privacyGranted = false) { fixture ->
            runCurrent()
            fixture.model.foreground(true)
            fixture.model.start()
            runCurrent()

            assertEquals(VoiceTalkPhase.ERROR, fixture.model.uiState.value.phase)
            assertTrue(fixture.model.uiState.value.message.orEmpty().contains("隐私"))
            assertEquals(0, fixture.capture.starts)
            PrivacyGate.allow()
            runCurrent()
            assertEquals(0, fixture.capture.starts)
        }
    }

    @Test fun humanVoiceRestrictionDoesNotCheckRecordingTranslationAccess() = runTest {
        val checks = mutableListOf<Feature>()
        val access = FeatureAccessManager { feature ->
            checks += feature
            feature != Feature.HumanVoice
        }
        withVoice(access = access) { fixture ->
            runCurrent()
            fixture.model.foreground(true)
            fixture.model.start()
            runCurrent()

            assertEquals(VoiceTalkPhase.ERROR, fixture.model.uiState.value.phase)
            assertEquals(0, fixture.capture.starts)
            assertEquals(listOf(Feature.HumanVoice), checks)
            assertFalse(checks.contains(Feature.RecordingTranslation))
        }
    }

    @Test fun petSelectionPersistsCanonicallyButIsIgnoredWhileRecording() = runTest {
        withVoice(initialPet = PetType.Cat) { fixture ->
            runCurrent()
            assertEquals(PetType.Cat, fixture.model.selectedPet.value)
            fixture.model.selectPet(PetType.Dog)
            assertTrue(fixture.model.uiState.value.petChanging)
            runCurrent()
            assertEquals(PetType.Dog, fixture.preferences.petType.value)
            assertEquals(PetType.Dog, fixture.model.selectedPet.value)
            assertEquals(listOf(PetType.Dog), fixture.preferences.writes)
            assertFalse(fixture.model.uiState.value.petChanging)

            startRecording(fixture)
            val discards = fixture.capture.discards
            fixture.model.selectPet(PetType.Cat)
            runCurrent()

            assertEquals(PetType.Dog, fixture.model.selectedPet.value)
            assertEquals(PetType.Dog, fixture.preferences.petType.value)
            assertEquals(listOf(PetType.Dog), fixture.preferences.writes)
            assertFalse(fixture.model.uiState.value.petChanging)
            assertEquals(VoiceTalkPhase.RECORDING, fixture.model.uiState.value.phase)
            assertEquals(1, fixture.capture.active)
            assertEquals(discards, fixture.capture.discards)
        }
    }

    @Test fun leaveWaitsForCaptureFinallyToReleaseBeforeCompleting() = runTest {
        withVoice { fixture ->
            val release = CompletableDeferred<Unit>()
            fixture.capture.cleanupBarrier = release
            startRecording(fixture)
            val completed = CompletableDeferred<Unit>()
            val leaving = launch {
                fixture.model.leaveAndAwait()
                completed.complete(Unit)
            }
            try {
                runCurrent()
                assertTrue(fixture.capture.cleanupStarted.isCompleted)
                assertFalse(completed.isCompleted)
                assertEquals(1, fixture.capture.active)
                fixture.model.start()
                runCurrent()
                assertEquals(1, fixture.capture.starts)
            } finally {
                release.complete(Unit)
            }
            leaving.join()
            runCurrent()

            assertTrue(completed.isCompleted)
            assertEquals(0, fixture.capture.active)
            assertEquals(1, fixture.capture.releases)
            assertEquals(1, fixture.capture.discards)
            assertEquals(VoiceTalkPhase.READY, fixture.model.uiState.value.phase)
        }
    }

    @Test fun invalidSoundEvidenceNeverPublishesOrPlaysAResult() = runTest {
        val analyzer = FakeAnalyzer().apply {
            suppliedFeatures = VoiceFeatures(1_000L, 0L, 0f, 0f, 0, emptyList(),
                valid = false, invalidReason = "没有足够的声音，请重新录制")
        }
        withVoice(analyzer = analyzer) { fixture ->
            completeRecording(fixture)

            assertEquals(VoiceTalkPhase.ERROR, fixture.model.uiState.value.phase)
            assertTrue(fixture.model.uiState.value.message.orEmpty().contains("没有足够"))
            assertNull(fixture.model.uiState.value.result)
            assertTrue(fixture.player.played.isEmpty())
            assertEquals(1, fixture.capture.discards)
        }
    }

    @Test fun missingDeveloperAssetsReportsNotReadyBeforeOpeningMicrophone() = runTest {
        withVoice(sounds = emptyList()) { fixture ->
            runCurrent()
            fixture.model.foreground(true)
            fixture.model.start()
            advanceUntilIdle()

            assertEquals(VoiceTalkPhase.ERROR, fixture.model.uiState.value.phase)
            assertTrue(fixture.model.uiState.value.message.orEmpty().contains("音源尚未就绪"))
            assertEquals(0, fixture.capture.starts)
            assertEquals(0, fixture.analyzer.calls)
            assertTrue(fixture.player.played.isEmpty())
        }
    }

    @Test fun leavingDuringAnalysisWaitsUntilConsumerFinallyBeforeDeletingPcm() = runTest {
        val analysisRelease = CompletableDeferred<Unit>()
        val consumerFinally = CompletableDeferred<Unit>()
        val analyzer = FakeAnalyzer().apply {
            analysisBarrier = analysisRelease
            cleanupBarrier = consumerFinally
        }
        withVoice(analyzer = analyzer) { fixture ->
            startRecording(fixture)
            fixture.model.stop()
            fixture.capture.result.complete(captureMetadata(1_000L))
            runCurrent()
            assertTrue(analyzer.started.isCompleted)
            assertEquals(VoiceTalkPhase.CONVERTING, fixture.model.uiState.value.phase)
            assertEquals(0, fixture.capture.active) // AudioRecord is already released.
            assertEquals(0, fixture.capture.discards) // Analysis still owns its PCM.

            val leaving = launch { fixture.model.leaveAndAwait() }
            runCurrent()
            assertTrue(analyzer.cleanupStarted.isCompleted)
            assertFalse(analyzer.finished)
            assertFalse(leaving.isCompleted)
            assertEquals(0, fixture.capture.discards)
            assertNull(fixture.model.uiState.value.result)
            assertTrue(fixture.player.played.isEmpty())

            consumerFinally.complete(Unit)
            leaving.join()
            runCurrent()
            assertTrue(analyzer.finished)
            assertEquals(1, fixture.capture.discards)
            assertEquals(VoiceTalkPhase.READY, fixture.model.uiState.value.phase)
            assertNull(fixture.model.uiState.value.result)
        }
    }

    @Test fun replayReusesTheSameSequenceWithoutAnalyzingOrSelectingAgain() = runTest {
        withVoice(initialPet = PetType.Cat) { fixture ->
            completeRecording(fixture)
            val first = checkNotNull(fixture.model.uiState.value.result)
            assertEquals(PetType.Cat, first.pet)
            assertTrue(first.items.all { it.asset.pet == PetType.Cat })

            fixture.model.replay()
            advanceUntilIdle()

            assertEquals(1, fixture.analyzer.calls)
            assertEquals(1, fixture.capture.starts)
            assertEquals(2, fixture.player.played.size)
            assertSame(first, fixture.player.played[0])
            assertSame(first, fixture.player.played[1])
            assertSame(first, fixture.model.uiState.value.result)
        }
    }

    @Test fun recordAgainAndLeavingEachStopAndReleaseTheCurrentPlayback() = runTest {
        for (leave in listOf(false, true)) {
            withVoice(player = FakePlayer(holdPlayback = true)) { fixture ->
                completeRecording(fixture)
                assertEquals(1, fixture.player.active)
                assertTrue(fixture.model.uiState.value.isPlaying)

                if (leave) fixture.model.leaveAndAwait() else fixture.model.reset()
                runCurrent()

                assertEquals(0, fixture.player.active)
                assertEquals(1, fixture.player.stops)
                assertEquals(1, fixture.player.finishedSessions)
                assertEquals(VoiceTalkPhase.READY, fixture.model.uiState.value.phase)
                assertNull(fixture.model.uiState.value.result)
                assertFalse(fixture.model.uiState.value.isPlaying)
            }
        }
    }

    @Test fun externalPetPreferenceChangeInvalidatesResultAndCannotReplayOtherPetsAudio() = runTest {
        withVoice(initialPet = PetType.Cat, player = FakePlayer(holdPlayback = true)) { fixture ->
            completeRecording(fixture)
            assertEquals(PetType.Cat, fixture.player.played.single().pet)

            fixture.preferences.petType.value = PetType.Dog
            runCurrent()

            assertEquals(PetType.Dog, fixture.model.selectedPet.value)
            assertEquals(0, fixture.player.active)
            assertEquals(VoiceTalkPhase.READY, fixture.model.uiState.value.phase)
            assertNull(fixture.model.uiState.value.result)
            fixture.model.replay()
            fixture.model.togglePlayback()
            runCurrent()
            assertEquals(1, fixture.player.played.size)
            assertTrue(fixture.player.played.single().items.all { it.asset.pet == PetType.Cat })
        }
    }

    @Test fun repeatedBackgroundSignalsDuringPlaybackCleanupRetainReplayableResult() = runTest {
        val release = CompletableDeferred<Unit>()
        val player = FakePlayer(holdPlayback = true).apply { cleanupBarrier = release }
        withVoice(player = player) { fixture ->
            completeRecording(fixture)
            val sequence = checkNotNull(fixture.model.uiState.value.result)
            assertTrue(fixture.model.uiState.value.isPlaying)

            fixture.model.foreground(false)
            fixture.model.interrupt()
            fixture.model.foreground(false)
            runCurrent()

            assertTrue(player.cleanupStarted.isCompleted)
            assertEquals(VoiceTalkPhase.STOPPING, fixture.model.uiState.value.phase)
            assertFalse(fixture.model.uiState.value.isPlaying)
            assertEquals(1, player.active)

            release.complete(Unit)
            runCurrent()
            assertEquals(0, player.active)
            assertEquals(VoiceTalkPhase.RESULT, fixture.model.uiState.value.phase)
            assertFalse(fixture.model.uiState.value.isPlaying)
            assertSame(sequence, fixture.model.uiState.value.result)

            fixture.model.foreground(true)
            fixture.model.replay()
            runCurrent()
            assertEquals(2, player.played.size)
            assertSame(sequence, player.played.last())
            assertEquals(VoiceTalkPhase.RESULT, fixture.model.uiState.value.phase)
            assertTrue(fixture.model.uiState.value.isPlaying)
        }
    }

    private fun TestScope.completeRecording(fixture: Fixture) {
        startRecording(fixture)
        fixture.model.stop()
        fixture.capture.result.complete(captureMetadata(1_000L))
        advanceUntilIdle()
    }

    private fun TestScope.startRecording(fixture: Fixture, elapsedMs: Long = 1_000L) {
        runCurrent()
        fixture.model.foreground(true)
        fixture.model.start()
        runCurrent()
        fixture.capture.emit(CaptureFrame(elapsedMs * 16L, elapsedMs, .25f, .7f))
        assertEquals(VoiceTalkPhase.RECORDING, fixture.model.uiState.value.phase)
    }

    private suspend fun TestScope.withVoice(
        initialPet: PetType = PetType.Dog,
        privacyGranted: Boolean = true,
        access: FeatureAccessManager = FeatureAccessManager { true },
        analyzer: FakeAnalyzer = FakeAnalyzer(),
        player: FakePlayer = FakePlayer(),
        sounds: List<VoiceSoundAsset> = ApprovedVoiceSounds.assets,
        body: suspend TestScope.(Fixture) -> Unit,
    ) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        if (privacyGranted) PrivacyGate.allow() else PrivacyGate.block()
        val store = ViewModelStore()
        val preferences = FakePreferences(initialPet)
        val capture = FakeCapture()
        val model = VoiceTalkViewModel(preferences, capture, access, dispatcher,
            analyzer = analyzer, soundLibrary = VoiceSoundLibrary { sounds }, player = player,
            ioDispatcher = dispatcher)
        store.put("voice-under-test", model)
        val phases = mutableListOf<VoiceTalkPhase>()
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.uiState.collect { phases += it.phase }
        }
        try {
            body.invoke(this, Fixture(preferences, capture, model, phases, analyzer, player))
        } finally {
            capture.cleanupBarrier?.complete(Unit)
            analyzer.analysisBarrier?.complete(Unit)
            analyzer.cleanupBarrier?.complete(Unit)
            player.cleanupBarrier?.complete(Unit)
            withContext(NonCancellable) { model.leaveAndAwait() }
            store.clear()
            observer.cancel()
            runCurrent()
            PrivacyGate.block()
            Dispatchers.resetMain()
        }
    }

    private data class Fixture(
        val preferences: FakePreferences,
        val capture: FakeCapture,
        val model: VoiceTalkViewModel,
        val phases: List<VoiceTalkPhase>,
        val analyzer: FakeAnalyzer,
        val player: FakePlayer,
    )

    private class FakePreferences(initial: PetType) : PetPreferences {
        override val petType = MutableStateFlow(initial)
        val writes = mutableListOf<PetType>()
        override suspend fun setPetType(type: PetType) {
            writes += type
            petType.value = type
        }
    }

    /** A metadata-only capture; analysis and playback are deterministic JVM substitutes. */
    private fun captureMetadata(durationMs: Long) = RecordedAudio(
        filePath = "fixture-only.pcm", sampleRate = 16_000,
        sampleCount = durationMs * 16L, durationMs = durationMs, rms = .25f, peak = .7f,
    )

    private class FakeCapture : AudioCapture {
        val result = CompletableDeferred<RecordedAudio>()
        val cleanupStarted = CompletableDeferred<Unit>()
        var cleanupBarrier: CompletableDeferred<Unit>? = null
        var starts = 0
            private set
        var stops = 0
            private set
        var discards = 0
            private set
        var releases = 0
            private set
        var active = 0
            private set
        private var onFrame: ((CaptureFrame) -> Unit)? = null

        override suspend fun record(onFrame: (CaptureFrame) -> Unit): RecordedAudio {
            starts++
            active++
            this.onFrame = onFrame
            try {
                return result.await()
            } finally {
                cleanupStarted.complete(Unit)
                withContext(NonCancellable) { cleanupBarrier?.await() }
                this.onFrame = null
                active--
                releases++
            }
        }

        fun emit(frame: CaptureFrame) {
            checkNotNull(onFrame) { "Capture must be active before a frame is emitted" }(frame)
        }

        override fun stop() { stops++ }
        override fun discard() { discards++ }
    }

    private class FakeAnalyzer : VoiceFeatureAnalyzer {
        var suppliedFeatures: VoiceFeatures? = null
        var analysisBarrier: CompletableDeferred<Unit>? = null
        var cleanupBarrier: CompletableDeferred<Unit>? = null
        val started = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        var calls = 0
        var finished = false

        override suspend fun analyze(audio: RecordedAudio): VoiceFeatures {
            calls++
            started.complete(Unit)
            try {
                analysisBarrier?.await()
                return suppliedFeatures ?: VoiceFeatures(audio.durationMs,
                    activeDurationMs = minOf(500L, audio.durationMs), averageEnergy = .2f,
                    energyVariation = .3f, activitySegmentCount = 1, pauseDurationsMs = emptyList(), valid = true)
            } finally {
                cleanupStarted.complete(Unit)
                withContext(NonCancellable) { cleanupBarrier?.await() }
                finished = true
            }
        }
    }

    private class FakePlayer(private val holdPlayback: Boolean = false) : VoiceSequencePlayer {
        val played = mutableListOf<VoicePlaybackSequence>()
        var active = 0
        var stops = 0
        var finishedSessions = 0
        val cleanupStarted = CompletableDeferred<Unit>()
        var cleanupBarrier: CompletableDeferred<Unit>? = null
        private var completion: CompletableDeferred<Unit>? = null
        private var progress: ((VoicePlaybackProgress) -> Unit)? = null
        private var current: VoicePlaybackSequence? = null

        override suspend fun play(sequence: VoicePlaybackSequence, onProgress: (VoicePlaybackProgress) -> Unit) {
            check(active == 0) { "Playback sessions must not overlap" }
            val done = CompletableDeferred<Unit>()
            completion = done
            current = sequence
            progress = onProgress
            played += sequence
            active++
            try {
                onProgress(VoicePlaybackProgress(0, sequence.estimatedDurationMs, true))
                if (holdPlayback) done.await()
                onProgress(VoicePlaybackProgress(sequence.estimatedDurationMs, sequence.estimatedDurationMs, false))
            } finally {
                cleanupStarted.complete(Unit)
                withContext(NonCancellable) { cleanupBarrier?.await() }
                active--
                finishedSessions++
                if (completion === done) {
                    completion = null
                    current = null
                    progress = null
                }
            }
        }

        override fun pause() { progress?.invoke(VoicePlaybackProgress(0, current?.estimatedDurationMs ?: 0, false)) }
        override fun resume() { progress?.invoke(VoicePlaybackProgress(0, current?.estimatedDurationMs ?: 0, true)) }
        override fun stop() { stops++; completion?.cancel() }
        override fun release() { completion?.cancel() }
    }
}
