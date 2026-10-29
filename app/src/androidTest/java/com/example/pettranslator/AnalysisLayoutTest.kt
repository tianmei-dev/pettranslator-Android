package com.example.pettranslator

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.ui.analysis.AnalysisScreen
import com.example.pettranslator.ui.theme.PetTranslatorTheme
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AnalysisLayoutTest {
    @get:Rule val rule = createComposeRule()
    @Before fun wake() = wakeTestDevice()

    private fun verify(width: Int, height: Int, fontScale: Float, name: String) {
        rule.mainClock.autoAdvance = false
        val started = System.nanoTime() - 3_500_000_000L
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                PetTranslatorTheme {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        Box(if (width == 0) Modifier.fillMaxSize() else Modifier.requiredSize(width.dp, height.dp)) {
                            AnalysisScreen(PetType.Cat, 4, started, 5_000L, {})
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(160)
        rule.onNodeWithText("正在分析").assertIsDisplayed()
        rule.onNodeWithText("分析过程仅在本地进行，不会上传你的录音，请放心使用！").assertIsDisplayed()
        rule.onNodeWithText("正在认真听它说话…").assertIsDisplayed()
        rule.onNodeWithTag("analysis_progress").assertExists()
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("analysis_progress").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("生成趣味解读…").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("分析过程仅在本地进行，不会上传你的录音，请放心使用！")
            .assertIsDisplayed()
        val page = rule.onNodeWithTag("analysis_page").fetchSemanticsNode().boundsInRoot
        val footer = rule.onNodeWithTag("analysis_footer").fetchSemanticsNode().boundsInRoot
        check(kotlin.math.abs(page.bottom - footer.bottom) <= 1f) { "Footer must stay at page bottom" }
        if (fontScale == 1f) {
            val stage = rule.onNodeWithText("生成趣味解读…").fetchSemanticsNode().boundsInRoot
            val tip = rule.onNodeWithText("温馨提示").fetchSemanticsNode().boundsInRoot
            check(tip.top > stage.bottom) { "Tip overlaps stages" }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "a1412-evidence").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun regularPhone() = verify(0, 0, 1f, "analysis-regular")
    @Test fun smallLargeFont() = verify(320, 568, 2f, "analysis-small-font2")
}
