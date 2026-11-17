package com.example.pettranslator

import android.os.Bundle
import android.os.Build
import android.view.ViewTreeObserver
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
import com.example.pettranslator.privacy.ConsentPhase
import com.example.pettranslator.startup.StartupArtwork
import com.example.pettranslator.startup.StartupGate

class MainActivity : ComponentActivity() {
    private var initialDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()), navigationBarStyle = SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()))
        val consent = AndroidPrivacyConsent.get(applicationContext)
        consent.attach(this)
        consent.initialize()
        // Keep the system starting window until the existing asynchronous consent read finishes.
        val decor = window.decorView
        val initialDraw = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (consent.state.value.phase == ConsentPhase.Loading) return false
                decor.viewTreeObserver.removeOnPreDrawListener(this)
                initialDrawListener = null
                return true
            }
        }
        initialDrawListener = initialDraw
        decor.viewTreeObserver.addOnPreDrawListener(initialDraw)
        if (Build.VERSION.SDK_INT >= 31) {
            splashScreen.setOnExitAnimationListener { provider -> provider.remove() }
        }
        setContent {
            val consentState by consent.state.collectAsStateWithLifecycle()
            PetTranslatorTheme {
                PrivacyConsentHost(consentState, onAgree = consent::agree, onDecline = consent::declineAndExit,
                    onRetryRevocation = consent::revoke, onExit = consent::exitWithoutConsent,
                    startupBackground = { StartupArtwork(showEntering = false) }) {
                    StartupGate {
                        val preferences = androidx.compose.runtime.remember { PetPreferencesRepository(applicationContext) }
                        PetTranslatorApp(preferences, onRevokeConsent = consent::revoke)
                    }
                }
            }
        }
    }
    override fun onDestroy() {
        initialDrawListener?.let { listener ->
            val observer = window.decorView.viewTreeObserver
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
        }
        initialDrawListener = null
        super.onDestroy()
    }
}
