package com.example.pettranslator

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import java.io.File

class RecordingFlowTest {
    @Before fun wake() = wakeTestDevice()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun grant() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("pm grant ${rule.activity.packageName} ${Manifest.permission.RECORD_AUDIO}")
            .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }
    private fun waitText(text: String, timeout: Long = 6000) {
        rule.waitUntil(timeout) { rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun liveNode(text: String): android.view.accessibility.AccessibilityNodeInfo? {
        fun find(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
            if (node == null) return null
            if ((node.text?.toString()?.contains(text) == true || node.contentDescription?.toString() == text) &&
                node.packageName?.toString() == rule.activity.packageName) return node
            for (index in 0 until node.childCount) find(node.getChild(index))?.let { return it }
            return null
        }
        // Compose virtual nodes do not implement framework text-search consistently.
        return find(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow)
    }
    private fun waitLiveText(text: String, timeout: Long = 6000) {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeout
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            // Compose tests own a virtual frame clock: drive frames without awaiting
            // animation idleness, while AudioRecord continues on real elapsed time.
            rule.mainClock.advanceTimeBy(32)
            if (liveNode(text) != null) return
            android.os.SystemClock.sleep(50)
        }
        capture("diagnostic-missing-control.png", active = true)
        error("Missing live recording control: $text")
    }
    private fun waitRecognitionFinal(timeout: Long = 20000) {
        val results = listOf("听到毛孩子的声音啦！", "没有听清毛孩子的声音", "好像听到了", "声音分析失败")
        val deadline = android.os.SystemClock.elapsedRealtime() + timeout
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            rule.mainClock.advanceTimeBy(32)
            results.firstOrNull { liveNode(it) != null }?.let {
                android.util.Log.i("RecognitionTest", "real microphone reached final UI status: $it")
                return
            }
            android.os.SystemClock.sleep(50)
        }
        capture("analysis-timeout.png", active = true)
        error("Recognition did not reach a final status")
    }
    private fun liveClick(text: String) {
        waitLiveText(text)
        val node = checkNotNull(liveNode(text))
        if (node.isClickable && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val down = android.os.SystemClock.uptimeMillis()
        listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP).forEach { action ->
            val event = android.view.MotionEvent.obtain(down, android.os.SystemClock.uptimeMillis(), action,
                bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
    }
    private fun start(text: String = "开始录音") {
        // Real microphone noise keeps spring animations active. OS accessibility/input
        // avoids Compose's idle synchronization without pausing UI or capture clocks.
        liveClick(text)
        waitLiveText("停止录音")
    }
    private fun enter(pet: String, scene: String) {
        rule.waitUntil(5000) { rule.onAllNodes(hasText(pet) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(pet).performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodes(hasText(pet) and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(scene).performScrollTo().performClick()
        rule.onNodeWithText("开始录音翻译").performScrollTo().performClick()
        rule.onNodeWithContentDescription("$pet · $scene").assertIsDisplayed()
        rule.onAllNodesWithText("首页").assertCountEquals(0)
    }
    private fun capture(name: String, active: Boolean = false) {
        if (!active) { rule.waitForIdle(); rule.onRoot().captureToImage() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(rule.activity.getExternalFilesDir(null), "a121-evidence").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun catManualDogAutoAndScreenshots() {
        grant()
        enter("猫咪", "玩耍")
        capture("cat-ready.png")
        start()
        liveClick("停止录音")
        waitRecognitionFinal()
        assertRecognitionVisible()
        capture("completed.png")
        start("重新录音")
        repeat(30) {
            rule.mainClock.advanceTimeBy(32)
            android.os.SystemClock.sleep(35)
        }
        capture("recording.png", active = true)
        liveClick("取消录音")
        waitLiveText("开始录音")
        waitText("开始录音")
        rule.onNodeWithTag("recording_back").performClick()
        rule.onNodeWithText("玩耍").performScrollTo().assertIsSelected()
        enter("狗狗", "吃饭前后")
        capture("dog-ready.png")
        start()
        repeat(30) {
            rule.mainClock.advanceTimeBy(32)
            android.os.SystemClock.sleep(35)
        }
        capture("dog-recording.png", active = true)
        waitRecognitionFinal(20000)
        assertRecognitionVisible()
        rule.onNodeWithTag("recording_back").performClick()
        rule.onNodeWithText("吃饭前后").performScrollTo().assertIsSelected()
    }

    private fun assertRecognitionVisible() {
        val possible = listOf("听到毛孩子的声音啦！", "没有听清毛孩子的声音", "好像听到了", "声音分析失败")
        check(possible.any { liveNode(it) != null }) { "No recognition result is visible" }
    }
    @Test fun recreationAndBackCancelActiveCapture() {
        grant(); enter("猫咪", "不确定")
        start()
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        waitText("开始录音")
        start()
        rule.activityRule.scenario.recreate()
        waitText("开始录音")
        rule.onAllNodesWithText("录音完成").assertCountEquals(0)
        start()
        liveClick("返回")
        rule.onNodeWithText("不确定").performScrollTo().assertIsSelected()
        rule.waitUntil(5000) { File(rule.activity.cacheDir, "pet_recordings").listFiles().orEmpty().isEmpty() }
    }
}
