package com.example.pettranslator.data.recognition

import android.content.Context
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

internal const val MODEL_RATE = 16000
internal const val WINDOW_SIZE = 15600
internal const val WINDOW_HOP = 7680
internal const val MAX_SAMPLES = 160000

internal class YamnetEngine(private val context: Context) : AutoCloseable {
    companion object {
        const val VERSION = "google/yamnet/tflite/classification-tflite/1"
        const val SHA256 = "10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de"
    }

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()

    fun load() {
        if (interpreter != null) return
        val model = context.assets.open("yamnet.tflite").use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(model).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        check(model.size == 4_126_810 && digest == SHA256) { "YAMNet 模型校验失败(size=${model.size}, sha256=$digest)" }
        val manifest = context.assets.open("yamnet-model-manifest.json").bufferedReader().use { JSONObject(it.readText()) }
        check(manifest.optString("sha256") == digest && manifest.optInt("version") == 1) { "YAMNet 模型清单不匹配" }
        labels = context.assets.open("yamnet_label_list.txt").bufferedReader().use { it.readLines() }
        val expected = mapOf(0 to "Speech", 69 to "Dog", 70 to "Bark", 75 to "Whimper (dog)",
            76 to "Cat", 77 to "Purr", 78 to "Meow", 79 to "Hiss", 80 to "Caterwaul", 494 to "Silence")
        check(labels.size == 521 && labels.toSet().size == 521 && expected.all { labels[it.key] == it.value }) {
            "YAMNet 标签文件校验失败"
        }
        val modelBuffer = ByteBuffer.allocateDirect(model.size).order(ByteOrder.nativeOrder()).apply { put(model); rewind() }
        val candidate = Interpreter(modelBuffer, Interpreter.Options().setNumThreads(1).setUseNNAPI(false).setUseXNNPACK(false))
        try {
            candidate.allocateTensors()
            check(candidate.inputTensorCount == 1 && candidate.outputTensorCount == 1)
            check(candidate.getInputTensor(0).dataType() == DataType.FLOAT32 &&
                candidate.getInputTensor(0).shape().contentEquals(intArrayOf(WINDOW_SIZE)))
            check(candidate.getOutputTensor(0).dataType() == DataType.FLOAT32 &&
                candidate.getOutputTensor(0).shape().contentEquals(intArrayOf(1, 521)))
            interpreter = candidate
        } catch (failure: Throwable) {
            candidate.close()
            throw failure
        }
    }

    /** Executes only on the repository's single background executor. */
    fun classify(audioFile: File, sampleRate: Int, sampleCount: Long, cancelled: () -> Boolean): RawScores {
        require(sampleRate == MODEL_RATE && sampleCount in 1..MAX_SAMPLES.toLong()) { "录音格式或时长无效" }
        val expectedBytes = sampleCount * 2L
        require(audioFile.isFile && audioFile.length() == expectedBytes && expectedBytes <= MAX_SAMPLES * 2L) { "录音文件损坏或为空" }
        val sampleTotal = sampleCount.toInt()
        val samples = FloatArray(sampleTotal)
        audioFile.inputStream().buffered().use { input ->
            val bytes = ByteArray(8192)
            var sampleIndex = 0
            var pending = -1
            while (sampleIndex < sampleTotal) {
                if (cancelled()) throw InterruptedException("分析已取消")
                val read = input.read(bytes)
                if (read < 0) break
                var index = 0
                if (pending >= 0 && read > 0) {
                    samples[sampleIndex++] = (((bytes[0].toInt() shl 8) or (pending and 0xff)).toShort() / 32768f)
                    pending = -1
                    index = 1
                }
                while (index + 1 < read && sampleIndex < sampleTotal) {
                    samples[sampleIndex++] = (((bytes[index + 1].toInt() shl 8) or (bytes[index].toInt() and 0xff)).toShort() / 32768f)
                    index += 2
                }
                if (index < read) pending = bytes[index].toInt()
            }
            check(sampleIndex == sampleTotal && pending < 0) { "录音 PCM 数据不完整" }
        }
        val interpreter = this.interpreter ?: error("YAMNet 尚未加载")
        val starts = windowStarts(sampleTotal)
        val perWindow = ArrayList<FloatArray>(starts.size)
        val input = ByteBuffer.allocateDirect(WINDOW_SIZE * 4).order(ByteOrder.nativeOrder())
        val output = arrayOf(FloatArray(521))
        starts.forEach { start ->
            if (cancelled()) throw InterruptedException("分析已取消")
            input.clear()
            repeat(WINDOW_SIZE) { index -> input.putFloat(samples.getOrElse(start + index) { 0f }) }
            input.rewind()
            interpreter.run(input, output)
            val row = output[0].copyOf()
            check(row.all { it.isFinite() && it >= -0.0001f && it <= 1.0001f }) { "模型返回异常分数" }
            perWindow += row
        }
        val mean = FloatArray(521) { classIndex -> perWindow.sumOf { it[classIndex].toDouble() }.div(perWindow.size).toFloat() }
        fun group(indices: IntRange) = indices.maxOf { mean[it] }
        val cat = group(76..80)
        val dog = group(69..75)
        val other = (0..520).asSequence().filter { it !in 67..80 && it != 117 }.maxOf { mean[it] }
        val primaryIndex = mean.indices.maxByOrNull { mean[it] }!!
        var squared = 0.0
        var clips = 0
        samples.forEach { sample -> squared += sample.toDouble() * sample; if (abs(sample) >= .999f) clips++ }
        val rms = sqrt(squared / samples.size)
        var silentFrames = 0
        var frames = 0
        val frameSize = MODEL_RATE / 100
        var offset = 0
        while (offset < samples.size) {
            val end = minOf(samples.size, offset + frameSize)
            var frameSq = 0.0
            for (i in offset until end) frameSq += samples[i].toDouble() * samples[i]
            val frameRms = sqrt(frameSq / (end - offset))
            if (frameRms < 0.00316227766) silentFrames++ // -50 dBFS, diagnostic only
            frames++
            offset = end
        }
        val db = 20.0 * log10(max(rms, 1e-12))
        return RawScores(mean, perWindow, labels, cat, dog, other, primaryIndex,
            sampleTotal.toLong(), (sampleTotal * 1000L / MODEL_RATE), db,
            clips.toDouble() / sampleTotal, silentFrames.toDouble() / frames.coerceAtLeast(1))
    }

    override fun close() { interpreter?.close(); interpreter = null }

    private fun windowStarts(size: Int): List<Int> {
        if (size <= WINDOW_SIZE) return listOf(0)
        val starts = mutableListOf<Int>()
        var offset = 0
        while (offset + WINDOW_SIZE <= size) { starts += offset; offset += WINDOW_HOP }
        if (starts.last() != size - WINDOW_SIZE) starts += size - WINDOW_SIZE
        return starts
    }
}

internal data class RawScores(
    val means: FloatArray, val windows: List<FloatArray>, val labels: List<String>,
    val cat: Float, val dog: Float, val other: Float, val primaryIndex: Int,
    val sampleCount: Long, val durationMs: Long, val rmsDbfs: Double,
    val clippedFraction: Double, val silentFrameFraction: Double,
)
