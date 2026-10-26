package com.example.pettranslator.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pettranslator.core.*
import com.example.pettranslator.domain.*
import java.io.IOException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(val pet: PetType = PetType.Cat, val scene: PetScene = PetScene.Unknown, val ready: Boolean = false)
sealed interface HomeEffect {
    data class Message(val text: String) : HomeEffect
    data class OpenRecording(val pet: PetType, val scene: PetScene) : HomeEffect
}
class HomeViewModel(
    private val preferences: PetPreferences,
    private val access: FeatureAccessManager,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val scene = savedState.getStateFlow("scene", PetScene.Unknown.name)
    val uiState = combine(preferences.petType, scene) { pet, name ->
        HomeUiState(pet, PetScene.entries.firstOrNull { it.name == name } ?: PetScene.Unknown, true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())
    private val events = Channel<HomeEffect>(Channel.BUFFERED)
    val effects = events.receiveAsFlow()
    fun selectPet(pet: PetType) { viewModelScope.launch {
        try { preferences.setPetType(pet) }
        catch (_: IOException) { events.send(HomeEffect.Message("选择保存失败，请重试")) }
    } }
    fun selectScene(scene: PetScene) { savedState["scene"] = scene.name }
    fun startRecording() { viewModelScope.launch {
        if (access.canAccess(Feature.RecordingTranslation)) events.send(HomeEffect.OpenRecording(uiState.value.pet, uiState.value.scene))
        else events.send(HomeEffect.Message("此功能暂不可用"))
    } }
    fun humanVoice() { viewModelScope.launch {
        events.send(HomeEffect.Message(if (access.canAccess(Feature.HumanVoice)) "人声变宠物语开发中" else "此功能暂不可用"))
    } }
}
