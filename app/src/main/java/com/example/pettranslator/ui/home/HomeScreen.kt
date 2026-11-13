package com.example.pettranslator.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.ceil
import com.example.pettranslator.R
import com.example.pettranslator.domain.*
import com.example.pettranslator.ui.theme.*

@Composable
fun HomeScreen(
    state: HomeUiState,
    onPet: (PetType) -> Unit,
    onScene: (PetScene) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("home_viewport")
        .background(Brush.verticalGradient(listOf(Color(0xFFFFEAF4), Color(0xFFE9F2FF), Color(0xFFF4ECFF), Blush)))) {
        val viewport = maxHeight
        val scale = LocalDensity.current.fontScale
        SubcomposeLayout(Modifier.widthIn(max = 600.dp).fillMaxWidth().align(Alignment.TopCenter)
            .verticalScroll(rememberScrollState())) { constraints ->
            val childConstraints = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            val hero = subcompose("hero") {
                HomeHero((viewport.value * .21f + 2f).coerceIn(114f, 152f).dp)
            }.single().measure(childConstraints)
            val dimensions = HomeResponsiveDimensions.resolve((viewport.toPx() - hero.height) / density,
                scale, constraints.maxWidth / density)
            val modules = subcompose("modules") { HomeSelectionModules(state, onPet, onScene, dimensions) }
                .single().measure(childConstraints)
            layout(constraints.maxWidth, hero.height + modules.height) {
                hero.placeRelative(0, 0)
                modules.placeRelative(0, hero.height)
            }
        }
    }
}

