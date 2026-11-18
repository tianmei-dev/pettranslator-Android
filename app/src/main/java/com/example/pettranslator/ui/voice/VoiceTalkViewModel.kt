package com.example.pettranslator.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pettranslator.core.DefaultFeatureAccessManager
import com.example.pettranslator.core.Feature
import com.example.pettranslator.core.FeatureAccessManager
import com.example.pettranslator.domain.PetPreferences
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.domain.recording.AudioCapture
import com.example.pettranslator.domain.recording.CaptureException
import com.example.pettranslator.domain.recording.CaptureFrame
import com.example.pettranslator.domain.recording.RecordedAudio
import com.example.pettranslator.privacy.PrivacyGate
import com.example.pettranslator.ui.voice.conversion.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class VoiceTalkPhase { READY, STARTING, RECORDING, STOPPING, RECORDED, CONVERTING, RESULT, ERROR }

data class VoiceTalkUiState(
    val phase: VoiceTalkPhase = VoiceTalkPhase.READY,
    val elapsedMs: Long = 0,
    val levels: List<Float> = emptyList(),
    val message: String? = null,
    val petChanging: Boolean = false,
    val result: VoicePlaybackSequence? = null,
    val playbackPositionMs: Long = 0,
    val playbackDurationMs: Long = 0,
    val isPlaying: Boolean = false,
)

