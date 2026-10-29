package com.example.pettranslator.ui.recording

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import com.example.pettranslator.R
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UncertainRecordingSheet(pet: PetType, onDismiss: () -> Unit, onAgain: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val height = with(LocalDensity.current) { windowHeight.toDp() }
    val illustrationHeight = (height * .175f).coerceIn(118.dp, 140.dp)
    val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        shape = shape, containerColor = Color(0xFFFFF1F7), scrimColor = Ink.copy(alpha = .22f),
        tonalElevation = 2.dp, dragHandle = null, contentWindowInsets = { WindowInsets(0) }) {
        SheetNavigationBar()
        Box(Modifier.fillMaxWidth().heightIn(max = height * .86f)
            .background(Brush.verticalGradient(listOf(Color(0xFFFFFCFA), Color(0xFFFFF2FA), Color(0xFFFFEDF5))))
            .navigationBarsPadding()) {
            // Edge clouds sit behind the content; the supplied artwork already has hearts and paws.
            Canvas(Modifier.matchParentSize()) {
                val cloud = Color.White.copy(alpha = .56f)
                fun cloudEdge(x: Float) {
                    drawCircle(cloud, size.width * .15f, Offset(x, size.height * .99f))
                    drawCircle(cloud, size.width * .12f, Offset(x, size.height * .90f))
                    drawCircle(cloud, size.width * .10f, Offset(x + size.width * .13f, size.height * 1.02f))
                }
                cloudEdge(-size.width * .04f)
                cloudEdge(size.width * .98f)
            }
            Column(Modifier.fillMaxWidth().testTag("uncertain_sheet")
                .verticalScroll(rememberScrollState()).padding(horizontal = 26.dp).padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Color(0xFFF7BDD5)))
                UncertainPetIllustration(pet, illustrationHeight)
                Spacer(Modifier.height(4.dp))
                Text("哎呀，这次没听清毛孩子的声音～", color = Ink, fontSize = 18.sp,
                    lineHeight = 26.sp, fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("换个安静点的地方，靠近毛孩子再试试吧！", color = Muted,
                    fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                val buttonShape = RoundedCornerShape(28.dp)
                Button(onClick = onAgain, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                    .shadow(5.dp, buttonShape, ambientColor = Pink.copy(alpha = .35f), spotColor = Pink.copy(alpha = .45f))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFF8AC9), Color(0xFFF65BAC))), buttonShape),
                    shape = buttonShape, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) {
                    Canvas(Modifier.size(22.dp)) {
                        val stroke = 2.dp.toPx()
                        drawArc(Color.White, 35f, 285f, false, Offset(size.width * .12f, size.height * .12f),
                            Size(size.width * .76f, size.height * .76f), style = Stroke(stroke, cap = StrokeCap.Round))
                        drawLine(Color.White, Offset(size.width * .79f, size.height * .08f),
                            Offset(size.width * .82f, size.height * .32f), stroke, StrokeCap.Round)
                        drawLine(Color.White, Offset(size.width * .82f, size.height * .32f),
                            Offset(size.width * .58f, size.height * .30f), stroke, StrokeCap.Round)
                    }
                    Spacer(Modifier.width(9.dp))
                    Text("重新录音", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Only the modal's window is styled; the recording activity's system bars stay untouched. */
@Suppress("DEPRECATION")
@Composable
private fun SheetNavigationBar() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val previousColor = window?.navigationBarColor
        val previousContrast = if (android.os.Build.VERSION.SDK_INT >= 29) window?.isNavigationBarContrastEnforced else null
        window?.navigationBarColor = Color(0xFFFFEDF5).toArgb()
        if (android.os.Build.VERSION.SDK_INT >= 29) window?.isNavigationBarContrastEnforced = false
        onDispose {
            if (previousColor != null) window?.navigationBarColor = previousColor
            if (android.os.Build.VERSION.SDK_INT >= 29 && previousContrast != null) window?.isNavigationBarContrastEnforced = previousContrast
        }
    }
}

@Composable
private fun UncertainPetIllustration(pet: PetType, height: androidx.compose.ui.unit.Dp) {
    val cat = pet == PetType.Cat
    val bitmap = ImageBitmap.imageResource(if (cat) R.drawable.record_uncertain_cat else R.drawable.record_uncertain_dog)
    // Display-only transparent-margin trim, with extra room around the measured alpha bounds.
    // The PNGs remain unchanged, and all ears, paws, cushion and embedded decorations are kept.
    val sourceOffset = if (cat) IntOffset(21, 101) else IntOffset(16, 210)
    val sourceSize = if (cat) IntSize(1220, 1074) else IntSize(1106, 960)
    Image(painter = BitmapPainter(bitmap, srcOffset = sourceOffset, srcSize = sourceSize),
        contentDescription = if (cat) "委屈含泪的猫咪趴在粉色格纹垫子上" else "委屈含泪的狗狗趴在粉色格纹垫子上",
        contentScale = ContentScale.Fit,
        modifier = Modifier.height(height).widthIn(max = 250.dp).fillMaxWidth()
            .testTag(if (cat) "uncertain_cat_illustration" else "uncertain_dog_illustration"))
}
