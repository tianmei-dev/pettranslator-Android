package com.example.pettranslator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.pettranslator.data.PetPreferencesRepository
import com.example.pettranslator.ui.navigation.PetTranslatorApp
import com.example.pettranslator.ui.theme.PetTranslatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()), navigationBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()))
        val preferences = PetPreferencesRepository(applicationContext)
        setContent { PetTranslatorTheme { PetTranslatorApp(preferences) } }
    }
}
