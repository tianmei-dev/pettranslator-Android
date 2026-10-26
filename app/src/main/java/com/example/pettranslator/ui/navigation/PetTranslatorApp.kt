package com.example.pettranslator.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import com.example.pettranslator.domain.*
import com.example.pettranslator.ui.home.*
import com.example.pettranslator.ui.play.PlayScreen
import com.example.pettranslator.ui.profile.ProfileScreen
import com.example.pettranslator.ui.theme.*

/** Injectable recording entry; the default opens the full-screen recording destination. */
fun interface RecordingEntry { suspend fun open(pet: PetType, scene: PetScene) }
private enum class Tab(val route: String, val title: String, val icon: ImageVector) {
    Home("home", "首页", Icons.Default.Home), Play("play", "逗宠", Icons.Default.Favorite), Profile("profile", "我的", Icons.Default.Person)
}
@Composable fun PetTranslatorApp(preferences: PetPreferences, recordingEntry: RecordingEntry? = null) {
    val controller = rememberNavController()
    val entry by controller.currentBackStackEntryAsState()
    val snackbar = remember { SnackbarHostState() }
    val recording = entry?.destination?.route?.startsWith("recording/") == true
    val appContext = LocalContext.current.applicationContext
    val model: HomeViewModel = viewModel(factory = viewModelFactory { initializer {
        HomeViewModel(preferences, DefaultFeatureAccessManager(), createSavedStateHandle())
    } })
    val state by model.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(model, recordingEntry) {
        model.effects.collect { effect -> when (effect) {
            is HomeEffect.Message -> snackbar.showSnackbar(effect.text)
            is HomeEffect.OpenRecording -> if (recordingEntry != null) recordingEntry.open(effect.pet, effect.scene) else if (controller.currentDestination?.route == Tab.Home.route) {
                val destination: RecordingEntry = RecordingEntry { pet, scene ->
                    controller.navigate("recording/${pet.name}/${scene.name}") { launchSingleTop = true }
                }
                destination.open(effect.pet, effect.scene)
            }
        } }
    }
    Scaffold(containerColor = Blush, contentWindowInsets = if (recording) WindowInsets.safeDrawing else WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal), snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (!recording) NavigationBar(containerColor = Color.White, windowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)) {
            Tab.entries.forEach { tab -> NavigationBarItem(selected = entry?.destination?.route == tab.route, onClick = {
                controller.navigate(tab.route) {
                    popUpTo(controller.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }, icon = { Icon(tab.icon, null) }, label = { Text(tab.title) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Pink, selectedTextColor = Pink, indicatorColor = Blush, unselectedIconColor = Muted, unselectedTextColor = Muted)) }
        }
    }) { padding ->
        NavHost(controller, startDestination = Tab.Home.route, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable(Tab.Home.route) { HomeScreen(state, model::selectPet, model::selectScene, model::startRecording, model::humanVoice) }
            composable("recording/{pet}/{scene}") { recordingEntry ->
                val pet = PetType.entries.firstOrNull { it.name == recordingEntry.arguments?.getString("pet") } ?: PetType.Cat
                val scene = PetScene.entries.firstOrNull { it.name == recordingEntry.arguments?.getString("scene") } ?: PetScene.Unknown
                val recordingModel: RecordingViewModel = viewModel(viewModelStoreOwner = recordingEntry,
                    factory = viewModelFactory { initializer {
                        RecordingViewModel(pet, scene, AndroidPcmRecorder(appContext), YamnetRuntime.get(appContext))
                    } })
                RecordingRoute(recordingModel) { controller.popBackStack() }
            }
            composable(Tab.Play.route) { PlayScreen { controller.popBackStack() } }
            composable(Tab.Profile.route) { ProfileScreen { controller.popBackStack() } }
        }
    }
}
