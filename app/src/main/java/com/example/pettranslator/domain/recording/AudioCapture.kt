package com.example.pettranslator.domain.recording

/** Headerless, signed 16-bit little-endian PCM in app-private cache. No content validity claim. */
data class RecordedAudio(
    val filePath: String,
    val sampleRate: Int,
    val sampleCount: Long,
    val durationMs: Long,
    val format: String = "PCM_S16LE",
    val channels: Int = 1,
    val rms: Float,
    val peak: Float,
    /** SHA-256 of the original PCM bytes, computed while writing the private cache file. */
    val audioFingerprint: String? = null,
)
/** Read while the recording destination still owns the cache file; exit/rerecord invalidates it. */
data class RecordingInput(
    val audio: RecordedAudio,
    val pet: com.example.pettranslator.domain.PetType,
    val scene: com.example.pettranslator.domain.PetScene,
)
data class CaptureFrame(val sampleCount: Long, val elapsedMs: Long, val rms: Float, val peak: Float = rms)
interface AudioCapture {
    suspend fun record(onFrame: (CaptureFrame) -> Unit): RecordedAudio
    fun stop()
    /** Cancels capture and deletes its output, including a previously completed clip. */
    fun discard()
}
class CaptureException(message: String) : Exception(message)
