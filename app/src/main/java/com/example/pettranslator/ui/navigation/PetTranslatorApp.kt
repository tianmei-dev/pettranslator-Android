package com.example.pettranslator.ui.navigation

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.pettranslator.data.recording.AndroidPcmRecorder
import com.example.pettranslator.data.recognition.YamnetRuntime
import com.example.pettranslator.ui.recording.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.example.pettranslator.core.DefaultFeatureAccessManager
import com.example.pettranslator.core.FeatureAccessManager
import com.example.pettranslator.core.Feature
import com.example.pettranslator.privacy.PrivacyGate
import com.example.pettranslator.domain.*
import com.example.pettranslator.ui.home.*
import com.example.pettranslator.ui.play.PlayScreen
import com.example.pettranslator.ui.voice.VoiceTalkRoute
import com.example.pettranslator.ui.voice.VoiceTalkViewModel
import com.example.pettranslator.ui.voice.conversion.AndroidVoiceSequencePlayer
import com.example.pettranslator.ui.voice.conversion.AssetVoiceSoundLibrary
import com.example.pettranslator.ui.voice.conversion.PcmVoiceFeatureAnalyzer
import com.example.pettranslator.ui.profile.ProfileScreen
import com.example.pettranslator.ui.profile.ProfileRoutes
import com.example.pettranslator.ui.profile.PermissionDetailsScreen
import com.example.pettranslator.ui.profile.LegalDocumentScreen
import com.example.pettranslator.ui.profile.AboutScreen
import com.example.pettranslator.ui.profile.ProfileSettingsRoutes
import com.example.pettranslator.ui.theme.*

/** Injectable recording entry; the default opens the full-screen recording destination. */
fun interface RecordingEntry { suspend fun open(pet: PetType, scene: PetScene) }
private const val VoiceTalkDestination = "voice-talk"
private enum class Tab(val route: String, val title: String, val icon: ImageVector) {
    Home("home", "首页", Icons.Default.Home), Play("play", "逗宠", Icons.Default.Favorite), Profile("profile", "我的", Icons.Default.Person)
}
@Composable fun PetTranslatorApp(preferences: PetPreferences, recordingEntry: RecordingEntry? = null,
    onRevokeConsent: () -> Unit = {}) {
    val controller = rememberNavController()
    val entry by controller.currentBackStackEntryAsState()
    val snackbar = remember { SnackbarHostState() }
    val recording = entry?.destination?.route?.startsWith("recording/") == true
    val voiceTalk = entry?.destination?.route == VoiceTalkDestination
    val profileDetail = entry?.destination?.route == ProfileRoutes.Permissions ||
        entry?.destination?.route == ProfileRoutes.Document || entry?.destination?.route == ProfileSettingsRoutes.About
    val appContext = LocalContext.current.applicationContext
    val model: HomeViewModel = viewModel(factory = viewModelFactory { initializer {
        HomeViewModel(preferences, FeatureAccessManager { feature ->
            PrivacyGate.isGranted() && DefaultFeatureAccessManager().canAccess(feature)
        }, createSavedStateHandle())
    } })
    val state by model.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(model, recordingEntry) {
        model.effects.collect { effect -> when (effect) {
            is HomeEffect.Message -> snackbar.showSnackbar(effect.text)
            is HomeEffect.OpenRecording -> if (!PrivacyGate.isGranted()) Unit else if (recordingEntry != null) recordingEntry.open(effect.pet, effect.scene) else if (controller.currentDestination?.route == Tab.Home.route) {
                val destination: RecordingEntry = RecordingEntry { pet, scene ->
                    controller.navigate("recording/${pet.name}/${scene.name}") { launchSingleTop = true }
                }
                destination.open(effect.pet, effect.scene)
            }
        } }
    }
    Scaffold(containerColor = Blush, contentWindowInsets = if (recording || voiceTalk || profileDetail) WindowInsets.safeDrawing else WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal), snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (!recording && !voiceTalk && !profileDetail) Column {
            if (entry?.destination?.route == Tab.Home.route) HomeRecordActionBar(state.ready, model::startRecording)
            Surface(color = Color.White) {
            Row(Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .fillMaxWidth().height(58.dp)) {
                Tab.entries.forEach { tab ->
                    val selected = entry?.destination?.route == tab.route
                    val tint = if (selected) Pink else Muted
                    Column(Modifier.weight(1f).fillMaxHeight().selectable(selected, role = Role.Tab, onClick = {
                    controller.navigate(tab.route) {
                        popUpTo(controller.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    }), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                        Box(Modifier.size(width = 64.dp, height = 28.dp)
                            .background(if (selected) Blush else Color.Transparent, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                            Icon(tab.icon, null, tint = tint, modifier = Modifier.size(24.dp))
                        }
                        Text(tab.title, color = tint, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
            }
        }
    }) { padding ->
        NavHost(controller, startDestination = Tab.Home.route, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable(Tab.Home.route) { HomeScreen(state, model::selectPet, model::selectScene) }
            composable("recording/{pet}/{scene}") { recordingEntry ->
                val pet = PetType.entries.firstOrNull { it.name == recordingEntry.arguments?.getString("pet") } ?: PetType.Cat
                val scene = PetScene.entries.firstOrNull { it.name == recordingEntry.arguments?.getString("scene") } ?: PetScene.Unknown
                val recordingModel: RecordingViewModel = viewModel(viewModelStoreOwner = recordingEntry,
                    factory = viewModelFactory { initializer {
                        RecordingViewModel(pet, scene, AndroidPcmRecorder(appContext), YamnetRuntime.get(appContext))
                    } })
                RecordingRoute(recordingModel, onBack = { controller.popBackStack() }, onHome = {
                    controller.popBackStack(Tab.Home.route, inclusive = false)
                })
            }
            composable(Tab.Play.route) {
                PlayScreen(onVoiceTalk = {
                    if (PrivacyGate.isGranted() && DefaultFeatureAccessManager().canAccess(Feature.HumanVoice)) {
                        controller.navigate(VoiceTalkDestination) { launchSingleTop = true }
                    }
                })
            }
            composable(VoiceTalkDestination) { voiceEntry ->
                val voiceModel: VoiceTalkViewModel = viewModel(viewModelStoreOwner = voiceEntry,
                    factory = viewModelFactory { initializer {
                        VoiceTalkViewModel(preferences, AndroidPcmRecorder(appContext),
                            audit = { Log.i("VoiceTalkCapture", it) },
                            analyzer = PcmVoiceFeatureAnalyzer(),
                            soundLibrary = AssetVoiceSoundLibrary(appContext),
                            player = AndroidVoiceSequencePlayer(appContext))
                    } })
                VoiceTalkRoute(voiceModel, onBack = { controller.popBackStack() })
            }
            composable(Tab.Profile.route) {
                ProfileScreen(onPermissions = { controller.navigate(ProfileRoutes.Permissions) { launchSingleTop = true } },
                    onDocument = { document -> controller.navigate(ProfileRoutes.document(document)) { launchSingleTop = true } },
                    onRevokeConsent = onRevokeConsent,
                    onAbout = { controller.navigate(ProfileSettingsRoutes.About) { launchSingleTop = true } },
                    onBack = { controller.popBackStack() })
            }
            composable(ProfileRoutes.Permissions) { PermissionDetailsScreen { controller.popBackStack() } }
            composable(ProfileSettingsRoutes.About) { AboutScreen { controller.popBackStack() } }
            composable(ProfileRoutes.Document) { documentEntry ->
                LegalDocumentScreen(documentEntry.arguments?.getString("documentId")) { controller.popBackStack() }
            }
        }
    }
}
