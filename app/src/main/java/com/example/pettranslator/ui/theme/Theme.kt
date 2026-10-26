package com.example.pettranslator.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Pink = Color(0xFFF76BB4)
val Blue = Color(0xFF70BDF5)
val Ink = Color(0xFF172B75)
val Muted = Color(0xFF626E9C)
val Blush = Color(0xFFFFF2F7)
val Mist = Color(0xFFEEF5FF)
object AppSpace { val page = 20.dp; val gap = 12.dp; val cardRadius = 24.dp }
private val Colors = lightColorScheme(primary = Pink, secondary = Blue, background = Blush, surface = Color.White, onSurface = Ink, onBackground = Ink, onPrimary = Color.White)
private val Fonts = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp)
)
@Composable fun PetTranslatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Fonts, content = content)
}
