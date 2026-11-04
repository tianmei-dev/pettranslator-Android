package com.example.pettranslator.ui.components

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pettranslator.ui.theme.Ink
import com.example.pettranslator.ui.theme.PetFontFamilies

/**
 * A shallow, upright arc for a short pet-themed title. Glyph advances come from
 * the actual font; first/last glyph baselines sit 8dp below the middle of the arc.
 * Ink bounds distribute this larger curve inside the same existing measured box.
 *
 * The single Text remains the accessibility and text-layout semantics host. The
 * glyph drawing has no separate accessibility children. If the title wraps, or
 * its independently measured glyphs do not fit, Text draws normally and may wrap
 * naturally at the user's chosen font scale. There is no accessibility downscale.
 */
@Composable
internal fun PetArtisticTitle(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    color: Color = Ink,
) {
    val size = if (fontSize == TextUnit.Unspecified) 22.sp else fontSize
    val style = LocalTextStyle.current.merge(PetFontFamilies.TitleStyle).copy(
        color = color,
        fontSize = size,
        lineHeight = size * 1.35f,
        fontWeight = FontWeight.Normal,
        fontSynthesis = FontSynthesis.None,
        letterSpacing = 0.sp,
        textAlign = TextAlign.Center,
    )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val typeface = LocalFontFamilyResolver.current.resolve(style.fontFamily,
        FontWeight.Normal, FontStyle.Normal, FontSynthesis.None).value as? Typeface ?: Typeface.DEFAULT
    val glyphs = remember(text, style, measurer) {
        text.codePoints().toArray().map { codePoint ->
            measurer.measure(String(Character.toChars(codePoint)), style.copy(textAlign = TextAlign.Start),
                softWrap = false)
        }
    }
    // Use fractional advances instead of rounding each glyph to its bitmap width.
    val advances = remember(glyphs) {
        glyphs.map { glyph ->
            kotlin.math.abs(glyph.getHorizontalPosition(glyph.layoutInput.text.length, true) -
                glyph.getHorizontalPosition(0, true))
        }
    }
    // Layout boxes include font leading, not the actual glyph ink. Resolve the
    // same platform font and measure ink once so the 8dp curve can use available
    // leading plus the existing 4dp lower padding without changing layout height.
    val inkBounds = remember(text, typeface, size, density) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = with(density) { size.toPx() }
        }
        text.codePoints().toArray().map { codePoint ->
            val character = String(Character.toChars(codePoint))
            val bounds = Rect()
            paint.getTextBounds(character, 0, character.length, bounds)
            bounds.top.toFloat() to bounds.bottom.toFloat()
        }
    }
    val totalAdvance = advances.sum()
    // Combining sequences/emoji should retain platform shaping as a whole.
    val simpleGlyphSequence = remember(text) { text.codePoints().toArray().none { code ->
        code == 0x200D || code in 0xFE00..0xFE0F || code >= 0x1F000 ||
            Character.getType(code) == Character.NON_SPACING_MARK.toInt() ||
            Character.getType(code) == Character.COMBINING_SPACING_MARK.toInt()
    } }
    var textLayout by remember(text, style) { mutableStateOf<TextLayoutResult?>(null) }
    // Keep the caller's Text tag/action (e.g. GetTextLayoutResult) and a separate
    // component tag. This container does not merge or duplicate announcements.
    Box(Modifier.testTag("art_title_glyphs")) {
        Text(
            text = text,
            style = style,
            onTextLayout = { textLayout = it },
            modifier = modifier.drawWithContent {
                val layout = textLayout
                val canDrawArc = simpleGlyphSequence && layout != null && layout.lineCount == 1 &&
                    !layout.didOverflowHeight && glyphs.size > 1 &&
                    totalAdvance > 0f && totalAdvance <= layout.size.width.toFloat() &&
                    !text.contains('\n') && !text.contains('\r')
                if (!canDrawArc) {
                    drawContent()
                } else {
                    val arcLayout = requireNotNull(layout)
                    val firstCenter = advances.first() / 2f
                    val lastCenter = totalAdvance - advances.last() / 2f
                    val centerSpan = lastCenter - firstCenter
                    var advanceBefore = 0f
                    val drops = advances.map { advance ->
                        val center = advanceBefore + advance / 2f
                        advanceBefore += advance
                        val normalized = if (centerSpan > 0f)
                            ((center - firstCenter) / centerSpan * 2f - 1f).coerceIn(-1f, 1f) else 0f
                        8.dp.toPx() * normalized * normalized
                    }
                    // Each glyph imposes an interval for a common vertical shift.
                    // Intersect those intervals, preferring -4dp: a balanced -4..+4
                    // curve. This leaves the component baseline and following UI
                    // exactly where they were while keeping actual ink inside it.
                    val minimumShift = inkBounds.indices.maxOf { index ->
                        -arcLayout.firstBaseline - inkBounds[index].first - drops[index]
                    }
                    val maximumShift = inkBounds.indices.minOf { index ->
                        this.size.height - arcLayout.firstBaseline - inkBounds[index].second - drops[index]
                    }
                    if (minimumShift > maximumShift) {
                        drawContent()
                        return@drawWithContent
                    }
                    val shift = (-4).dp.toPx().coerceIn(minimumShift, maximumShift)
                    var x = (arcLayout.size.width - totalAdvance) / 2f
                    glyphs.forEachIndexed { index, glyph ->
                        drawText(glyph, topLeft = Offset(x,
                            arcLayout.firstBaseline - glyph.firstBaseline + shift + drops[index]))
                        x += advances[index]
                    }
                }
            }.padding(bottom = 4.dp),
        )
    }
}
