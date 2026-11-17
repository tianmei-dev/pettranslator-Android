package com.example.pettranslator.ui.voice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pettranslator.R
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.privacy.PrivacyGate
import com.example.pettranslator.ui.theme.PetTranslatorTheme
import kotlinx.coroutines.launch

internal val VoiceInk = Color(0xFF57457E)
internal val VoiceMuted = Color(0xFF8A7DA3)
internal val VoicePurple = Color(0xFF8B47D5)
internal val VoicePink = Color(0xFFFA72C1)

/** The parent navigation Scaffold owns system insets. This route adds none. */
@Composable
fun VoiceTalkRoute(model: VoiceTalkViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val windowInfo = LocalWindowInfo.current
    val focused = windowInfo.isWindowFocused
    val state by model.uiState.collectAsStateWithLifecycle()
    val pet by model.selectedPet.collectAsStateWithLifecycle()
    val latestState by rememberUpdatedState(state)
    val latestPet by rememberUpdatedState(pet)
    val latestBack by rememberUpdatedState(onBack)
    val scope = rememberCoroutineScope()
    var exiting by remember { mutableStateOf(false) }
    var requestingPermission by remember { mutableStateOf(false) }
    var permissionClickPending by remember { mutableStateOf(false) }
    var pendingStartAfterGrant by remember { mutableStateOf(false) }
    var permanentDenial by rememberSaveable { mutableStateOf(false) }

    fun permissionGranted() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    fun startWhileResumed() {
        if (!exiting && latestPet != null && !latestState.petChanging && PrivacyGate.isGranted() &&
            permissionGranted() && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            windowInfo.isWindowFocused) {
            pendingStartAfterGrant = false
            permanentDenial = false
            model.foreground(true)
            model.start()
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        val requestedHere = permissionClickPending
        requestingPermission = false
        permissionClickPending = false
        if (requestedHere && !exiting) {
            if (allowed && PrivacyGate.isGranted()) {
                // Android may deliver the result just before ON_RESUME. Only this explicit
                // permission grant can carry a start across that boundary; ON_STOP cancels it.
                pendingStartAfterGrant = true
                startWhileResumed()
            } else if (!allowed) {
                pendingStartAfterGrant = false
                permanentDenial = context.findVoiceActivity()?.let {
                    !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
                } ?: false
                model.permissionDenied(permanentDenial)
            }
        }
    }

    LaunchedEffect(focused, pendingStartAfterGrant, owner, model) {
        val visible = focused && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        model.foreground(visible)
        if (visible && pendingStartAfterGrant) startWhileResumed()
    }

    DisposableEffect(owner, model) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    model.foreground(windowInfo.isWindowFocused)
                    if (pendingStartAfterGrant) startWhileResumed()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    model.foreground(false)
                    model.interrupt()
                }
                Lifecycle.Event.ON_STOP -> {
                    permissionClickPending = false
                    pendingStartAfterGrant = false
                    model.foreground(false)
                    model.interrupt()
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        model.foreground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && windowInfo.isWindowFocused)
        onDispose {
            permissionClickPending = false
            pendingStartAfterGrant = false
            owner.lifecycle.removeObserver(observer)
            model.foreground(false)
            model.interrupt()
        }
    }

    val leave: () -> Unit = {
        if (!exiting) {
            exiting = true
            permissionClickPending = false
            pendingStartAfterGrant = false
            scope.launch {
                model.leaveAndAwait()
                latestBack()
            }
        }
    }
    BackHandler(onBack = leave)

    VoiceTalkScreen(
        state = state.copy(petChanging = state.petChanging || requestingPermission || exiting),
        pet = pet,
        onPet = model::selectPet,
        onRecord = {
            if (!requestingPermission && !exiting && pet != null && !state.petChanging) {
                if (!PrivacyGate.isGranted()) {
                    Toast.makeText(context, "请先同意隐私政策", Toast.LENGTH_SHORT).show()
                } else if (permissionGranted()) {
                    startWhileResumed()
                } else if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && windowInfo.isWindowFocused) {
                    permissionClickPending = true
                    requestingPermission = true
                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        },
        onStop = model::stop,
        onReset = {
            if (!exiting) {
                permanentDenial = false
                model.reset()
            }
        },
        onBack = leave,
        onSettings = if (!permanentDenial) null else ({
            permissionClickPending = false
            pendingStartAfterGrant = false
            try {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: RuntimeException) {
                Toast.makeText(context, "请在系统设置中管理本应用的麦克风权限", Toast.LENGTH_SHORT).show()
            }
        }),
    )
}

