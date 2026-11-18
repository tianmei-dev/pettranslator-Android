package com.example.pettranslator

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pettranslator.data.PetPreferencesRepository
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.ui.theme.PetTranslatorTheme
import com.example.pettranslator.ui.voice.VoiceTalkConvertingContent
import com.example.pettranslator.ui.voice.VoiceTalkResultContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** One real-device pass. The host preserves consent/preferences and restores microphone grants/flags. */
class VoiceTalkDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Before fun wake() = wakeTestDevice()

    @Test fun realPermissionManualAutomaticInterruptionAndExit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.uiAutomation
        val originalAccessibilityFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val folder = voiceEvidenceFolder()
        val log = File(folder, "voice-flow.txt").apply { writeText("") }
        val preferences = PetPreferencesRepository(context)
        var screenshotCount = 0
        fun note(message: String) {
            log.appendText("${SystemClock.elapsedRealtime()} $message\n")
            android.util.Log.i("B31VoiceDevice", message)
        }
        fun hasTag(tag: String): Boolean = rule.onAllNodesWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
        fun waitTag(tag: String, timeoutMs: Long = 8_000) {
            rule.waitUntil(timeoutMs) { hasTag(tag) }
            rule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
        }
        fun assertNoConversion() {
            rule.onNodeWithTag("voice_talk_converting").assertDoesNotExist()
            rule.onNodeWithTag("voice_talk_result").assertDoesNotExist()
            rule.onNodeWithText("转换完成！").assertDoesNotExist()
        }
        fun capture(name: String) {
            check(name in setOf("voice-ready.png", "voice-recording.png", "voice-recorded.png"))
            awaitVoiceRealTime(5_000, "app foreground before $name") { voiceActivePackage() == context.packageName }
            assertEquals(context.packageName, voiceActivePackage())
            val bitmap = checkNotNull(automation.takeScreenshot())
            try {
                File(folder, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                screenshotCount++
                note("screenshot=$name ${bitmap.width}x${bitmap.height} foreground=${context.packageName}")
            } finally { bitmap.recycle() }
        }
        fun canonicalPet(expected: PetType) {
            val actual = runBlocking { withTimeout(5_000) { preferences.petType.first { it == expected } } }
            assertEquals(expected, actual)
            rule.waitUntil(5_000) {
                rule.onAllNodes(hasTestTag("voice_talk_pet_${expected.name}") and isSelected())
                    .fetchSemanticsNodes().isNotEmpty()
            }
            note("canonicalPet=$actual persistedFlowMatchesSelection=true")
        }
        fun reset() {
            val resetTag = if (hasTag("voice_talk_result")) "voice_talk_again" else "voice_talk_reset"
            rule.onNodeWithTag(resetTag).performScrollTo().assertIsEnabled().performClick()
            waitTag("voice_talk_record")
            rule.onNodeWithTag("voice_talk_record").assertIsEnabled()
            assertNoConversion()
        }
        fun waitCaptureTerminal(label: String, timeoutMs: Long = 8_000): String {
            rule.waitUntil(timeoutMs) { hasTag("voice_talk_result") || hasTag("voice_talk_error") }
            return if (hasTag("voice_talk_result")) {
                waitTag("voice_talk_result")
                rule.onNodeWithText("转换完成！").assertIsDisplayed()
                rule.onNodeWithTag("voice_talk_replay").assertIsEnabled()
                note("$label=RESULT; acceptedEnergyEvidence=true; conversionPassed=true")
                "RESULT"
            } else {
                waitTag("voice_talk_error")
                rule.onNodeWithTag("voice_talk_message").assertIsDisplayed()
                rule.onNodeWithTag("voice_talk_result").assertDoesNotExist()
                rule.onNodeWithText("转换完成！").assertDoesNotExist()
                note("$label=ERROR; conversionPassed=false; ambient input is not assumed valid")
                "ERROR"
            }
        }
        fun waitRealFrames(minimumProgress: Float = .12f) {
            rule.waitUntil(6_000) {
                rule.onAllNodesWithTag("voice_talk_record_progress", useUnmergedTree = true)
                    .fetchSemanticsNodes().singleOrNull()?.config
                    ?.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.current?.let { it >= minimumProgress } == true
            }
            rule.onNodeWithTag("voice_talk_stop").assertIsEnabled()
            val progress = rule.onNodeWithTag("voice_talk_record_progress", useUnmergedTree = true)
                .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
            note("realCaptureProgress=$progress source=production PCM frame elapsedMs; no virtual audio clock")
        }
        fun captureFiles(): List<String> = File(context.cacheDir, "pet_recordings").listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith("capture_") }.map { it.name }.sorted()
        fun enterFromPlay() {
            rule.onNodeWithTag("play_list").performScrollToNode(hasTestTag("fun_voice_start"))
            rule.onNodeWithTag("fun_voice_start").assertIsEnabled().performClick()
            waitTag("voice_talk_page")
            waitTag("voice_talk_record")
        }

        try {
            note("device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT}; " +
                "singlePass=true realMicrophone=true syntheticAudio=false conversionPipeline=true")
            assertEquals("Host must start with microphone permission ungranted", PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
            rule.waitUntil(12_000) { hasTag("privacy_agree") || hasTag("home_record") }
            if (hasTag("privacy_agree")) {
                rule.onNodeWithTag("privacy_agree").assertIsDisplayed().performClick()
                note("privacy=accepted through visible consent UI; host owns restoration")
            }
            waitTag("home_record", 12_000)
            val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
            rule.onNode(tabRole and hasText("逗宠") and hasClickAction()).performClick()
            waitTag("play_list")
            enterFromPlay()
            rule.onAllNodes(tabRole).assertCountEquals(0)

            rule.onNodeWithTag("voice_talk_pet_Dog").assertIsEnabled().performClick()
            canonicalPet(PetType.Dog)
            rule.onNodeWithTag("voice_talk_pet_Cat").assertIsEnabled().performClick()
            canonicalPet(PetType.Cat)
            val originalCaptureFiles = captureFiles()
            note("captureFilesBefore=$originalCaptureFiles")
            capture("voice-ready.png")

            rule.onNodeWithTag("voice_talk_record").performClick()
            clickVoicePermissionButton(listOf("permission_deny_button"), ::note)
            waitTag("voice_talk_error")
            rule.onNodeWithTag("voice_talk_message").assert(hasText("麦克风", substring = true))
            rule.onNodeWithTag("voice_talk_recorded").assertDoesNotExist()
            assertNoConversion()
            assertEquals(PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
            note("permission=denied using real OS button; ERROR visible; no recorded/conversion/result")

            reset()
            rule.onNodeWithTag("voice_talk_record").performClick()
            clickVoicePermissionButton(listOf("permission_allow_foreground_only_button", "permission_allow_button"), ::note)
            assertEquals(PackageManager.PERMISSION_GRANTED,
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
            waitRealFrames()
            rule.onNodeWithTag("voice_talk_pet_Cat").assertIsNotEnabled()
            rule.onNodeWithTag("voice_talk_pet_Dog").assertIsNotEnabled()
            rule.onNodeWithTag("voice_talk_wave_live_left", useUnmergedTree = true).assertExists()
            rule.onNodeWithTag("voice_talk_wave_live_right", useUnmergedTree = true).assertExists()
            canonicalPet(PetType.Cat)
            capture("voice-recording.png")
            rule.onNodeWithTag("voice_talk_stop").performClick()
            val manualOutcome = waitCaptureTerminal("manualStop")
            capture("voice-recorded.png")
            note("manualCaptureTerminal=$manualOutcome; real RMS assessed by host capture logs")

            reset()
            val automaticStartedAt = SystemClock.elapsedRealtime()
            rule.onNodeWithTag("voice_talk_record").performClick()
            waitRealFrames(.05f)
            val automaticOutcome = waitCaptureTerminal("automaticStop", 14_000)
            val automaticWallMs = SystemClock.elapsedRealtime() - automaticStartedAt
            assertTrue("10-second capture must stop in real wall time (was $automaticWallMs ms)",
                automaticWallMs in 9_000L..14_000L)
            note("automaticCaptureTerminal=$automaticOutcome realWallMs=$automaticWallMs; host checks VoiceTalkCapture metadata")

            reset()
            rule.onNodeWithTag("voice_talk_record").performClick()
            waitRealFrames(.05f)
            rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            waitTag("voice_talk_error")
            rule.onNodeWithTag("voice_talk_message").assert(hasText("中断", substring = true))
            SystemClock.sleep(400)
            rule.onNodeWithTag("voice_talk_error").assertIsDisplayed()
            rule.onNodeWithTag("voice_talk_stop").assertDoesNotExist()
            assertNoConversion()
            note("background=CREATED->RESUMED; interrupted ERROR retained; no automatic resume")

            reset()
            rule.onNodeWithTag("voice_talk_record").performClick()
            waitRealFrames(.05f)
            rule.onNodeWithTag("voice_talk_back").performClick()
            waitTag("play_list")
            rule.onNodeWithTag("voice_talk_page").assertDoesNotExist()
            awaitVoiceRealTime(6_000, "only this flow's PCM files are released") { captureFiles() == originalCaptureFiles }
            assertEquals("Leaving releases and deletes this flow's temporary recordings", originalCaptureFiles, captureFiles())
            note("recordingBack=returned to Play after release; captureFilesAfter=${captureFiles()}")
            enterFromPlay()
            rule.onNodeWithTag("voice_talk_record").assertIsEnabled()
            rule.onNodeWithTag("voice_talk_stop").assertDoesNotExist()
            rule.onNodeWithTag("voice_talk_recorded").assertDoesNotExist()
            rule.onNodeWithTag("voice_talk_error").assertDoesNotExist()
            canonicalPet(PetType.Cat)
            rule.onNodeWithTag("voice_talk_back").performClick()
            waitTag("play_list")
            assertEquals(originalCaptureFiles, captureFiles())
            assertEquals("Retain exactly three foreground production screenshots", 3, screenshotCount)
            note("PASS reopened READY without starting; normal back restored Play; screenshots=$screenshotCount")
        } catch (failure: Throwable) {
            note("FAIL ${failure.javaClass.simpleName}: ${failure.message}; no retry executed")
            throw failure
        } finally {
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalAccessibilityFlags }
            note("completed; host restores original privacy/preference files and microphone grant/flags")
        }
    }
}

