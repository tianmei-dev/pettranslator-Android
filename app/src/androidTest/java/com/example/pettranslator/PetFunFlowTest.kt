package com.example.pettranslator

import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*

class PetFunFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Before fun wake() = wakeTestDevice()
    @Test fun previewOnlyFlow() {
        rule.onNodeWithText("逗宠").performClick()
        rule.onNodeWithText("逗宠乐园").assertIsDisplayed()
        rule.onNodeWithTag("fun_pet_Cat").assertIsSelected()
        rule.onNodeWithTag("home_actions").assertDoesNotExist()
        capture("play-samsung-top.png")
        rule.onNodeWithTag("fun_voice_start").performClick()
        rule.onNodeWithText("趣味说话功能即将上线～").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()
        rule.onNodeWithText("录音翻译").assertDoesNotExist()
        rule.onNodeWithTag("fun_pet_Dog").performClick()
        rule.onNodeWithTag("fun_pet_Dog").assertIsSelected()
        rule.onNodeWithTag("play_list").performScrollToNode(hasTestTag("fun_sound_dog_praise"))
        rule.onNodeWithText("夸夸你").assertIsDisplayed()
        rule.onNodeWithTag("fun_preview_dog_praise").performClick()
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithText("音效资源将在下一阶段接入～").assertIsDisplayed() }.isSuccess
        }
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()
        rule.onNodeWithTag("play_list").performScrollToNode(hasTestTag("fun_sound_dog_meal"))
        rule.onNodeWithText("开饭啦").assertIsDisplayed()
        capture("play-samsung-bottom.png")
        rule.onNodeWithTag("play_list").performScrollToNode(hasTestTag("fun_pet_Cat"))
        rule.onNodeWithTag("fun_pet_Cat").performClick()
        rule.onNodeWithTag("play_list").performScrollToNode(hasText("夸夸我"))
        rule.onNodeWithText("夸夸我").assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("逗宠").assertIsDisplayed()
        rule.onNodeWithText("我的").performClick()
        rule.onNodeWithText("返回首页").performClick()
        rule.onNodeWithTag("home_actions").assertIsDisplayed()
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val resolver = instrumentation.targetContext.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/B11")
        }))
        checkNotNull(resolver.openOutputStream(uri)).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
}
