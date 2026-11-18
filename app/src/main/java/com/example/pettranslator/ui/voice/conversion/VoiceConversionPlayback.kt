package com.example.pettranslator.ui.voice.conversion

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.example.pettranslator.privacy.PrivacyGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class VoiceConversionException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class VoicePlaybackProgress(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
)

interface VoiceSequencePlayer {
    /** Returns only after this immutable sequence completes or all native resources are released. */
    suspend fun play(sequence: VoicePlaybackSequence, onProgress: (VoicePlaybackProgress) -> Unit)
    fun pause()
    fun resume()
    fun stop()
    fun release()
}

object UnavailableVoiceSequencePlayer : VoiceSequencePlayer {
    override suspend fun play(sequence: VoicePlaybackSequence, onProgress: (VoicePlaybackProgress) -> Unit) {
        throw VoiceConversionException("音源尚未就绪，请使用包含开发音效的Debug版本")
    }
    override fun pause() = Unit
    override fun resume() = Unit
    override fun stop() = Unit
    override fun release() = Unit
}

fun interface VoiceSoundLibrary {
    suspend fun available(): List<VoiceSoundAsset>
}

/** Main/Release also compiles: approved candidates exist only in the debug asset source set. */
class AssetVoiceSoundLibrary(context: Context) : VoiceSoundLibrary {
    private val assets = context.applicationContext.assets

    override suspend fun available(): List<VoiceSoundAsset> = withContext(Dispatchers.IO) {
        ApprovedVoiceSounds.assets.filter { asset ->
            currentCoroutineContext().ensureActive()
            runCatching { assets.openFd(asset.assetPath).use { it.length > 0 } }.getOrDefault(false)
        }
    }
}

/** A page-owned queue. Capture/analysis remains separate; cancellation owns the cleanup barrier. */
class AndroidVoiceSequencePlayer(context: Context) : VoiceSequencePlayer {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private var active: Session? = null

    private class Session(
        val token: Any,
        val job: Job,
        val sequence: VoicePlaybackSequence,
        val onProgress: (VoicePlaybackProgress) -> Unit,
    ) {
        var player: MediaPlayer? = null
        var prepared: CompletableDeferred<Unit>? = null
        var completed: CompletableDeferred<Unit>? = null
        var readyToStart = false
        var paused = false
        var completedMs = 0L
        var totalMs = sequence.estimatedDurationMs
        var gapElapsedMs = 0L
        var gapDurationMs = 0L
        var gapUpdatedAtMs = 0L
        var gapActive = false
        var lastPositionMs = 0L
        var focus: AudioFocusRequest? = null
        var focusGranted = false
        var failure: VoiceConversionException? = null
    }

