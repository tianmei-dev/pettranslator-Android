package com.example.pettranslator.data.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import com.example.pettranslator.domain.recording.*
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.sqrt

class AndroidPcmRecorder(context: Context) : AudioCapture {
    private val app = context.applicationContext
    private val audioManager = app.getSystemService(AudioManager::class.java)
    private val running = AtomicBoolean(false)
    private enum class Command { RUN, STOP, CANCEL }
    private val command = AtomicReference(Command.CANCEL)
    private val output = AtomicReference<File?>(null)

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override suspend fun record(onFrame: (CaptureFrame) -> Unit): RecordedAudio = withContext(Dispatchers.IO) {
        check(running.compareAndSet(false, true)) { "A capture is already running" }
        var recorder: AudioRecord? = null
        var completed = false
        var file: File? = null
        try {
            ensureActive()
            command.set(Command.RUN)
            if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                throw CaptureException("请允许麦克风权限后再试一次")
            if (audioManager.mode != AudioManager.MODE_NORMAL || audioManager.activeRecordingConfigurations.isNotEmpty())
                throw CaptureException("麦克风正被其他应用或通话使用，请稍后重试")
            val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuffer <= 0) throw CaptureException("这台设备暂不支持所需录音格式")
            recorder = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(maxOf(minBuffer, 6400)).build()
            if (recorder.state != AudioRecord.STATE_INITIALIZED || recorder.sampleRate != RATE)
                throw CaptureException("麦克风初始化失败，请稍后重试")
            val directory = File(app.cacheDir, "pet_recordings").apply { mkdirs() }
            file = File.createTempFile("capture_", ".pcm", directory)
            output.getAndSet(file)?.delete()
            ensureActive()
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING)
                throw CaptureException("无法启动麦克风，请检查是否被占用")
            val started = SystemClock.elapsedRealtime()
            var lastData = started
            var count = 0L
            var totalSquares = 0.0
            var peak = 0f
            val samples = ShortArray(800)
            val bytes = ByteArray(samples.size * 2)
            val audioDigest = MessageDigest.getInstance("SHA-256")
            FileOutputStream(file).use { stream ->
                while (command.get() == Command.RUN && count < MAX_SAMPLES && SystemClock.elapsedRealtime() - started < MAX_MS) {
                    ensureActive()
                    if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                        throw CaptureException("麦克风权限已关闭，本次录音已取消")
                    if (audioManager.mode != AudioManager.MODE_NORMAL ||
                        (Build.VERSION.SDK_INT >= 29 && recorder.activeRecordingConfiguration?.isClientSilenced == true))
                        throw CaptureException("麦克风被中断或静音，本次录音已取消")
                    val read = recorder.read(samples, 0, minOf(samples.size.toLong(), MAX_SAMPLES - count).toInt(), AudioRecord.READ_NON_BLOCKING)
                    if (read < 0) throw CaptureException("声音采集被中断，请重新录音")
                    if (read == 0) {
                        if (SystemClock.elapsedRealtime() - lastData > 1500) throw CaptureException("暂时无法采集声音，请检查麦克风后重试")
                        delay(10)
                        continue
                    }
                    lastData = SystemClock.elapsedRealtime()
                    var squares = 0.0
                    var framePeak = 0f
                    for (index in 0 until read) {
                        val value = samples[index].toInt()
                        bytes[index * 2] = (value and 255).toByte()
                        bytes[index * 2 + 1] = (value shr 8).toByte()
                        squares += value.toDouble() * value
                        val magnitude = abs(value / 32768f)
                        peak = maxOf(peak, magnitude)
                        framePeak = maxOf(framePeak, magnitude)
                    }
                    stream.write(bytes, 0, read * 2)
                    audioDigest.update(bytes, 0, read * 2)
                    totalSquares += squares
                    count += read
                    onFrame(CaptureFrame(count, count * 1000 / RATE,
                        (sqrt(squares / read) / 32768).toFloat(), framePeak))
                    delay(20)
                }
            }
            ensureActive()
            if (command.get() == Command.CANCEL) throw CancellationException("Capture cancelled")
            if (count == 0L) throw CaptureException("没有采集到音频数据，请重试")
            completed = true
            RecordedAudio(file.absolutePath, recorder.sampleRate, count, count * 1000 / RATE,
                rms = (sqrt(totalSquares / count) / 32768).toFloat(), peak = peak,
                audioFingerprint = audioDigest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) })
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            throw CaptureException("麦克风权限不可用，请在设置中允许")
        } catch (error: IllegalStateException) {
            throw CaptureException("麦克风暂时不可用，请关闭其他录音应用后重试")
        } finally {
            try { if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() }
            catch (_: IllegalStateException) { /* Release still runs after an interrupted stop. */ }
            finally {
                try { recorder?.release() }
                finally {
                    if (!completed || command.get() == Command.CANCEL) file?.delete()
                    running.set(false)
                }
            }
        }
    }

    override fun stop() { command.compareAndSet(Command.RUN, Command.STOP) }
    override fun discard() { command.set(Command.CANCEL); output.get()?.delete() }
    companion object {
        const val RATE = 16000
        const val MAX_MS = 10000L
        const val MAX_SAMPLES = 160000L
    }
}
