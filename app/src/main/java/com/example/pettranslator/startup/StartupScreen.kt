package com.example.pettranslator.startup

import android.os.SystemClock
import android.util.Log
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pettranslator.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private object ProcessStartup {
    val session = StartupSession(SystemClock::elapsedRealtime, audit = { Log.i("StartupPresentation", it) })
}

@Composable
fun StartupGate(content: @Composable () -> Unit) {
    val session = ProcessStartup.session
    val finished by session.finished.collectAsStateWithLifecycle()
    if (finished) {
        content()
        return
    }
    StartupArtwork(showEntering = true)
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val firstDraw = remember(view) { CompletableDeferred<Unit>() }
    DisposableEffect(view, owner) {
        // Start after a drawn, foreground frame; never count the consent-loading period.
        val draw = ViewTreeObserver.OnDrawListener {
            if (!firstDraw.isCompleted && view.isShown && view.hasWindowFocus() &&
                owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                view.post { firstDraw.complete(Unit) }
            }
        }
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (focused) view.invalidate() }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) view.invalidate() }
        view.viewTreeObserver.addOnDrawListener(draw)
        view.viewTreeObserver.addOnWindowFocusChangeListener(focus)
        owner.lifecycle.addObserver(observer)
        view.invalidate()
        onDispose {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnDrawListener(draw)
                view.viewTreeObserver.removeOnWindowFocusChangeListener(focus)
            }
            owner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(session, firstDraw) {
        firstDraw.await()
        do {
            val remaining = session.onVisibleFrame()
            withContext(Dispatchers.Default) { delay(remaining) }
        } while (!session.finishIfElapsed())
    }
}

@Composable
fun StartupArtwork(showEntering: Boolean) {
    val brand = Color(0xFFB64D91)
    BoxWithConstraints(Modifier.fillMaxSize().testTag(if (showEntering) "startup_screen" else "startup_backdrop")
        .background(Brush.linearGradient(listOf(Color(0xFFFFEAF6), Color(0xFFE5EAFF), Color(0xFFDCEFFF), Color(0xFFFFE8F5))))) {
        Canvas(Modifier.fillMaxSize()) {
            fun cloud(x: Float, y: Float, width: Float) {
                val paint = Color.White.copy(alpha = .46f)
                drawOval(paint, Offset(x, y + width * .16f), Size(width, width * .31f))
                drawCircle(paint, width * .20f, Offset(x + width * .33f, y + width * .22f))
                drawCircle(paint, width * .25f, Offset(x + width * .63f, y + width * .20f))
            }
            fun paw(x: Float, y: Float, width: Float) {
                val paint = Color.White.copy(alpha = .33f)
                drawOval(paint, Offset(x + width * .23f, y + width * .40f), Size(width * .58f, width * .48f))
                for ((dx, dy) in listOf(.08f to .29f, .30f to .08f, .56f to .08f, .80f to .28f)) {
                    drawOval(paint, Offset(x + width * dx, y + width * dy), Size(width * .20f, width * .27f))
                }
            }
            cloud(size.width * .66f, size.height * .08f, size.width * .27f)
            cloud(-size.width * .10f, size.height * .40f, size.width * .36f)
            cloud(size.width * .78f, size.height * .70f, size.width * .27f)
            paw(size.width * .12f, size.height * .17f, size.width * .12f)
            paw(size.width * .80f, size.height * .31f, size.width * .11f)
        }
        Image(painterResource(R.drawable.my_header_pets_cloud_v2), contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().aspectRatio(3f))
        Column(Modifier.align(Alignment.Center).offset(y = (-38).dp).fillMaxWidth()
            .padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // ImageView renders the actual adaptive launcher drawable, including its platform mask.
            AndroidView(factory = { context -> ImageView(context).apply {
                setImageResource(R.mipmap.ic_launcher)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = context.getString(R.string.app_name) + "应用图标"
            } }, modifier = Modifier.size(110.dp).testTag("startup_app_icon"))
            Text(stringResource(R.string.app_name), color = brand, fontSize = 23.sp, lineHeight = 32.sp,
                fontWeight = FontWeight.SemiBold)
        }
        if (showEntering) {
            Text("正在进入…", color = brand, fontSize = 14.sp, lineHeight = 22.sp,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = maxWidth / 3f + 28.dp).navigationBarsPadding())
        }
    }
}
