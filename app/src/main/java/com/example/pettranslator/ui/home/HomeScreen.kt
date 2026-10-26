package com.example.pettranslator.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pettranslator.R
import com.example.pettranslator.domain.*
import com.example.pettranslator.ui.theme.*

@Composable
fun HomeScreen(
    state: HomeUiState,
    onPet: (PetType) -> Unit,
    onScene: (PetScene) -> Unit,
    onRecord: () -> Unit,
    onHumanVoice: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Blush, Mist, Blush)))) {
        Column(
            Modifier.widthIn(max = 600.dp).fillMaxWidth().align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HomeHero()
            HomeSection("1", "选择宠物") {
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PetType.Dog, PetType.Cat).forEach { pet ->
                        PetChoice(pet, state.pet == pet, state.ready, { onPet(pet) }, Modifier.weight(1f))
                    }
                }
            }
            HomeSection("2", "它现在在哪里？") {
                BoxWithConstraints {
                    val columns = if (maxWidth < 290.dp || LocalDensity.current.fontScale >= 1.3f) 2 else 3
                    Column(Modifier.selectableGroup().testTag("scene_grid_$columns"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PetScene.entries.chunked(columns).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { scene ->
                                    SceneChoice(scene, state.scene == scene, { onScene(scene) }, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            RecordButton(state.ready, onRecord)
            HumanVoiceEntry(onHumanVoice)
            Text(
                "趣味声音模拟，不代表宠物真实语言翻译",
                style = MaterialTheme.typography.labelSmall, color = Muted,
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpace.page),
            )
        }
    }
}

@Composable
private fun HomeHeader() {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    if (largeFont) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Brand(Modifier.weight(1f)) }
            Box(Modifier.align(Alignment.End)) { VipPlaceholder() }
        }
    } else {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Brand(Modifier.weight(1f))
            VipPlaceholder()
        }
    }
}

@Composable
private fun Brand(modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandPaw()
        Text("宠物翻译器", style = MaterialTheme.typography.titleLarge, color = Ink,
            modifier = Modifier.weight(1f).padding(start = 5.dp))
    }
}

@Composable
private fun BrandPaw() {
    Canvas(Modifier.size(30.dp).clearAndSetSemantics {}) {
        val unit = size.width / 30f
        listOf(6f to 11f, 12f to 6f, 20f to 6f, 26f to 12f).forEach { (x, y) ->
            drawOval(Pink, androidx.compose.ui.geometry.Offset((x - 3f) * unit, (y - 4f) * unit),
                androidx.compose.ui.geometry.Size(6f * unit, 8f * unit))
        }
        drawOval(Pink, androidx.compose.ui.geometry.Offset(7f * unit, 15f * unit),
            androidx.compose.ui.geometry.Size(18f * unit, 14f * unit))
    }
}

@Composable
private fun VipPlaceholder() {
    Surface(
        color = Color(0xFFFFEFBE), shape = RoundedCornerShape(30.dp),
        modifier = Modifier.semantics { contentDescription = "VIP 入口，暂不可用"; disabled() },
    ) {
        Text("♛ VIP", color = Color(0xFF99641C), fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
private fun HomeHero() {
    Box(Modifier.fillMaxWidth().testTag("home_hero")) {
        // Panoramic room fills the measured content height. Text and pets remain separate layers.
        Image(painterResource(R.drawable.pet_home_room_bg), null,
            modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
            Blush.copy(alpha = .68f), Color.White.copy(alpha = .32f), Blush.copy(alpha = .10f), Blush))))
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Box(Modifier.padding(horizontal = AppSpace.page)) { HomeHeader() }
            Column(Modifier.padding(horizontal = AppSpace.page).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = Pink)) { append("今天") }
                    withStyle(SpanStyle(color = Ink)) { append("想听懂谁？") }
                }, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                Text("选择它和场景，听听毛孩子的小心思～", color = Ink,
                    fontSize = 12.sp, lineHeight = 18.sp)
            }
            Image(painterResource(R.drawable.pet_home_hero), null,
                modifier = Modifier.fillMaxWidth().aspectRatio(1419f / 515f).clearAndSetSemantics {},
                contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun HomeSection(number: String, title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpace.page).testTag("home_section_$number"),
        shape = RoundedCornerShape(AppSpace.cardRadius), color = Color.White.copy(alpha = .94f),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(25.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)).background(Brush.verticalGradient(listOf(Color(0xFFFF98CA), Pink)), CircleShape), contentAlignment = Alignment.Center) {
                    Text(number, color = Color.White, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
                }
                Text(title, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = Ink,
                    modifier = Modifier.weight(1f).padding(start = 8.dp))
            }
            content()
        }
    }
}

