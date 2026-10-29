package com.example.pettranslator.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pettranslator.ui.theme.*

@Composable fun PlayScreen(onBack: () -> Unit, onHumanVoice: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Blush, Mist))),
        contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 500.dp).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("逗宠", style = MaterialTheme.typography.headlineLarge)
            Text("更多陪伴毛孩子的乐趣，敬请期待", color = Muted)
            Surface(onClick = onHumanVoice, shape = RoundedCornerShape(22.dp),
                color = ColorForVoiceCard, shadowElevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("≋", color = Pink, fontSize = 28.sp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("把我的话说给它听", style = MaterialTheme.typography.titleMedium, color = Ink)
                        Text("把你的心里话变成萌萌的宠物语～", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted)
                }
            }
            OutlinedButton(onClick = onBack) { Text("返回首页") }
        }
    }
}
private val ColorForVoiceCard = androidx.compose.ui.graphics.Color(0xFFF5EEFF)
