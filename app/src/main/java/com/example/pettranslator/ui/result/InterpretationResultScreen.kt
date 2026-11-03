package com.example.pettranslator.ui.result

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import com.example.pettranslator.ui.components.PetSpeechBubble
import com.example.pettranslator.ui.components.PetSpeechBubbleStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.pettranslator.R
import com.example.pettranslator.domain.PetScene
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.domain.interpretation.PetInterpretationResult
import com.example.pettranslator.domain.interpretation.displayTitle
import com.example.pettranslator.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InterpretationResultScreen(result: PetInterpretationResult, onBack: () -> Unit, onHome: () -> Unit, onAgain: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); notice = null } }

    suspend fun export(save: Boolean) {
        if (busy) return
        busy = true
        try {
            val output = withContext(Dispatchers.IO) {
                val bitmap = ResultCardExporter.render(context, result)
                try { if (save) ResultCardExporter.save(context, bitmap) else ResultCardExporter.writeShareFile(context, bitmap) }
                finally { bitmap.recycle() }
            }
            if (save) notice = if (output == true) "结果卡片已保存到相册" else "保存失败，请稍后重试"
            else { ResultCardExporter.share(context, output as File); notice = "分享卡片已准备好" }
        } catch (_: ActivityNotFoundException) { notice = "没有可用的分享应用" }
        catch (_: Exception) { notice = if (save) "保存失败，请检查存储权限" else "分享失败，请稍后重试" }
        finally { busy = false }
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) scope.launch { export(true) } else notice = "需要照片存储权限才能保存卡片"
    }

    val headerArt = if (result.petType == PetType.Cat) R.drawable.result_cat_header else R.drawable.result_dog_header
    val quoteArt = if (result.petType == PetType.Cat) R.drawable.result_cat_quote else R.drawable.result_dog_quote
    val tipArt = if (result.petType == PetType.Cat) R.drawable.result_cat_tip else R.drawable.result_dog_tip
    Scaffold(modifier = Modifier.fillMaxSize().testTag("result_page")
        .background(Brush.verticalGradient(listOf(Color(0xFFFFF2F8), Color(0xFFF2F3FF), Color(0xFFFFF6FA)))),
        containerColor = Color.Transparent, contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            Row(Modifier.fillMaxWidth().testTag("result_actions")
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 10.dp, vertical = 7.dp).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionTile("↻", "再翻译一次", Brush.linearGradient(listOf(Color.White, Color(0xFFE9F1FF))), Ink,
                    enabled = !busy, onClick = onAgain, modifier = Modifier.weight(1f))
                ActionTile("☆", if (busy) "处理中" else "保存结果", Brush.linearGradient(listOf(Color(0xFFFF70B6), Color(0xFFDD50B8))), Color.White,
                    enabled = !busy, onClick = {
                        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                            saveLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        else scope.launch { export(true) }
                    }, modifier = Modifier.weight(1f))
                ActionTile("•", "分享心声", Brush.linearGradient(listOf(Color(0xFF65C3F4), Color(0xFF4A91EF))), Color.White,
                    enabled = !busy, onClick = { scope.launch { export(false) } }, modifier = Modifier.weight(1f))
            }
        }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)
            .verticalScroll(rememberScrollState()).testTag("result_scroll")) {
            ResultHeroWithBubble(result.petType, navigation = {
                Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                    Text("翻译结果", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 2.dp).testTag("recording_back")) { Text("‹", color = Ink, fontSize = 30.sp) }
                    TextButton(onClick = onHome, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).testTag("result_home")) { Text("⌂", color = Ink, fontSize = 22.sp) }
                }
            }) {
                Image(painterResource(headerArt), contentDescription = "${result.petType.title}在温馨房间里的插画",
                    contentScale = ContentScale.Crop, alignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().height(208.dp).align(Alignment.BottomCenter))
                Box(Modifier.fillMaxWidth().height(208.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(0f to Color.Transparent, .62f to Color.Transparent, 1f to Color(0xFFFFF2F8))))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PetInfoBar(result)
                QuoteCard(result, quoteArt)
                SoundAnalysisCard(result)
                InteractionCard(result.interactionSuggestion, tipArt)
                Text("趣味解读仅供娱乐参考，请结合宠物实际表现判断。", Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    color = Muted, fontSize = 10.sp, lineHeight = 15.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable private fun ResultCelebrationBubble(modifier: Modifier = Modifier) {
    PetSpeechBubble("毛孩子的\n心声来啦！", modifier, style = PetSpeechBubbleStyle.Dialogue,
        fontSize = 16.sp, rotationDegrees = -3f)
}

/** Keep the existing 208dp artwork/crop; the extra room belongs only to the
 * speech bubble and grows with accessibility text instead of covering the pet. */
@Composable private fun ResultHeroWithBubble(
    pet: PetType,
    navigation: @Composable () -> Unit,
    artwork: @Composable BoxScope.() -> Unit,
) {
    SubcomposeLayout(Modifier.fillMaxWidth().testTag("result_header")) { constraints ->
        val width = constraints.maxWidth
        val artHeight = 208.dp.roundToPx()
        val nav = subcompose("navigation", navigation).single()
            .measure(androidx.compose.ui.unit.Constraints.fixedWidth(width))
        val bubbleWidth = maxOf(width * .40f, 16.sp.toPx() * 5 + 40.dp.toPx())
            .coerceAtMost(width - 12.dp.toPx()).toInt()
        val bubble = subcompose("resultBubble") {
            ResultCelebrationBubble(Modifier.testTag("result_art_bubble"))
        }.single().measure(androidx.compose.ui.unit.Constraints.fixedWidth(bubbleWidth))
        // The current opaque header resources place both pets near the center.
        // Their highest important details start about 38dp/40dp below the crop.
        val earliestPetY = (if (pet == PetType.Cat) 38.dp else 40.dp).roundToPx()
        val bubbleY = nav.height + 2.dp.roundToPx()
        val headroom = (bubbleY + bubble.height + 4.dp.roundToPx() - earliestPetY).coerceAtLeast(0)
        val art = subcompose("artwork") { Box(Modifier.fillMaxWidth().height(208.dp), content = artwork) }
            .single().measure(androidx.compose.ui.unit.Constraints.fixed(width, artHeight))
        layout(width, artHeight + headroom) {
            art.place(0, headroom)
            nav.place(0, 0)
            bubble.place(width - bubble.width - 6.dp.roundToPx(), bubbleY)
        }
    }
}

@Composable internal fun PetInfoBar(result: PetInterpretationResult) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val baseStyle = LocalTextStyle.current
    val dateStyle = baseStyle.copy(fontSize = 10.sp, lineHeight = 14.sp)
    val fullDate = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA).format(Date(result.createdAtEpochMs))
    val shortDate = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(Date(result.createdAtEpochMs))
    fun textWidth(text: String, style: androidx.compose.ui.text.TextStyle) =
        with(density) { measurer.measure(text, baseStyle.merge(style), softWrap = false).size.width.toDp() } + 2.dp
    Surface(modifier = Modifier.fillMaxWidth().testTag("result_pet_info"),
        shape = RoundedCornerShape(20.dp), color = Color(0xFFFFF9FC),
        border = BorderStroke(.5.dp, Color(0xFFF5E3EE))) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                val leftWidth = 48.dp + textWidth(result.petType.subtitle,
                    androidx.compose.ui.text.TextStyle(fontSize = 11.sp))
                val sceneWidth = 42.dp + textWidth(result.scene.title,
                    androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                val fullWidth = maxOf(sceneWidth, textWidth(fullDate, dateStyle))
                val shortWidth = maxOf(sceneWidth, textWidth(shortDate, dateStyle))
                val useFullDate = leftWidth + 12.dp + fullWidth <= maxWidth
                val rightWidth = if (useFullDate) fullWidth else shortWidth
                val stacked = leftWidth + 12.dp + rightWidth > maxWidth
                val petContent: @Composable () -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PawBadge()
                        Column {
                            Text(result.petType.title, color = Ink, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
                            Text(result.petType.subtitle, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }
                val sceneContent: @Composable () -> Unit = {
                    Column(Modifier.width(rightWidth), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.background(Color(0xFFF0E9FC), RoundedCornerShape(16.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp).testTag("result_scene"),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Image(painterResource(sceneIcon(result.scene)), contentDescription = null,
                                contentScale = ContentScale.Fit, modifier = Modifier.size(22.dp))
                            Text(result.scene.title, color = Ink, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(if (useFullDate) fullDate else shortDate, color = Muted, style = dateStyle,
                            maxLines = 1, softWrap = false, modifier = Modifier.testTag("result_created_at"))
                    }
                }
                if (stacked) {
                    // Large-font fallback preserves readable pet text and a single-line date.
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        petContent()
                        Box(Modifier.align(Alignment.End)) { sceneContent() }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { petContent() }
                        Spacer(Modifier.width(12.dp))
                        sceneContent()
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(
                listOf(Color(0xFFFBEAF3), Color(0xFFF3CDE3), Color(0xFFFBEAF3)))))
            Spacer(Modifier.height(3.dp))
        }
    }
}

@Composable private fun PawBadge() {
    Canvas(Modifier.size(40.dp).background(Color(0xFFFFE7F3), CircleShape).padding(7.dp)) {
        val pink = Color(0xFFF05AAA)
        fun oval(x: Float, y: Float, w: Float, h: Float) = drawOval(pink,
            androidx.compose.ui.geometry.Offset(size.width * x, size.height * y),
            androidx.compose.ui.geometry.Size(size.width * w, size.height * h))
        oval(.04f, .25f, .20f, .29f)
        oval(.25f, .04f, .21f, .31f)
        oval(.54f, .04f, .21f, .31f)
        oval(.76f, .25f, .20f, .29f)
        val pad = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * .5f, size.height * .40f)
            cubicTo(size.width * .30f, size.height * .40f, size.width * .10f, size.height * .80f, size.width * .25f, size.height * .88f)
            cubicTo(size.width * .38f, size.height * .96f, size.width * .43f, size.height * .84f, size.width * .5f, size.height * .86f)
            cubicTo(size.width * .57f, size.height * .84f, size.width * .62f, size.height * .96f, size.width * .75f, size.height * .88f)
            cubicTo(size.width * .90f, size.height * .80f, size.width * .70f, size.height * .40f, size.width * .5f, size.height * .40f)
            close()
        }
        drawPath(pad, pink)
    }
}

