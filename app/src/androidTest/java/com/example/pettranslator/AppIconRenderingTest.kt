package com.example.pettranslator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PathMeasure
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Test

/** Check the inflated Android icon, including its actual device-supplied launcher mask. */
class AppIconRenderingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val folder get() = File(context.getExternalFilesDir(null), "icon-border-evidence")
        .apply { mkdirs() }

    @Test fun adaptiveIconsHaveNoWhiteRimAtLauncherSizes() {
        for (resource in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            for (size in listOf(96, 192)) {
                val name = "${context.resources.getResourceEntryName(resource)}-$size"
                val icon = context.getDrawable(resource)!!.mutate() as AdaptiveIconDrawable
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                try {
                    icon.setBounds(0, 0, size, size)
                    icon.draw(Canvas(bitmap))
                    val perimeter = PathMeasure(icon.iconMask, true)
                    val position = FloatArray(2)
                    val samples = IntArray(720) { index ->
                        check(perimeter.getPosTan(perimeter.length * index / 720f, position, null))
                        val dx = size / 2f - position[0]
                        val dy = size / 2f - position[1]
                        val distance = hypot(dx, dy)
                        // Sample inside the mask, beyond antialiasing and any launcher shadow.
                        val x = (position[0] + 3f * dx / distance).roundToInt().coerceIn(0, size - 1)
                        val y = (position[1] + 3f * dy / distance).roundToInt().coerceIn(0, size - 1)
                        bitmap.getPixel(x, y)
                    }
                    verifyAndSave(name, bitmap, samples)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    @Test fun sharedArtworkHasNoBakedWhiteFrame() {
        val bitmap = checkNotNull(BitmapFactory.decodeResource(context.resources, R.drawable.app_icon))
        try {
            // Walk the outer edge in order so a continuous white frame cannot hide in averages.
            val samples = buildList {
                for (x in 0 until bitmap.width) add(bitmap.getPixel(x, 0))
                for (y in 1 until bitmap.height) add(bitmap.getPixel(bitmap.width - 1, y))
                for (x in bitmap.width - 2 downTo 0) add(bitmap.getPixel(x, bitmap.height - 1))
                for (y in bitmap.height - 2 downTo 1) add(bitmap.getPixel(0, y))
            }.toIntArray()
            verifyAndSave("shared-artwork", bitmap, samples)
        } finally {
            bitmap.recycle()
        }
    }

    private fun verifyAndSave(name: String, bitmap: Bitmap, samples: IntArray) {
        val white = samples.map { pixel ->
            Color.alpha(pixel) >= 250 && Color.red(pixel) >= 245 &&
                Color.green(pixel) >= 245 && Color.blue(pixel) >= 245
        }
        var run = 0
        var longestRun = 0
        // Repeat once to include a white segment crossing the path's starting point.
        for (index in 0 until white.size * 2) {
            run = if (white[index % white.size]) run + 1 else 0
            longestRun = maxOf(longestRun, run.coerceAtMost(white.size))
        }
        val whiteFraction = white.count { it }.toFloat() / samples.size
        val runFraction = longestRun.toFloat() / samples.size
        val transparent = samples.count { Color.alpha(it) < 250 }
        val report = "$name: samples=${samples.size}, white=$whiteFraction, " +
            "longestWhiteRun=$runFraction, nonOpaque=$transparent"
        File(folder, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        File(folder, "$name.txt").writeText("$report\n")
        // Isolated illustration highlights are allowed; a white margin or sustained rim is not.
        assertTrue("Unexpected transparent gap inside icon edge: $report", transparent == 0)
        assertTrue("Excessive neutral-white icon edge: $report", whiteFraction <= .10f)
        assertTrue("Continuous white rim on icon: $report", runFraction <= .06f)
    }
}