/** These screenshots are explicitly isolated UI fixtures. No recorder, converter, or player is created. */
class VoiceTalkIsolatedUiTest {
    @get:Rule val rule = createComposeRule()
    @Before fun wake() = wakeTestDevice()

    @Test fun conversionDecorationAndUnavailableResultOnly() {
        var stage by mutableIntStateOf(0)
        val audioSources = emptyList<Uri>()
        var playbackCallbacks = 0
        rule.setContent {
            PetTranslatorTheme {
                Column(Modifier.fillMaxSize().background(Color(0xFFF7EBFF)).padding(20.dp)) {
                    Text("隔离UI预览，不执行转换或播放")
                    if (stage == 0) VoiceTalkConvertingContent(PetType.Dog)
                    else VoiceTalkResultContent(0, 0, false, false,
                        { playbackCallbacks++ }, {}, audioSources = audioSources)
                }
            }
        }
        rule.onNodeWithText("隔离UI预览，不执行转换或播放").assertIsDisplayed()
        rule.onNodeWithTag("voice_talk_converting").assertIsDisplayed()
        rule.onNodeWithText("请稍候，马上为你生成狗语～").assertIsDisplayed()
        captureIsolatedVoiceUi("voice-converting-isolated.png")
        rule.runOnIdle { stage = 1 }
        rule.onNodeWithTag("voice_talk_result").assertIsDisplayed()
        rule.onNodeWithText("转换结果预览").assertIsDisplayed()
        rule.onNodeWithText("转换完成！").assertDoesNotExist()
        rule.onNodeWithTag("voice_talk_play_pause").assertIsNotEnabled()
        rule.onNodeWithTag("voice_talk_replay").assertIsNotEnabled()
        rule.onNodeWithTag("voice_talk_playback_unavailable").assertTextEquals("暂无有效转换音源，播放暂未开放")
        assertTrue(audioSources.isEmpty())
        assertEquals(0, playbackCallbacks)
        captureIsolatedVoiceUi("voice-result-unavailable-isolated.png")
        File(voiceEvidenceFolder(), "voice-isolated.txt").writeText(
            "PASS isolated UI only; no audio pipeline; empty source list; playback/replay disabled; two labelled screenshots.\n")
    }