/** A page-local capture, analysis and playback session; no raw samples enter UI state. */
class VoiceTalkViewModel(
    private val preferences: PetPreferences,
    capture: AudioCapture,
    private val featureAccessManager: FeatureAccessManager = DefaultFeatureAccessManager(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val audit: (String) -> Unit = {},
    private val analyzer: VoiceFeatureAnalyzer = PcmVoiceFeatureAnalyzer(),
    private val soundLibrary: VoiceSoundLibrary = VoiceSoundLibrary { emptyList() },
    private val player: VoiceSequencePlayer = UnavailableVoiceSequencePlayer,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val capture = ExclusiveVoiceAudioCapture(capture, audit)
    private val selector = VoiceSoundSelector()
    private val actionLock = Any()
    private val state = MutableStateFlow(VoiceTalkUiState())
    val uiState: StateFlow<VoiceTalkUiState> = state.asStateFlow()
    private val pet = MutableStateFlow<PetType?>(null)
    val selectedPet: StateFlow<PetType?> = pet.asStateFlow()
    private var inForeground = false
    private var cleared = false
    private var session: CaptureSession? = null
    private var playback: PlaybackSession? = null
    private var petChange: Job? = null
    private val previousIds = mutableMapOf<PetType, List<String>>()

    private class CaptureSession(val pet: PetType) {
        lateinit var job: Job
        var interrupted = false
        var terminal = VoiceTalkUiState()
    }
    private class PlaybackSession(val sequence: VoicePlaybackSequence) {
        lateinit var job: Job
        var override: VoiceTalkUiState? = null
        var terminal = VoiceTalkUiState()
    }

    init {
        viewModelScope.launch(dispatcher) {
            try {
                preferences.petType.collect { selected ->
                    synchronized(actionLock) {
                        val changed = pet.value != null && pet.value != selected
                        pet.value = selected
                        if (changed && (session != null || playback != null || state.value.result != null)) {
                            cancelWorkLocked(VoiceTalkUiState())
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                synchronized(actionLock) {
                    pet.value = null
                    cancelWorkLocked(errorState("读取宠物选择失败，请稍后重试"))
                }
            }
        }
    }

    fun foreground(active: Boolean) {
        synchronized(actionLock) {
            if (cleared) return
            inForeground = active
            if (!active) interruptLocked()
        }
    }

    fun start() {
        val next = synchronized(actionLock) {
            if (cleared || !inForeground || session != null || playback != null || petChange != null ||
                state.value.petChanging || state.value.phase !in startPhases) return
            val selected = pet.value ?: return
            if (!PrivacyGate.isGranted()) { state.value = errorState("请先同意隐私政策"); return }
            if (!featureAccessManager.canAccess(Feature.HumanVoice)) { state.value = errorState("此功能暂不可用"); return }
            CaptureSession(selected).also { current ->
                state.value = VoiceTalkUiState(VoiceTalkPhase.STARTING)
                session = current
                current.job = viewModelScope.launch(dispatcher, start = CoroutineStart.LAZY) { record(current) }
                current.job.invokeOnCompletion {
                    synchronized(actionLock) {
                        if (session === current) {
                            session = null
                            state.value = current.terminal.copy(petChanging = state.value.petChanging)
                            val result = state.value.result
                            if (!current.interrupted && !cleared && inForeground && PrivacyGate.isGranted() &&
                                result != null && pet.value == result.pet) startPlaybackLocked(result)
                        }
                    }
                }
            }
        }
        next.job.start()
    }

    private suspend fun record(current: CaptureSession) {
        try {
            PrivacyGate.requireConsent()
            if (!featureAccessManager.canAccess(Feature.HumanVoice)) throw CaptureException("此功能暂不可用")
            PrivacyGate.trackCurrentJob(currentCoroutineContext().job).use {
                val available = withContext(ioDispatcher) { soundLibrary.available() }
                val approved = ApprovedVoiceSounds.assets
                if (available.size != approved.size || available.map { it.id }.toSet() != approved.map { it.id }.toSet()) {
                    throw VoiceConversionException("转换音源尚未就绪，请使用包含开发音效的 Debug 版本")
                }
                val result = capture.recordAndUse({ frame -> onFrame(current, frame) }) { audio ->
                    currentCoroutineContext().ensureActive()
                    PrivacyGate.requireConsent()
                    if (!isSupported(audio)) throw CaptureException("录音格式不可用，请重新录音")
                    if (audio.durationMs < MIN_DURATION_MS) throw CaptureException("录音时间太短啦，请至少录制0.8秒后再试")
                    synchronized(actionLock) {
                        checkCurrent(current)
                        state.value = state.value.copy(phase = VoiceTalkPhase.CONVERTING, elapsedMs = audio.durationMs)
                    }
                    val features = withContext(ioDispatcher) { analyzer.analyze(audio) }
                    currentCoroutineContext().ensureActive()
                    PrivacyGate.requireConsent()
                    if (!features.valid) throw VoiceConversionException(features.invalidReason ?: "没有足够的声音，请在安静环境下说一小段话后重试")
                    val last = synchronized(actionLock) { previousIds[current.pet].orEmpty() }
                    val sequence = withContext(ioDispatcher) { selector.select(features, current.pet, available, last) }
                    synchronized(actionLock) { checkCurrent(current) }
                    report("analysis pet=${current.pet} totalMs=${features.totalDurationMs} activeMs=${features.activeDurationMs} segments=${features.activitySegmentCount} ids=${sequence.audioIds}")
                    sequence
                }
                // recordAndUse has now deleted this session's PCM and released its ownership.
                synchronized(actionLock) {
                    checkCurrent(current)
                    previousIds[current.pet] = result.audioIds
                    current.terminal = VoiceTalkUiState(VoiceTalkPhase.RESULT, elapsedMs = state.value.elapsedMs,
                        result = result, playbackDurationMs = result.estimatedDurationMs)
                }
            }
        } catch (cancelled: CancellationException) {
            synchronized(actionLock) { if (!current.interrupted) current.terminal = interruptedState() }
            throw cancelled
        } catch (error: Exception) {
            synchronized(actionLock) {
                if (!current.interrupted) current.terminal = errorState(errorMessage(error))
            }
        }
    }

    private fun checkCurrent(current: CaptureSession) {
        if (session !== current || current.interrupted || !inForeground || cleared || pet.value != current.pet) {
            throw CancellationException("Voice session no longer current")
        }
    }

    private fun onFrame(current: CaptureSession, frame: CaptureFrame) {
        synchronized(actionLock) {
            if (session !== current || current.interrupted || !inForeground || !PrivacyGate.isGranted() ||
                state.value.phase !in framePhases || frame.sampleCount <= 0 || frame.elapsedMs <= 0) return
            val level = if (frame.rms.isFinite()) frame.rms.coerceIn(0f, 1f) else 0f
            state.value = state.value.copy(phase = VoiceTalkPhase.RECORDING,
                elapsedMs = frame.elapsedMs.coerceIn(0L, MAX_DURATION_MS), levels = (state.value.levels + level).takeLast(40))
        }
    }

    fun stop() {
        synchronized(actionLock) {
            if (session == null || state.value.phase != VoiceTalkPhase.RECORDING) return
            state.value = state.value.copy(phase = VoiceTalkPhase.STOPPING)
            try { capture.stop() }
            catch (_: Exception) { cancelWorkLocked(errorState("结束录音失败，请重新开始")) }
        }
    }

    private fun startPlaybackLocked(sequence: VoicePlaybackSequence) {
        if (cleared || !inForeground || playback != null || session != null || pet.value != sequence.pet) return
        if (!PrivacyGate.isGranted()) { state.value = errorState("请先同意隐私政策"); return }
        if (!featureAccessManager.canAccess(Feature.HumanVoice)) { state.value = errorState("此功能暂不可用"); return }
        val current = PlaybackSession(sequence)
        playback = current
        state.value = state.value.copy(phase = VoiceTalkPhase.RESULT, result = sequence,
            playbackPositionMs = 0, playbackDurationMs = sequence.estimatedDurationMs, isPlaying = false)
        current.terminal = state.value
        current.job = viewModelScope.launch(dispatcher, start = CoroutineStart.LAZY) {
            try {
                PrivacyGate.trackCurrentJob(currentCoroutineContext().job).use {
                    player.play(sequence) { progress ->
                        synchronized(actionLock) {
                            if (playback === current && current.override == null && inForeground && !cleared &&
                                PrivacyGate.isGranted() && pet.value == sequence.pet) {
                                state.value = state.value.copy(playbackPositionMs = progress.positionMs,
                                    playbackDurationMs = progress.durationMs, isPlaying = progress.isPlaying)
                            }
                        }
                    }
                    synchronized(actionLock) {
                        current.terminal = state.value.copy(isPlaying = false,
                            playbackPositionMs = state.value.playbackDurationMs)
                    }
                    report("playback_completed pet=${sequence.pet} ids=${sequence.audioIds}")
                }
            } catch (cancelled: CancellationException) {
                synchronized(actionLock) {
                    if (current.override == null) current.terminal = if (PrivacyGate.isGranted())
                        state.value.copy(isPlaying = false) else errorState("隐私授权已关闭")
                }
                throw cancelled
            } catch (error: Exception) {
                synchronized(actionLock) { current.terminal = errorState(errorMessage(error)) }
            }
        }
        current.job.invokeOnCompletion {
            synchronized(actionLock) {
                if (playback === current) {
                    playback = null
                    val terminal = current.override ?: current.terminal
                    state.value = (if (terminal.result != null && pet.value != terminal.result.pet)
                        VoiceTalkUiState() else terminal).copy(petChanging = state.value.petChanging)
                }
            }
        }
        current.job.start()
    }

    fun replay() {
        val previous = synchronized(actionLock) {
            if (cleared || !inForeground || state.value.petChanging || state.value.phase != VoiceTalkPhase.RESULT) return
            val sequence = state.value.result ?: return
            val old = playback
            if (old == null) { startPlaybackLocked(sequence); return }
            cancelPlaybackLocked(state.value.copy(isPlaying = false, playbackPositionMs = 0))
            old to sequence
        }
        viewModelScope.launch(dispatcher) {
            previous.first.job.join()
            synchronized(actionLock) {
                if (!cleared && inForeground && state.value.phase == VoiceTalkPhase.RESULT &&
                    state.value.result === previous.second && pet.value == previous.second.pet) startPlaybackLocked(previous.second)
            }
        }
    }

    fun togglePlayback() {
        synchronized(actionLock) {
            if (cleared || !inForeground || !PrivacyGate.isGranted() || state.value.petChanging ||
                state.value.phase != VoiceTalkPhase.RESULT) return
            val sequence = state.value.result ?: return
            if (playback == null) { startPlaybackLocked(sequence); return }
            try { if (state.value.isPlaying) player.pause() else player.resume() }
            catch (error: Exception) { cancelPlaybackLocked(errorState(errorMessage(error))) }
        }
    }

    fun reset() { synchronized(actionLock) { if (!cleared) cancelWorkLocked(VoiceTalkUiState()) } }
    fun interrupt() { synchronized(actionLock) { if (!cleared) interruptLocked() } }
    private fun interruptLocked() {
        if (session != null) cancelWorkLocked(interruptedState())
        else if (playback != null && playback?.override == null) {
            cancelPlaybackLocked(state.value.copy(phase = VoiceTalkPhase.RESULT,
                isPlaying = false, playbackPositionMs = 0))
        }
    }

    fun permissionDenied(permanent: Boolean) {
        synchronized(actionLock) {
            if (!cleared) cancelWorkLocked(errorState(if (permanent) "麦克风权限已关闭，请在系统设置中允许后再试"
                else "需要麦克风权限，才能录制你的声音"))
        }
    }

    fun selectPet(selected: PetType) {
        val change = synchronized(actionLock) {
            if (cleared || session != null || petChange != null || state.value.phase !in selectionPhases || pet.value == selected) return
            val oldPlayback = playback?.job
            cancelWorkLocked(VoiceTalkUiState(petChanging = true))
            state.value = state.value.copy(petChanging = true)
            viewModelScope.launch(dispatcher, start = CoroutineStart.LAZY) {
                try {
                    oldPlayback?.join()
                    currentCoroutineContext().ensureActive()
                    PrivacyGate.requireConsent()
                    preferences.setPetType(selected)
                    selectedPet.first { it == selected }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { synchronized(actionLock) { if (!cleared) state.value = errorState("选择保存失败，请重试").copy(petChanging = true) } }
            }.also { next ->
                petChange = next
                next.invokeOnCompletion { synchronized(actionLock) {
                    if (petChange === next) { petChange = null; state.value = state.value.copy(petChanging = false) }
                } }
            }
        }
        change.start()
    }

    suspend fun leaveAndAwait() {
        val jobs = synchronized(actionLock) {
            inForeground = false
            val jobs = listOfNotNull(session?.job, playback?.job, petChange)
            cancelWorkLocked(VoiceTalkUiState())
            petChange?.cancel()
            jobs
        }
        withContext(NonCancellable) { jobs.joinAll() }
    }

    override fun onCleared() {
        synchronized(actionLock) {
            cleared = true; inForeground = false
            cancelWorkLocked(VoiceTalkUiState()); petChange?.cancel(); player.release()
        }
        super.onCleared()
    }

    private fun cancelWorkLocked(terminal: VoiceTalkUiState) {
        cancelPlaybackLocked(terminal)
        val current = session
        if (current == null) {
            if (playback == null) state.value = terminal.copy(petChanging = state.value.petChanging)
        } else {
            current.interrupted = true; current.terminal = terminal
            state.value = state.value.copy(phase = VoiceTalkPhase.STOPPING, result = null, isPlaying = false, message = terminal.message)
            current.job.cancel()
        }
    }

    private fun cancelPlaybackLocked(terminal: VoiceTalkUiState) {
        val current = playback ?: return
        current.override = terminal
        state.value = state.value.copy(phase = VoiceTalkPhase.STOPPING, isPlaying = false)
        player.stop()
        current.job.cancel()
    }

    private fun errorState(message: String) = VoiceTalkUiState(VoiceTalkPhase.ERROR, message = message)
    private fun interruptedState() = errorState(if (PrivacyGate.isGranted()) "录音已中断，请重新开始" else "隐私授权已关闭，请同意隐私政策后重试")
    private fun errorMessage(error: Exception): String = when {
        !PrivacyGate.isGranted() -> "隐私授权已关闭，请同意隐私政策后重试"
        error is CaptureException || error is VoiceConversionException -> error.message ?: "转换暂时不可用，请重新录音"
        error is SecurityException -> "麦克风权限不可用，请在设置中允许"
        else -> "声音处理或播放失败，请重新录音后重试"
    }
    private fun report(message: String) { runCatching { audit(message) } }
    private fun isSupported(audio: RecordedAudio): Boolean = audio.sampleRate == 16_000 && audio.channels == 1 &&
        audio.format == "PCM_S16LE" && audio.sampleCount in 1L..160_000L && audio.durationMs in 1L..MAX_DURATION_MS

    private companion object {
        const val MIN_DURATION_MS = 800L
        const val MAX_DURATION_MS = 10_000L
        val startPhases = setOf(VoiceTalkPhase.READY, VoiceTalkPhase.RECORDED, VoiceTalkPhase.ERROR)
        val selectionPhases = startPhases + VoiceTalkPhase.RESULT
        val framePhases = setOf(VoiceTalkPhase.STARTING, VoiceTalkPhase.RECORDING)
    }
}
