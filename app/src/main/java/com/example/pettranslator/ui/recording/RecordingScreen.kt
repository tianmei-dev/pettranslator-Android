package com.example.pettranslator.ui.recording

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.net.toUri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pettranslator.R
import com.example.pettranslator.domain.*
import com.example.pettranslator.ui.theme.*
import com.example.pettranslator.ui.analysis.AnalysisScreen
import com.example.pettranslator.ui.result.InterpretationResultScreen
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun RecordingRoute(model: RecordingViewModel, onBack: () -> Unit, onHome: () -> Unit = onBack) {
    val context = LocalContext.current
    val activity = context as Activity
    val owner = LocalLifecycleOwner.current
    val windowInfo = LocalWindowInfo.current
    val focused = windowInfo.isWindowFocused
    var pendingStart by remember { mutableStateOf(false) }
    var requestingPermission by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }
    fun granted() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun resumeIfVisible() {
        val visible = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && windowInfo.isWindowFocused
        model.foreground(visible)
        if (visible && granted()) {
            model.permissionAvailable()
            if (pendingStart) { pendingStart = false; model.start() }
        }
    }
    LaunchedEffect(focused, owner, model) { resumeIfVisible() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        requestingPermission = false
        if (allowed) {
            model.permissionAvailable()
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && windowInfo.isWindowFocused) model.start() else pendingStart = true
        } else {
            pendingStart = false
            model.permissionDenied(!ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO))
        }
    }
    DisposableEffect(owner, model) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumeIfVisible()
                Lifecycle.Event.ON_PAUSE -> { pendingStart = false; model.foreground(false) }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        resumeIfVisible()
        onDispose { pendingStart = false; owner.lifecycle.removeObserver(observer); model.compositionDisposed() }
    }
    val state by model.uiState.collectAsStateWithLifecycle()
    val leave: () -> Unit = {
        if (!exiting) { exiting = true; pendingStart = false; model.leave(); onBack() }
    }
    BackHandler(onBack = leave)
    when {
        state.phase == RecordingPhase.ANALYZING -> AnalysisScreen(model.pet, state.analysisStep,
            state.analysisStartedAtNanos, state.analysisTargetDurationMs, onBack = leave)
        state.phase == RecordingPhase.SUCCESS && state.interpretation != null -> InterpretationResultScreen(
            result = state.interpretation!!, onBack = leave, onHome = { if (!exiting) { exiting = true; model.leave(); onHome() } },
            onAgain = { if (model.prepareAnotherRecording()) Unit else scopeSnackbar(context, "当前暂不可使用") },
        )
        state.phase in setOf(RecordingPhase.UNCERTAIN, RecordingPhase.PET_MISMATCH, RecordingPhase.ANALYSIS_ERROR) -> AnalysisOutcomeScreen(
            pet = model.pet, phase = state.phase, message = state.message,
            onBack = leave, onHome = { if (!exiting) { exiting = true; model.leave(); onHome() } },
            onAgain = { if (model.prepareAnotherRecording()) Unit else scopeSnackbar(context, "当前暂不可使用") },
            onSwitch = model::switchPetAndRejudge, onRetry = model::retryAnalysis,
        )
        else -> RecordingScreen(model.pet, model.scene, state, onBack = leave, onStart = {
        if (!requestingPermission && !exiting) {
            if (granted()) {
                pendingStart = true
                resumeIfVisible()
            } else {
                requestingPermission = true
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }, onStop = model::stop, onCancel = { model.cancel() }, onSettings = {
        pendingStart = false
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
    }, onSwitchPet = model::switchPetAndRejudge, onRetryAnalysis = model::retryAnalysis)
    }
}

private fun scopeSnackbar(context: android.content.Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
}

@Composable
fun RecordingScreen(
    pet: PetType, scene: PetScene, state: RecordingUiState,
    onBack: () -> Unit, onStart: () -> Unit, onStop: () -> Unit,
    onCancel: () -> Unit, onSettings: () -> Unit, onSwitchPet: () -> Unit = {}, onRetryAnalysis: () -> Unit = {},
) {
    val image = if (pet == PetType.Cat) R.drawable.pet_cat_main else R.drawable.pet_dog_main
    val active = state.phase == RecordingPhase.RECORDING
    val transitioning = state.phase in setOf(RecordingPhase.STARTING, RecordingPhase.STOPPING, RecordingPhase.ANALYZING)
    val quiet = active && state.elapsedMs >= 1500 && state.levels.takeLast(16).let {
        it.size == 16 && it.all { rms -> rmsToWaveLevel(rms) < .08f }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(
        Brush.verticalGradient(colorStops = arrayOf(
            0f to Color(0xFFFFF1F7), .46f to Color(0xFFF3F1FC),
            .72f to Color(0xFFEEF5FF), 1f to Color(0xFFFFF2F8))))
    ) {
        val compact = maxHeight < 620.dp || LocalDensity.current.fontScale > 1.3f
        val pageWidth = maxWidth
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.widthIn(max = 520.dp).fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
                Text("录音翻译", color = Ink, fontSize = 20.sp, lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 60.dp, vertical = 10.dp))
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)
                    .padding(start = 6.dp).testTag("recording_back").semantics { contentDescription = "返回" }) {
                    Canvas(Modifier.size(24.dp)) {
                        drawLine(Ink, Offset(size.width * .64f, size.height * .2f), Offset(size.width * .34f, size.height * .5f), 2.dp.toPx(), StrokeCap.Round)
                        drawLine(Ink, Offset(size.width * .34f, size.height * .5f), Offset(size.width * .64f, size.height * .8f), 2.dp.toPx(), StrokeCap.Round)
                    }
                }
            }
            Column(Modifier.weight(1f).widthIn(max = 520.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.Transparent, shadowElevation = .5.dp,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${pet.title} · ${scene.title}" }) {
                    Row(Modifier.fillMaxWidth().background(
                        Brush.horizontalGradient(listOf(Color(0xFFFFF8FC), Color(0xFFF3F7FF))), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(50.dp).clip(CircleShape).background(Color(0xFFFFEAF4)), contentAlignment = Alignment.Center) {
                            Image(painterResource(image), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(2.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(pet.title, color = Ink, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("正在听它的小心思", color = Muted, fontSize = 11.sp, lineHeight = 15.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Surface(shape = RoundedCornerShape(15.dp), color = Color.White.copy(alpha = .60f),
                            modifier = Modifier.widthIn(max = 124.dp)) {
                            Row(Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Image(painterResource(recordingSceneArtwork(scene)), contentDescription = null,
                                    contentScale = ContentScale.Fit, modifier = Modifier.size(26.dp))
                                Text(scene.title, color = Ink, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                PetRoom(image, pet, pageWidth)
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = .78f)) {
                    Text(when (state.phase) {
                        RecordingPhase.READY -> "准备好了吗？让我们听听它的声音～"
                        RecordingPhase.STARTING -> "正在准备麦克风…"
                        RecordingPhase.RECORDING -> if (quiet) "声音较轻，可以再靠近一点～" else "正在听毛孩子的声音…"
                        RecordingPhase.STOPPING -> "正在保存这段声音…"
                        RecordingPhase.ANALYZING -> "正在本地分析毛孩子的声音…"
                        RecordingPhase.SUCCESS -> "听到毛孩子的声音啦！"
                        RecordingPhase.UNCERTAIN -> "没有听清毛孩子的声音，再试一次吧～"
                        RecordingPhase.PET_MISMATCH -> "好像听到了${if (pet == PetType.Cat) "狗狗" else "猫咪"}的声音，要切换试试吗？"
                        RecordingPhase.ANALYSIS_ERROR -> "声音分析失败，请重试"
                        RecordingPhase.ERROR -> "录音失败，请重试"
                    }, color = Ink, fontSize = 14.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                }
                state.message?.let { Text(it, color = Ink, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center) }
                if (state.phase == RecordingPhase.SUCCESS) {
                    Text("趣味解读功能即将接入", color = Muted, fontSize = 13.sp,
                        lineHeight = 20.sp, textAlign = TextAlign.Center)
                }
                if (state.phase == RecordingPhase.ANALYSIS_ERROR) {
                    Text("请检查录音文件后重试；录音仍保留在本机。", color = Muted, fontSize = 13.sp,
                        lineHeight = 20.sp, textAlign = TextAlign.Center)
                    OutlinedButton(onClick = onRetryAnalysis) { Text("重试分析") }
                }
                if (state.phase == RecordingPhase.PET_MISMATCH) {
                    OutlinedButton(onClick = onSwitchPet, modifier = Modifier.testTag("switch_pet_and_rejudge")) {
                        Text("切换为${if (pet == PetType.Cat) "狗狗" else "猫咪"}并重新分析")
                    }
                    Text("使用刚才同一段录音重新判断", color = Muted, fontSize = 11.sp)
                }
                if (state.phase == RecordingPhase.UNCERTAIN) {
                    Text("可以靠近一些再录一次，或换到安静的环境。", color = Muted, fontSize = 12.sp)
                }
                if (state.audio != null && state.phase in setOf(RecordingPhase.SUCCESS, RecordingPhase.UNCERTAIN,
                        RecordingPhase.PET_MISMATCH, RecordingPhase.ANALYSIS_ERROR)) {
                    Text(String.format(Locale.ROOT, "已保存 %.1f 秒声音", state.elapsedMs / 1000f), color = Muted, fontSize = 12.sp)
                }
                if (active || state.audio != null) Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.testTag("recording_timer").semantics {
                        contentDescription = "已录制 ${state.elapsedMs / 1000} 秒，最长 10 秒"
                    }) {
                    Text(String.format(Locale.ROOT, "00:%02d", (state.elapsedMs / 1000).coerceAtMost(10)), color = Ink,
                        fontFamily = FontFamily.Monospace, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                    Text("/ 00:10", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 24.sp)
                }
                RecordingWaveform(active)
                if (compact) RecordingTip()
            }
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    val label = when (state.phase) {
                        RecordingPhase.RECORDING -> "停止录音"
                        RecordingPhase.STARTING, RecordingPhase.STOPPING -> "请稍候…"
                        RecordingPhase.ANALYZING -> "正在分析…"
                        RecordingPhase.SUCCESS, RecordingPhase.UNCERTAIN, RecordingPhase.PET_MISMATCH -> "重新录音"
                        RecordingPhase.ANALYSIS_ERROR -> "重新录音"
                        RecordingPhase.ERROR -> "重试录音"
                        RecordingPhase.READY -> "开始录音"
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(100.dp).background(
                            Brush.radialGradient(listOf(Pink.copy(alpha = .2f), Pink.copy(alpha = 0f))), CircleShape),
                            contentAlignment = Alignment.Center) {
                            Button(onClick = if (active) onStop else onStart,
                                shape = CircleShape, contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Pink, disabledContainerColor = Color(0xFFECA7CB)),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp),
                                enabled = !transitioning,
                                modifier = Modifier.size(76.dp).testTag("recording_primary_action")
                                    .semantics { contentDescription = label }) {
                                RecordingGlyph(if (active) "stop" else "mic", Color.White, Modifier.size(30.dp))
                            }
                        }
                        Text(label, color = Ink, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    if (state.phase in setOf(RecordingPhase.STARTING, RecordingPhase.RECORDING, RecordingPhase.STOPPING)) {
                        Column(Modifier.align(Alignment.TopStart).padding(top = 22.dp, start = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            FilledTonalIconButton(onClick = onCancel, modifier = Modifier.size(48.dp).testTag("recording_cancel").semantics { contentDescription = "取消录音" },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White.copy(alpha = .9f), contentColor = Muted)) {
                                RecordingGlyph("close", Muted, Modifier.size(20.dp))
                            }
                            Text("取消", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                    }
                }
                if (state.permissionBlocked) OutlinedButton(onClick = onSettings) { Text("前往应用设置") }
                if (!compact) RecordingTip()
                Text("仅在前台录音 · 音频临时保存在本机，不会上传", color = Muted, fontSize = 10.sp,
                    lineHeight = 15.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable private fun PetRoom(image: Int, pet: PetType, screenWidth: androidx.compose.ui.unit.Dp) {
    val pageEdge = Color(0xFFFFF1F7)
    val pageBottom = Color(0xFFF3F1FC)
    Box(Modifier.requiredWidth(screenWidth).aspectRatio(1.55f).testTag("recording_room")) {
        Image(painterResource(R.drawable.pet_recording_room_bg), null, contentScale = ContentScale.Crop,
            alignment = androidx.compose.ui.BiasAlignment(0f, .18f), modifier = Modifier.matchParentSize())
        Canvas(Modifier.matchParentSize()) {
            // Feather the photo into the page at its side edges and soften its lower edge.
            drawRect(Brush.horizontalGradient(colorStops = arrayOf(
                0f to pageEdge.copy(alpha = .78f), .10f to Color.Transparent,
                .90f to Color.Transparent, 1f to pageEdge.copy(alpha = .78f))))
            drawRect(Brush.verticalGradient(colorStops = arrayOf(
                0f to Color.Transparent, .42f to Color.Transparent, 1f to pageBottom)))
        }
        Image(painterResource(image), "${pet.title}插画", contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(.74f)
                .padding(bottom = 2.dp).aspectRatio(if (pet == PetType.Cat) 620f / 426f else 697f / 426f))
    }
}

@Composable private fun RecordingTip() {
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = .76f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("✧", color = Pink, fontSize = 20.sp)
            Column {
                Text("小提示", color = Ink, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("靠近宠物录音，尽量保持周围环境安静。", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable private fun RecordingGlyph(kind: String, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        when (kind) {
            "stop" -> drawRoundRect(color, Offset(size.width * .2f, size.height * .2f), Size(size.width * .6f, size.height * .6f), CornerRadius(3.dp.toPx()))
            "close" -> {
                drawLine(color, Offset(size.width * .2f, size.height * .2f), Offset(size.width * .8f, size.height * .8f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * .8f, size.height * .2f), Offset(size.width * .2f, size.height * .8f), stroke, StrokeCap.Round)
            }
            else -> {
                drawRoundRect(color, Offset(size.width * .36f, size.height * .1f), Size(size.width * .28f, size.height * .5f), CornerRadius(size.width * .14f), style = Stroke(stroke))
                drawArc(color, 0f, 180f, false, Offset(size.width * .22f, size.height * .3f), Size(size.width * .56f, size.height * .43f), style = Stroke(stroke, cap = StrokeCap.Round))
                drawLine(color, Offset(size.width * .5f, size.height * .73f), Offset(size.width * .5f, size.height * .9f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * .34f, size.height * .9f), Offset(size.width * .66f, size.height * .9f), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable private fun RecordingWaveform(active: Boolean) {
    var elapsedSeconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (!active) {
            elapsedSeconds = 0f
        } else {
            val startedAt = SystemClock.elapsedRealtimeNanos()
            while (true) {
                elapsedSeconds = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000_000f
                delay(33L)
            }
        }
    }

    val phases = remember { List(32) { index -> index * 0.47f + (index % 5) * 0.13f } }
    Canvas(Modifier.widthIn(max = 360.dp).fillMaxWidth().height(88.dp)
        .testTag(if (active) "recording_waveform" else "recording_waveform_idle")
        .semantics { contentDescription = if (active) "装饰性动态声波，录音中" else "声波已停止" }) {
        if (!active) return@Canvas

        val count = phases.size
        val barWidth = 5.dp.toPx()
        val gap = ((size.width - count * barWidth) / (count - 1)).coerceAtLeast(1.dp.toPx())
        val totalWidth = count * barWidth + (count - 1) * gap
        val startX = (size.width - totalWidth) / 2f
        val centerY = size.height / 2f
        val minHeight = 14.dp.toPx()
        val maxHeight = 70.dp.toPx()
        val gradient = Brush.horizontalGradient(listOf(Pink, Color(0xFFA48BE8), Blue), startX, startX + totalWidth)

        phases.forEachIndexed { index, phase ->
            val speed = 1.45f + (index % 6) * 0.105f
            val broad = kotlin.math.sin(elapsedSeconds * speed + phase)
            val detail = kotlin.math.sin(elapsedSeconds * (2.05f + (index % 4) * 0.09f) + phase * 1.73f)
            val swell = (0.48f + broad * 0.30f + detail * 0.16f).coerceIn(0f, 1f)
            val height = minHeight + (maxHeight - minHeight) * swell
            drawRoundRect(gradient,
                Offset(startX + index * (barWidth + gap), centerY - height / 2f),
                Size(barWidth, height), CornerRadius(barWidth / 2f))
        }
    }
}

private fun recordingSceneArtwork(scene: PetScene): Int = when (scene) {
    PetScene.Door -> R.drawable.scene_door_window
    PetScene.Meal -> R.drawable.scene_food
    PetScene.Play -> R.drawable.scene_play
    PetScene.Alone -> R.drawable.scene_alone
    PetScene.Held -> R.drawable.scene_held
    PetScene.Unknown -> R.drawable.scene_unknown
}