@Composable
private fun HomeSelectionModules(state: HomeUiState, onPet: (PetType) -> Unit,
    onScene: (PetScene) -> Unit, sizes: HomeResponsiveDimensions) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        val cloudX = (maxWidth - sizes.cloudWidth.dp) / 2
        // Retain the old section's height budget; removing its title must not grow or move the cloud.
        val petSectionHeight = with(LocalDensity.current) { 22.sp.toDp() } + 14.dp + sizes.petButtonHeight.dp
        Box(Modifier.fillMaxWidth().height((sizes.cloudTop + sizes.cloudHeight).dp)) {
            HomeCloud(state, onScene, sizes,
                Modifier.offset(cloudX, sizes.cloudTop.dp).size(sizes.cloudWidth.dp, sizes.cloudHeight.dp)
                    .testTag("home_section_2"))
            Box(Modifier.fillMaxWidth().height(petSectionHeight).testTag("home_section_1"),
                contentAlignment = Alignment.Center) {
                val shape = RoundedCornerShape(50)
                Row(Modifier.width(sizes.petButtonWidth.dp * 2 + 4.dp).height(sizes.petButtonHeight.dp)
                    .testTag("home_pet_switch").selectableGroup()
                    .shadow(2.dp, shape, ambientColor = Pink.copy(alpha = .12f), spotColor = Blue.copy(alpha = .12f))
                    .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF6F2FF))), shape)
                    .padding(horizontal = 2.dp)) {
                    listOf(PetType.Dog, PetType.Cat).forEach { pet ->
                        PetChoice(pet, state.pet == pet, state.ready, { onPet(pet) },
                            Modifier.width(sizes.petButtonWidth.dp).height(sizes.petButtonHeight.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCloud(state: HomeUiState, onScene: (PetScene) -> Unit,
    sizes: HomeResponsiveDimensions, modifier: Modifier) {
    Box(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize().testTag("home_cloud")) {
            val contentWidth = maxWidth
            val contentHeight = maxHeight
            if (sizes.expandedCloud) {
                // Extend the cloud silhouette for accessibility fonts; keep the supplied image undistorted.
                val shape = remember { GenericShape { size, _ ->
                    val radius = size.width * .20f
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(
                        androidx.compose.ui.geometry.Rect(radius * .35f, 0f, size.width - radius * .35f, size.height),
                        androidx.compose.ui.geometry.CornerRadius(radius)))
                    val count = (size.height / (radius * 1.7f)).toInt().coerceAtLeast(2)
                    repeat(count) { index ->
                        val y = radius + (size.height - 2f * radius) * index / (count - 1)
                        addOval(androidx.compose.ui.geometry.Rect(0f, y - radius, radius * 2f, y + radius))
                        addOval(androidx.compose.ui.geometry.Rect(size.width - radius * 2f, y - radius, size.width, y + radius))
                    }
                } }
                Box(Modifier.matchParentSize().shadow(10.dp, shape,
                    ambientColor = Pink.copy(alpha = .25f), spotColor = Blue.copy(alpha = .25f))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFFAFE), Color(0xFFF6F2FF))), shape))
            }
            if (sizes.expandedCloud) {
                Image(painterResource(R.drawable.pastel_cloud_pet_app_frame), contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.matchParentSize().testTag("home_cloud_artwork"))
            } else {
                CloudBackdrop(Modifier.matchParentSize().testTag("home_cloud_artwork"))
            }
            val titleHeight = with(LocalDensity.current) { 26.sp.toDp() }
            Text("它现在在哪里？", color = Ink, fontSize = 18.sp, lineHeight = 26.sp,
                fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.offset(y = sizes.titleY.dp - titleHeight / 2)
                    .fillMaxWidth().testTag("home_scene_title"))
            Box(Modifier.fillMaxSize().selectableGroup().testTag("scene_orbit")) {
                PetScene.entries.forEachIndexed { index, scene ->
                    val island = sizes.islands[index]
                    SceneChoice(scene, state.scene == scene, { onScene(scene) },
                        Modifier.offset(contentWidth * island.xFraction - (island.diameter / 2).dp,
                            contentHeight * island.yFraction - (island.diameter / 2).dp)
                            .size(island.diameter.dp), 2f, (island.diameter * .48f).coerceIn(28f, 44f))
                }
            }
            Box(Modifier.offset((contentWidth - sizes.centerPetWidth.dp) / 2,
                contentHeight * sizes.centerPetYFraction - (sizes.centerPetHeight / 2).dp)
                .size(sizes.centerPetWidth.dp, sizes.centerPetHeight.dp).testTag("home_center_pet")) {
                Image(painterResource(petArtwork(state.pet)), contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                        .height((sizes.centerPetHeight * if (state.pet == PetType.Dog) .96f else 1f).dp)
                        .align(Alignment.Center).testTag("home_center_pet_${state.pet.name}"))
            }
        }
    }
}

@Composable
private fun CloudBackdrop(modifier: Modifier) {
    val artwork = ImageBitmap.imageResource(R.drawable.pastel_cloud_pet_app_frame)
    Canvas(modifier) {
        // All source rows remain visible. Only the diffuse bottom glow is compacted;
        // the complete cloud contour gets the available width without a crop.
        val split = minOf(1210, artwork.height - 1)
        val width = size.width.roundToInt()
        val height = size.height.roundToInt()
        val tail = minOf(8.dp.toPx().roundToInt(), height / 10).coerceAtLeast(1)
        val body = height - tail
        drawImage(artwork, srcSize = IntSize(artwork.width, split),
            dstSize = IntSize(width, body), filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
        drawImage(artwork, srcOffset = IntOffset(0, split),
            srcSize = IntSize(artwork.width, artwork.height - split),
            dstOffset = IntOffset(0, body), dstSize = IntSize(width, tail),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
    }
}

/** Owned by the app Scaffold so it stays visible while HomeScreen scrolls. */
@Composable
fun HomeRecordActionBar(enabled: Boolean, onRecord: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFFF8EFFB), Blush)))
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().align(Alignment.Center)
            .padding(top = 6.dp, bottom = 6.dp).testTag("home_actions"),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RecordButton(enabled, onRecord)
            Text("趣味声音模拟，不代表宠物真实语言翻译",
                style = MaterialTheme.typography.labelSmall, color = Muted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpace.page))
        }
    }
}