@Composable private fun QuoteCard(result: PetInterpretationResult, quoteArt: Int) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val quote = "“${result.innerMonologue}”"
    val textStyle = LocalTextStyle.current.merge(PetFontFamilies.MonologueStyle)
        .copy(fontSize = 18.sp, lineHeight = 25.sp)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val artworkWidth = if (maxWidth < 350.dp) 88.dp else 106.dp
        val textWidth = (maxWidth - 19.dp - 8.dp - artworkWidth).coerceAtLeast(1.dp)
        val sideLayout = measurer.measure(quote, textStyle,
            constraints = androidx.compose.ui.unit.Constraints(maxWidth = with(density) { textWidth.roundToPx() }))
        val stacked = sideLayout.lineCount > 4 || result.innerMonologue.codePointCount(0, result.innerMonologue.length) > 35 ||
            (maxWidth < 300.dp && density.fontScale > 1.3f)
        val textContent: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFE95AAB)) {
                    Text("♥ ${result.coreThemeId.displayTitle}", Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        color = Color.White, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(quote, color = Ink, style = textStyle, modifier = Modifier.testTag("font_trial_monologue"))
            }
        }
        val art: @Composable (Modifier) -> Unit = { modifier ->
            Image(painterResource(quoteArt), contentDescription = "${result.petType.title}插画",
                contentScale = ContentScale.Fit, modifier = modifier.width(if (stacked) 68.dp else artworkWidth)
                    .aspectRatio(.76f).testTag("result_quote_art"))
        }
        Surface(modifier = Modifier.fillMaxWidth().testTag("result_quote"),
            shape = RoundedCornerShape(22.dp), color = Color.Transparent, shadowElevation = 2.dp) {
            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFFFE2F0), Color(0xFFF2E6FF), Color(0xFFFFF1F8))),
                RoundedCornerShape(22.dp)).padding(start = 14.dp, top = 12.dp, bottom = 10.dp, end = 5.dp)) {
                if (stacked) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        textContent()
                        art(Modifier.align(Alignment.End))
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { textContent() }
                        art(Modifier)
                    }
                }
            }
        }
    }
}

