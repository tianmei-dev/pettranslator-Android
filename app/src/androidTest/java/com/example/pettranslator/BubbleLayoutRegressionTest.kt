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
import com.example.pettranslator.domain.interpretation.PetInterpretationEngine
import com.example.pettranslator.domain.interpretation.PetInterpretationResult
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

/** Guards against decorative bubbles adding layout headroom or moving pet artwork. */
class BubbleLayoutRegressionTest {
    @get:Rule val rule = createComposeRule()
    @Before fun wake() = wakeTestDevice()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val folder get() = File(instrumentation.targetContext.getExternalFilesDir(null), "a1531-evidence").apply { mkdirs() }
    private val report = StringBuilder()
    private enum class Page { Recording, Result, Analysis }

    private fun result(pet: PetType): PetInterpretationResult {
        val cat = pet == PetType.Cat
        val sound = SoundEvent(if (cat) 78 else 70, if (cat) "Meow" else "Bark", .74f)
        val recognition = PetRecognitionResult(selectedPet = pet, scene = PetScene.Alone, detectedPet = pet,
            status = RecognitionStatus.SUCCESS, catScore = if (cat) .74f else .05f,
            dogScore = if (cat) .05f else .74f, otherScore = .08f, primarySound = sound, soundEvents = listOf(sound),
            quality = AudioQuality(3000, -25.0, 0.0, .05, 3, true),
            modelVersion = "bubble-layout-fixture", ruleVersion = "bubble-layout-fixture")
        return checkNotNull(PetInterpretationEngine(now = { 1_800_000_000_000L })
            .interpret(recognition, "a".repeat(64)))
    }