@Composable
private fun HomeHeader() {
    val name = stringResource(R.string.app_name)
    val textMeasurer = rememberTextMeasurer()
    val titleStyle = LocalTextStyle.current.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        BoxWithConstraints(Modifier.weight(1f).height(44.dp).padding(end = 8.dp),
            contentAlignment = Alignment.CenterStart) {
            val widthBudget = (constraints.maxWidth - with(LocalDensity.current) { 2.dp.roundToPx() }).coerceAtLeast(1)
            fun measureTitle(fontSize: Float) = textMeasurer.measure(name,
                titleStyle.copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.25f).sp),
                softWrap = false, maxLines = 1)
            fun fits(fontSize: Float): Boolean {
                val paragraph = measureTitle(fontSize).multiParagraph
                return paragraph.maxIntrinsicWidth <= widthBudget && paragraph.height <= constraints.maxHeight
            }
            var fontSize = 16f
            if (!fits(fontSize)) {
                var lower = 1f
                var upper = fontSize
                repeat(12) {
                    val candidate = (lower + upper) / 2f
                    if (fits(candidate)) lower = candidate else upper = candidate
                }
                fontSize = lower
            }
            val fittedStyle = titleStyle.copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.25f).sp)
            val fittedWidth = ceil(measureTitle(fontSize).multiParagraph.maxIntrinsicWidth).toInt()
            // Leave raster rounding room when a large accessibility font reaches the width limit.
            val textWidth = with(LocalDensity.current) { (fittedWidth + 2).toDp() }.coerceAtMost(maxWidth)
            Text(name, color = Color(0xFFB64D91),
                style = fittedStyle, maxLines = 1, softWrap = false,
                modifier = Modifier.width(textWidth).testTag("home_brand"))
        }
        VipPlaceholder()
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
private fun HomeHero(illustrationHeight: androidx.compose.ui.unit.Dp) {
    Box(Modifier.fillMaxWidth().testTag("home_hero")) {
        // Panoramic room fills the measured content height. Text and pets remain separate layers.
        Image(painterResource(R.drawable.pet_home_room_bg), null,
            modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
            Color(0xFFFFEAF4).copy(alpha = .82f), Color.White.copy(alpha = .52f),
            Color(0xFFE9F2FF).copy(alpha = .28f), Color(0xFFE9F2FF)))))
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Box(Modifier.padding(horizontal = AppSpace.page)) { HomeHeader() }
            Image(painterResource(R.drawable.pet_home_hero), null,
                modifier = Modifier.fillMaxWidth().height(illustrationHeight).clearAndSetSemantics {},
                contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun PetChoice(pet: PetType, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val tint by animateColorAsState(if (selected) Color(0xFFFFE4F0) else Color(0xFFFCFAFF), label = "petTint")
    val shape = RoundedCornerShape(50)
    Row(modifier.testTag("pet_${pet.name}")
        .shadow(if (selected) 3.dp else 0.dp, shape,
            ambientColor = Pink.copy(alpha = .18f), spotColor = Blue.copy(alpha = .14f))
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color.White, tint)))
        .border(if (selected) 1.5.dp else 0.dp, if (selected) Color(0xFFF39AC3) else Color.Transparent, shape)
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        Image(painterResource(petArtwork(pet)), contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.size(40.dp, 32.dp))
        Text(pet.title, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Color(0xFFB64D91) else Ink,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
    }
}

private fun petArtwork(pet: PetType): Int =
    if (pet == PetType.Cat) R.drawable.pet_cat_main else R.drawable.pet_dog_main

