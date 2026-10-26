package com.example.pettranslator

import androidx.lifecycle.SavedStateHandle
import com.example.pettranslator.core.*
import com.example.pettranslator.domain.*
import com.example.pettranslator.ui.home.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private class MemoryPreferences : PetPreferences {
        override val petType = MutableStateFlow(PetType.Cat)
        override suspend fun setPetType(type: PetType) { petType.value = type }
    }
    @Test fun selectionRestorationAndRecordingPayload() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val preferences = MemoryPreferences()
            val handle = SavedStateHandle()
            val model = HomeViewModel(preferences, DefaultFeatureAccessManager(), handle)
            runCurrent()
            assertEquals(PetType.Cat, model.uiState.value.pet)
            assertEquals(PetScene.Unknown, model.uiState.value.scene)
            model.selectPet(PetType.Dog); model.selectScene(PetScene.Meal)
            runCurrent()
            assertEquals(PetType.Dog, preferences.petType.value)
            model.startRecording()
            runCurrent()
            assertEquals(HomeEffect.OpenRecording(PetType.Dog, PetScene.Meal), model.effects.first())
            val restored = HomeViewModel(preferences, DefaultFeatureAccessManager(), handle)
            runCurrent()
            assertEquals(PetType.Dog, restored.uiState.value.pet)
            assertEquals(PetScene.Meal, restored.uiState.value.scene)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun deniedFeatureDoesNotOpenRecording() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val model = HomeViewModel(MemoryPreferences(), FeatureAccessManager { false }, SavedStateHandle())
            model.startRecording(); runCurrent()
            assertTrue(model.effects.first() is HomeEffect.Message)
        } finally { Dispatchers.resetMain() }
    }
}
