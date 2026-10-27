package com.example.pettranslator.ui.result

import android.content.ActivityNotFoundException
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.pettranslator.R
import com.example.pettranslator.domain.interpretation.PetInterpretationResult
import com.example.pettranslator.domain.interpretation.displayTitle
import com.example.pettranslator.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File

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
                try {
                    if (save) ResultCardExporter.save(context, bitmap) else ResultCardExporter.writeShareFile(context, bitmap)
                } finally { bitmap.recycle() }
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
    val petIcon = if (result.petType.name == "Cat") R.drawable.pet_cat_main else R.drawable.pet_dog_main
    val sceneIcon = when (result.scene.name) {
        "Door" -> R.drawable.scene_door_window; "Meal" -> R.drawable.scene_food; "Play" -> R.drawable.scene_play
        "Alone" -> R.drawable.scene_alone; "Held" -> R.drawable.scene_held; else -> R.drawable.scene_unknown
    }
    Scaffold(containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snackbar) }) { insets ->
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFF2F7), Color(0xFFF0F4FF), Color(0xFFFFF7FA))))
            .padding(insets)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
                Text("翻译结果", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) { Text("‹ 返回", color = Muted) }
                TextButton(onClick = onHome, modifier = Modifier.align(Alignment.CenterEnd)) { Text("首页", color = Pink) }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Box(Modifier.fillMaxWidth().height(146.dp).clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFFE6F1), Color(0xFFE4F1FF)))), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.pet_home_hero), null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(.78f))
                    Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = .9f)) {
                        Text("毛孩子的心声来啦！", Modifier.padding(horizontal = 14.dp, vertical = 5.dp), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = .92f), shadowElevation = 2.dp) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(painterResource(petIcon), result.petType.title, contentScale = ContentScale.Fit,
                            modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFFFEEF5)).padding(2.dp))
                        Column(Modifier.weight(1f)) {
                            Text(result.petType.title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(result.petType.subtitle, color = Muted, fontSize = 12.sp)
                            Text("${result.scene.title} · ${SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(Date(result.createdAtEpochMs))}", color = Muted, fontSize = 11.sp)
                        }
                        Image(painterResource(sceneIcon), null, contentScale = ContentScale.Fit, modifier = Modifier.size(34.dp))
                    }
                }
                Surface(shape = RoundedCornerShape(25.dp), color = Color.Transparent, shadowElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFFFEAF3), Color(0xFFFFF7FB), Color(0xFFEEF5FF))), RoundedCornerShape(25.dp))
                        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("💗 ${result.coreThemeId.displayTitle}", color = Color(0xFFE9579B), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(result.innerMonologue, color = Ink, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 31.sp)
                        Image(painterResource(petIcon), null, contentScale = ContentScale.Fit,
                            modifier = Modifier.align(Alignment.End).size(84.dp))
                        Text("${result.petType.title}的声音 × ${result.scene.title}情境，组合成一份轻松的趣味联想。", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
                InfoCard("声音分析", result.soundFeatures.soundCategory.title,
                    "识别已通过 · ${result.evidenceSummary}\n${result.explanation}")
                InfoCard("互动建议", result.interactionSuggestion)
                Text("趣味解读仅供娱乐参考，请结合宠物实际表现判断。", Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    color = Muted, fontSize = 11.sp, lineHeight = 17.sp, textAlign = TextAlign.Center)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onAgain, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 7.dp)) { Text("再翻译一次", fontSize = 12.sp) }
                Button(onClick = {
                    if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                        saveLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    else scope.launch { export(true) }
                }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 7.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pink)) { Text(if (busy) "处理中" else "保存结果", fontSize = 12.sp) }
                Button(onClick = { scope.launch { export(false) } }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 7.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF73B9EA))) { Text("分享心声", fontSize = 12.sp) }
            }
        }
    }
}

@Composable private fun InfoCard(title: String, primary: String, secondary: String? = null) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = .91f), shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(primary, color = Color(0xFFE9579B), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
            secondary?.let { Text(it, color = Muted, fontSize = 12.sp, lineHeight = 19.sp) }
        }
    }
}
