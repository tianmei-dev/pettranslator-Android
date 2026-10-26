package com.example.pettranslator.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.pettranslator.ui.theme.*

@Composable fun PlaceholderScreen(title: String, subtitle: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Blush, Mist))), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 500.dp).verticalScroll(rememberScrollState()).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            Text(subtitle, color = Muted)
            OutlinedButton(onClick = onBack) { Text("返回首页") }
        }
    }
}
