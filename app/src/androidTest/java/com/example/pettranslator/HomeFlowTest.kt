package com.example.pettranslator

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Before

class HomeFlowTest {
    @Before fun wake() = wakeTestDevice()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun selectionTabsAndComingSoon() {
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("狗狗") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("狗狗").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("狗狗") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("狗狗").assertIsSelected()
        listOf("门口或窗边", "吃饭前后", "玩耍", "独处", "被抱起", "不确定").forEach { title ->
            rule.onNodeWithText(title).performScrollTo().performClick()
            rule.onNodeWithText(title).assertIsSelected()
        }
        rule.onNodeWithText("吃饭前后").performScrollTo().performClick()
        rule.onNodeWithText("吃饭前后").assertIsSelected()
        rule.onNodeWithText("开始录音翻译").performScrollTo().performClick()
        rule.onNodeWithText("录音翻译").assertIsDisplayed()
        rule.onNodeWithContentDescription("狗狗 · 吃饭前后").assertIsDisplayed()
        rule.onNodeWithText("开始录音").assertIsDisplayed()
        rule.onNodeWithTag("recording_back").performClick()
        rule.onNodeWithText("逗宠").performClick()
        rule.onNodeWithText("返回首页").assertIsDisplayed().performClick()
        rule.onNodeWithText("吃饭前后").performScrollTo().assertIsSelected()
        rule.onNodeWithText("我的").performClick()
        rule.onNodeWithText("返回首页").performClick()
        rule.onNodeWithText("狗狗").performScrollTo().assertIsSelected()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("狗狗") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("狗狗").performScrollTo().assertIsSelected()
        rule.onNodeWithText("不确定").performScrollTo().performClick()
        rule.onNodeWithText("猫咪").performScrollTo().performClick()
    }
}
