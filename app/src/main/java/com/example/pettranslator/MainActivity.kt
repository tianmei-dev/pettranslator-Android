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
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pettranslator.privacy.AndroidPrivacyConsent
import com.example.pettranslator.privacy.PrivacyConsentHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()), navigationBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()))
        val consent = AndroidPrivacyConsent.get(applicationContext)
        consent.attach(this)
        consent.initialize()
        setContent {
            val consentState by consent.state.collectAsStateWithLifecycle()
            PetTranslatorTheme {
                PrivacyConsentHost(consentState, onAgree = consent::agree, onDecline = consent::decline,
                    onRetryRevocation = consent::revoke, onExit = consent::exitWithoutConsent) {
                    val preferences = androidx.compose.runtime.remember { PetPreferencesRepository(applicationContext) }
                    PetTranslatorApp(preferences, onRevokeConsent = consent::revoke)
                }
            }
        }
    }
}
