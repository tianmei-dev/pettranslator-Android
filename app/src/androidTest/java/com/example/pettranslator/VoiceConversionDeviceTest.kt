package com.example.pettranslator

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pettranslator.data.recording.AndroidPcmRecorder
import com.example.pettranslator.domain.*
import com.example.pettranslator.domain.recording.*
import com.example.pettranslator.privacy.PrivacyGate
import com.example.pettranslator.ui.play.PlayScreen
import com.example.pettranslator.ui.theme.PetTranslatorTheme
import com.example.pettranslator.ui.voice.*
import com.example.pettranslator.ui.voice.conversion.*
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin

/** Device integration uses explicitly synthetic PCM; actual microphone check is separately labelled. */
@RunWith(AndroidJUnit4::class)
class VoiceConversionDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun deterministicPipelineNativePlaybackAndMicrophoneCleanupOneRound() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val folder = File(context.getExternalFilesDir(null), "b322-evidence").apply { mkdirs() }
        val log = File(folder, "device-results.txt")
        fun note(text: String) { log.appendText("${SystemClock.elapsedRealtime()} $text\n"); Log.i("B322Device", text) }
        fun click(tag: String) { rule.onNodeWithTag(tag).performScrollTo().assertIsEnabled().performClick() }
        fun waitFor(timeout: Long = 8_000, predicate: () -> Boolean) = rule.waitUntil(timeout, predicate)
        fun screenshot(name: String) {
            rule.waitForIdle()
            val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            try { File(folder,name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) } }
            finally { bitmap.recycle() }
            note("screenshot=$name input=deterministic_PCM_fixture_not_human_speech")
        }
        wakeTestDevice()
        PrivacyGate.allow()
        val prefs = MemoryPets()
        val capture = FixtureCapture(context)
        val tracking = TrackingPlayer(AndroidVoiceSequencePlayer(context))
        val model = VoiceTalkViewModel(prefs,capture,analyzer=PcmVoiceFeatureAnalyzer(),
            soundLibrary=AssetVoiceSoundLibrary(context),player=tracking,audit={note(it)})
        val store = ViewModelStore().apply { put("b322",model) }
        var showPlay by mutableStateOf(false)
        rule.setContent { PetTranslatorTheme {
            if (showPlay) PlayScreen(onVoiceTalk={showPlay=false})
            else VoiceTalkRoute(model,onBack={showPlay=true})
        } }
        try {
            note("AUTOMATED_ONLY manualSpeech=false humanListening=false device=${android.os.Build.MODEL} sdk=${android.os.Build.VERSION.SDK_INT}")
            val musicVolume = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).getStreamVolume(AudioManager.STREAM_MUSIC)
            note("system_music_volume=$musicVolume volume_not_changed=true")
            val available=runBlocking { AssetVoiceSoundLibrary(context).available() }
            assertEquals(ApprovedVoiceSounds.assets,available)
            note("assets_readable=${available.map{it.id}}")
            waitFor { model.selectedPet.value!=null }
            fun ready() {
                if (model.uiState.value.phase!=VoiceTalkPhase.READY) {
                    if (model.uiState.value.phase==VoiceTalkPhase.ERROR) click("voice_talk_reset") else click("voice_talk_again")
                }
                waitFor { model.uiState.value.phase==VoiceTalkPhase.READY }
            }
            fun recordFixture(pet:PetType,duration:Int,bursts:List<Burst>):VoicePlaybackSequence {
                ready()
                if(model.selectedPet.value!=pet) {
                    click("voice_talk_pet_${pet.name}")
                    waitFor { model.selectedPet.value==pet && !model.uiState.value.petChanging }
                }
                capture.durationMs=duration;capture.bursts=bursts
                click("voice_talk_record")
                waitFor { model.uiState.value.phase==VoiceTalkPhase.RECORDING }
                click("voice_talk_stop")
                waitFor { model.uiState.value.phase in setOf(VoiceTalkPhase.RESULT,VoiceTalkPhase.ERROR) }
                assertEquals(model.uiState.value.message,VoiceTalkPhase.RESULT,model.uiState.value.phase)
                val result=checkNotNull(model.uiState.value.result)
                assertEquals(pet,result.pet);assertTrue(result.items.all{it.asset.pet==pet})
                assertTrue(result.audioIds.all{it in available.map{a->a.id}})
                assertFalse("PCM must be gone before RESULT",capture.file?.exists()==true)
                note("fixture pet=$pet durationMs=$duration selected=${result.audioIds} raw_deleted=true")
                return result
            }
            fun completed() { waitFor(9_000) { !tracking.active && model.uiState.value.phase==VoiceTalkPhase.RESULT } }
            val shortCat=recordFixture(PetType.Cat,1200,listOf(Burst(200,700,.12)))
            assertFalse(shortCat.items.single().asset.continuous);completed()
            val previousStarts=tracking.sequences.size
            click("voice_talk_replay");waitFor { tracking.sequences.size>previousStarts };completed()
            assertSame(shortCat,model.uiState.value.result)
            assertEquals(tracking.sequences[previousStarts-1].audioIds,tracking.sequences[previousStarts].audioIds)
            note("V01,V06,V07 PASS cat_short auto_play_completed replay_fixed=true")

            val longCat=recordFixture(PetType.Cat,4500,listOf(Burst(100,900,.08),Burst(1200,2000,.2),Burst(2400,3700,.12)))
            assertEquals(listOf("cat_fs_728095"),longCat.audioIds)
            waitFor { model.uiState.value.isPlaying && model.uiState.value.playbackPositionMs>100 }
            rule.onNodeWithTag("voice_talk_result").performScrollTo();screenshot("01-cat-result-fixture.png")
            click("voice_talk_play_pause");waitFor { !model.uiState.value.isPlaying }
            val frozen=model.uiState.value.playbackPositionMs
            SystemClock.sleep(180);assertTrue(kotlin.math.abs(model.uiState.value.playbackPositionMs-frozen)<120)
            click("voice_talk_play_pause");completed()
            note("V02 PASS long_cat complete_source; pause_resume native=true")

            val shortDog=recordFixture(PetType.Dog,1000,listOf(Burst(200,450,.14)))
            assertEquals(listOf("dog_fs_813120"),shortDog.audioIds);assertEquals(1,shortDog.items.size);completed()
            note("V03 PASS tiny_dog_once no_loop=true")
            val longDog=recordFixture(PetType.Dog,5000,listOf(Burst(150,1000,.1),Burst(1300,2200,.22),Burst(2600,4300,.15)))
            assertTrue(longDog.items.single().asset.continuous)
            assertTrue(longDog.audioIds.single() in listOf("dog_fs_9032","dog_fs_807270"))
            waitFor { model.uiState.value.isPlaying && model.uiState.value.playbackPositionMs>100 }
            rule.onNodeWithTag("voice_talk_result").performScrollTo();screenshot("02-dog-result-fixture.png")
            rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            val stopped=SystemClock.elapsedRealtime()+4_000
            while(tracking.active && SystemClock.elapsedRealtime()<stopped) SystemClock.sleep(20)
            assertFalse("Native playback should finish cleanup on background",tracking.active)
            rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            waitFor { model.uiState.value.phase==VoiceTalkPhase.RESULT }
            val stoppedStarts=tracking.sequences.size;SystemClock.sleep(180)
            assertEquals(stoppedStarts,tracking.sequences.size)
            click("voice_talk_replay");waitFor { model.uiState.value.isPlaying }
            click("voice_talk_pet_Cat")
            waitFor { model.selectedPet.value==PetType.Cat && model.uiState.value.phase==VoiceTalkPhase.READY }
            assertNull(model.uiState.value.result);assertFalse(tracking.active)
            note("V04,V08,V09,V12,V13 PASS dog_long background_release no_resume pet_change_invalidates")

            val startsBeforeSilence=tracking.sequences.size
            capture.durationMs=1200;capture.bursts=emptyList()
            click("voice_talk_record");waitFor { model.uiState.value.phase==VoiceTalkPhase.RECORDING };click("voice_talk_stop")
            waitFor { model.uiState.value.phase==VoiceTalkPhase.ERROR }
            assertNull(model.uiState.value.result);assertEquals(startsBeforeSilence,tracking.sequences.size)
            rule.onNodeWithTag("voice_talk_error").performScrollTo();screenshot("03-silence-error-fixture.png")
            note("V05 PASS silent_real_PCM_analysis no_generated_result=true")
            click("voice_talk_reset");waitFor { model.uiState.value.phase==VoiceTalkPhase.READY }

            // No generated input is sent to the microphone. This records uncontrolled room sound,
            // not an asserted human sentence; only physical capture/lease cleanup is checked.
            val native=ExclusiveVoiceAudioCapture(AndroidPcmRecorder(context)){note("hardware $it")}
            val hardware=runBlocking { withTimeout(7_000) {
                native.recordAndUse({frame->if(frame.elapsedMs>=1200)native.stop()}) { audio->
                    assertTrue(File(audio.filePath).isFile)
                    val features=PcmVoiceFeatureAnalyzer().analyze(audio)
                    note("HARDWARE_ONLY ambient_uncontrolled=true durationMs=${audio.durationMs} activeMs=${features.activeDurationMs} validEnergy=${features.valid} humanSpeechNotVerified=true")
                    audio.filePath
                }
            } }
            assertFalse(File(hardware).exists())
            note("hardware microphone capture_release_and_own_PCM_cleanup PASS; no_manual_speech_or_naturalness_claim")

            click("voice_talk_back");waitFor { showPlay }
            rule.onNodeWithText("趣味逗宠音效").assertIsDisplayed()
            rule.onNodeWithTag("fun_sound_cat_call").performScrollTo().performClick()
            rule.onNodeWithText("音效资源将在下一阶段接入～").assertIsDisplayed()
            rule.onNodeWithTag("fun_pet_Dog").performScrollTo().performClick()
            rule.onNodeWithTag("fun_sound_dog_call").performScrollTo().assertIsDisplayed()
            note("V14 PASS original_play_list_and_pending_behavior_unchanged")
            assertTrue(tracking.nativePlayingEvents.get()>0)
            note("DEVICE_ROUND_PASS realMediaPlayerStarts=${tracking.sequences.size} completed=${tracking.completed.get()} released=${tracking.released.get()} nativeProgressEvents=${tracking.nativePlayingEvents.get()} V10,V11=unit_tests")
        } catch(error:Throwable) {
            note("DEVICE_ROUND_FAIL ${error.javaClass.simpleName}: ${error.message}")
            throw error
        } finally {
            runBlocking { withContext(Dispatchers.Main) { model.leaveAndAwait();store.clear() } }
            capture.discard();PrivacyGate.block()
            note("cleanup complete; canonical user DataStore was not modified")
        }
    }

    private class MemoryPets:PetPreferences {
        override val petType=MutableStateFlow(PetType.Cat)
        override suspend fun setPetType(type:PetType){petType.value=type}
    }
    private data class Burst(val start:Int,val end:Int,val amplitude:Double)
    private class FixtureCapture(context:Context):AudioCapture {
        private val directory=File(context.cacheDir,"b322_test_fixtures").apply{mkdirs()}
        @Volatile var durationMs=1200
        @Volatile var bursts:List<Burst> = emptyList()
        @Volatile var file:File?=null
        @Volatile private var stopSignal:CompletableDeferred<Unit>?=null
        override suspend fun record(onFrame:(CaptureFrame)->Unit):RecordedAudio {
            val duration=durationMs;val plan=bursts;val count=duration*16
            val done=CompletableDeferred<Unit>();stopSignal=done
            val created=withContext(Dispatchers.IO) {
                val bytes=ByteArray(count*2)
                var squares=0.0;var peak=0f
                repeat(count){i->val ms=i/16;val amplitude=plan.firstOrNull{ms in it.start until it.end}?.amplitude?:0.0
                    val sample=(sin(2*PI*350*i/16000)*amplitude*32767).toInt()
                    squares+=sample.toDouble()*sample;peak=maxOf(peak,kotlin.math.abs(sample/32768f))
                    bytes[i*2]=(sample and 255).toByte();bytes[i*2+1]=(sample shr 8).toByte()}
                Triple(File.createTempFile("fixture_",".pcm",directory).also{it.writeBytes(bytes)},
                    (kotlin.math.sqrt(squares/count)/32768).toFloat(),peak)
            }
            file=created.first
            try {
                onFrame(CaptureFrame(count.toLong(),duration.toLong(),created.second,created.third));done.await()
                return RecordedAudio(created.first.absolutePath,16000,count.toLong(),duration.toLong(),rms=created.second,peak=created.third)
            } finally {stopSignal=null}
        }
        override fun stop(){stopSignal?.complete(Unit)}
        override fun discard(){stopSignal?.cancel();file?.delete()}
    }
    private class TrackingPlayer(private val delegate:VoiceSequencePlayer):VoiceSequencePlayer {
        val sequences=CopyOnWriteArrayList<VoicePlaybackSequence>()
        val completed=AtomicInteger();val released=AtomicInteger();val nativePlayingEvents=AtomicInteger()
        @Volatile var active=false
        override suspend fun play(sequence:VoicePlaybackSequence,onProgress:(VoicePlaybackProgress)->Unit){
            sequences+=sequence;active=true
            try{delegate.play(sequence){if(it.isPlaying)nativePlayingEvents.incrementAndGet();onProgress(it)};completed.incrementAndGet()}
            finally{active=false;released.incrementAndGet()}
        }
        override fun pause()=delegate.pause()
        override fun resume()=delegate.resume()
        override fun stop()=delegate.stop()
        override fun release()=delegate.release()
    }
}
