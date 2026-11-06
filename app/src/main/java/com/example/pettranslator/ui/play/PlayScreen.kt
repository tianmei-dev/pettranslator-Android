package com.example.pettranslator.ui.play

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.example.pettranslator.R
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val FunPurple = Color(0xFF6650A0)
private val FunPink = Color(0xFFF66CB8)

@Composable fun PlayScreen() {
    var pet by rememberSaveable { mutableStateOf(PetType.Cat) }
    var highlighted by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun message(text: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(text) } }
    LaunchedEffect(highlighted) { if (highlighted != null) { delay(650); highlighted = null } }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Blush, Color(0xFFF0E9FF), Mist, Blush)))) {
        LazyColumn(Modifier.widthIn(max = 600.dp).fillMaxWidth().align(Alignment.TopCenter).testTag("play_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { FunTopGroup { message("趣味说话功能即将上线～") } }
            item { Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FunWave(Modifier.size(24.dp), FunPurple)
                Text("趣味逗宠音效", color = Ink, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
            } }
            item { PetSwitch(pet) { pet = it; highlighted = null } }
            items(PetFunSoundCatalog.items(pet), key = { it.id }) { sound ->
                SoundRow(sound, highlighted == sound.id) {
                    highlighted = sound.id
                    message("音效资源将在下一阶段接入～")
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }
}

/** Measure the union of banner and card; overlap only their decorative cloud/background edges. */
@Composable private fun FunTopGroup(onClick: () -> Unit) {
    Layout(content = {
        Column(Modifier.testTag("fun_header_text"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("逗宠乐园", style = PetFontFamilies.TitleStyle, fontSize = 26.sp, lineHeight = 34.sp, color = FunPurple)
            Text("用声音和它玩耍，\n让陪伴更有趣！", fontSize = 13.sp, lineHeight = 20.sp, color = Muted)
        }
        Image(painterResource(R.drawable.pet_fun_header), "依偎在一起的猫咪和狗狗",
            Modifier.aspectRatio(1016f / 648f).testTag("fun_header_art"), contentScale = ContentScale.Fit)
        Box(Modifier.testTag("fun_voice_card")) { VoiceCard(onClick) }
    }, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val width = constraints.maxWidth
        val overlap = 10.dp.roundToPx()
        val imageWidth = (width * .60f).toInt().coerceAtMost(300.dp.roundToPx())
        val text = measurables[0].measure(Constraints(maxWidth = (width - imageWidth - 6.dp.roundToPx()).coerceAtLeast(1)))
        val image = measurables[1].measure(Constraints.fixedWidth(imageWidth))
        val card = measurables[2].measure(Constraints.fixedWidth(width))
        val bannerHeight = maxOf(image.height, text.height + overlap + 6.dp.roundToPx())
        val cardTop = bannerHeight - overlap
        layout(width, cardTop + card.height) {
            card.placeRelative(0, cardTop)
            text.placeRelative(0, ((cardTop - text.height) / 2).coerceAtLeast(0))
            image.placeRelative(width - image.width, bannerHeight - image.height)
        }
    }
}

@Composable private fun VoiceCard(onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(26.dp), color = Color.Transparent, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFFFF5FB), Color(0xFFF6E5FB), Color(0xFFE5E7FF))))
            .border(1.dp, Color.White.copy(alpha = .9f), RoundedCornerShape(26.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1.9f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("把我的话说给它听", fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, color = FunPurple)
                Text("录音、自动趣味转换、自动播放", fontSize = 12.sp, lineHeight = 18.sp, color = Muted)
                Button(onClick, contentPadding = PaddingValues(0.dp), shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("fun_voice_start")) {
                    Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(FunPink, Color(0xFFA381F3))))
                        .padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                        FunMicrophone(Modifier.size(22.dp))
                        Text("开始说话", fontSize = 17.sp, lineHeight = 23.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            Image(painterResource(R.drawable.pet_fun_microphone), "粉紫色麦克风插画",
                Modifier.weight(1f).heightIn(min = 120.dp, max = 190.dp).aspectRatio(724f/745f), contentScale = ContentScale.Fit)
        }
    }
}