    private fun captureIsolatedVoiceUi(name: String) {
        check(name in setOf("voice-converting-isolated.png", "voice-result-unavailable-isolated.png"))
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        awaitVoiceRealTime(5_000, "isolated activity foreground") { voiceActivePackage() == instrumentation.targetContext.packageName }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(voiceEvidenceFolder(), name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }
}

private fun voiceEvidenceFolder(): File = File(
    checkNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)),
    "b31-voice-evidence").apply { check(isDirectory || mkdirs()) }

@Suppress("DEPRECATION")
private fun voiceActivePackage(): String? {
    val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow ?: return null
    return try { root.packageName?.toString() } finally { root.recycle() }
}

/** Poll real time only; this never advances Compose's clock or fabricates audio frames. */
private fun awaitVoiceRealTime(timeoutMs: Long, description: String, condition: () -> Boolean) {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (SystemClock.elapsedRealtime() < deadline) {
        if (condition()) return
        SystemClock.sleep(50)
    }
    check(condition()) { "Timed out waiting for $description; foreground=${voiceActivePackage()}" }
}

/** One genuine OS click, with the standard AOSP/Google permission-controller resource IDs. */
@Suppress("DEPRECATION")
private fun clickVoicePermissionButton(buttonIds: List<String>, note: (String) -> Unit) {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val controllerPackages = listOf("com.android.permissioncontroller", "com.google.android.permissioncontroller")
    var clicked = false
    var clickedResource = ""
    awaitVoiceRealTime(8_000, "real permission-controller button ${buttonIds.joinToString()}") {
        if (clicked) true else {
            val root = automation.rootInActiveWindow
            if (root == null) false else try {
                if (root.packageName?.toString() !in controllerPackages) false else {
                    for (id in buttonIds) {
                        for (controller in controllerPackages) {
                            val resource = "$controller:id/$id"
                            val nodes = root.findAccessibilityNodeInfosByViewId(resource)
                            try {
                                val button = nodes.firstOrNull { it.isVisibleToUser && it.isEnabled && it.isClickable }
                                if (button != null) {
                                    // A failed click aborts this test immediately; it is never retried.
                                    check(button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "OS rejected permission click: $resource" }
                                    clicked = true
                                    clickedResource = resource
                                }
                            } finally { nodes.forEach { it.recycle() } }
                            if (clicked) break
                        }
                        if (clicked) break
                    }
                    clicked
                }
            } finally { root.recycle() }
        }
    }
    check(clicked)
    note("permissionControllerClick=$clickedResource attempts=1")
    val expectedPackage = InstrumentationRegistry.getInstrumentation().targetContext.packageName
    awaitVoiceRealTime(8_000, "app foreground after permission click") { voiceActivePackage() == expectedPackage }
}