@Composable private fun SoundAnalysisCard(result: PetInterpretationResult) {
    val features = result.soundFeatures
    val petTitle = if (result.petType == PetType.Cat) "猫咪" else "狗狗"
    val hasWindowStats = features.windowCount > 0
    Surface(shape = RoundedCornerShape(19.dp), color = Color.White.copy(alpha = .94f), shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(29.dp).clip(CircleShape).background(Color(0xFFFFE9F3)), contentAlignment = Alignment.Center) {
                    Text("♫", color = Pink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Text("AI 声音分析", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Text("AI 检测到${petTitle}相关声音，并完成了音频分段分析。", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            if (hasWindowStats) {
                Row(Modifier.fillMaxWidth().background(Color(0xFFFFF4F9), RoundedCornerShape(13.dp)).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    StatCell("音频分析片段", "${features.windowCount} 段", Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(28.dp).background(Color(0xFFE9DDE8)))
                    StatCell("相关声音证据", "${features.windowsWithPetEvidence} 段", Modifier.weight(1f))
                }
                Text("AI 对录音进行分段识别，其中 ${features.windowsWithPetEvidence} 个片段检测到与${petTitle}相关的声音类别。统计表示证据分布，不代表识别准确率或情绪判断。",
                    color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Muted, fontSize = 9.sp, lineHeight = 13.sp, textAlign = TextAlign.Center)
        Text(value, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun InteractionCard(suggestion: String, tipArt: Int) {
    val lines = suggestionLines(suggestion)
    Surface(shape = RoundedCornerShape(19.dp), color = Color.White.copy(alpha = .94f), shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 7.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("☼", color = Color(0xFFFFB335), fontSize = 18.sp)
                    Text("互动建议", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                lines.forEach { line ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("✓", color = Color(0xFF31C88A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(line, color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                    }
                }
            }
            Image(painterResource(tipArt), contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.width(66.dp).aspectRatio(0.75f))
        }
    }
}

@Composable private fun ActionTile(glyph: String, label: String, brush: Brush, contentColor: Color,
                                   enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(17.dp), color = Color.Transparent,
        modifier = modifier.heightIn(min = 65.dp).fillMaxHeight()) {
        Column(Modifier.fillMaxWidth().background(brush, RoundedCornerShape(17.dp)).padding(horizontal = 4.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(glyph, color = contentColor, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
            Text(label, color = contentColor, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

private fun sceneIcon(scene: PetScene): Int = when (scene) {
    PetScene.Door -> R.drawable.scene_door_window
    PetScene.Meal -> R.drawable.scene_food
    PetScene.Play -> R.drawable.scene_play
    PetScene.Alone -> R.drawable.scene_alone
    PetScene.Held -> R.drawable.scene_held
    PetScene.Unknown -> R.drawable.scene_unknown
}

/** Breaks the approved engine suggestion for display without inventing extra advice. */
private fun suggestionLines(suggestion: String): List<String> {
    val text = suggestion.trim()
    val sentenceParts = text.split(Regex("(?<=[。！？；])")).filter { it.isNotBlank() }
    if (sentenceParts.size > 1) return listOf(sentenceParts.first(), sentenceParts.drop(1).joinToString(""))
    val comma = text.indexOf('，')
    return if (comma in 5 until text.lastIndex) listOf(text.substring(0, comma + 1), text.substring(comma + 1)) else listOf(text)
}