    override suspend fun play(
        sequence: VoicePlaybackSequence,
        onProgress: (VoicePlaybackProgress) -> Unit,
    ): Unit = withContext(Dispatchers.Main.immediate) {
        coroutineScope {
            PrivacyGate.requireConsent()
            validate(sequence)
            val token = Any()
            if (!playbackMutex.tryLock(token)) throw VoiceConversionException("已有音效正在播放，请稍后重试")
            val session = Session(token, currentCoroutineContext()[Job]!!, sequence, onProgress)
            active = session
            try {
                PrivacyGate.trackCurrentJob(session.job).use {
                    requestFocus(session)
                    emit(session)
                    val polling = launch {
                        while (isActive) {
                            ensureUsable(session)
                            emit(session)
                            delay(80)
                        }
                    }
                    try {
                        for (item in sequence.items) {
                            ensureUsable(session)
                            playItem(session, item)
                            if (item.pauseAfterMs > 0) playGap(session, item.pauseAfterMs)
                        }
                        ensureUsable(session)
                        session.lastPositionMs = session.totalMs
                        session.onProgress(VoicePlaybackProgress(session.totalMs, session.totalMs, false))
                        audit("sequence_completed ids=${sequence.audioIds} durationMs=${session.totalMs}")
                    } finally {
                        polling.cancel()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: VoiceConversionException) {
                throw error
            } catch (error: Exception) {
                throw VoiceConversionException("播放未能完成，请重新播放或再录一次", error)
            } finally {
                releasePlayer(session)
                session.focus?.let { runCatching { manager.abandonAudioFocusRequest(it) } }
                session.focus = null
                session.focusGranted = false
                if (active === session) active = null
                playbackMutex.unlock(token)
                audit("released ids=${sequence.audioIds} positionMs=${session.lastPositionMs}")
            }
        }
    }

    private fun validate(sequence: VoicePlaybackSequence) {
        val approved = ApprovedVoiceSounds.assets.associateBy { it.id }
        if (sequence.items.size !in 1..2 || sequence.items.map { it.asset.id }.distinct().size != sequence.items.size ||
            sequence.items.any { it.asset.pet != sequence.pet || approved[it.asset.id] != it.asset || it.pauseAfterMs !in 0L..1_000L }) {
            throw VoiceConversionException("音效序列不可用，请重新录音")
        }
    }

    private fun requestFocus(session: Session) {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                if (active === session && change in setOf(AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)) {
                    audit("focus_lost ids=${session.sequence.audioIds} change=$change")
                    fail(session, VoiceConversionException("播放被其他音频打断，请点击重新播放"))
                }
            }, main)
            .build()
        session.focus = request
        val result = PrivacyGate.withConsent { manager.requestAudioFocus(request) }
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            throw VoiceConversionException("暂时无法获得音频播放权限，请稍后重试")
        }
        session.focusGranted = true
        ensureUsable(session)
        audit("focus_granted ids=${session.sequence.audioIds}")
    }

    private suspend fun playItem(session: Session, item: VoiceSequenceItem) {
        val player = MediaPlayer()
        val prepared = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        session.player = player
        session.prepared = prepared
        session.completed = completed
        session.readyToStart = false
        try {
            player.setAudioAttributes(attributes)
            player.setOnPreparedListener {
                if (active === session && session.player === it) prepared.complete(Unit)
            }
            player.setOnCompletionListener {
                if (active === session && session.player === it) completed.complete(Unit)
            }
            player.setOnErrorListener { native, _, _ ->
                if (active === session && session.player === native) {
                    fail(session, VoiceConversionException("音源无法播放，请重新播放或再录一次"))
                }
                true
            }
            try {
                app.assets.openFd(item.asset.assetPath).use { file ->
                    if (file.length <= 0) throw VoiceConversionException("转换音源尚未就绪")
                    player.setDataSource(file.fileDescriptor, file.startOffset, file.length)
                }
            } catch (error: Exception) {
                throw VoiceConversionException("转换音源缺失或不可读取，请使用包含开发音效的Debug版本", error)
            }
            PrivacyGate.withConsent { player.prepareAsync() }
            try {
                withTimeout(5_000) { prepared.await() }
            } catch (error: TimeoutCancellationException) {
                throw VoiceConversionException("音源加载超时，请点击重新播放", error)
            }
            ensureUsable(session)
            val actualDuration = player.duration.toLong()
            if (actualDuration <= 0) throw VoiceConversionException("音源时长无效，请重新录音")
            session.totalMs += actualDuration - item.asset.durationMs
            session.readyToStart = true
            audit("prepared id=${item.asset.id} durationMs=$actualDuration")
            if (!session.paused) startNative(session)
            completed.await()
            ensureUsable(session)
            session.completedMs += actualDuration
            session.lastPositionMs = session.completedMs
            audit("clip_completed id=${item.asset.id} positionMs=${session.completedMs}")
        } finally {
            releasePlayer(session)
        }
    }

    private suspend fun playGap(session: Session, durationMs: Long) {
        session.gapActive = true
        session.gapElapsedMs = 0
        session.gapDurationMs = durationMs
        session.gapUpdatedAtMs = SystemClock.elapsedRealtime()
        try {
            while (session.gapElapsedMs < durationMs) {
                ensureUsable(session)
                updateGap(session)
                emit(session)
                delay(25)
            }
            session.completedMs += durationMs
            session.lastPositionMs = session.completedMs
        } finally {
            session.gapElapsedMs = 0
            session.gapDurationMs = 0
            session.gapActive = false
        }
    }

    private fun updateGap(session: Session) {
        if (!session.gapActive) return
        val now = SystemClock.elapsedRealtime()
        if (!session.paused) session.gapElapsedMs =
            (session.gapElapsedMs + now - session.gapUpdatedAtMs).coerceAtMost(session.gapDurationMs)
        session.gapUpdatedAtMs = now
    }

    private fun startNative(session: Session) {
        ensureUsable(session)
        if (!session.readyToStart || session.paused) return
        session.player?.let { player ->
            PrivacyGate.withConsent { player.start() }
            audit("started ids=${session.sequence.audioIds} positionMs=${session.lastPositionMs}")
            emit(session)
        }
    }

    override fun pause() = onMain {
        val session = active ?: return@onMain
        runCatching {
            ensureUsable(session)
            updateGap(session)
            session.paused = true
            session.player?.let { if (session.readyToStart && it.isPlaying) it.pause() }
            emit(session)
            audit("paused ids=${session.sequence.audioIds} positionMs=${session.lastPositionMs}")
        }.onFailure { fail(session, VoiceConversionException("暂停播放失败，请重新播放", it)) }
    }

    override fun resume() = onMain {
        val session = active ?: return@onMain
        runCatching {
            ensureUsable(session)
            updateGap(session)
            session.paused = false
            startNative(session)
            emit(session)
            audit("resumed ids=${session.sequence.audioIds} positionMs=${session.lastPositionMs}")
        }.onFailure { fail(session, VoiceConversionException("恢复播放失败，请重新播放", it)) }
    }

    override fun stop() = onMain {
        active?.let { session ->
            audit("stop_requested ids=${session.sequence.audioIds} positionMs=${session.lastPositionMs}")
            session.job.cancel(CancellationException("Voice playback stopped"))
        }
    }

    override fun release() = stop()

    private fun ensureUsable(session: Session) {
        if (active !== session) throw CancellationException("Old voice playback session")
        session.job.ensureActive()
        session.failure?.let { throw it }
        PrivacyGate.requireConsent()
        if (!session.focusGranted) throw VoiceConversionException("音频焦点已失效，请重新播放")
    }

    private fun emit(session: Session) {
        if (active !== session || !session.job.isActive) return
        val native = session.player
        val localPosition = if (native != null && session.readyToStart) native.currentPosition.toLong() else 0L
        val position = (session.completedMs + localPosition + session.gapElapsedMs).coerceIn(0, session.totalMs)
        session.lastPositionMs = position
        val playing = !session.paused && (session.gapActive ||
            (native != null && session.readyToStart && native.isPlaying))
        session.onProgress(VoicePlaybackProgress(position, session.totalMs, playing))
    }

    private fun fail(session: Session, error: VoiceConversionException) {
        if (active !== session || session.failure != null) return
        session.failure = error
        session.prepared?.completeExceptionally(error)
        session.completed?.completeExceptionally(error)
        releasePlayer(session)
    }

    private fun releasePlayer(session: Session) {
        val player = session.player
        session.player = null
        session.readyToStart = false
        if (player != null) {
            runCatching { player.setOnPreparedListener(null) }
            runCatching { player.setOnCompletionListener(null) }
            runCatching { player.setOnErrorListener(null) }
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        session.prepared = null
        session.completed = null
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post { action() }
    }

    private fun audit(message: String) { Log.i("VoiceConversionPlayback", message) }

    private companion object {
        val playbackMutex = Mutex()
    }
}
