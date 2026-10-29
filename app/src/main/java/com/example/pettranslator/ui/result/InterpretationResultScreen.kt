package com.example.pettranslator.ui.result

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.pettranslator.R
import com.example.pettranslator.domain.PetScene
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.domain.interpretation.PetInterpretationResult
import com.example.pettranslator.domain.interpretation.displayTitle
import com.example.pettranslator.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InterpretationResultScreen(result: PetInterpretationResult, onBack: () -> Unit, onHome: () -> Unit, onAgain: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); notice = null } }

    suspend fun export(save: Boolean) {
        if (busy) return
        busy = true
        try {
            val output = withContext(Dispatchers.IO) {
                val bitmap = ResultCardExporter.render(context, result)
                try { if (save) ResultCardExporter.save(context, bitmap) else ResultCardExporter.writeShareFile(context, bitmap) }
                finally { bitmap.recycle() }
            }
            if (save) notice = if (output == true) "结果卡片已保存到相册" else "保存失败，请稍后重试"
            else { ResultCardExporter.share(context, output as File); notice = "分享卡片已准备好" }
        } catch (_: ActivityNotFoundException) { notice = "没有可用的分享应用" }
        catch (_: Exception) { notice = if (save) "保存失败，请检查存储权限" else "分享失败，请稍后重试" }
        finally { busy = false }
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) scope.launch { export(true) } else notice = "需要照片存储权限才能保存卡片"
    }

    val petIcon = petIcon(result.petType)
    val headerArt = if (result.petType == PetType.Cat) R.drawable.result_cat_header else R.drawable.result_dog_header
    val quoteArt = if (result.petType == PetType.Cat) R.drawable.result_cat_quote else R.drawable.result_dog_quote
    val tipArt = if (result.petType == PetType.Cat) R.drawable.result_cat_tip else R.drawable.result_dog_tip
    val sceneArt = sceneIcon(result.scene)
    Scaffold(containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snackbar) }) { insets ->
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFF2F8), Color(0xFFF2F3FF), Color(0xFFFFF6FA))))
            .padding(insets)) {
            Box(Modifier.fillMaxWidth().height(208.dp)) {
                Image(painterResource(headerArt), contentDescription = "${result.petType.title}在温馨房间里的插画",
                    contentScale = ContentScale.Crop, alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .62f to Color.Transparent, 1f to Color(0xFFFFF2F8))))
                Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                    Text("翻译结果", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 2.dp).testTag("recording_back")) { Text("‹", color = Ink, fontSize = 30.sp) }
                    TextButton(onClick = onHome, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) { Text("⌂", color = Ink, fontSize = 22.sp) }
                }
                Surface(Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 18.dp), shape = RoundedCornerShape(23.dp),
                    color = Color.White.copy(alpha = .91f), shadowElevation = 1.dp) {
                    Text("毛孩子的\n心声来啦！", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Pink,
                        fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PetInfoBar(result, petIcon, sceneArt)
                QuoteCard(result, quoteArt)
                SoundAnalysisCard(result)
                InteractionCard(result.interactionSuggestion, tipArt)
                Text("趣味解读仅供娱乐参考，请结合宠物实际表现判断。", Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    color = Muted, fontSize = 10.sp, lineHeight = 15.sp, textAlign = TextAlign.Center)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionTile("↻", "再翻译一次", Brush.linearGradient(listOf(Color.White, Color(0xFFE9F1FF))), Ink,
                    enabled = !busy, onClick = onAgain, modifier = Modifier.weight(1f))
                ActionTile("☆", if (busy) "处理中" else "保存结果", Brush.linearGradient(listOf(Color(0xFFFF70B6), Color(0xFFDD50B8))), Color.White,
                    enabled = !busy, onClick = {
                        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                            saveLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        else scope.launch { export(true) }
                    }, modifier = Modifier.weight(1f))
                ActionTile("•", "分享心声", Brush.linearGradient(listOf(Color(0xFF65C3F4), Color(0xFF4A91EF))), Color.White,
                    enabled = !busy, onClick = { scope.launch { export(false) } }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun PetInfoBar(result: PetInterpretationResult, petIcon: Int, sceneArt: Int) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = .88f), shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painterResource(petIcon), result.petType.title, contentScale = ContentScale.Fit,
                modifier = Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFFFEAF3)).padding(1.dp))
            Column(Modifier.weight(1f)) {
                Text(result.petType.title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(result.petType.subtitle, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
            }
            Image(painterResource(sceneArt), null, contentScale = ContentScale.Fit, modifier = Modifier.size(30.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(result.scene.title, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA).format(Date(result.createdAtEpochMs)),
                    color = Muted, fontSize = 9.sp, lineHeight = 13.sp)
            }
        }
    }
}