@Composable private fun PetSwitch(pet: PetType, onSelect: (PetType) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(Color.White.copy(alpha = .65f)).padding(3.dp).selectableGroup()) {
        listOf(PetType.Cat, PetType.Dog).forEach { option ->
            val selected = pet == option
            val tint by animateColorAsState(if (selected) Color(0xFFFFE4F3) else Color(0xFFF4F1FF), label = "funPetTint")
            Row(Modifier.weight(1f).heightIn(min = 52.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = .85f), tint)))
                .border(1.dp, if (selected) FunPink else Color.White, CircleShape)
                .selectable(selected, role = Role.RadioButton, onClick = { onSelect(option) }).testTag("fun_pet_${option.name}")
                .padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                Image(painterResource(if (option == PetType.Cat) R.drawable.pet_cat_main else R.drawable.pet_dog_main), null,
                    Modifier.size(38.dp), contentScale = ContentScale.Fit)
                Text(option.title, color = if (selected) FunPink else Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable private fun SoundRow(sound: PetFunSoundItem, highlighted: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(if (highlighted) Color(0xFFFFE4F4) else Color(0xEEFFFFFF), label = "soundFeedback")
    Surface(onClick, Modifier.fillMaxWidth().testTag("fun_sound_${sound.id}"), shape = RoundedCornerShape(18.dp),
        color = tint, shadowElevation = 1.dp, border = BorderStroke(1.dp, Color.White.copy(alpha = .8f))) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (sound.icon) {
                FunSoundIcon.Meal -> Image(painterResource(R.drawable.scene_food), null, Modifier.size(40.dp), contentScale = ContentScale.Fit)
                FunSoundIcon.Sit, FunSoundIcon.Paw -> Image(painterResource(R.drawable.scene_held), null, Modifier.size(40.dp), contentScale = ContentScale.Fit)
                else -> Text(if (sound.icon == FunSoundIcon.Call) "📣" else "💗", fontSize = 28.sp, modifier = Modifier.width(40.dp).clearAndSetSemantics {})
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(sound.title, color = Ink, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
                Text(sound.subtitle, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
            FunWave(Modifier.width(20.dp).height(16.dp), Color(0xFFCAA8EB))
            IconButton(onClick, Modifier.size(48.dp).testTag("fun_preview_${sound.id}")
                .semantics { contentDescription = "预览${sound.title}音效，资源待接入" }) {
                Canvas(Modifier.size(30.dp)) {
                    drawCircle(Brush.linearGradient(listOf(Color(0xFFD3B4FC), Color(0xFF9C70E6))))
                    val path = Path().apply { moveTo(size.width*.4f,size.height*.3f); lineTo(size.width*.7f,size.height*.5f); lineTo(size.width*.4f,size.height*.7f); close() }
                    drawPath(path, Color.White)
                }
            }
        }
    }
}

@Composable private fun FunWave(modifier: Modifier, color: Color) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val heights = listOf(.25f,.6f,1f,.65f,.3f)
        heights.forEachIndexed { index, height ->
            val x = size.width*(index+1)/6f
            drawLine(color, Offset(x,size.height*(1-height)/2), Offset(x,size.height*(1+height)/2), size.width/11f, StrokeCap.Round)
        }
    }
}
@Composable private fun FunMicrophone(modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val w=size.width; val h=size.height; val stroke=Stroke(w*.08f)
        drawRoundRect(Color.White, Offset(w*.34f,0f), Size(w*.32f,h*.6f), CornerRadius(w*.16f), style=stroke)
        drawArc(Color.White,0f,180f,false,Offset(w*.16f,h*.25f),Size(w*.68f,h*.55f),style=stroke)
        drawLine(Color.White,Offset(w*.5f,h*.8f),Offset(w*.5f,h),w*.08f)
    }
}
