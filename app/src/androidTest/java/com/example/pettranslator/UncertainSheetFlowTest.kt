package com.example.pettranslator

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pettranslator.data.recognition.PetSoundRecognizer
import com.example.pettranslator.domain.*
import com.example.pettranslator.domain.recording.*
import com.example.pettranslator.domain.recognition.*
import com.example.pettranslator.ui.recording.*
import com.example.pettranslator.ui.theme.PetTranslatorTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UncertainSheetFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Before fun wake() = wakeTestDevice()

    @Test fun compactLandscapeWindowKeepsButtonReachable() {
        try {
            rule.activityRule.scenario.onActivity {
                it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            rule.waitUntil(5000) {
                rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            }
            rule.runOnUiThread {
                rule.activity.setContent { PetTranslatorTheme { UncertainRecordingSheet(PetType.Dog, {}, {}) } }
            }
            rule.onNodeWithTag("uncertain_dog_illustration").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("重新录音").performScrollTo().assertIsDisplayed()
        } finally {
            rule.activityRule.scenario.onActivity {
                it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    @Test fun dogSessionKeepsItsIllustrationWhenRecomposed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("pm grant ${rule.activity.packageName} ${Manifest.permission.RECORD_AUDIO}")
            .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        var calls = 0
        val capture = object : AudioCapture {
            override suspend fun record(onFrame: (CaptureFrame) -> Unit) = RecordedAudio(
                "/unused/dog.pcm", 16000, 16000, 1000, rms = .01f, peak = .1f, audioFingerprint = "b".repeat(64))
            override fun stop() {}
            override fun discard() {}
        }
        val recognizer = object : PetSoundRecognizer {
            override suspend fun infer(audio: RecordedAudio): YamnetRawResult {
                calls++
                val scores = FloatArray(521)
                return YamnetRawResult(scores, listOf(scores), List(521) { "label-$it" },
                    0f, 0f, 0f, null, AudioQuality(1000, -80.0, 0.0, 1.0, 1, false))
            }
        }
        val model = RecordingViewModel(PetType.Dog, PetScene.Meal, capture, recognizer)
        fun mount() = rule.runOnUiThread {
            rule.activity.setContent { PetTranslatorTheme { RecordingRoute(model, {}) } }
        }
        mount()
        rule.onNodeWithContentDescription("开始录音").performClick()
        rule.waitUntil(8000) { model.uiState.value.phase == RecordingPhase.UNCERTAIN }
        rule.onNodeWithTag("uncertain_dog_illustration").assertIsDisplayed()
        rule.onNodeWithTag("uncertain_cat_illustration").assertDoesNotExist()
        mount()
        rule.onNodeWithTag("uncertain_dog_illustration").assertIsDisplayed()
        assertEquals(1, calls)
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "a144-evidence").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(output, "uncertain-dog.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        rule.onNodeWithText("重新录音").performClick()
        rule.onNodeWithContentDescription("开始录音").assertIsDisplayed()
        assertEquals(1, calls)
        rule.runOnUiThread { model.leave() }
    }

    @Test fun largeFontSheetKeepsContentAndButtonReachable() {
        rule.runOnUiThread {
            rule.activity.setContent {
                val density = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)) {
                    PetTranslatorTheme { UncertainRecordingSheet(PetType.Cat, {}, {}) }
                }
            }
        }
        rule.onNodeWithText("哎呀，这次没听清毛孩子的声音～").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("换个安静点的地方，靠近毛孩子再试试吧！").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("重新录音").performScrollTo().assertIsDisplayed()
    }

    @Test fun otherResultsRemainSeparateFromUncertain() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("pm grant ${rule.activity.packageName} ${Manifest.permission.RECORD_AUDIO}")
            .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        for (fail in listOf(false, true)) {
            val capture = object : AudioCapture {
                override suspend fun record(onFrame: (CaptureFrame) -> Unit) = RecordedAudio(
                    "/unused/test.pcm", 16000, 16000, 1000, rms = .02f, peak = .1f, audioFingerprint = "a".repeat(64))
                override fun stop() {}
                override fun discard() {}
            }
            var calls = 0
            val recognizer = object : PetSoundRecognizer {
                override suspend fun infer(audio: RecordedAudio): YamnetRawResult {
                    calls++
                    if (fail) error("injected model failure")
                    val scores = FloatArray(521).apply { this[70] = .9f }
                    return YamnetRawResult(scores, listOf(scores), List(521) { "label-$it" },
                        .01f, .9f, .01f, SoundEvent(70, "Bark", .9f), AudioQuality(1000, -20.0, 0.0, 0.0, 1, true))
                }
            }
            val model = RecordingViewModel(PetType.Cat, PetScene.Play, capture, recognizer)
            rule.runOnUiThread { rule.activity.setContent { PetTranslatorTheme { RecordingRoute(model, {}) } } }
            rule.onNodeWithContentDescription("开始录音").performClick()
            rule.waitUntil(4000) { model.uiState.value.phase == if (fail) RecordingPhase.ANALYSIS_ERROR else RecordingPhase.PET_MISMATCH }
            rule.onNodeWithTag("uncertain_sheet").assertDoesNotExist()
            if (fail) {
                rule.onNodeWithText("声音分析失败，请重试").assertIsDisplayed()
                rule.onNodeWithText("重试分析").performClick()
                rule.waitUntil(4000) { calls == 2 && model.uiState.value.phase == RecordingPhase.ANALYSIS_ERROR }
            } else {
                rule.onNodeWithText("切换为狗狗并重新分析").performClick()
                rule.waitUntil(8000) { model.uiState.value.phase == RecordingPhase.SUCCESS }
                rule.onNodeWithText("翻译结果").assertIsDisplayed()
                assertEquals(1, calls)
            }
            rule.runOnUiThread { model.leave() }
        }
    }

    @Test fun retryDismissRecompositionAndBackNeverStartAnotherCapture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("pm grant ${rule.activity.packageName} ${Manifest.permission.RECORD_AUDIO}")
            .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        var captures = 0
        var released = false
        var inferences = 0
        var backs = 0
        val audio = RecordedAudio("/unused/test.pcm", 16000, 16000, 1000, rms = .02f, peak = .1f, audioFingerprint = "a".repeat(64))
        val capture = object : AudioCapture {
            override suspend fun record(onFrame: (CaptureFrame) -> Unit): RecordedAudio {
                captures++; released = true; return audio
            }
            override fun stop() {}
            override fun discard() {}
        }
        val recognizer = object : PetSoundRecognizer {
            override suspend fun infer(audio: RecordedAudio): YamnetRawResult {
                assertTrue(released); inferences++
                val scores = FloatArray(521)
                return YamnetRawResult(scores, listOf(scores), List(521) { "label-$it" },
                    0f, 0f, 0f, null, AudioQuality(1000, -80.0, 0.0, 1.0, 1, false))
            }
        }
        val model = RecordingViewModel(PetType.Cat, PetScene.Play, capture, recognizer)
        fun mount() = rule.runOnUiThread {
            rule.activity.setContent { PetTranslatorTheme { RecordingRoute(model, onBack = { backs++ }) } }
        }
        mount()
        rule.onNodeWithContentDescription("开始录音").performClick()
        rule.waitUntil(8000) { model.uiState.value.phase == RecordingPhase.UNCERTAIN }
        rule.onNodeWithText("哎呀，这次没听清毛孩子的声音～").assertIsDisplayed()
        rule.onNodeWithTag("uncertain_cat_illustration").assertIsDisplayed()
        rule.onNodeWithTag("uncertain_dog_illustration").assertDoesNotExist()
        rule.onNodeWithText("换个安静点的地方，靠近毛孩子再试试吧！").assertIsDisplayed()
        rule.onNodeWithText("声音分析").assertDoesNotExist()
        rule.waitForIdle()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "a143-evidence").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "uncertain-sheet.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        rule.onNodeWithText("重新录音").performClick()
        rule.onNodeWithContentDescription("开始录音").assertIsDisplayed()
        assertEquals(1, captures); assertEquals(1, inferences)
        mount()
        rule.onNodeWithText("哎呀，这次没听清毛孩子的声音～").assertDoesNotExist()
        assertEquals(1, captures)
        rule.onNodeWithContentDescription("开始录音").performClick()
        rule.waitUntil(8000) { model.uiState.value.phase == RecordingPhase.UNCERTAIN }
        rule.onNodeWithText("重新录音").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        rule.onNodeWithContentDescription("开始录音").assertIsDisplayed()
        assertEquals(2, captures); assertEquals(2, inferences)
        rule.onNodeWithContentDescription("开始录音").performClick()
        rule.waitUntil(8000) { model.uiState.value.phase == RecordingPhase.UNCERTAIN }
        rule.onNodeWithText("重新录音").assertIsDisplayed()
        val down = android.os.SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(android.view.MotionEvent.obtain(down, down,
            android.view.MotionEvent.ACTION_DOWN, 20f, 300f, 0))
        instrumentation.sendPointerSync(android.view.MotionEvent.obtain(down, down + 80,
            android.view.MotionEvent.ACTION_UP, 20f, 300f, 0))
        rule.waitUntil(3000) { model.uiState.value.phase == RecordingPhase.READY }
        rule.onNodeWithContentDescription("开始录音").assertIsDisplayed()
        assertEquals(3, captures)
        rule.onNodeWithContentDescription("开始录音").performClick()
        rule.waitUntil(8000) { model.uiState.value.phase == RecordingPhase.UNCERTAIN }
        rule.onNodeWithTag("uncertain_sheet").performTouchInput { swipeDown() }
        rule.waitUntil(3000) { model.uiState.value.phase == RecordingPhase.READY }
        rule.onNodeWithContentDescription("开始录音").assertIsDisplayed()
        assertEquals(4, captures); assertEquals(4, inferences)
        rule.onNodeWithTag("recording_back").performClick()
        assertEquals(1, backs)
        assertEquals(RecordingPhase.READY, model.uiState.value.phase)
        model.leave()
    }
}
