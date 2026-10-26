package com.example.pettranslator.ui.recording

import androidx.lifecycle.ViewModel
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.domain.PetScene
import com.example.pettranslator.domain.recording.*
import com.example.pettranslator.domain.recognition.*
import com.example.pettranslator.data.recognition.PetSoundRecognizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class RecordingPhase { READY, STARTING, RECORDING, STOPPING, ANALYZING, SUCCESS, UNCERTAIN, PET_MISMATCH, ANALYSIS_ERROR, ERROR }
data class RecordingUiState(
    val phase: RecordingPhase = RecordingPhase.READY,
    val elapsedMs: Long = 0,
    val levels: List<Float> = emptyList(),
    val peakLevels: List<Float> = emptyList(),
    val message: String? = null,
    val permissionBlocked: Boolean = false,
    val audio: RecordedAudio? = null,
    val recognition: PetRecognitionResult? = null,
    val rawRecognition: YamnetRawResult? = null,
)
/** Capture closes/releases the microphone before local inference begins. */
class RecordingViewModel(
    initialPet: PetType, val scene: PetScene, private val capture: AudioCapture,
    private val recognizer: PetSoundRecognizer,
) : ViewModel() {
    var pet: PetType = initialPet
        private set
    private val state = MutableStateFlow(RecordingUiState())
    val uiState = state.asStateFlow()
    private var task: Job? = null
    private var analysisTask: Job? = null
    @Volatile private var foreground = false
    @Volatile private var generation = 0

    fun foreground(active: Boolean) {
        foreground = active
        if (!active && state.value.phase in activePhases) cancel("录音已中断，请重新开始")
    }

    /** Composition can be replaced during Activity recreation; still cancel any live mic capture. */
    fun compositionDisposed() {
        foreground = false
        if (state.value.phase in activePhases) cancel("录音已中断，请重新开始")
    }
    fun start() {
        if (!foreground || task?.isActive == true || analysisTask?.isActive == true || state.value.phase in blockedStartPhases) return
        capture.discard()
        val token = ++generation
        state.value = RecordingUiState(phase = RecordingPhase.STARTING)
        task = viewModelScope.launch {
            var delivered = false
            try {
                val result = capture.record { frame ->
                    if (token == generation && foreground) state.update { old ->
                        if (token != generation || !foreground || old.phase !in setOf(RecordingPhase.STARTING, RecordingPhase.RECORDING)) old
                        else old.copy(phase = RecordingPhase.RECORDING,
                            elapsedMs = frame.elapsedMs,
                            levels = (old.levels + frame.rms).takeLast(40),
                            peakLevels = (old.peakLevels + frame.peak).takeLast(40))
                    }
                }
                if (token == generation && foreground) {
                    state.value = RecordingUiState(RecordingPhase.ANALYZING, result.durationMs, audio = result)
                    delivered = true
                    analyze(result, token)
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (token == generation) state.value = RecordingUiState(phase = RecordingPhase.ERROR,
                    message = (error as? CaptureException)?.message ?: "录音保存失败，请稍后重试")
            } finally {
                if (!delivered) capture.discard()
            }
        }
    }
    fun stop() {
        if (state.value.phase != RecordingPhase.RECORDING) return
        state.update { it.copy(phase = RecordingPhase.STOPPING) }
        capture.stop()
    }
    fun cancel(message: String? = null) {
        val token = ++generation
        capture.discard()
        val previous = task
        if (previous?.isActive == true) {
            state.value = RecordingUiState(phase = RecordingPhase.STOPPING, message = message)
            previous.invokeOnCompletion {
                if (generation == token) state.value = RecordingUiState(message = message)
            }
            previous.cancel()
        } else state.value = RecordingUiState(message = message)
    }
    fun permissionDenied(blocked: Boolean) {
        state.value = RecordingUiState(message = "需要麦克风权限，才能听见毛孩子的声音", permissionBlocked = blocked)
    }
    fun permissionAvailable() {
        if (state.value.permissionBlocked) state.update { it.copy(permissionBlocked = false, message = null) }
    }
    fun completedInput(): RecordingInput? = state.value.audio?.let { RecordingInput(it, pet, scene) }

    /** Re-evaluates cached, choice-independent model output; no second inference is run. */
    fun switchPetAndRejudge() {
        if (state.value.phase != RecordingPhase.PET_MISMATCH) return
        val raw = state.value.rawRecognition ?: return
        pet = if (pet == PetType.Cat) PetType.Dog else PetType.Cat
        applyDecision(raw, pet, generation)
    }

    fun retryAnalysis() {
        if (state.value.phase != RecordingPhase.ANALYSIS_ERROR || analysisTask?.isActive == true) return
        val audio = state.value.audio ?: return
        val token = generation
        state.value = state.value.copy(phase = RecordingPhase.ANALYZING, message = null, recognition = null)
        analyze(audio, token)
    }

    fun leave() {
        foreground = false
        generation++
        analysisTask?.cancel()
        capture.discard()
        val previous = task
        previous?.cancel()
        state.value = RecordingUiState()
    }
    override fun onCleared() { leave() }
    private val activePhases = setOf(RecordingPhase.STARTING, RecordingPhase.RECORDING, RecordingPhase.STOPPING)
    private val blockedStartPhases = activePhases + RecordingPhase.ANALYZING

    private fun analyze(audio: RecordedAudio, token: Int) {
        analysisTask?.cancel()
        analysisTask = viewModelScope.launch {
            try {
                val raw = recognizer.infer(audio)
                if (token == generation) {
                    state.update { it.copy(rawRecognition = raw) }
                    applyDecision(raw, pet, token)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("PetRecognition", "Local YAMNet analysis failed (${error.javaClass.simpleName})", error)
                if (token == generation) state.update {
                    it.copy(phase = RecordingPhase.ANALYSIS_ERROR, message = "声音分析失败，请重试", recognition = null)
                }
            }
        }
    }

    private fun applyDecision(raw: YamnetRawResult, selectedPet: PetType, token: Int) {
        if (token != generation) return
        val result = PetRecognitionPolicy.decide(raw, selectedPet, scene)
        val phase = when (result.status) {
            RecognitionStatus.SUCCESS -> RecordingPhase.SUCCESS
            RecognitionStatus.UNCERTAIN -> RecordingPhase.UNCERTAIN
            RecognitionStatus.PET_MISMATCH -> RecordingPhase.PET_MISMATCH
        }
        state.update { it.copy(phase = phase, recognition = result, rawRecognition = raw, message = null) }
    }
}
