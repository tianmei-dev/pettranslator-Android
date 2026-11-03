package com.example.pettranslator

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pettranslator.domain.*
import com.example.pettranslator.domain.interpretation.*
import com.example.pettranslator.domain.recognition.*
import com.example.pettranslator.ui.analysis.AnalysisScreen
import com.example.pettranslator.ui.recording.*
import com.example.pettranslator.ui.result.InterpretationResultScreen
import com.example.pettranslator.ui.theme.PetFontFamilies
import com.example.pettranslator.ui.theme.PetTranslatorTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Production page screenshots on a physical device; recording is an injected UI state. */
class ArtisticDecorationTest {
    @get:Rule val rule = createComposeRule()
    @Before fun wake() = wakeTestDevice()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val folder get() = File(instrumentation.targetContext.getExternalFilesDir(null), "a153-evidence").apply { mkdirs() }
    private val metrics = StringBuilder()
    private enum class Page { Recording, Analysis, Result }

    private fun result(pet: PetType): PetInterpretationResult {
        val cat = pet == PetType.Cat
        val event = SoundEvent(if (cat) 78 else 70, if (cat) "Meow" else "Bark", .74f)
        val recognition = PetRecognitionResult(selectedPet = pet, scene = PetScene.Alone, detectedPet = pet,
            status = RecognitionStatus.SUCCESS, catScore = if (cat) .74f else .05f,
            dogScore = if (cat) .05f else .74f, otherScore = .08f,
            primarySound = event, soundEvents = listOf(event),
            quality = AudioQuality(3000, -25.0, 0.0, .05, 3, true), modelVersion = "artistic-ui-fixture", ruleVersion = "artistic-ui-fixture")
        return checkNotNull(PetInterpretationEngine(now = { 1_800_000_000_000L })
            .interpret(recognition, "a".repeat(64)))
    }

