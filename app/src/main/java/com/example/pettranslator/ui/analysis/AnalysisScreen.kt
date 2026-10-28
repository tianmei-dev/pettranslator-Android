package com.example.pettranslator.ui.analysis

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pettranslator.R
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.ui.theme.*
import kotlinx.coroutines.delay

private val stageLabels = listOf("分析音频特征…", "识别宠物声音类型…", "结合当前场景…", "生成趣味解读…")

@Composable
fun AnalysisScreen(pet: PetType, completedSteps: Int, startedAtNanos: Long, targetDurationMs: Long, onBack: () -> Unit) {
    var nowNanos by remember(startedAtNanos) { mutableLongStateOf(System.nanoTime()) }
    LaunchedEffect(startedAtNanos) {
        while (true) { nowNanos = System.nanoTime(); delay(32) }
    }
    val duration = targetDurationMs.coerceIn(3_000L, 5_000L)
    val elapsedMs = ((nowNanos - startedAtNanos).coerceAtLeast(0L) / 1_000_000L)
    val timeline = (elapsedMs.toFloat() / duration).coerceIn(0f, 1f)
    val ready = completedSteps >= 4
    val stageCeiling = when (completedSteps.coerceIn(0, 4)) {
        0 -> .249f
        1 -> .499f
        2 -> .749f
        3 -> .95f
        else -> 1f
    }
    val progress = if (ready && timeline >= 1f) 1f else minOf(timeline, stageCeiling, .95f)
    val animatedProgress by animateFloatAsState(progress, tween(90), label = "analysisTimeline")
    val completedStages = (progress * stageLabels.size).toInt().coerceIn(0, stageLabels.size)
    val activeStage = if (progress >= 1f) -1 else (progress * stageLabels.size).toInt().coerceIn(0, stageLabels.lastIndex)
    val compact = LocalConfiguration.current.screenHeightDp < 700
    val scrollContent = compact || LocalDensity.current.fontScale >= 1.3f
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(colorStops = arrayOf(
        0f to Color(0xFFFFEAF4), .20f to Color(0xFFF3EEFF), .52f to Color(0xFFEAF4FF),
        .78f to Color(0xFFF7EEFF), 1f to Color(0xFFFFF0F7)))), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            Text("正在分析", color = Ink, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 3.dp).testTag("recording_back")) {
                Text("‹", color = Ink, fontSize = 28.sp, lineHeight = 30.sp)
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth()
            .then(if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(if (compact) 205.dp else 242.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    val cloudBrush = Brush.verticalGradient(listOf(Color.White.copy(alpha = .76f), Color(0xFFDDEBFF).copy(alpha = .48f), Color(0xFFFFE5F2).copy(alpha = .20f)))
                    fun cloud(cx: Float, cy: Float, width: Float, height: Float) {
                        val x = cx * size.width
                        val y = cy * size.height
                        drawOval(cloudBrush, Offset(x - width * .48f, y - height * .08f), Size(width * .96f, height * .72f))
                        drawOval(cloudBrush, Offset(x - width * .32f, y - height * .48f), Size(width * .42f, height * .88f))
                        drawOval(cloudBrush, Offset(x - width * .06f, y - height * .66f), Size(width * .48f, height * 1.02f))
                        drawOval(cloudBrush, Offset(x + width * .20f, y - height * .38f), Size(width * .38f, height * .82f))
                    }
                    val cloudHeight = 54.dp.toPx()
                    cloud(.13f, .53f, 148.dp.toPx(), cloudHeight)
                    cloud(.84f, .42f, 160.dp.toPx(), cloudHeight * 1.08f)
                    cloud(.48f, .88f, size.width * 1.05f, cloudHeight * .78f)
                }
                Image(painterResource(R.drawable.pet_home_hero), contentDescription = "${pet.title}和伙伴依偎在云朵中的插画",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.requiredWidth((maxWidth * 1.18f).coerceAtMost(420.dp))
                        .aspectRatio(1419f / 515f).align(Alignment.BottomCenter))
                Text("♫", color = Color(0xFF9B8AEF), fontSize = 21.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 31.dp))
                Text("♡", color = Pink, fontSize = 23.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 22.dp, top = 24.dp))
            }
            Text("正在认真听它说话…", color = Ink, fontSize = 19.sp, lineHeight = 25.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("AI正在分析声音类型，马上揭晓它的小心思～", color = Muted,
                fontSize = 11.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Canvas(Modifier.weight(1f).height(8.dp).testTag("analysis_progress")
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(animatedProgress, 0f..1f) }) {
                    drawRoundRect(Color(0xFFE8E3F0), cornerRadius = CornerRadius(size.height / 2))
                    val width = size.width * animatedProgress.coerceIn(0f, 1f)
                    if (width > 0f) drawRoundRect(Brush.horizontalGradient(
                        listOf(Pink, Color(0xFFC45CDC), Color(0xFF4DAFF5)), endX = width),
                        size = Size(width, size.height), cornerRadius = CornerRadius(size.height / 2))
                }
                Text("${(animatedProgress * 100).toInt()}%", color = Ink, fontSize = 12.sp,
                    lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.fillMaxWidth().padding(start = 27.dp, top = 1.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                stageLabels.forEachIndexed { index, label ->
                    val isDone = index < completedStages
                    val isActive = index == activeStage
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(18.dp).background(if (isDone) Pink else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                            when {
                                isDone -> Text("✓", color = Color.White, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
                                isActive -> CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 1.7.dp,
                                    color = Color(0xFF627FE8), trackColor = Color(0x44627FE8))
                                else -> Box(Modifier.size(14.dp).background(Color(0xFFE6E4ED), CircleShape))
                            }
                        }
                        Text(label, color = if (isDone || isActive) Color(0xFF405A9D) else Muted,
                            fontSize = 11.sp, lineHeight = 15.sp, fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal)
                    }
                }
            }
            if (!scrollContent) Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(top = 1.dp)
                .background(Color(0xFFFFEDF6).copy(alpha = .78f), RoundedCornerShape(15.dp))
                .border(1.dp, Pink.copy(alpha = .17f), RoundedCornerShape(15.dp))
                .padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("♡", color = Pink, fontSize = 17.sp, lineHeight = 20.sp)
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("温馨提示", color = Pink, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
                    Text("分析过程仅在本地进行，不会上传你的录音，请放心使用！", color = Muted,
                        fontSize = 9.sp, lineHeight = 14.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
