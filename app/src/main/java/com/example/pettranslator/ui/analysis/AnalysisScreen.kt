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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
    val base = Color(0xFFFFF0F7)
    BoxWithConstraints(Modifier.fillMaxSize().testTag("analysis_page").background(Brush.verticalGradient(
        listOf(Color(0xFFFFEDF6), Color(0xFFEBF3FF), base)))) {
        val illustrationHeight = (maxHeight * .34f).coerceIn(170.dp, 260.dp)
        // Fit the original portrait aspect ratio; soften only its bottom edge.
        Image(painterResource(R.drawable.analysis_cloud_background), contentDescription = null,
            contentScale = ContentScale.Fit, alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxWidth().aspectRatio(941f / 1672f)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(
                        0f to Color.White, .55f to Color.White,
                        .90f to Color.Transparent, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                })
        // A translucent veil protects text contrast without washing out the characters.
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Color.White.copy(alpha = .12f), .42f to Color.White.copy(alpha = .08f),
            .65f to Color(0xFFF5F1FC).copy(alpha = .75f), 1f to base.copy(alpha = .85f))))
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                Text("正在分析", color = Ink, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)
                    .padding(start = 3.dp).testTag("recording_back")) {
                    Text("‹", color = Ink, fontSize = 28.sp, lineHeight = 30.sp)
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                BoxWithConstraints(Modifier.fillMaxWidth().height(illustrationHeight), contentAlignment = Alignment.Center) {
                    val petWidth = minOf((maxWidth * .90f).coerceAtMost(360.dp) * 1.27f, maxWidth + 44.dp)
                    Image(painterResource(R.drawable.pet_home_hero),
                        contentDescription = "${pet.title}和伙伴依偎在云朵中的插画",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.requiredWidth(petWidth)
                            .aspectRatio(1419f / 515f).align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                // Original asset has a hard lower edge: feather it, keep faces opaque.
                                drawRect(Brush.verticalGradient(0f to Color.White, .92f to Color.White,
                                    1f to Color.Transparent), blendMode = BlendMode.DstIn)
                            })
                }
                Text("正在认真听它说话…", color = Ink, fontSize = 18.sp, lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text("AI正在分析声音类型，马上揭晓它的小心思～", color = Muted,
                    fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(.94f).heightIn(min = 26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Canvas(Modifier.weight(1f).height(9.dp).testTag("analysis_progress")
                        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(animatedProgress, 0f..1f) }) {
                        drawRoundRect(Color(0xFFE8E3F0), cornerRadius = CornerRadius(size.height / 2))
                        val width = size.width * animatedProgress.coerceIn(0f, 1f)
                        if (width > 0f) drawRoundRect(Brush.horizontalGradient(
                            listOf(Pink, Color(0xFFC45CDC), Color(0xFF4DAFF5)), endX = width),
                            size = Size(width, size.height), cornerRadius = CornerRadius(size.height / 2))
                    }
                    Text("${(animatedProgress * 100).toInt()}%", color = Ink,
                        modifier = Modifier.widthIn(min = 36.dp), fontSize = 13.sp,
                        lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(14.dp))
                Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    stageLabels.forEachIndexed { index, label ->
                        val isDone = index < completedStages
                        val isActive = index == activeStage
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(22.dp).background(if (isDone) Pink else Color.Transparent,
                                CircleShape), contentAlignment = Alignment.Center) {
                                when {
                                    isDone -> Canvas(Modifier.size(13.dp)) {
                                        drawLine(Color.White, Offset(size.width * .12f, size.height * .52f),
                                            Offset(size.width * .40f, size.height * .78f), 2.dp.toPx(),
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                        drawLine(Color.White, Offset(size.width * .40f, size.height * .78f),
                                            Offset(size.width * .88f, size.height * .22f), 2.dp.toPx(),
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                    }
                                    isActive -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                                        color = Color(0xFF627FE8), trackColor = Color(0x44627FE8))
                                    else -> Box(Modifier.size(18.dp).background(Color(0xFFE6E4ED), CircleShape))
                                }
                            }
                            Text(label, color = if (isDone || isActive) Color(0xFF405A9D) else Muted,
                                modifier = Modifier.weight(1f), fontSize = 14.sp, lineHeight = 21.sp,
                                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            Box(Modifier.fillMaxWidth().testTag("analysis_footer")
                .padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center) {
                Row(Modifier.widthIn(max = 360.dp).fillMaxWidth()
                    .background(Color(0xFFFFEDF6).copy(alpha = .78f), RoundedCornerShape(15.dp))
                    .border(1.dp, Pink.copy(alpha = .22f), RoundedCornerShape(15.dp))
                    .padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("♡", color = Pink, fontSize = 22.sp, lineHeight = 24.sp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("温馨提示", color = Pink, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold)
                        Text("分析过程仅在本地进行，不会上传你的录音，请放心使用！", color = Muted,
                            fontSize = 11.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
    }
}