    @Composable private fun Screen(page: Page, pet: PetType, width: Int, fontScale: Float,
                                   phase: RecordingPhase = RecordingPhase.RECORDING) {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            PetTranslatorTheme {
                Box(Modifier.fillMaxSize()) {
                    Box((if (width == 0) Modifier.fillMaxSize() else Modifier.width(width.dp).fillMaxHeight())
                        .testTag("regression_frame")) {
                        key(page, pet, width, fontScale, phase) {
                            when (page) {
                                Page.Recording -> Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                                    RecordingScreen(pet, PetScene.Alone,
                                        RecordingUiState(phase = phase, elapsedMs = if (phase == RecordingPhase.RECORDING) 3000L else 0L),
                                        {}, {}, {}, {}, {})
                                }
                                Page.Result -> InterpretationResultScreen(result(pet), {}, {}, {})
                                Page.Analysis -> Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                                    AnalysisScreen(pet, 3, Long.MAX_VALUE, 5000L, {})
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun await(tag: String) {
        rule.mainClock.advanceTimeBy(160L)
        rule.waitForIdle()
        rule.waitUntil(5000L) {
            runCatching { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        rule.waitForIdle()
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureToImage()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun textLayout(tag: String, label: String): TextLayoutResult {
        val node = rule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val value = layouts.single()
        assertFalse("$label text height overflow", value.didOverflowHeight)
        assertEquals("$label text must be complete", value.layoutInput.text.length,
            value.getLineEnd(value.lineCount - 1))
        for (line in 0 until value.lineCount) {
            assertFalse("$label text ellipsis", value.isLineEllipsized(line))
            assertTrue("$label text wider than layout", value.getLineRight(line) - value.getLineLeft(line) <= value.size.width + 1f)
        }
        report.appendLine("$label textSize=${value.size}, lines=${value.lineCount}, text=${value.layoutInput.text}")
        return value
    }

    private data class RoomGeometry(val room: DpRect, val art: DpRect) {
        val artRelativeTop get() = art.top - room.top
        val artRelativeLeft get() = art.left - room.left
    }

    private fun roomGeometry(pet: PetType, label: String): RoomGeometry {
        val room = rule.onNodeWithTag("recording_room").getUnclippedBoundsInRoot()
        val art = rule.onNodeWithTag("recording_room_art").getUnclippedBoundsInRoot()
        val ratio = if (pet == PetType.Cat) 620f / 426f else 697f / 426f
        assertEquals("$label room must retain width/1.55, without bubble headroom",
            room.width.value / 1.55f, room.height.value, .7f)
        assertEquals("$label pet width must retain 74% of the room", room.width.value * .74f, art.width.value, .7f)
        // The image's semantics tag may be before or after the retained 2dp bottom padding.
        assertTrue("$label pet must retain its original proportional height",
            kotlin.math.abs(art.height.value - art.width.value / ratio) <= 2.7f)
        assertEquals("$label pet must stay centered", room.left.value + room.width.value / 2f,
            art.left.value + art.width.value / 2f, .7f)
        assertTrue("$label pet must stay at the original bottom edge", (room.bottom - art.bottom).value in -.2f..2.7f)
        report.appendLine("$label room=$room, art=$art; expectedOriginalRoomHeight=${room.width / 1.55f}")
        return RoomGeometry(room, art)
    }

    private fun checkBubbleInside(parentTag: String, bubbleTag: String, label: String) {
        val bubble = rule.onNodeWithTag(bubbleTag).assertIsDisplayed().getUnclippedBoundsInRoot()
        val parent = rule.onNodeWithTag(parentTag).getUnclippedBoundsInRoot()
        assertTrue("$label bubble leaves the unchanged hero", bubble.left >= parent.left && bubble.right <= parent.right &&
            bubble.top >= parent.top && bubble.bottom <= parent.bottom)
        val text = textLayout("pet_bubble_text", label)
        assertEquals(PetFontFamilies.Title, text.layoutInput.style.fontFamily)
        val textBounds = rule.onNodeWithTag("pet_bubble_text", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("$label text leaves the bubble", textBounds.left >= bubble.left && textBounds.right <= bubble.right &&
            textBounds.top >= bubble.top && textBounds.bottom <= bubble.bottom)
        val back = rule.onNodeWithTag("recording_back").getUnclippedBoundsInRoot()
        fun overlaps(a: DpRect, b: DpRect) =
            a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
        assertFalse("$label bubble covers navigation", overlaps(bubble, back))
        if (parentTag == "result_header") {
            val home = rule.onNodeWithTag("result_home").getUnclippedBoundsInRoot()
            assertFalse("$label bubble covers home action", overlaps(bubble, home))
        }
        report.appendLine("$label bubble=$bubble; text=$textBounds")
    }

    @Test fun physicalViewportScreenshots() {
        rule.mainClock.autoAdvance = false
        var page by mutableStateOf(Page.Recording)
        var pet by mutableStateOf(PetType.Cat)
        // width=0 uses the phone's complete native viewport, without a 320/360dp fixture.
        rule.setContent { Screen(page, pet, width = 0, fontScale = 1f) }
        for (current in PetType.entries) {
            rule.runOnIdle { page = Page.Recording; pet = current }
            await("recording_art_bubble")
            val name = "phone-recording-${pet.name.lowercase()}"
            roomGeometry(pet, name)
            checkBubbleInside("recording_room", "recording_art_bubble", name)
            capture(name)
        }
        rule.runOnIdle { page = Page.Analysis; pet = PetType.Cat }
        await("font_trial_analysis")
        val title = textLayout("font_trial_analysis", "phone-analysis")
        assertEquals(PetFontFamilies.Title, title.layoutInput.style.fontFamily)
        assertEquals(22.sp, title.layoutInput.style.fontSize)
        capture("phone-analysis")
        for (current in PetType.entries) {
            rule.runOnIdle { page = Page.Result; pet = current }
            await("result_art_bubble")
            val name = "phone-result-${pet.name.lowercase()}"
            val header = rule.onNodeWithTag("result_header").getUnclippedBoundsInRoot()
            assertEquals("$name result hero must retain 208dp at the phone's native width", 208f, header.height.value, .5f)
            checkBubbleInside("result_header", "result_art_bubble", name)
            capture(name)
        }
        val frame = rule.onNodeWithTag("regression_frame").getUnclippedBoundsInRoot()
        report.appendLine("Physical device viewport: $frame; density=${rule.density.density}; no fixed-width fixture.")
        File(folder, "phone-viewport-geometry.txt").writeText(report.toString())
    }

    @Test fun recordingOverlayKeepsReadyArtworkDimensionsAndOffsets() {
        rule.mainClock.autoAdvance = false
        var width by mutableIntStateOf(360)
        var scale by mutableFloatStateOf(1f)
        var pet by mutableStateOf(PetType.Cat)
        var phase by mutableStateOf(RecordingPhase.READY)
        rule.setContent { Screen(Page.Recording, pet, width, scale, phase) }
        for (requestedWidth in listOf(360, 320)) for (requestedScale in listOf(1f, 1.5f, 2f)) for (requestedPet in PetType.entries) {
            rule.runOnIdle { width = requestedWidth; scale = requestedScale; pet = requestedPet; phase = RecordingPhase.READY }
            await("recording_room_art")
            rule.onNodeWithTag("recording_art_bubble").assertDoesNotExist()
            rule.onNodeWithTag("recording_room").performScrollTo()
            val name = "${pet.name.lowercase()}-${width}dp-font$scale"
            val before = roomGeometry(pet, "$name-READY")
            if (width == 360 && scale == 1f) capture("recording-$name-ready")
            rule.runOnIdle { phase = RecordingPhase.RECORDING }
            await("recording_art_bubble")
            rule.onNodeWithTag("recording_room").performScrollTo()
            val after = roomGeometry(pet, "$name-RECORDING")
            assertEquals("$name bubble changed room height", before.room.height.value, after.room.height.value, .5f)
            assertEquals("$name bubble changed pet height", before.art.height.value, after.art.height.value, .5f)
            assertEquals("$name bubble moved the pet vertically", before.artRelativeTop.value, after.artRelativeTop.value, .5f)
            assertEquals("$name bubble moved the pet horizontally", before.artRelativeLeft.value, after.artRelativeLeft.value, .5f)
            checkBubbleInside("recording_room", "recording_art_bubble", name)
            capture("recording-$name")
        }
        File(folder, "recording-before-after-geometry.txt").writeText(report.toString())
    }

    @Test fun resultOverlayRetainsExactly208dpAndInformationCardPosition() {
        rule.mainClock.autoAdvance = false
        var width by mutableIntStateOf(360)
        var scale by mutableFloatStateOf(1f)
        var pet by mutableStateOf(PetType.Cat)
        rule.setContent { Screen(Page.Result, pet, width, scale) }
        for (requestedWidth in listOf(360, 320)) for (requestedScale in listOf(1f, 1.5f, 2f)) for (requestedPet in PetType.entries) {
            rule.runOnIdle { width = requestedWidth; scale = requestedScale; pet = requestedPet }
            await("result_art_bubble")
            val name = "${pet.name.lowercase()}-${width}dp-font$scale"
            val header = rule.onNodeWithTag("result_header").getUnclippedBoundsInRoot()
            val info = rule.onNodeWithTag("result_pet_info").getUnclippedBoundsInRoot()
            val picture = rule.onNodeWithContentDescription("${pet.title}在温馨房间里的插画").getUnclippedBoundsInRoot()
            assertEquals("$name result header must not grow for its bubble", 208f, header.height.value, .5f)
            assertEquals("$name header artwork remains 208dp", 208f, picture.height.value, .5f)
            assertEquals("$name artwork width remains unchanged", header.width.value, picture.width.value, .5f)
            assertEquals("$name artwork must not be moved down", header.top.value, picture.top.value, .5f)
            assertEquals("$name pet information must immediately follow the original 208dp header", header.bottom.value, info.top.value, .5f)
            checkBubbleInside("result_header", "result_art_bubble", name)
            report.appendLine("$name expectedHeaderBefore=208dp measuredAfter=$header; picture=$picture; infoTop=${info.top}")
            capture("result-$name")
        }
        File(folder, "result-header-before-after-geometry.txt").writeText(report.toString())
    }

    @Test fun artisticArcKeepsItsOriginalTextHostSpace() {
        rule.mainClock.autoAdvance = false
        var width by mutableIntStateOf(360)
        var scale by mutableFloatStateOf(1f)
        rule.setContent { Screen(Page.Analysis, PetType.Cat, width, scale) }
        for (requestedWidth in listOf(360, 320)) for (requestedScale in listOf(1f, 1.5f, 2f)) {
            rule.runOnIdle { width = requestedWidth; scale = requestedScale }
            await("font_trial_analysis")
            rule.onNodeWithTag("font_trial_analysis").performScrollTo()
            val name = "analysis-${width}dp-font$scale"
            val layout = textLayout("font_trial_analysis", name)
            assertEquals(PetFontFamilies.Title, layout.layoutInput.style.fontFamily)
            assertEquals(22.sp, layout.layoutInput.style.fontSize)
            val host = rule.onNodeWithTag("art_title_glyphs").getUnclippedBoundsInRoot()
            val textHeightDp = with(rule.density) { layout.size.height.toDp() }
            assertEquals("$name drawing-only arc change must keep the existing 4dp host padding",
                textHeightDp.value + 4f, host.height.value, .7f)
            report.appendLine("$name normalTextHeight=$textHeightDp; originalPadding=4dp; measuredHost=$host")
            capture(name)
        }
        File(folder, "analysis-title-host-geometry.txt").writeText(report.toString())
    }
}
