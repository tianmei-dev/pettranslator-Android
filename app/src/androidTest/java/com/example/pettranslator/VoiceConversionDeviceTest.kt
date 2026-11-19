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
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pettranslator.audio.PetClipType
import com.example.pettranslator.audio.PetSoundCatalog
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

/** Exercises native audio playback with deterministic PCM as the input. */
@RunWith(AndroidJUnit4::class)
class VoiceConversionDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun catAndDogUseSharedAssetsAndReplayKeepsTheResult() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val folder = File(context.getExternalFilesDir(null), "b324-full-evidence").apply { mkdirs() }
        val log = File(folder, "device-results.txt")
        fun note(text: String) { log.appendText("${SystemClock.elapsedRealtime()} $text\n"); Log.i("B324Device", text) }
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
            assertEquals(PetSoundCatalog.assets, available)
            note("assets_readable=${available.map{it.audioId}}")
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
                // The feature consumes a fixture, so this test can leave the device's
                // real microphone permission and settings untouched.
                rule.runOnIdle { model.start() }
                waitFor { model.uiState.value.phase==VoiceTalkPhase.RECORDING }
                click("voice_talk_stop")
                waitFor { model.uiState.value.phase in setOf(VoiceTalkPhase.RESULT,VoiceTalkPhase.ERROR) }
                assertEquals(model.uiState.value.message,VoiceTalkPhase.RESULT,model.uiState.value.phase)
                val result=checkNotNull(model.uiState.value.result)
                assertEquals(pet,result.pet);assertTrue(result.items.all{it.asset.petType==pet})
                assertTrue(result.audioIds.all{it in available.map{a->a.audioId}})
                assertFalse("PCM must be gone before RESULT",capture.file?.exists()==true)
                note("fixture pet=$pet durationMs=$duration selected=${result.audioIds} raw_deleted=true")
                return result
            }
            fun completed() {
                waitFor(12_000) {
                    val state = model.uiState.value
                    !tracking.active && state.phase == VoiceTalkPhase.RESULT &&
                        state.playbackPositionMs >= state.playbackDurationMs && state.playbackDurationMs > 0
                }
            }
            val shortCat=recordFixture(PetType.Cat,1200,listOf(Burst(200,700,.12)))
            assertEquals(PetClipType.SINGLE, shortCat.items.single().asset.clipType);completed()
            note("Cat short auto play completed")

            val longCat=recordFixture(PetType.Cat,4500,listOf(Burst(100,900,.08),Burst(1200,2000,.2),Burst(2400,3700,.12)))
            assertEquals(PetClipType.CONTINUOUS, longCat.items.single().asset.clipType)
            waitFor { model.uiState.value.isPlaying && model.uiState.value.playbackPositionMs>100 }
            rule.onNodeWithTag("voice_talk_result").performScrollTo();screenshot("01-cat-result-fixture.png")
            click("voice_talk_play_pause");waitFor { !model.uiState.value.isPlaying }
            val frozen=model.uiState.value.playbackPositionMs
            SystemClock.sleep(180);assertTrue(kotlin.math.abs(model.uiState.value.playbackPositionMs-frozen)<120)
            click("voice_talk_play_pause");completed()
            note("V02 PASS long_cat complete_source; pause_resume native=true")

            val shortDog=recordFixture(PetType.Dog,1000,listOf(Burst(200,450,.14)))
            assertEquals(PetClipType.CONTINUOUS, shortDog.items.single().asset.clipType)
            assertTrue(shortDog.estimatedDurationMs <= 2_500)
            completed()
            note("Short dog used one short continuous clip: ${shortDog.audioIds}")

            val longDog=recordFixture(PetType.Dog,5000,listOf(Burst(150,1000,.1),Burst(1300,2200,.22),Burst(2600,4300,.15)))
            assertEquals(PetClipType.CONTINUOUS, longDog.items.single().asset.clipType)
            waitFor { model.uiState.value.isPlaying && model.uiState.value.playbackPositionMs>100 }
            rule.onNodeWithTag("voice_talk_result").performScrollTo();screenshot("02-dog-result-fixture.png")

            // Replay while active must release the old instance, then start the same result.
            val previousStarts=tracking.sequences.size
            rule.runOnIdle { model.replay() }
            waitFor { tracking.sequences.size > previousStarts && model.uiState.value.isPlaying }
            assertSame(longDog, model.uiState.value.result)
            assertEquals(longDog.audioIds, tracking.sequences[previousStarts].audioIds)
            note("Replayed identical dog audio IDs ${longDog.audioIds}")

            click("voice_talk_again")
            waitFor { model.uiState.value.phase==VoiceTalkPhase.READY && !tracking.active }
            note("Record again stopped native playback")
            click("voice_talk_back")
            waitFor { showPlay && !tracking.active }
            assertTrue(tracking.nativePlayingEvents.get()>0)
            note("DEVICE_ROUND_PASS nativeVoiceStarts=${tracking.sequences.size} completed=${tracking.completed.get()} released=${tracking.released.get()}")
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
        private val directory=File(context.cacheDir,"b324_test_fixtures").apply{mkdirs()}
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
