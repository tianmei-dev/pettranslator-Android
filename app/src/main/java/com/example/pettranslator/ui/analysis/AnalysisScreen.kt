package com.example.pettranslator.ui.analysis

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pettranslator.R
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.ui.theme.*
import kotlinx.coroutines.delay

private val analysisStages = listOf(
    "分析音频特征…",
    "识别宠物声音类型…",
    "结合当前场景…",
    "生成趣味解读…",
)

@Composable
fun AnalysisScreen(pet: PetType, completedSteps: Int, onBack: () -> Unit) {
    var illustrativeProgress by remember { mutableFloatStateOf(.06f) }
    val target = when (completedSteps.coerceIn(0, 4)) {
        0 -> .64f
        1 -> .70f
        2 -> .84f
        3 -> .94f
        else -> 1f
    }
    LaunchedEffect(target) {
        if (target >= 1f) illustrativeProgress = 1f
        else while (illustrativeProgress < target) {
            delay(45)
            illustrativeProgress = (illustrativeProgress + .012f).coerceAtMost(target)
        }
    }
    val animatedProgress by animateFloatAsState(illustrativeProgress, tween(220), label = "analysisProgress")
    BoxWithConstraints(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color(0xFFFFF2F7), Color(0xFFF4F2FC), Color(0xFFEEF5FF), Color(0xFFFFF6F9))))) {
        val compact = maxHeight < 690.dp
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
                Text("正在分析", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 5.dp)) {
                    Text("‹ 返回", color = Muted, fontSize = 14.sp)
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min = if (compact) 170.dp else 220.dp, max = if (compact) 210.dp else 270.dp)
                    .clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFFFFE8F3), Color(0xFFEAF3FF)))),
                    contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.pet_home_room_bg), null,
                        modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    Image(painterResource(if (pet == PetType.Cat) R.drawable.pet_cat_main else R.drawable.pet_dog_main),
                        contentDescription = pet.title, modifier = Modifier.fillMaxSize(.78f).align(Alignment.BottomCenter),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                    Text("✧", color = Color(0xFFFF75B9), fontSize = 28.sp, modifier = Modifier.align(Alignment.TopStart).padding(20.dp))
                    Text("♡", color = Color(0xFF8D9DE7), fontSize = 27.sp, modifier = Modifier.align(Alignment.TopEnd).padding(24.dp))
                }
                Text("正在认真听它说话…", color = Ink, fontSize = if (compact) 21.sp else 23.sp,
                    lineHeight = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("AI正在分析声音类型，马上揭晓它的小心思～", color = Muted, fontSize = 14.sp,
                    lineHeight = 21.sp, textAlign = TextAlign.Center)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = .84f))
                    .padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth().height(9.dp)
                        .clip(CircleShape), color = Pink, trackColor = Color(0xFFF0EAF5))
                    analysisStages.forEachIndexed { index, label ->
                        val completed = index < completedSteps
                        val active = index == completedSteps.coerceAtMost(3) && completedSteps < 4
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            Box(Modifier.size(25.dp).clip(CircleShape).background(
                                if (completed) Pink else if (active) Color(0xFFFFE2F0) else Color(0xFFF0EFF5)),
                                contentAlignment = Alignment.Center) {
                                if (completed) Text("✓", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                else if (active) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Pink)
                                else Text("${index + 1}", color = Muted, fontSize = 11.sp)
                            }
                            Text(label, color = if (completed || active) Ink else Muted, fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(17.dp), color = Color.White.copy(alpha = .72f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("✦", color = Pink, fontSize = 18.sp)
                        Text("分析过程仅在本地进行，不会上传你的录音，请放心使用！", color = Muted,
                            fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