@Composable private fun QuoteCard(result: PetInterpretationResult, quoteArt: Int) {
    BoxWithConstraints {
        val artworkWidth = if (maxWidth < 350.dp) 88.dp else 106.dp
        Surface(shape = RoundedCornerShape(22.dp), color = Color.Transparent, shadowElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFFFE2F0), Color(0xFFF2E6FF), Color(0xFFFFF1F8))),
                RoundedCornerShape(22.dp)).padding(start = 14.dp, top = 12.dp, bottom = 10.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFE95AAB)) {
                        Text("♥ ${result.coreThemeId.displayTitle}", Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            color = Color.White, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("“${result.innerMonologue}”", color = Ink, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                }
                Image(painterResource(quoteArt), contentDescription = "${result.petType.title}插画",
                    contentScale = ContentScale.Fit, modifier = Modifier.width(artworkWidth).heightIn(max = 138.dp).aspectRatio(0.76f))
            }
        }
    }
}

@Composable private fun SoundAnalysisCard(result: PetInterpretationResult) {
    val features = result.soundFeatures
    val petTitle = if (result.petType == PetType.Cat) "猫咪" else "狗狗"
    val hasWindowStats = features.windowCount > 0
    Surface(shape = RoundedCornerShape(19.dp), color = Color.White.copy(alpha = .94f), shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(29.dp).clip(CircleShape).background(Color(0xFFFFE9F3)), contentAlignment = Alignment.Center) {
                    Text("♫", color = Pink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Text("AI 声音分析", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Text("AI 检测到${petTitle}相关声音，并完成了音频分段分析。", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            if (hasWindowStats) {
                Row(Modifier.fillMaxWidth().background(Color(0xFFFFF4F9), RoundedCornerShape(13.dp)).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    StatCell("音频分析片段", "${features.windowCount} 段", Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(28.dp).background(Color(0xFFE9DDE8)))
                    StatCell("相关声音证据", "${features.windowsWithPetEvidence} 段", Modifier.weight(1f))
                }
                Text("AI 对录音进行分段识别，其中 ${features.windowsWithPetEvidence} 个片段检测到与${petTitle}相关的声音类别。统计表示证据分布，不代表识别准确率或情绪判断。",
                    color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Muted, fontSize = 9.sp, lineHeight = 13.sp, textAlign = TextAlign.Center)
        Text(value, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun InteractionCard(suggestion: String, tipArt: Int) {
    val lines = suggestionLines(suggestion)
    Surface(shape = RoundedCornerShape(19.dp), color = Color.White.copy(alpha = .94f), shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 7.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("☼", color = Color(0xFFFFB335), fontSize = 18.sp)
                    Text("互动建议", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                lines.forEach { line ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("✓", color = Color(0xFF31C88A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(line, color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                    }
                }
            }
            Image(painterResource(tipArt), contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.width(66.dp).aspectRatio(0.75f))
        }
    }
}

@Composable private fun ActionTile(glyph: String, label: String, brush: Brush, contentColor: Color,
                                   enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(17.dp), color = Color.Transparent,
        modifier = modifier.height(65.dp)) {
        Column(Modifier.fillMaxSize().background(brush, RoundedCornerShape(17.dp)), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(glyph, color = contentColor, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
            Text(label, color = contentColor, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

private fun petIcon(pet: PetType): Int = if (pet == PetType.Cat) R.drawable.pet_cat_main else R.drawable.pet_dog_main
private fun sceneIcon(scene: PetScene): Int = when (scene) {
    PetScene.Door -> R.drawable.scene_door_window
    PetScene.Meal -> R.drawable.scene_food
    PetScene.Play -> R.drawable.scene_play
    PetScene.Alone -> R.drawable.scene_alone
    PetScene.Held -> R.drawable.scene_held
    PetScene.Unknown -> R.drawable.scene_unknown
}

/** Breaks the approved engine suggestion for display without inventing extra advice. */
private fun suggestionLines(suggestion: String): List<String> {
    val text = suggestion.trim()
    val sentenceParts = text.split(Regex("(?<=[。！？；])")).filter { it.isNotBlank() }
    if (sentenceParts.size > 1) return listOf(sentenceParts.first(), sentenceParts.drop(1).joinToString(""))
    val comma = text.indexOf('，')
    return if (comma in 5 until text.lastIndex) listOf(text.substring(0, comma + 1), text.substring(comma + 1)) else listOf(text)
}