private fun Context.findVoiceActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.takeUnless { it === this }?.findVoiceActivity()
    else -> null
}

/** A single scroll surface also keeps all controls reachable with large accessibility text. */
@Composable
fun VoiceTalkScreen(
    state: VoiceTalkUiState,
    pet: PetType?,
    onPet: (PetType) -> Unit,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onSettings: (() -> Unit)? = null,
) {
    val busy = state.phase in setOf(VoiceTalkPhase.STARTING, VoiceTalkPhase.RECORDING,
        VoiceTalkPhase.STOPPING, VoiceTalkPhase.CONVERTING)
    val canSelect = !busy && !state.petChanging && pet != null
    val ready = state.phase == VoiceTalkPhase.READY
    BoxWithConstraints(Modifier.fillMaxSize().testTag("voice_talk_page").background(
        Brush.horizontalGradient(listOf(Color(0xFFFFEDFA), Color(0xFFF5EAFF), Color(0xFFDEEAFE))))) {
        val compact = maxHeight < 660.dp
        VoiceClouds(Modifier.matchParentSize())
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxWidth()
            .verticalScroll(rememberScrollState()).testTag("voice_talk_scroll")
            // Keep the viewport as a minimum, so READY can fill spare space without
            // constraining natural content height on short screens or with large text.
            .heightIn(min = if (ready) maxHeight else 0.dp)
            .padding(top = 4.dp, bottom = if (ready) 20.dp else 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            VoiceTalkHero(onBack)
            VoicePetSelector(pet, canSelect, onPet, Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
            when (state.phase) {
                VoiceTalkPhase.READY -> {
                    Spacer(Modifier.weight(1f))
                    Column(Modifier.fillMaxWidth().testTag("voice_talk_ready_controls"),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        VoiceCaptureControl(state, enabled = pet != null && !state.petChanging,
                            onAction = onRecord, modifier = Modifier.padding(horizontal = 24.dp))
                        Text("录下你想对它说的话，\n一键变成猫语或狗语播放给它听～",
                            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp)
                                .testTag("voice_talk_subtitle"),
                            color = VoiceMuted, fontSize = 13.sp, lineHeight = 20.sp,
                            textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
                    VoiceRecordingTips(Modifier.padding(horizontal = 24.dp))
                }
                VoiceTalkPhase.STARTING, VoiceTalkPhase.RECORDING, VoiceTalkPhase.STOPPING -> {
                    VoiceCaptureControl(state, enabled = state.phase == VoiceTalkPhase.RECORDING,
                        onAction = onStop, modifier = Modifier.padding(horizontal = 24.dp))
                }
                VoiceTalkPhase.CONVERTING -> VoiceTalkConvertingContent(
                    pet = pet, modifier = Modifier.padding(horizontal = 24.dp))
                VoiceTalkPhase.RECORDED, VoiceTalkPhase.RESULT -> VoiceRecordedContent(
                    state.message ?: "录音完成啦！趣味转换功能正在准备中～",
                    state.elapsedMs, onReset, !state.petChanging, Modifier.padding(horizontal = 24.dp))
                VoiceTalkPhase.ERROR -> VoiceErrorContent(state.message ?: "录音暂时不可用，请重试～",
                    onReset, onSettings, !state.petChanging, Modifier.padding(horizontal = 24.dp))
            }
        }
    }
}

/** Match the opaque artwork's own top edge instead of tinting a visible rectangle. */
@Composable
private fun VoiceTalkHero(onBack: () -> Unit) {
    val artwork = ImageBitmap.imageResource(R.drawable.voice_talk_header_listening)
    val topEdge = remember(artwork) {
        val pixels = artwork.toPixelMap()
        List(artwork.width) { x -> pixels[x, 0] }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val illustrationHeight = (maxWidth * (artwork.height.toFloat() / artwork.width) * 1.14f)
            .coerceIn(100.dp, 200.dp)
        // FillHeight preserves the image ratio. Its small horizontal overflow contains
        // only the outer background; use the same source range for the Hero colours.
        val sourceWidth = minOf(artwork.width.toFloat(), artwork.height * maxWidth.value / illustrationHeight.value)
        val sourceLeft = (artwork.width - sourceWidth) / 2f
        val edgeColours = remember(topEdge, sourceWidth) {
            List(33) { index ->
                topEdge[(sourceLeft + sourceWidth * index / 32f).toInt().coerceIn(topEdge.indices)]
            }
        }
        Canvas(Modifier.matchParentSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Brush.horizontalGradient(edgeColours))
            drawRect(Brush.verticalGradient(0f to Color.White, .65f to Color.White,
                .85f to Color.Transparent, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            drawRect(Brush.horizontalGradient(0f to Color.Transparent, .025f to Color.White,
                .975f to Color.White, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
        }
        Column {
            VoiceHeader(onBack)
            Image(bitmap = artwork, contentDescription = "猫咪和狗狗开心地侧耳倾听",
                modifier = Modifier.fillMaxWidth().height(illustrationHeight).clipToBounds()
                    .testTag("voice_talk_header_art")
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        // These narrow bands cover the background edges, keeping ears
                        // and paws outside the fade rather than washing out the pets.
                        val verticalFeather = Brush.verticalGradient(
                            0f to Color.Transparent, .02f to Color.White,
                            .96f to Color.White, 1f to Color.Transparent)
                        val horizontalFeather = Brush.horizontalGradient(
                            0f to Color.Transparent, .025f to Color.White,
                            .975f to Color.White, 1f to Color.Transparent)
                        onDrawWithContent {
                            drawContent()
                            drawRect(verticalFeather, blendMode = BlendMode.DstIn)
                            drawRect(horizontalFeather, blendMode = BlendMode.DstIn)
                        }
                    },
                contentScale = ContentScale.FillHeight)
        }
    }
}

@Composable
private fun VoiceHeader(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("voice_talk_back")) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = .85f), shadowElevation = 1.dp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", Modifier.padding(6.dp).size(20.dp), tint = VoicePurple)
            }
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start) {
            Text("把我的话说给它听", color = Color(0xFF6650A0), fontSize = 22.sp, lineHeight = 29.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f, fill = false).testTag("voice_talk_title").semantics { heading() })
            VoicePaw(Modifier.padding(start = 3.dp).size(20.dp), Color(0xFFFFA4D4))
        }
    }
}