    @Composable private fun Content(page: Page, pet: PetType, width: Int = 0, fontScale: Float = 1f) {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            PetTranslatorTheme {
                Box(Modifier.fillMaxSize()) {
                    Box((if (width == 0) Modifier.fillMaxSize() else Modifier.width(width.dp).fillMaxHeight())
                        .testTag("decoration_surface")) {
                        key(page, pet, width, fontScale) {
                            when (page) {
                                Page.Recording -> Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                                    RecordingScreen(pet, PetScene.Alone,
                                        RecordingUiState(phase = RecordingPhase.RECORDING, elapsedMs = 3_000L),
                                        onBack = {}, onStart = {}, onStop = {}, onCancel = {}, onSettings = {})
                                }
                                Page.Analysis -> Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                                    // Initial progress is held only in this screenshot fixture.
                                    AnalysisScreen(pet, 3, Long.MAX_VALUE, 5_000L, {})
                                }
                                Page.Result -> InterpretationResultScreen(result(pet), {}, {}, {})
                            }
                        }
                    }
                }
            }
        }
    }

    private fun awaitTag(tag: String) {
        rule.mainClock.advanceTimeBy(160L)
        rule.waitForIdle()
        rule.waitUntil(5_000) {
            runCatching { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        rule.waitForIdle()
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureToImage()
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    private fun textLayout(tag: String, label: String): TextLayoutResult {
        val node = rule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val value = layouts.single()
        assertFalse("$label text height overflow", value.didOverflowHeight)
        for (line in 0 until value.lineCount) {
            assertFalse("$label ellipsis", value.isLineEllipsized(line))
            // Centered semantics can retain the parent's paragraph origin. Compare extent.
            assertTrue("$label line width overflow", value.getLineRight(line) - value.getLineLeft(line) <= value.size.width + 1f)
        }
        metrics.appendLine("$label textSize=${value.size} font=${value.layoutInput.style.fontFamily} " +
            "fontSize=${value.layoutInput.style.fontSize} lines=${value.lineCount} text=${value.layoutInput.text}")
        return value
    }

    private fun verifyBubble(page: Page, label: String, scroll: Boolean = false) {
        val tag = if (page == Page.Recording) "recording_art_bubble" else "result_art_bubble"
        val parentTag = if (page == Page.Recording) "recording_room" else "result_header"
        val node = rule.onNodeWithTag(tag)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed()
        val bubble = node.getUnclippedBoundsInRoot()
        val parent = rule.onNodeWithTag(parentTag).getUnclippedBoundsInRoot()
        val surface = rule.onNodeWithTag("decoration_surface").getUnclippedBoundsInRoot()
        assertTrue("$label bubble outside visual: $bubble / $parent",
            bubble.left >= parent.left && bubble.right <= parent.right && bubble.top >= parent.top && bubble.bottom <= parent.bottom)
        assertTrue("$label bubble outside device/container", bubble.left >= surface.left && bubble.right <= surface.right)
        val value = textLayout("pet_bubble_text", label)
        assertEquals(PetFontFamilies.Title, value.layoutInput.style.fontFamily)
        val expected = if (page == Page.Recording) "正在听毛孩子的声音…" else "毛孩子的心声来啦！"
        assertEquals(expected, value.layoutInput.text.text.replace("\n", ""))
        assertTrue("$label expected explicit two-line wording", value.lineCount >= 2)
        val text = rule.onNodeWithTag("pet_bubble_text", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("$label text outside bubble horizontally", text.left >= bubble.left && text.right <= bubble.right)
        assertTrue("$label text outside bubble vertically", text.top >= bubble.top && text.bottom <= bubble.bottom)
        val navigation = rule.onNodeWithTag("recording_back").getUnclippedBoundsInRoot()
        assertFalse("$label bubble covers navigation", overlaps(bubble, navigation))
        if (page == Page.Result) {
            val home = rule.onNodeWithTag("result_home").getUnclippedBoundsInRoot()
            assertFalse("$label bubble covers home action", overlaps(bubble, home))
        }
        metrics.appendLine("$label bubble=$bubble visual=$parent text=$text navigation=$navigation")
    }

    private fun overlaps(a: DpRect, b: DpRect) =
        a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top

    private fun verifyTitle(label: String, scroll: Boolean = false) {
        val node = rule.onNodeWithTag("font_trial_analysis")
        if (scroll) node.performScrollTo()
        val value = textLayout("font_trial_analysis", label)
        assertEquals("正在认真听它说话…", value.layoutInput.text.text)
        assertEquals(PetFontFamilies.Title, value.layoutInput.style.fontFamily)
        assertEquals(22.sp, value.layoutInput.style.fontSize)
        val bounds = node.getUnclippedBoundsInRoot()
        val page = rule.onNodeWithTag("decoration_surface").getUnclippedBoundsInRoot()
        assertTrue("$label title outside page", bounds.left >= page.left && bounds.right <= page.right)
        metrics.appendLine("$label title=$bounds; shallow arc is drawn from actual glyph advances and inspected in screenshots")
    }

    @Test fun fourRequestedScreensAndDogRecordingUseProductionComponents() {
        rule.mainClock.autoAdvance = false
        var page by mutableStateOf(Page.Recording)
        var pet by mutableStateOf(PetType.Cat)
        rule.setContent { Content(page, pet) }
        for (current in PetType.entries) {
            rule.runOnIdle { page = Page.Recording; pet = current }
            awaitTag("recording_art_bubble")
            verifyBubble(page, "${current.name}-recording")
            rule.onNodeWithText("00:03").assertExists()
            capture("recording-${current.name.lowercase()}")
        }
        rule.runOnIdle { page = Page.Analysis; pet = PetType.Cat }
        awaitTag("font_trial_analysis")
        verifyTitle("analysis")
        assertEquals(1, textLayout("font_trial_analysis", "analysis-normal-line").lineCount)
        capture("analysis-title")
        for (current in PetType.entries) {
            rule.runOnIdle { page = Page.Result; pet = current }
            awaitTag("result_art_bubble")
            verifyBubble(page, "${current.name}-result")
            capture("result-${current.name.lowercase()}")
        }
        File(folder, "decoration-layouts.txt").writeText(metrics.toString())
    }

    @Test fun narrowScreenAndLargeFontKeepBubblesAndTitleReadable() {
        rule.mainClock.autoAdvance = false
        var page by mutableStateOf(Page.Recording)
        var pet by mutableStateOf(PetType.Cat)
        var scale by mutableFloatStateOf(1f)
        rule.setContent { Content(page, pet, width = 320, fontScale = scale) }
        for (requestedScale in listOf(1f, 1.5f, 2f)) {
            for (currentPage in Page.entries) {
                for (currentPet in if (currentPage == Page.Analysis) listOf(PetType.Cat) else PetType.entries) {
                    rule.runOnIdle { page = currentPage; pet = currentPet; scale = requestedScale }
                    val label = "${page.name.lowercase()}-${pet.name.lowercase()}-320-font$scale"
                    awaitTag(if (page == Page.Analysis) "font_trial_analysis" else if (page == Page.Recording) "recording_art_bubble" else "result_art_bubble")
                    if (page == Page.Analysis) verifyTitle(label, scroll = true) else verifyBubble(page, label, scroll = true)
                    capture(label)
                }
            }
        }
        File(folder, "decoration-adaptation.txt").writeText(metrics.toString())
    }

    @Test fun resultBubbleMovesWithItsScrollingHeader() {
        rule.mainClock.autoAdvance = true
        val long = result(PetType.Cat).copy(innerMonologue = "喵呜～想和你一起看看窗外，聊聊今天的小心思！".repeat(12))
        rule.setContent { PetTranslatorTheme { InterpretationResultScreen(long, {}, {}, {}) } }
        awaitTag("result_art_bubble")
        val headerBefore = rule.onNodeWithTag("result_header").getUnclippedBoundsInRoot()
        val bubbleBefore = rule.onNodeWithTag("result_art_bubble").getUnclippedBoundsInRoot()
        val actionsBefore = rule.onNodeWithTag("result_actions").getUnclippedBoundsInRoot()
        rule.onNodeWithText("趣味解读仅供娱乐参考，请结合宠物实际表现判断。").performScrollTo().assertIsDisplayed()
        val headerAfter = rule.onNodeWithTag("result_header").getUnclippedBoundsInRoot()
        val bubbleAfter = rule.onNodeWithTag("result_art_bubble").getUnclippedBoundsInRoot()
        val actionsAfter = rule.onNodeWithTag("result_actions").getUnclippedBoundsInRoot()
        assertTrue(headerAfter.top < headerBefore.top)
        assertEquals((bubbleBefore.top - headerBefore.top).value, (bubbleAfter.top - headerAfter.top).value, .5f)
        assertEquals(actionsBefore.top.value, actionsAfter.top.value, .5f)
        capture("result-bubble-scrolled")
        File(folder, "decoration-scroll.txt").writeText(
            "Header and bubble scroll together with preserved relative offset; fixed actions do not move.\n" +
                "before header=$headerBefore bubble=$bubbleBefore; after header=$headerAfter bubble=$bubbleAfter\n")
    }
}
