package com.example.pettranslator

import android.content.Context
import android.media.AudioManager
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pettranslator.audio.PetSoundCatalog
import com.example.pettranslator.domain.PetPreferences
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.privacy.PrivacyGate
import com.example.pettranslator.ui.navigation.PetTranslatorApp
import com.example.pettranslator.ui.theme.PetTranslatorTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Drives the production navigation and verifies MediaPlayer lifecycle events. */
@RunWith(AndroidJUnit4::class)
class PetFunAudioDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val runId = SystemClock.elapsedRealtime()
    private val playing = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "正在播放")

    private val cats = listOf(
        Preview("cat_call", "cat_continuous_01"),
        Preview("cat_sit", "cat_continuous_03"),
        Preview("cat_paw", "cat_continuous_06"),
        Preview("cat_praise", "cat_continuous_09"),
        Preview("cat_meal", "cat_continuous_04"),
    )
    private val dogs = listOf(
        Preview("dog_call", "dog_continuous_03"),
        Preview("dog_sit", "dog_continuous_04"),
        Preview("dog_paw", "dog_continuous_05"),
        Preview("dog_praise", "dog_continuous_06"),
        Preview("dog_meal", "dog_continuous_07"),
    )

    @Test fun tenPreviewsPlayCompleteClipsAndReleaseOnSwitchAndExit() {
        wakeTestDevice()
        val grantedBefore = PrivacyGate.isGranted()
        PrivacyGate.allow()
        try {
            val prefs = object : PetPreferences {
                override val petType = MutableStateFlow(PetType.Cat)
                override suspend fun setPetType(type: PetType) { petType.value = type }
            }
            rule.setContent { PetTranslatorTheme { PetTranslatorApp(prefs) } }
            val audio = rule.activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            note("ROUND_BEGIN device=${android.os.Build.MODEL} media_volume=${audio.getStreamVolume(AudioManager.STREAM_MUSIC)}")
            rule.onNodeWithText("逗宠").performClick()
            selectPet(PetType.Cat)
            cats.forEach(::checkFullPreview)
            selectPet(PetType.Dog)
            dogs.forEach(::checkFullPreview)
            note("TEN_PREVIEWS_PASS")

            selectPet(PetType.Cat)
            val call = cats[0]
            val sit = cats[1]
            rule.onNodeWithTag("play_list").performScrollToIndex(3)
            val switching = begin("rapid_switch")
            clickPreview(call)
            waitForPlaying(call)
            clickPreview(call)
            clickPreview(sit)
            clickPreview(call)
            clickPreview(sit)
            waitForPlaying(sit)
            val switched = awaitRelease(switching, sit, mustComplete = true)
            assertEquals(switched.toString(), call.audioId, switched.first { it.kind == "started" }.id)
            assertTrue(switched.toString(), switched.indexOf(NativeEvent("released", call.audioId)) in
                0 until switched.indexOf(NativeEvent("started", sit.audioId)))
            assertSerial(switched)
            note("PASS rapid_switch")

            rule.onNodeWithTag("play_list").performScrollToIndex(2)
            checkStopped("pet_switch", call) { selectPet(PetType.Dog) }

            show(dogs.last().tag)
            checkStopped("tab_switch", dogs.last()) {
                rule.onNodeWithText("首页").performClick()
                rule.onNodeWithTag("home_actions").assertIsDisplayed()
            }
            rule.onNodeWithText("逗宠").performClick()
            selectPet(PetType.Cat)
            show(call.tag)
            checkStopped("voice_handoff", call) {
                show("fun_voice_start")
                rule.onNodeWithTag("fun_voice_start").performClick()
                rule.onNodeWithTag("voice_talk_page").assertIsDisplayed()
            }
            rule.onNodeWithTag("voice_talk_back").performClick()
            show(call.tag)
            checkStopped("background", call) {
                rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            }
            rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            note("ROUND_PASS")
        } finally {
            if (!grantedBefore) PrivacyGate.block()
        }
    }

    private fun checkFullPreview(preview: Preview) {
        show(preview.tag)
        val mark = begin("${preview.soundId} audioId=${preview.audioId}")
        clickPreview(preview)
        waitForPlaying(preview)
        waitForEndOfPreview(preview)
        val native = awaitRelease(mark, preview)
        assertEquals(native.toString(), listOf(preview.audioId), native.filter { it.kind == "started" }.map { it.id })
        assertEquals(native.toString(), listOf(preview.audioId), native.filter { it.kind == "sequence_completed" }.map { it.id })
        assertSerial(native)
        rule.onNodeWithText("音效资源将在下一阶段接入～").assertDoesNotExist()
        note("PASS ${preview.soundId} audioId=${preview.audioId} completed=true")
    }

    private fun checkStopped(label: String, clip: Preview, stop: () -> Unit) {
        val mark = begin("$label audioId=${clip.audioId}")
        clickPreview(clip)
        waitForPlaying(clip)
        stop()
        val native = awaitRelease(mark, clip)
        assertTrue(native.toString(), NativeEvent("started", clip.audioId) in native)
        assertFalse(native.toString(), NativeEvent("sequence_completed", clip.audioId) in native)
        assertSerial(native)
        note("PASS $label released=true")
    }

    private fun selectPet(pet: PetType) {
        val tag = "fun_pet_${pet.name}"
        show(tag)
        rule.onNodeWithTag(tag).performClick().assertIsSelected()
    }

    private fun show(tag: String) {
        rule.onNodeWithTag("play_list").performScrollToNode(hasTestTag(tag))
        rule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun clickPreview(preview: Preview) =
        rule.onNodeWithTag(preview.tag).assertIsDisplayed().assertIsEnabled().performClick()

    private fun isPlaying(preview: Preview) =
        rule.onAllNodes(hasTestTag(preview.tag) and playing).fetchSemanticsNodes().isNotEmpty()

    private fun waitForPlaying(preview: Preview) = rule.waitUntil(6_000) { isPlaying(preview) }

    private fun waitForEndOfPreview(preview: Preview) {
        val duration = PetSoundCatalog.byId.getValue(preview.audioId).durationMs
        rule.waitUntil(duration + 6_000) { !isPlaying(preview) }
    }

    private fun awaitRelease(mark: String, preview: Preview, mustComplete: Boolean = false): List<NativeEvent> {
        var native = emptyList<NativeEvent>()
        val timeout = if (mustComplete) PetSoundCatalog.byId.getValue(preview.audioId).durationMs + 6_000 else 4_000
        rule.waitUntil(timeout) {
            native = readNativeEvents(mark)
            val released = native.lastIndexOf(NativeEvent("released", preview.audioId))
            val completed = native.lastIndexOf(NativeEvent("sequence_completed", preview.audioId))
            released >= 0 && (!mustComplete || completed in 0 until released)
        }
        return native
    }

    private fun assertSerial(events: List<NativeEvent>) {
        var active: String? = null
        for (event in events) {
            when (event.kind) {
                "started" -> {
                    assertEquals(events.toString(), null, active)
                    active = event.id
                }
                "released" -> if (active != null) {
                    assertEquals(events.toString(), active, event.id)
                    active = null
                }
            }
        }
        assertEquals(events.toString(), null, active)
    }

    private fun begin(case: String) = "$runId BEGIN $case".also { Log.i(TAG, it) }
    private fun note(message: String) { Log.i(TAG, "$runId $message") }

    private fun readNativeEvents(mark: String): List<NativeEvent> {
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        val command = "logcat -d -v brief --pid=${Process.myPid()} -s $TAG:I VoiceConversionPlayback:I"
        val log = ParcelFileDescriptor.AutoCloseInputStream(shell.executeShellCommand(command))
            .bufferedReader().use { it.readText() }
        return log.lineSequence().dropWhile { !it.endsWith(mark) }.drop(1).mapNotNull { line ->
            if ("VoiceConversionPlayback" !in line) return@mapNotNull null
            nativeEvent.find(line)?.let { NativeEvent(it.groupValues[1], it.groupValues[2]) }
        }.toList()
    }

    private data class Preview(val soundId: String, val audioId: String) {
        val tag: String get() = "fun_preview_$soundId"
    }
    private data class NativeEvent(val kind: String, val id: String)

    private companion object {
        const val TAG = "B324Fun"
        val nativeEvent = Regex("""\b(started|sequence_completed|released) ids=\[([^\]]+)]""")
    }
}