@Composable
private fun VoicePetSelector(pet: PetType?, enabled: Boolean, onPet: (PetType) -> Unit, modifier: Modifier) {
    Surface(modifier.fillMaxWidth().testTag("voice_talk_pet_selector"), shape = RoundedCornerShape(32.dp),
        color = Color.White.copy(alpha = .76f), shadowElevation = 2.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = .8f))) {
        Row(Modifier.selectableGroup().padding(6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("选择宠物", color = VoiceInk, fontSize = 13.sp, lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.weight(.85f).padding(horizontal = 2.dp))
            listOf(PetType.Cat, PetType.Dog).forEach { option ->
                val selected = option == pet
                val shape = RoundedCornerShape(25.dp)
                Row(Modifier.weight(1f).heightIn(min = 44.dp).clip(shape)
                    .background(if (selected) Color(0xFFFFE6F5) else Color(0xFFF7F7FF))
                    .border(1.dp, if (selected) VoicePink else Color.White, shape)
                    .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { onPet(option) })
                    .testTag("voice_talk_pet_${option.name}").padding(horizontal = 5.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Image(painterResource(if (option == PetType.Cat) R.drawable.pet_cat_main else R.drawable.pet_dog_main),
                        null, Modifier.size(30.dp), contentScale = ContentScale.Fit)
                    Text(option.title, color = if (selected) Color(0xFFEE68AF) else VoiceMuted,
                        fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun VoiceRecordingTips(modifier: Modifier) {
    Surface(modifier.fillMaxWidth().testTag("voice_talk_recording_tips"), shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = .62f), border = BorderStroke(1.dp, Color.White.copy(alpha = .9f))) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("ⓘ 录音提示", color = VoiceInk, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
            listOf("建议说一小段完整的话，效果更有趣。", "最长录音 10 秒。", "请在安静环境下录制，效果更好。").forEach {
                Text("• $it", color = VoiceMuted, fontSize = 12.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun VoiceRecordedContent(message: String, elapsedMs: Long, onReset: () -> Unit, enabled: Boolean, modifier: Modifier) {
    Surface(modifier.fillMaxWidth().testTag("voice_talk_recorded"), shape = RoundedCornerShape(30.dp),
        color = Color.White.copy(alpha = .65f)) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            VoicePaw(Modifier.size(44.dp), VoicePink)
            Text(message, color = VoiceInk, fontSize = 17.sp, lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.testTag("voice_talk_message"))
            Text("已录制 ${formatVoiceTime(elapsedMs)}",
                color = VoiceMuted, fontSize = 12.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
            VoiceGradientButton("重新录制", onReset, enabled, Modifier.fillMaxWidth().testTag("voice_talk_reset"))
        }
    }
}

@Composable
private fun VoiceErrorContent(message: String, onReset: () -> Unit, onSettings: (() -> Unit)?, enabled: Boolean, modifier: Modifier) {
    Surface(modifier.fillMaxWidth().testTag("voice_talk_error"), shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = .7f)) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("这次没录好，再试一次吧", color = VoiceInk, fontSize = 18.sp, lineHeight = 27.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(message, color = VoiceMuted, fontSize = 14.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
                modifier = Modifier.testTag("voice_talk_message"))
            VoiceGradientButton("重新录制", onReset, enabled, Modifier.fillMaxWidth().testTag("voice_talk_reset"))
            if (onSettings != null) TextButton(onClick = onSettings,
                modifier = Modifier.heightIn(min = 48.dp).testTag("voice_talk_permission_settings")) {
                Text("前往系统设置", color = VoicePurple)
            }
        }
    }
}

@Composable
private fun VoiceClouds(modifier: Modifier) {
    Canvas(modifier) {
        val baseY = size.height * .97f
        val cloud = Color.White.copy(alpha = .56f)
        listOf(-.03f to .13f, .10f to .10f, .26f to .12f, .45f to .09f,
            .61f to .13f, .80f to .10f, 1f to .16f).forEach { (x, radius) ->
            drawCircle(cloud, size.width * radius, Offset(size.width * x, baseY))
        }
    }
}

@Preview(name = "Voice READY · UI only", widthDp = 390, heightDp = 760)
@Composable
private fun VoiceTalkReadyPreview() {
    PetTranslatorTheme {
        VoiceTalkScreen(VoiceTalkUiState(), PetType.Cat, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Voice RECORDING · sample UI only", widthDp = 390, heightDp = 760)
@Composable
private fun VoiceTalkRecordingPreview() {
    PetTranslatorTheme {
        VoiceTalkScreen(VoiceTalkUiState(phase = VoiceTalkPhase.RECORDING, elapsedMs = 3_000,
            levels = listOf(.01f, .08f, .03f, .2f, .06f, .12f, .04f, .3f)), PetType.Cat, {}, {}, {}, {}, {})
    }
}
