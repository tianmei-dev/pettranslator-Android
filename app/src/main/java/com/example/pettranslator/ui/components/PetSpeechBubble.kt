package com.example.pettranslator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pettranslator.ui.theme.PetFontFamilies
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

enum class PetSpeechBubbleStyle { Cloud, Dialogue }

/** A width-constrained bubble whose height follows its accessible, wrapping text. */
@Composable
fun PetSpeechBubble(
    text: String,
    modifier: Modifier = Modifier,
    style: PetSpeechBubbleStyle = PetSpeechBubbleStyle.Cloud,
    fontSize: TextUnit = 16.sp,
    rotationDegrees: Float = -5f,
    textColor: Color = Color(0xFFEF4F9C),
) {
    val shape = when (style) {
        PetSpeechBubbleStyle.Cloud -> CloudBubbleShape
        PetSpeechBubbleStyle.Dialogue -> DialogueBubbleShape
    }
    Layout(
        modifier = modifier
            .shadow(1.dp, shape, clip = false,
                ambientColor = Color(0xFFE2C7D6), spotColor = Color(0xFFE2C7D6))
            .background(Color(0xFFFFF9FC).copy(alpha = .98f), shape)
            .border(.5.dp, Color(0xFFF2E2EB), shape),
        content = {
            Text(
                text = text,
                color = textColor,
                style = PetFontFamilies.TitleStyle.copy(fontSize = fontSize, lineHeight = fontSize * 1.35f,
                    letterSpacing = 0.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("pet_bubble_text"),
            )
        },
    ) { measurables, constraints ->
        // Reserve the soft-lobed edges and the tilted text's corner clearance.
        // The text itself is measured normally, retaining wrapping and semantics.
        val horizontalInset = 18.dp.roundToPx()
        val verticalInset = 12.dp.roundToPx()
        val tailHeight = 10.dp.roundToPx()
        val maxTextWidth = if (constraints.hasBoundedWidth) {
            (constraints.maxWidth - horizontalInset * 2).coerceAtLeast(1)
        } else Constraints.Infinity
        val textPlaceable = measurables.single().measure(Constraints(maxWidth = maxTextWidth))
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val rotatedHeight = ceil(abs(textPlaceable.width * sin(radians)) +
            abs(textPlaceable.height * cos(radians))).toInt()
        val width = constraints.constrainWidth(textPlaceable.width + horizontalInset * 2)
        // At large font scales the text must also remain within the cloud body,
        // above its lobed lower edge and tail, rather than just inside the root.
        val height = constraints.constrainHeight(maxOf(
            rotatedHeight + verticalInset * 2 + tailHeight,
            ceil(rotatedHeight / .64f).toInt(),
        ))
        layout(width, height) {
            textPlaceable.placeRelativeWithLayer(
                x = (width - textPlaceable.width) / 2,
                y = (height * .44f - textPlaceable.height / 2f).toInt(),
            ) { rotationZ = rotationDegrees }
        }
    }
}

private object CloudBubbleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            fun point(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(size.width * x, size.height * y)
            fun curve(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
                val a = point(x1, y1); val b = point(x2, y2); val c = point(x3, y3)
                cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)
            }
            moveTo(size.width * .13f, size.height * .20f)
            curve(.10f, .06f, .29f, .01f, .38f, .09f)
            curve(.46f, .01f, .65f, .01f, .71f, .11f)
            curve(.87f, .05f, .99f, .19f, .95f, .35f)
            curve(1f, .48f, .96f, .65f, .86f, .68f)
            curve(.87f, .82f, .70f, .88f, .61f, .80f)
            curve(.48f, .89f, .35f, .87f, .29f, .78f)
            // A short, curved tail points towards the pet on the lower left.
            curve(.24f, .85f, .16f, .96f, .08f, .98f)
            curve(.14f, .88f, .17f, .79f, .16f, .73f)
            curve(.04f, .76f, .01f, .61f, .06f, .46f)
            curve(.01f, .35f, .02f, .19f, .13f, .20f)
            close()
        }
        return Outline.Generic(path)
    }
}

private object DialogueBubbleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            fun curve(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = cubicTo(
                size.width * x1, size.height * y1,
                size.width * x2, size.height * y2,
                size.width * x3, size.height * y3,
            )
            moveTo(size.width * .16f, size.height * .12f)
            curve(.33f, .03f, .72f, .01f, .87f, .13f)
            curve(.98f, .22f, 1f, .48f, .91f, .64f)
            curve(.84f, .79f, .66f, .83f, .50f, .80f)
            curve(.40f, .84f, .30f, .82f, .24f, .77f)
            curve(.20f, .84f, .12f, .94f, .05f, .97f)
            curve(.12f, .85f, .15f, .75f, .13f, .69f)
            curve(.02f, .60f, .01f, .39f, .07f, .25f)
            curve(.09f, .19f, .12f, .14f, .16f, .12f)
            close()
        }
        return Outline.Generic(path)
    }
}
