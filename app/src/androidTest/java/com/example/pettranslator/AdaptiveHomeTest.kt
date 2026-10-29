package com.example.pettranslator

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertFalse
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.pettranslator.ui.home.*
import com.example.pettranslator.ui.theme.PetTranslatorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Before

class AdaptiveHomeTest {
    @Before fun wake() = wakeTestDevice()
    @get:Rule val rule = createComposeRule()
    private fun verify(width: Int, scale: Float) {
        rule.setContent {
            val physicalDensity = LocalDensity.current.density
            val availableWidth = LocalConfiguration.current.screenWidthDp
            val density = physicalDensity * minOf(1f, availableWidth.toFloat() / width)
            CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                PetTranslatorTheme {
                    Box(Modifier.width(width.dp)) {
                        HomeScreen(HomeUiState(ready = true), {}, {}, {})
                    }
                }
            }
        }
        rule.onNodeWithText("今天想听懂谁？").assertIsDisplayed()
        capture("adaptive-$width-$scale-top.png")
        rule.onNodeWithTag(if (width < 350 || scale >= 1.3f) "scene_grid_2" else "scene_grid_3").assertExists()
        listOf("门口或窗边", "吃饭前后", "玩耍", "独处", "被抱起", "不确定").forEach { title ->
            rule.onNodeWithText(title).performScrollTo().assertIsDisplayed()
            rule.onNodeWithText(title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val results = mutableListOf<TextLayoutResult>()
                action(results)
                assertFalse("Scene text overflow: $title: " + results.joinToString { "text=${it.layoutInput.text} size=${it.size} lines=${it.lineCount} widthOverflow=${it.didOverflowWidth} heightOverflow=${it.didOverflowHeight} paragraphHeight=${it.multiParagraph.height}" }, results.any { it.hasVisualOverflow })
            }
        }
        rule.onNodeWithText("不确定").performScrollTo().assertIsSelected()
        rule.onNodeWithText("开始录音翻译").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("人声变宠物语").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("趣味声音模拟，不代表宠物真实语言翻译").performScrollTo().assertIsDisplayed()
        capture("adaptive-$width-$scale-bottom.png")
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "a112-evidence").apply { mkdirs() }
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun smallPhone() = verify(320, 1f)
    @Test fun smallPhoneLargeFont() = verify(320, 2f)
    @Test fun regularPhone360() = verify(360, 1f)
    @Test fun regularPhone411() = verify(411, 1f)
    @Test fun largeContent() = verify(600, 1f)
}
