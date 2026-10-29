package com.example.pettranslator.ui.recording

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.ui.theme.*

@Composable
fun AnalysisOutcomeScreen(pet: PetType, phase: RecordingPhase, message: String?, onBack: () -> Unit,
                         onHome: () -> Unit, onAgain: () -> Unit, onSwitch: () -> Unit, onRetry: () -> Unit) {
    val mismatch = phase == RecordingPhase.PET_MISMATCH
    val title = when (phase) {
        RecordingPhase.PET_MISMATCH -> "好像听到了${if (pet == PetType.Cat) "狗狗" else "猫咪"}的声音，要切换试试吗？"
        RecordingPhase.ANALYSIS_ERROR -> message ?: "声音分析暂时遇到问题"
        else -> "哎呀，这次没听清毛孩子的声音～"
    }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFF2F7), Color(0xFFEEF5FF))))
        .padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            Text("声音分析", color = Ink, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).testTag("recording_back")) { Text("‹ 返回", color = Muted) }
        }
        Spacer(Modifier.weight(1f))
        Text(if (mismatch) "🐾" else "🌷", fontSize = 54.sp)
        Spacer(Modifier.height(18.dp))
        Text(title, color = Ink, fontSize = 21.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(when (phase) {
            RecordingPhase.PET_MISMATCH -> "可以用同一段录音重新判断，也可以重新录一次。"
            RecordingPhase.ANALYSIS_ERROR -> "请稍后重试，录音仍保存在本机临时空间。"
            else -> "换个安静点的地方，再试一次吧！"
        }, color = Muted, fontSize = 14.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(26.dp))
        if (mismatch) Button(onClick = onSwitch, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = RoundedCornerShape(25.dp), colors = ButtonDefaults.buttonColors(containerColor = Pink)) {
            Text("切换为${if (pet == PetType.Cat) "狗狗" else "猫咪"}并重新分析", color = Color.White)
        } else Button(onClick = if (phase == RecordingPhase.ANALYSIS_ERROR) onRetry else onAgain,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Pink)) { Text(if (phase == RecordingPhase.ANALYSIS_ERROR) "重试分析" else "重新录音", color = Color.White) }
        if (mismatch) OutlinedButton(onClick = onAgain, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("重新录音", color = Ink) }
        TextButton(onClick = onHome) { Text("返回首页", color = Muted) }
        Spacer(Modifier.weight(1f))
    }
}
