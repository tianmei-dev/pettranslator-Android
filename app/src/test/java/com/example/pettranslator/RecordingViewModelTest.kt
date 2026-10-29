package com.example.pettranslator

import com.example.pettranslator.domain.*
import com.example.pettranslator.domain.recording.*
import com.example.pettranslator.domain.recognition.*
import com.example.pettranslator.data.recognition.PetSoundRecognizer
import com.example.pettranslator.ui.recording.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingViewModelTest {
    private class FakeRecognizer(private val raw: YamnetRawResult) : PetSoundRecognizer {
        var calls = 0
        override suspend fun infer(audio: RecordedAudio): YamnetRawResult { calls++; return raw }
    }
    private class ControlledCapture : AudioCapture {
        var starts = 0; var stops = 0; var discards = 0
        var result = CompletableDeferred<RecordedAudio>()
        override suspend fun record(onFrame: (CaptureFrame) -> Unit): RecordedAudio {
            starts++
            repeat(42) { index -> onFrame(CaptureFrame(800L * (index + 1), 50L * (index + 1), index / 1000f, index / 100f)) }
            return result.await()
        }
        override fun stop() { stops++ }
        override fun discard() { discards++ }
    }
    private fun rawDog(): YamnetRawResult {
        val labels = List(521) { "class-$it" }.toMutableList().apply { this[70] = "Bark"; this[76] = "Cat" }
        val scores = FloatArray(521).apply { this[70] = .8f; this[76] = .05f }
        return YamnetRawResult(scores, listOf(scores), labels, .05f, .8f, .02f,
            SoundEvent(70, "Bark", .8f), AudioQuality(1000, -24.0, 0.0, 0.0, 1, true))
    }
    private fun model(capture: AudioCapture, recognizer: FakeRecognizer = FakeRecognizer(rawDog())) =
        RecordingViewModel(PetType.Dog, PetScene.Play, capture, recognizer)
    private fun audio() = RecordedAudio("/private/test.pcm", 16000, 16000, 1000, rms = .02f, peak = .1f,
        audioFingerprint = "a".repeat(64))

    @Test fun uncertainWaitsForSessionAndDismissesOnceWithoutStartingCaptureOrCheckingAccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val capture = ControlledCapture()
            val recognizer = FakeRecognizer(rawDog().copy(quality = rawDog().quality.copy(rmsDbfs = -90.0)))
            var accessChecks = 0
            val model = RecordingViewModel(PetType.Dog, PetScene.Play, capture, recognizer,
                featureAccessManager = com.example.pettranslator.core.FeatureAccessManager { accessChecks++; false })
            model.foreground(true); model.start(); runCurrent(); model.stop()
            capture.result.complete(audio()); runCurrent()
            assertEquals(RecordingPhase.ANALYZING, model.uiState.value.phase)
            assertTrue(model.uiState.value.analysisTargetDurationMs in 3_000L..5_000L)
            assertEquals(1, recognizer.calls)
            advanceUntilIdle()
            assertEquals(RecordingPhase.UNCERTAIN, model.uiState.value.phase)
            assertNull(model.uiState.value.interpretation)
            model.compositionDisposed(); model.foreground(true)
            assertEquals(RecordingPhase.UNCERTAIN, model.uiState.value.phase)
            assertFalse(model.prepareAnotherRecording())
            assertEquals(1, accessChecks)
            model.dismissUncertain(); val discards = capture.discards
            model.dismissUncertain(); model.foreground(false); model.foreground(true)
            advanceUntilIdle()
            assertEquals(RecordingPhase.READY, model.uiState.value.phase)
            assertNull(model.uiState.value.audio)
            assertEquals(discards, capture.discards)
            assertEquals(1, capture.starts)
            assertEquals(1, recognizer.calls)
            assertEquals(1, accessChecks)
            model.leave()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun leavingDuringUncertainDelayCannotPublishOldSheet() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val capture = ControlledCapture()
            val recognizer = FakeRecognizer(rawDog().copy(quality = rawDog().quality.copy(rmsDbfs = -90.0)))
            val model = model(capture, recognizer)
            model.foreground(true); model.start(); runCurrent(); model.stop()
            capture.result.complete(audio()); runCurrent()
            assertEquals(RecordingPhase.ANALYZING, model.uiState.value.phase)
            model.leave(); advanceUntilIdle()
            assertEquals(RecordingPhase.READY, model.uiState.value.phase)
            assertNull(model.uiState.value.audio)
            assertEquals(1, recognizer.calls)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun singleStartAndStopAndCompletedHandoff() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val capture = ControlledCapture(); val model = model(capture)
            model.start(); runCurrent(); assertEquals(0, capture.starts)
            model.foreground(true); model.start(); model.start(); runCurrent()
            assertEquals(1, capture.starts)
            assertEquals(RecordingPhase.RECORDING, model.uiState.value.phase)
            assertEquals(40, model.uiState.value.levels.size)
            assertEquals(.002f, model.uiState.value.levels.first())
            assertEquals(.041f, model.uiState.value.levels.last())
            assertEquals(.02f, model.uiState.value.peakLevels.first())
            assertEquals(.41f, model.uiState.value.peakLevels.last())
            model.stop(); model.stop(); assertEquals(1, capture.stops)
            capture.result.complete(audio()); advanceUntilIdle()
            assertEquals(RecordingPhase.SUCCESS, model.uiState.value.phase)
            assertNotNull(model.uiState.value.interpretation)
            assertEquals(audio(), model.uiState.value.audio)
            assertEquals(RecordingInput(audio(), PetType.Dog, PetScene.Play), model.completedInput())
            assertEquals(PetType.Dog, model.pet); assertEquals(PetScene.Play, model.scene)
            model.leave(); assertNull(model.uiState.value.audio)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun mismatchSwitchRejudgesCachedModelOutputWithoutSecondInference() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val recognizer = FakeRecognizer(rawDog())
            val capture = ControlledCapture(); val model = RecordingViewModel(PetType.Cat, PetScene.Play, capture, recognizer)
            model.foreground(true); model.start(); runCurrent(); model.stop()
            capture.result.complete(audio()); runCurrent()
            assertEquals(RecordingPhase.PET_MISMATCH, model.uiState.value.phase)
            assertNull(model.uiState.value.interpretation)
            assertEquals(1, recognizer.calls)
            val rawBefore = model.uiState.value.rawRecognition
            model.switchPetAndRejudge(); advanceUntilIdle()
            assertEquals(RecordingPhase.SUCCESS, model.uiState.value.phase)
            assertNotNull(model.uiState.value.interpretation)
            assertEquals(PetType.Dog, model.pet)
            assertEquals(1, recognizer.calls)
            assertSame(rawBefore, model.uiState.value.rawRecognition)
            model.leave()
        } finally { Dispatchers.resetMain() }
    }
    @Test fun backgroundCancelsWithoutPublishingAndCanRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val capture = ControlledCapture(); val model = model(capture)
            model.foreground(true); model.start(); runCurrent()
            model.foreground(false); capture.result.complete(audio()); runCurrent()
            assertEquals(RecordingPhase.READY, model.uiState.value.phase)
            assertNull(model.uiState.value.audio)
            assertTrue(capture.discards >= 2)
            capture.result = CompletableDeferred()
            model.foreground(true); model.start(); runCurrent()
            assertEquals(2, capture.starts)
            model.cancel(); runCurrent(); assertNull(model.uiState.value.audio)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun failuresAndPermissionAreExplicit() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val model = model(object : AudioCapture {
                override suspend fun record(onFrame: (CaptureFrame) -> Unit): RecordedAudio = throw CaptureException("麦克风被占用")
                override fun stop() {}
                override fun discard() {}
            })
            model.permissionDenied(false); assertFalse(model.uiState.value.permissionBlocked)
            model.permissionDenied(true); assertTrue(model.uiState.value.permissionBlocked)
            model.permissionAvailable(); assertFalse(model.uiState.value.permissionBlocked)
            model.foreground(true); model.start(); runCurrent()
            assertEquals(RecordingPhase.ERROR, model.uiState.value.phase)
            assertEquals("麦克风被占用", model.uiState.value.message)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun tenAnalysisSessionsKeepOneThreeToFiveSecondTargetUntilSuccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val capture = ControlledCapture()
            val model = model(capture)
            model.foreground(true)
            repeat(10) { index ->
                if (index > 0) assertTrue(model.prepareAnotherRecording())
                capture.result = CompletableDeferred()
                model.start(); runCurrent(); model.stop()
                capture.result.complete(audio()); runCurrent()
                val pending = model.uiState.value
                assertEquals(RecordingPhase.ANALYZING, pending.phase)
                assertTrue(pending.analysisTargetDurationMs in 3_000L..5_000L)
                val start = pending.analysisStartedAtNanos
                val target = pending.analysisTargetDurationMs
                assertEquals(4, pending.analysisStep)
                assertEquals(start, model.uiState.value.analysisStartedAtNanos)
                assertEquals(target, model.uiState.value.analysisTargetDurationMs)
                advanceUntilIdle()
                assertEquals(RecordingPhase.SUCCESS, model.uiState.value.phase)
                assertNotNull(model.uiState.value.interpretation)
            }
            model.leave()
        } finally { Dispatchers.resetMain() }
    }
}