/** Both cards share selection semantics and an animated tint, without changing state ownership. */
@Composable
private fun ChoiceSurface(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val tint by animateColorAsState(if (selected) Color(0xFFFFE8F3) else Color(0xFFF0F6FF), label = "choiceTint")
    val outline by animateColorAsState(if (selected) Pink else Color(0xFFE6ECF7), label = "choiceOutline")
    Row(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(17.dp))
            .background(Brush.linearGradient(listOf(tint, Color.White.copy(alpha = .76f), tint))).border(if (selected) 1.5.dp else 1.dp, outline, RoundedCornerShape(17.dp))
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp), content = content,
    )
}

@Composable
private fun PetChoice(pet: PetType, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    ChoiceSurface(selected, enabled, onClick, modifier) {
        if (largeFont) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PetImage(pet)
                PetLabel(pet, selected)
            }
        } else {
            PetImage(pet)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PetLabel(pet, selected)
            }
        }
    }
}

@Composable
private fun PetImage(pet: PetType) {
    val resource = if (pet == PetType.Cat) R.drawable.pet_cat_main else R.drawable.pet_dog_main
    val ratio = if (pet == PetType.Cat) 620f / 426f else 697f / 426f
    Image(painterResource(resource), null, contentScale = ContentScale.Fit,
        modifier = Modifier.width(62.dp).aspectRatio(ratio).clearAndSetSemantics {})
}

@Composable
private fun PetLabel(pet: PetType, selected: Boolean) {
    Text(pet.title, style = MaterialTheme.typography.titleMedium, color = Ink)
    Text(if (selected) "✓ 已选择" else "选择", color = if (selected) Color(0xFFB52670) else Muted,
        fontSize = 11.sp, lineHeight = 16.sp)
}

private fun sceneArtwork(scene: PetScene): Int = when (scene) {
    PetScene.Door -> R.drawable.scene_door_window
    PetScene.Meal -> R.drawable.scene_food
    PetScene.Play -> R.drawable.scene_play
    PetScene.Alone -> R.drawable.scene_alone
    PetScene.Held -> R.drawable.scene_held
    PetScene.Unknown -> R.drawable.scene_unknown
}

@Composable
private fun SceneChoice(scene: PetScene, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    ChoiceSurface(selected, true, onClick, modifier.testTag("scene_${scene.name}")) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Image(painterResource(sceneArtwork(scene)), null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(46.dp).clearAndSetSemantics {})
            Text(scene.title, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold,
                color = Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RecordButton(enabled: Boolean, onClick: () -> Unit) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val gradient = Brush.horizontalGradient(listOf(Pink, Color(0xFFB389ED), Blue))
    Button(
        onClick = onClick, enabled = enabled, contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
        shape = RoundedCornerShape(40.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpace.page).testTag("home_record"),
    ) {
        if (largeFont) {
            Column(Modifier.fillMaxWidth().background(gradient).padding(horizontal = 16.dp, vertical = 17.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Microphone(Modifier.size(26.dp))
                RecordLabel(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                Modifier.fillMaxWidth().background(gradient).padding(horizontal = 24.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                Text("✦", color = Color.White, fontSize = 13.sp, modifier = Modifier.clearAndSetSemantics {})
                Microphone(Modifier.size(26.dp))
                RecordLabel(Modifier.widthIn(max = 280.dp).weight(1f, fill = false))
                Text("✧", color = Color.White, fontSize = 13.sp, modifier = Modifier.clearAndSetSemantics {})
            }
        }
    }
}

@Composable
private fun RecordLabel(modifier: Modifier) {
    Text("开始录音翻译", fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold,
        color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = modifier)
}

@Composable
private fun HumanVoiceEntry(onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.padding(horizontal = AppSpace.page), shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("≋", color = Pink, fontSize = 28.sp, modifier = Modifier.padding(end = 10.dp).clearAndSetSemantics {})
            Column(Modifier.weight(1f)) {
                Text("把我的话说给它听", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text("人声变宠物语", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted)
        }
    }
}

@Composable
private fun Microphone(modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val w = size.width; val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = w * .07f)
        drawRoundRect(Color.White, androidx.compose.ui.geometry.Offset(w*.35f, 0f), androidx.compose.ui.geometry.Size(w*.3f,h*.62f), androidx.compose.ui.geometry.CornerRadius(w*.15f), style = stroke)
        drawArc(Color.White, 0f, 180f, false, androidx.compose.ui.geometry.Offset(w*.18f,h*.25f), androidx.compose.ui.geometry.Size(w*.64f,h*.55f), style = stroke)
        drawLine(Color.White, androidx.compose.ui.geometry.Offset(w*.5f,h*.8f), androidx.compose.ui.geometry.Offset(w*.5f,h), w*.07f)
        drawLine(Color.White, androidx.compose.ui.geometry.Offset(w*.3f,h*.97f), androidx.compose.ui.geometry.Offset(w*.7f,h*.97f), w*.07f)
    }
}
