package com.example.pettranslator

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.Before

/** Run with adb instrumentation to retain screenshots and measured layout bounds. */
class HomeVisualEvidenceTest {
    @Before fun wake() = wakeTestDevice()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun captureHomeRegions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "a112-evidence").apply { mkdirs() }
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("猫咪") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        // Assert the complete primary flow is visible before any scrolling.
        rule.onNodeWithText("选择你想翻译的毛孩子").assertIsDisplayed()
        rule.onNodeWithText("不同场景下，TA的含义可能不同哦").assertIsDisplayed()
        rule.onNodeWithText("开始录音翻译").assertIsDisplayed()
        rule.onNodeWithText("趣味声音模拟，不代表宠物真实语言翻译").assertIsDisplayed()
        rule.onAllNodesWithText("把我的话说给它听").assertCountEquals(0)
        val viewport = rule.onNodeWithTag("home_viewport").getUnclippedBoundsInRoot()
        val actions = rule.onNodeWithTag("home_actions").getUnclippedBoundsInRoot()
        val scenes = rule.onNodeWithTag("home_section_2").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue("Fixed actions must sit outside scrolling content", actions.top >= viewport.bottom)
        org.junit.Assert.assertTrue("Actions must not cover scenes", actions.top >= scenes.bottom)
        rule.onNodeWithText("猫言狗语翻译官").assertIsDisplayed()
        rule.onAllNodesWithText("选择它和场景，听听毛孩子的小心思～").assertCountEquals(0)
        rule.onNodeWithText("不确定").performScrollTo()
        org.junit.Assert.assertEquals(actions, rule.onNodeWithTag("home_actions").getUnclippedBoundsInRoot())
        val metrics = listOf("home_hero", "home_section_1", "home_section_2", "home_record").joinToString("\n") { tag ->
            val bounds = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            "$tag: top=${bounds.top.value}dp height=${bounds.bottom.value - bounds.top.value}dp width=${bounds.right.value - bounds.left.value}dp"
        }
        File(folder, "layout.txt").writeText(metrics)
        fun capture(name: String) {
            rule.waitForIdle()
            rule.onRoot().captureToImage() // Wait for the window draw before the full-device capture.
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                checkNotNull(bitmap)
                File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        capture("home-top.png")
        rule.onNodeWithText("不确定").performScrollTo().assertIsDisplayed()
        capture("home-scenes.png")
        rule.onNodeWithText("趣味声音模拟，不代表宠物真实语言翻译").assertIsDisplayed()
        rule.onNodeWithText("开始录音翻译").assertIsDisplayed()
        rule.onNodeWithText("首页", useUnmergedTree = true).assertIsDisplayed()
        capture("home-record.png")
    }
}
