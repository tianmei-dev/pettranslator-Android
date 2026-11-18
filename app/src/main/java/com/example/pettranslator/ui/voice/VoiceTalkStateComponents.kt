package com.example.pettranslator.ui.voice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pettranslator.R
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.ui.recording.rmsToWaveLevel
import com.example.pettranslator.ui.theme.PetTranslatorTheme
import java.util.Locale

internal fun formatVoiceTime(elapsedMs: Long): String {
    val seconds = elapsedMs.coerceAtLeast(0L) / 1_000L
    return String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L)
}

@Composable
internal fun VoiceCaptureControl(state: VoiceTalkUiState, enabled: Boolean, onAction: () -> Unit, modifier: Modifier) {
    val ready = state.phase == VoiceTalkPhase.READY
    val recording = state.phase == VoiceTalkPhase.RECORDING
    val sampleLevels = if (ready) null else List((24 - state.levels.size).coerceAtLeast(0)) { 0f } + state.levels.takeLast(24)
    Column(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!ready) {
            Row(Modifier.padding(bottom = 7.dp).testTag("voice_talk_timer"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Canvas(Modifier.size(8.dp).clearAndSetSemantics {}) { drawCircle(VoicePink) }
                Text(formatVoiceTime(state.elapsedMs), color = VoicePink, fontSize = 16.sp,
                    lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                Text("/ 00:10", color = VoiceInk, fontSize = 15.sp, lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold)
            }
        }
        Row(Modifier.fillMaxWidth().height(136.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoiceWaveBars(sampleLevels?.take(12), Modifier.weight(1f).height(52.dp)
                .testTag(if (ready) "voice_talk_wave_idle_left" else "voice_talk_wave_live_left"))
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .85f),
                        Color.White.copy(alpha = .1f))))
                    drawCircle(Color.White.copy(alpha = .7f), radius = size.minDimension * .44f,
                        style = Stroke(3.dp.toPx()))
                }
                if (!ready) {
                    val progress = (state.elapsedMs / 10_000f).coerceIn(0f, 1f)
                    Canvas(Modifier.size(120.dp).testTag("voice_talk_record_progress").semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                        contentDescription = "10秒录音进度"
                    }) {
                        val inset = 3.dp.toPx()
                        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                        drawArc(VoicePink.copy(alpha = .18f), -90f, 360f, false, Offset(inset, inset), arcSize,
                            style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                        if (progress > 0f) drawArc(VoicePink, -90f, 360f * progress, false,
                            Offset(inset, inset), arcSize, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
                val colors = if (ready) listOf(Color(0xFFDE99FF), Color(0xFFA44DEF), Color(0xFFB471EF))
                    else listOf(Color(0xFFFFA1DB), Color(0xFFFA72C1), Color(0xFFF780CE))
                Surface(shape = CircleShape, shadowElevation = if (enabled) 5.dp else 0.dp,
                    color = Color.Transparent, modifier = Modifier.size(96.dp)) {
                    Box(Modifier.fillMaxSize().clip(CircleShape)
                        .background(Brush.linearGradient(if (enabled) colors else colors.map { it.copy(alpha = .65f) }))
                        .clickable(enabled = enabled, role = Role.Button, onClick = onAction)
                        .testTag(if (ready) "voice_talk_record" else "voice_talk_stop")
                        .semantics { contentDescription = if (ready) "开始录音" else "停止录音" },
                        contentAlignment = Alignment.Center) {
                        VoiceCaptureGlyph(stop = !ready, modifier = Modifier.size(42.dp))
                    }
                }
            }
            VoiceWaveBars(sampleLevels?.takeLast(12), Modifier.weight(1f).height(52.dp)
                .testTag(if (ready) "voice_talk_wave_idle_right" else "voice_talk_wave_live_right"))
        }
        Text(when {
            ready -> "点击开始录音"
            recording -> "录音中… 点击结束"
            state.phase == VoiceTalkPhase.STARTING -> "正在准备麦克风…"
            else -> "正在结束录音…"
        }, modifier = Modifier.padding(top = 4.dp).testTag("voice_talk_capture_status"),
            color = VoiceInk, fontSize = 16.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center)
    }
}

