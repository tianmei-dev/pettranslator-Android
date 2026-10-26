package com.example.pettranslator

import android.Manifest
import android.media.AudioManager
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pettranslator.data.recording.AndroidPcmRecorder
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import androidx.test.core.app.ActivityScenario
import org.junit.Test

class AudioRecordDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    @Before fun permission() {
        wakeTestDevice()
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}")
            .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario?.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    @After fun closeActivity() { scenario?.close() }
    @Test fun realManualStopFileAndDiscard() = runBlocking {
        val recorder = AndroidPcmRecorder(context)
        val firstFrame = CompletableDeferred<Unit>()
        try {
            val task = async { recorder.record { firstFrame.complete(Unit) } }
            withTimeout(5000) { firstFrame.await() }; delay(350)
            val competing = AndroidPcmRecorder(context)
            try {
                val failure = runCatching { competing.record {} }.exceptionOrNull()
                assertTrue(failure is com.example.pettranslator.domain.recording.CaptureException)
                assertTrue(failure?.message.orEmpty().contains("使用"))
            } finally { competing.discard() }
            recorder.stop(); recorder.stop()
            val audio = withTimeout(3000) { task.await() }
            assertEquals(16000, audio.sampleRate); assertEquals(1, audio.channels)
            assertEquals("PCM_S16LE", audio.format)
            assertTrue(audio.sampleCount > 0); assertTrue(audio.sampleCount < 160000)
            assertEquals(audio.sampleCount * 2, File(audio.filePath).length())
            assertEquals(audio.sampleCount * 1000 / 16000, audio.durationMs)
            assertTrue(audio.rms in 0f..1f)
            assertFalse(context.getSystemService(AudioManager::class.java).activeRecordingConfigurations
                .any { it.clientAudioSessionId != 0 })
            recorder.discard(); assertFalse(File(audio.filePath).exists())
        } finally { recorder.discard() }
    }
    @Test fun realAutomaticLimitAndCancellation() = runBlocking {
        val recorder = AndroidPcmRecorder(context)
        try {
            val result = withTimeout(14000) { recorder.record {} }
            assertTrue(result.sampleCount in 128000..160000)
            assertTrue(result.durationMs in 8000..10000)
            assertEquals(result.sampleCount * 2, File(result.filePath).length())
            recorder.discard(); assertFalse(File(result.filePath).exists())
            val first = CompletableDeferred<Unit>()
            val task = launch { recorder.record { first.complete(Unit) } }
            withTimeout(4000) { first.await() }
            recorder.discard(); task.cancelAndJoin()
            assertTrue(File(context.cacheDir, "pet_recordings").listFiles().orEmpty().isEmpty())
        } finally { recorder.discard() }
    }
}