private fun sceneArtwork(scene: PetScene): Int = when (scene) {
    PetScene.Door -> R.drawable.scene_door_window
    PetScene.Meal -> R.drawable.scene_food
    PetScene.Play -> R.drawable.scene_play
    PetScene.Alone -> R.drawable.scene_alone
    PetScene.Held -> R.drawable.scene_held
    PetScene.Unknown -> R.drawable.scene_unknown
}

@Composable
private fun SceneChoice(scene: PetScene, selected: Boolean, onClick: () -> Unit, modifier: Modifier,
    iconTextGap: Float, iconSize: Float) {
    val tint by animateColorAsState(if (selected) Color(0xFFFFE7F2) else Color(0xFFF7F5FF), label = "sceneTint")
    val shape = remember { GenericShape { size, _ -> addPath(sceneCloudPath(size)) } }
    Box(modifier.testTag("scene_${scene.name}")
        .shadow(if (selected) 5.dp else 4.dp, shape,
            ambientColor = Color(0xFFBBA6BB).copy(alpha = .14f),
            spotColor = Color(0xFF8B7592).copy(alpha = .18f))
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFFFFCFD), tint)))
        .border(if (selected) 1.5.dp else 1.dp,
            if (selected) Color(0xFFF1A0C5) else Color(0xFFE8DFEF), shape)
        .drawWithCache {
            val inset = 3.dp.toPx()
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
            val highlight = Brush.verticalGradient(listOf(Color.White, Color(0xFFFFFEFF), Color(0xFFF1E4EB)))
            val highlightPath = sceneCloudPath(Size((size.width - 2f * inset).coerceAtLeast(0f),
                (size.height - 2f * inset).coerceAtLeast(0f))).apply { translate(Offset(inset, inset)) }
            onDrawWithContent {
                drawPath(highlightPath, highlight, style = stroke)
                drawContent()
            }
        }
        .selectable(selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val diameter = maxWidth
            val labelHeight = with(LocalDensity.current) { 16.sp.toDp() }
            // Long labels sit in the widest part of the circle, above the curved lower edge.
            val labelTop = (diameter * if (scene.title.length >= 4) .60f else .64f) - labelHeight / 2
            val pictureSize = minOf(iconSize.dp, (labelTop - 6.dp).coerceAtLeast(12.dp))
            Image(painterResource(sceneArtwork(scene)), null, contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.TopCenter)
                    .offset(y = labelTop - pictureSize - iconTextGap.dp)
                    .size(pictureSize).clearAndSetSemantics {})
            Text(scene.title, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold,
                color = Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.offset(y = labelTop).fillMaxWidth().padding(horizontal = 4.dp))
        }
    }
}

/** Rounded, unequal cloud lobes stay inside the old circle, retaining its conservative collision bounds. */
private fun sceneCloudPath(size: Size): Path {
    val radius = size.minDimension / 2f
    val points = List(96) { index ->
        val angle = 2.0 * PI * index / 96
        val ripple = .96 + .032 * cos(8.0 * angle + PI) + .008 * cos(3.0 * angle - .65)
        // Labels occupy the lower half, including wrapped accessibility text; retain its old circular safety arc.
        val labelBand = ((sin(angle) + .08) / .30).coerceIn(0.0, 1.0)
        val blend = labelBand * labelBand * (3.0 - 2.0 * labelBand)
        val outline = ripple + (1.0 - ripple) * blend
        Offset(size.width / 2f + (radius * outline * cos(angle)).toFloat(),
            size.height / 2f + (radius * outline * sin(angle)).toFloat())
    }
    return Path().apply {
        val start = (points.last() + points.first()) / 2f
        moveTo(start.x, start.y)
        points.forEachIndexed { index, point ->
            val midpoint = (point + points[(index + 1) % points.size]) / 2f
            // Every control point is inside the old circle, so the quadratic curves are too.
            quadraticTo(point.x, point.y, midpoint.x, midpoint.y)
        }
        close()
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
                Modifier.fillMaxWidth().background(gradient).padding(horizontal = 20.dp, vertical = 11.dp),
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