/** Real microphone RMS is rendered directly; READY uses a separate, static decoration. */
@Composable
private fun VoiceWaveBars(levels: List<Float>?, modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val bars = levels?.map(::rmsToWaveLevel) ?: listOf(.08f, .22f, .35f, .15f, .55f, .38f, .18f, .32f, .13f)
        val step = size.width / bars.size.coerceAtLeast(1)
        val width = (step * .5f).coerceAtMost(4.dp.toPx())
        val brush = Brush.verticalGradient(if (levels == null) listOf(Color(0xFFCFABFA), Color(0xFFE3CEFC))
            else listOf(Color(0xFFD18AFF), VoicePink))
        bars.forEachIndexed { index, level ->
            val height = (3.dp.toPx() + (size.height - 3.dp.toPx()) * level).coerceAtMost(size.height)
            drawRoundRect(brush, Offset(step * (index + .5f) - width / 2, (size.height - height) / 2),
                Size(width, height), CornerRadius(width / 2))
        }
    }
}

@Composable
private fun VoiceCaptureGlyph(stop: Boolean, modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val w = size.width
        val h = size.height
        if (stop) {
            drawRoundRect(Color.White, Offset(w * .15f, h * .15f), Size(w * .7f, h * .7f), CornerRadius(5.dp.toPx()))
        } else {
            val stroke = Stroke(2.8.dp.toPx(), cap = StrokeCap.Round)
            drawRoundRect(Color.White, Offset(w * .33f, h * .05f), Size(w * .34f, h * .52f), CornerRadius(w * .17f), style = stroke)
            drawArc(Color.White, 0f, 180f, false, Offset(w * .2f, h * .27f), Size(w * .6f, h * .49f), style = stroke)
            drawLine(Color.White, Offset(w * .5f, h * .76f), Offset(w * .5f, h * .94f), stroke.width, StrokeCap.Round)
            drawLine(Color.White, Offset(w * .34f, h * .94f), Offset(w * .66f, h * .94f), stroke.width, StrokeCap.Round)
        }
    }
}

@Composable
internal fun VoicePaw(modifier: Modifier, color: Color) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val w = size.width
        val h = size.height
        drawOval(color, Offset(w * .25f, h * .52f), Size(w * .53f, h * .4f))
        drawOval(color, Offset(w * .04f, h * .34f), Size(w * .22f, h * .3f))
        drawOval(color, Offset(w * .24f, h * .08f), Size(w * .23f, h * .31f))
        drawOval(color, Offset(w * .53f, h * .06f), Size(w * .23f, h * .31f))
        drawOval(color, Offset(w * .77f, h * .3f), Size(w * .2f, h * .3f))
    }
}

@Composable
internal fun VoiceGradientButton(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Button(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(28.dp), modifier = modifier.heightIn(min = 50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(
            listOf(VoicePink.copy(alpha = if (enabled) 1f else .5f), Color(0xFFAA78EF).copy(alpha = if (enabled) 1f else .5f))))
            .padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
        }
    }
}

/** B3.2 can show this only while its actual conversion task is running. The paw conveys no progress. */
@Composable
fun VoiceTalkConvertingContent(pet: PetType?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 10.dp).testTag("voice_talk_converting"),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.voice_talk_converting_paw), null,
            Modifier.size(142.dp).testTag("voice_talk_converting_art"), contentScale = ContentScale.Fit)
        Text("正在趣味转换…", color = VoiceInk, fontSize = 19.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        Text("请稍候，马上为你生成${if (pet == PetType.Dog) "狗语" else "猫语"}～",
            color = VoiceMuted, fontSize = 13.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 5.dp))
    }
}

