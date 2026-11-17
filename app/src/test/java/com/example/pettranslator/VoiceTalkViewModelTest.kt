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

    @Test fun manualStopEndsRecordedWithExplicitPlaceholderAndNeverConverts() = runTest {
        withVoice { fixture ->
            startRecording(fixture, elapsedMs = 1_000L)
            fixture.model.stop()
            fixture.model.stop()
            assertEquals(VoiceTalkPhase.STOPPING, fixture.model.uiState.value.phase)
            assertEquals(1, fixture.capture.stops)

            fixture.capture.result.complete(captureMetadata(1_000L))
            advanceUntilIdle()

            val state = fixture.model.uiState.value
            assertEquals(VoiceTalkPhase.RECORDED, state.phase)
            assertEquals(1_000L, state.elapsedMs)
            assertTrue(state.message.orEmpty().contains("转换"))
            assertTrue(listOf("准备中", "未开放", "尚未").any { state.message.orEmpty().contains(it) })
            assertFalse(fixture.phases.contains(VoiceTalkPhase.CONVERTING))
            assertFalse(fixture.phases.contains(VoiceTalkPhase.RESULT))
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
        body: suspend TestScope.(Fixture) -> Unit,
    ) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        if (privacyGranted) PrivacyGate.allow() else PrivacyGate.block()
        val store = ViewModelStore()
        val preferences = FakePreferences(initialPet)
        val capture = FakeCapture()
        val model = VoiceTalkViewModel(preferences, capture, access, dispatcher)
        store.put("voice-under-test", model)
        val phases = mutableListOf<VoiceTalkPhase>()
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.uiState.collect { phases += it.phase }
        }
        try {
            body.invoke(this, Fixture(preferences, capture, model, phases))
        } finally {
            capture.cleanupBarrier?.complete(Unit)
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
    )

    private class FakePreferences(initial: PetType) : PetPreferences {
        override val petType = MutableStateFlow(initial)
        val writes = mutableListOf<PetType>()
        override suspend fun setPetType(type: PetType) {
            writes += type
            petType.value = type
        }
    }

    /** Only capture metadata is synthesized: no audio file, conversion or playback is produced. */
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
}
