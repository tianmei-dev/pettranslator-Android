package com.example.pettranslator

import androidx.test.platform.app.InstrumentationRegistry
import com.example.pettranslator.data.PetPreferencesRepository
import com.example.pettranslator.domain.PetType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PetPreferencesTest {
    @Test fun lastChoiceCanBeReadFromNewRepository() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = PetPreferencesRepository(context)
        val previous = repository.petType.first()
        try {
            repository.setPetType(PetType.Dog)
            assertEquals(PetType.Dog, PetPreferencesRepository(context).petType.first())
            repository.setPetType(PetType.Cat)
            assertEquals(PetType.Cat, PetPreferencesRepository(context).petType.first())
        } finally { repository.setPetType(previous) }
    }
}