/** Displays a fixed, validated sequence; replay never chooses a new sequence in the UI. */
@Composable
fun VoiceTalkResultContent(
    elapsedMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    canPlay: Boolean,
    onPlayPause: () -> Unit,
    onRecordAgain: () -> Unit,
    modifier: Modifier = Modifier,
    onReplay: () -> Unit = onPlayPause,
    audioSources: List<android.net.Uri> = emptyList(),
) {
    val hasAudio = durationMs > 0 && audioSources.isNotEmpty()
    val playbackEnabled = canPlay && hasAudio
    val positionMs = elapsedMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Column(modifier.fillMaxWidth().testTag("voice_talk_result"), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(30.dp), color = Color.White.copy(alpha = .65f)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (hasAudio) "转换完成！" else "转换结果预览", color = VoicePurple,
                    fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    VoiceWaveBars(null, Modifier.weight(1f).height(38.dp))
                    Surface(shape = CircleShape, modifier = Modifier.size(72.dp).alpha(if (playbackEnabled) 1f else .45f),
                        color = Color.Transparent, shadowElevation = 3.dp) {
                        Box(Modifier.clip(CircleShape).background(Brush.linearGradient(listOf(VoicePink, Color(0xFFB169EF))))
                            .clickable(enabled = playbackEnabled, role = Role.Button, onClick = onPlayPause)
                            .testTag("voice_talk_play_pause").semantics { contentDescription = if (isPlaying) "暂停播放" else "播放转换声音" },
                            contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(28.dp).clearAndSetSemantics {}) {
                                if (isPlaying) {
                                    drawRoundRect(Color.White, Offset(size.width * .18f, 0f), Size(size.width * .22f, size.height), CornerRadius(2.dp.toPx()))
                                    drawRoundRect(Color.White, Offset(size.width * .6f, 0f), Size(size.width * .22f, size.height), CornerRadius(2.dp.toPx()))
                                } else {
                                    drawPath(Path().apply { moveTo(size.width * .2f, 0f); lineTo(size.width, size.height / 2)
                                        lineTo(size.width * .2f, size.height); close() }, Color.White)
                                }
                            }
                        }
                    }
                    VoiceWaveBars(null, Modifier.weight(1f).height(38.dp))
                }
                Canvas(Modifier.fillMaxWidth().height(5.dp).testTag("voice_talk_playback_progress").semantics {
                    contentDescription = "转换音效播放进度"
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                }) {
                    val corner = CornerRadius(size.height / 2)
                    drawRoundRect(VoicePurple.copy(alpha = .13f), cornerRadius = corner)
                    if (progress > 0f) drawRoundRect(VoicePink,
                        size = Size(size.width * progress, size.height), cornerRadius = corner)
                }
                Text("${formatVoicePlaybackTime(positionMs)} / ${formatVoicePlaybackTime(durationMs)}",
                    color = VoiceMuted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.testTag("voice_talk_playback_timer"))
                if (!hasAudio) Text("暂无有效转换音源，播放暂未开放", color = VoiceMuted,
                    fontSize = 12.sp, lineHeight = 19.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("voice_talk_playback_unavailable"))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(onClick = onReplay, enabled = playbackEnabled,
                modifier = Modifier.weight(1f).heightIn(min = 50.dp).alpha(if (playbackEnabled) 1f else .45f)
                    .testTag("voice_talk_replay"),
                shape = RoundedCornerShape(28.dp), color = Color.White.copy(alpha = .7f), border = BorderStroke(1.dp, Color.White)) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 13.dp), contentAlignment = Alignment.Center) {
                    Text("重新播放", color = VoicePurple, fontSize = 15.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
                }
            }
            VoiceGradientButton("再说一次", onRecordAgain, true, Modifier.weight(1f).testTag("voice_talk_again"))
        }
    }
}

private fun formatVoicePlaybackTime(milliseconds: Long): String {
    val safe = milliseconds.coerceAtLeast(0L)
    return String.format(Locale.ROOT, "%s.%01d", formatVoiceTime(safe), safe % 1_000L / 100L)
}

@Preview(name = "CONVERTING · isolated UI only", widthDp = 350, heightDp = 300)
@Composable
private fun VoiceTalkConvertingPreview() {
    PetTranslatorTheme {
        Column(Modifier.background(Color(0xFFF7EBFF))) {
            Text("仅 UI 预览", color = VoiceMuted, modifier = Modifier.padding(8.dp))
            VoiceTalkConvertingContent(PetType.Dog)
        }
    }
}

@Preview(name = "RESULT · isolated UI only, no audio", widthDp = 350, heightDp = 300)
@Composable
private fun VoiceTalkResultPreview() {
    PetTranslatorTheme {
        Column(Modifier.background(Color(0xFFF7EBFF)).padding(12.dp)) {
            Text("仅 UI 预览 · 无音源", color = VoiceMuted, modifier = Modifier.padding(bottom = 8.dp))
            VoiceTalkResultContent(0, 3_000, false, false, {}, {})
        }
    }
}
