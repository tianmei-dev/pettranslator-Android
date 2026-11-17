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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class VoiceTalkPhase { READY, STARTING, RECORDING, STOPPING, RECORDED, CONVERTING, RESULT, ERROR }

data class VoiceTalkUiState(
    val phase: VoiceTalkPhase = VoiceTalkPhase.READY,
    val elapsedMs: Long = 0,
    val levels: List<Float> = emptyList(),
    val message: String? = null,
    val petChanging: Boolean = false,
)

/** Real local capture only. B3.1 does not consume, convert, or retain the recorded PCM. */
class VoiceTalkViewModel(
    private val preferences: PetPreferences,
    capture: AudioCapture,
    private val featureAccessManager: FeatureAccessManager = DefaultFeatureAccessManager(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    audit: (String) -> Unit = {},
) : ViewModel() {
    private val capture = ExclusiveVoiceAudioCapture(capture, audit)
    private val actionLock = Any()
    private val state = MutableStateFlow(VoiceTalkUiState())
    val uiState: StateFlow<VoiceTalkUiState> = state.asStateFlow()
    private val pet = MutableStateFlow<PetType?>(null)
    /** Only the canonical preferences flow writes this value; there is no page-local default. */
    val selectedPet: StateFlow<PetType?> = pet.asStateFlow()
    private var inForeground = false
    private var cleared = false
    private var session: CaptureSession? = null
    private var petChange: Job? = null

    private class CaptureSession(val pet: PetType) {
        lateinit var job: Job
        var interrupted = false
        var terminal = VoiceTalkUiState()
    }

    init {
        viewModelScope.launch(dispatcher) {
            try {
                preferences.petType.collect { selected ->
                    synchronized(actionLock) { pet.value = selected }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                synchronized(actionLock) {
                    pet.value = null
                    val error = VoiceTalkUiState(VoiceTalkPhase.ERROR,
                        message = "读取宠物选择失败，请稍后重试", petChanging = state.value.petChanging)
                    cancelCaptureLocked(error)
                }
            }
        }
    }

    fun foreground(active: Boolean) {
        synchronized(actionLock) {
            if (cleared) return
            inForeground = active
            if (!active && session != null) cancelCaptureLocked(interruptedState())
        }
    }

    fun start() {
        val next = synchronized(actionLock) {
            if (cleared || !inForeground || session != null || petChange != null ||
                state.value.petChanging || state.value.phase !in startPhases) return
            val selected = selectedPet.value ?: return
            if (!PrivacyGate.isGranted()) {
                state.value = VoiceTalkUiState(VoiceTalkPhase.ERROR, message = "请先同意隐私政策")
                return
            }
            if (!featureAccessManager.canAccess(Feature.HumanVoice)) {
                state.value = VoiceTalkUiState(VoiceTalkPhase.ERROR, message = "此功能暂不可用")
                return
            }
            CaptureSession(selected).also { next ->
                state.value = VoiceTalkUiState(VoiceTalkPhase.STARTING)
                session = next
                next.job = viewModelScope.launch(dispatcher, start = CoroutineStart.LAZY) { record(next) }
                next.job.invokeOnCompletion {
                    // A cancelled lazy job also reaches here, without ever touching the delegate.
                    synchronized(actionLock) {
                        if (session === next) {
                            session = null
                            state.value = next.terminal.copy(petChanging = state.value.petChanging)
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
            if (!featureAccessManager.canAccess(Feature.HumanVoice)) {
                throw CaptureException("此功能暂不可用")
            }
            PrivacyGate.trackCurrentJob(currentCoroutineContext()[Job]!!).use {
                currentCoroutineContext().ensureActive()
                val audio = capture.record { frame -> onFrame(current, frame) }
                currentCoroutineContext().ensureActive()
                PrivacyGate.requireConsent()
                val terminal = when {
                    !isSupported(audio) -> VoiceTalkUiState(VoiceTalkPhase.ERROR,
                        message = "录音格式不可用，请重新录音")
                    audio.durationMs < MIN_DURATION_MS -> VoiceTalkUiState(VoiceTalkPhase.ERROR,
                        elapsedMs = audio.durationMs, message = "录音时间太短啦，请至少录制0.8秒后再试")
                    else -> VoiceTalkUiState(VoiceTalkPhase.RECORDED, elapsedMs = audio.durationMs,
                        message = "录音完成啦！趣味转换功能正在准备中～")
                }
                synchronized(actionLock) {
                    if (session === current && !current.interrupted && inForeground) {
                        current.terminal = terminal.copy(levels = state.value.levels)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            synchronized(actionLock) {
                if (!current.interrupted) current.terminal = interruptedState()
            }
            throw cancelled
        } catch (error: Exception) {
            synchronized(actionLock) {
                if (!current.interrupted) {
                    current.terminal = VoiceTalkUiState(VoiceTalkPhase.ERROR,
                        message = when {
                            !PrivacyGate.isGranted() -> "隐私授权已关闭，请同意隐私政策后重试"
                            error is CaptureException -> error.message ?: "录音失败，请稍后重试"
                            error is SecurityException -> "麦克风权限不可用，请在设置中允许"
                            else -> "录音失败，请稍后重试"
                        })
                }
            }
        }
        // ExclusiveVoiceAudioCapture always discards our raw file before record returns/throws.
        // Only the Job completion callback clears the session, so start/reset cannot overlap cleanup.
    }

    private fun onFrame(current: CaptureSession, frame: CaptureFrame) {
        synchronized(actionLock) {
            if (session !== current || current.interrupted || !inForeground || !PrivacyGate.isGranted() ||
                state.value.phase !in framePhases || frame.sampleCount <= 0 || frame.elapsedMs <= 0) return
            val level = if (frame.rms.isFinite()) frame.rms.coerceIn(0f, 1f) else 0f
            state.value = state.value.copy(phase = VoiceTalkPhase.RECORDING,
                elapsedMs = frame.elapsedMs.coerceIn(0L, MAX_DURATION_MS),
                levels = (state.value.levels + level).takeLast(40))
        }
    }

    fun stop() {
        synchronized(actionLock) {
            if (session == null || state.value.phase != VoiceTalkPhase.RECORDING) return
            state.value = state.value.copy(phase = VoiceTalkPhase.STOPPING)
            try { capture.stop() }
            catch (_: Exception) {
                cancelCaptureLocked(VoiceTalkUiState(VoiceTalkPhase.ERROR,
                    message = "结束录音失败，请重新开始"))
            }
        }
    }

    fun reset() {
        synchronized(actionLock) {
            if (!cleared) cancelCaptureLocked(VoiceTalkUiState())
        }
    }

    fun interrupt() {
        synchronized(actionLock) {
            if (session != null) cancelCaptureLocked(interruptedState())
        }
    }

    fun permissionDenied(permanent: Boolean) {
        synchronized(actionLock) {
            if (cleared) return
            cancelCaptureLocked(VoiceTalkUiState(VoiceTalkPhase.ERROR,
                message = if (permanent) "麦克风权限已关闭，请在系统设置中允许后再试"
                else "需要麦克风权限，才能录制你的声音"))
        }
    }

    fun selectPet(selected: PetType) {
        val change = synchronized(actionLock) {
            if (cleared || session != null || petChange != null ||
                state.value.phase !in startPhases || selectedPet.value == selected) return
            state.value = VoiceTalkUiState(petChanging = true)
            viewModelScope.launch(dispatcher, start = CoroutineStart.LAZY) {
                try {
                    currentCoroutineContext().ensureActive()
                    PrivacyGate.requireConsent()
                    preferences.setPetType(selected)
                    // setPetType returning does not imply that the shared flow has emitted yet.
                    selectedPet.first { it == selected }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    synchronized(actionLock) {
                        if (!cleared) state.value = VoiceTalkUiState(VoiceTalkPhase.ERROR,
                            message = "选择保存失败，请重试", petChanging = true)
                    }
                }
            }.also { next ->
                petChange = next
                next.invokeOnCompletion {
                    synchronized(actionLock) {
                        if (petChange === next) {
                            petChange = null
                            state.value = state.value.copy(petChanging = false)
                        }
                    }
                }
            }
        }
        change.start()
    }

    /** Navigation waits for this before removing the route or opening a different mic destination. */
    suspend fun leaveAndAwait() {
        val jobs = synchronized(actionLock) {
            inForeground = false
            val jobs = listOfNotNull(session?.job, petChange)
            cancelCaptureLocked(VoiceTalkUiState())
            petChange?.cancel()
            jobs
        }
        withContext(NonCancellable) { jobs.joinAll() }
    }

    override fun onCleared() {
        synchronized(actionLock) {
            cleared = true
            inForeground = false
            cancelCaptureLocked(VoiceTalkUiState())
            petChange?.cancel()
        }
        super.onCleared()
    }

    /** Call under actionLock. STOPPING stays visible until the previous Job has fully released. */
    private fun cancelCaptureLocked(terminal: VoiceTalkUiState) {
        val current = session
        if (current == null) {
            state.value = terminal.copy(petChanging = state.value.petChanging)
        } else {
            current.interrupted = true
            current.terminal = terminal
            state.value = state.value.copy(phase = VoiceTalkPhase.STOPPING, message = terminal.message)
            current.job.cancel()
        }
    }

    private fun interruptedState() = VoiceTalkUiState(VoiceTalkPhase.ERROR,
        message = if (PrivacyGate.isGranted()) "录音已中断，请重新开始"
        else "隐私授权已关闭，请同意隐私政策后重试")

    private fun isSupported(audio: RecordedAudio): Boolean = audio.sampleRate == 16_000 &&
        audio.channels == 1 && audio.format == "PCM_S16LE" && audio.sampleCount in 1L..160_000L &&
        audio.durationMs in 1L..MAX_DURATION_MS

    private companion object {
        const val MIN_DURATION_MS = 800L
        const val MAX_DURATION_MS = 10_000L
        val startPhases = setOf(VoiceTalkPhase.READY, VoiceTalkPhase.RECORDED, VoiceTalkPhase.ERROR)
        val framePhases = setOf(VoiceTalkPhase.STARTING, VoiceTalkPhase.RECORDING)
    }
}
