package com.example.pettranslator.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.pettranslator.domain.PetPreferences
import com.example.pettranslator.domain.PetType
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.emptyPreferences

private val Context.petDataStore by preferencesDataStore("pet_preferences")
class PetPreferencesRepository(context: Context) : PetPreferences {
    private val store = context.applicationContext.petDataStore
    private val key = stringPreferencesKey("pet_type")
    override val petType = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { preferences -> PetType.entries.firstOrNull { it.name == preferences[key] } ?: PetType.Cat }
    override suspend fun setPetType(type: PetType) { store.edit { it[key] = type.name } }
}
