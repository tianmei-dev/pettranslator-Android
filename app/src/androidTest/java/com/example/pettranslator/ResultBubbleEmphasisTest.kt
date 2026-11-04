package com.example.pettranslator

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
import com.example.pettranslator.ui.result.InterpretationResultScreen
import com.example.pettranslator.ui.theme.PetFontFamilies
import com.example.pettranslator.ui.theme.PetTranslatorTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The celebration bubble is emphasized without changing the accepted hero/card geometry. */
class ResultBubbleEmphasisTest {
    @get:Rule val rule = createComposeRule()
    @Before fun wake() = wakeTestDevice()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val folder get() = File(instrumentation.targetContext.getExternalFilesDir(null), "result-bubble-emphasis-evidence").apply { mkdirs() }
    private val report = StringBuilder()

    private fun result(pet: PetType): PetInterpretationResult {
        val cat = pet == PetType.Cat
        val sound = SoundEvent(if (cat) 78 else 70, if (cat) "Meow" else "Bark", .74f)
        val recognition = PetRecognitionResult(selectedPet = pet, scene = PetScene.Alone, detectedPet = pet,
            status = RecognitionStatus.SUCCESS, catScore = if (cat) .74f else .05f,
            dogScore = if (cat) .05f else .74f, otherScore = .08f,
            primarySound = sound, soundEvents = listOf(sound),
            quality = AudioQuality(3000, -25.0, 0.0, .05, 3, true),
            modelVersion = "result-bubble-emphasis-fixture", ruleVersion = "result-bubble-emphasis-fixture")
        return checkNotNull(PetInterpretationEngine(now = { 1_800_000_000_000L })
            .interpret(recognition, "a".repeat(64)))
    }

    @Composable private fun Screen(pet: PetType, width: Int, scale: Float) {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
            PetTranslatorTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(if (width == 0) Modifier.fillMaxSize() else Modifier.width(width.dp).fillMaxHeight()) {
                        key(pet, width, scale) { InterpretationResultScreen(result(pet), {}, {}, {}) }
                    }
                }
            }
        }
    }

    private fun awaitContent() {
        rule.mainClock.advanceTimeBy(160L)
        rule.waitForIdle()
        rule.waitUntil(5000L) {
            runCatching { rule.onAllNodesWithTag("result_art_bubble").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        rule.waitForIdle()
    }

    private fun overlaps(a: DpRect, b: DpRect) =
        a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top

    private fun verify(pet: PetType, label: String) {
        val header = rule.onNodeWithTag("result_header").getUnclippedBoundsInRoot()
        val info = rule.onNodeWithTag("result_pet_info").getUnclippedBoundsInRoot()
        val bubble = rule.onNodeWithTag("result_art_bubble").assertIsDisplayed().getUnclippedBoundsInRoot()
        val onePixel = with(rule.density) { 1.toDp() }
        val geometry = "$label header=$header infoTop=${info.top}; bubble=$bubble; onePixel=$onePixel"
        File(folder, "measured-$label.txt").writeText(geometry)
        assertEquals("$label hero height must remain 208dp", 208f, header.height.value, .5f)
        assertEquals("$label information card must retain its original position", header.bottom.value, info.top.value, .5f)
        assertTrue("$label bubble leaves the hero: $geometry", bubble.left + onePixel >= header.left && bubble.right <= header.right + onePixel &&
            bubble.top + onePixel >= header.top && bubble.bottom <= header.bottom + onePixel)
        val back = rule.onNodeWithTag("recording_back").getUnclippedBoundsInRoot()
        val home = rule.onNodeWithTag("result_home").getUnclippedBoundsInRoot()
        assertFalse("$label bubble covers back navigation", overlaps(bubble, back))
        assertFalse("$label bubble covers home navigation", overlaps(bubble, home))

        // Compare with the accepted A1.5.3.1 geometry, rather than reproducing a
        // new implementation: old width=max(27% of hero,94dp), bottom inset=2dp.
        val oldWidth = maxOf(header.width.value * .27f, 94f)
        val widthRatio = bubble.width.value / oldWidth
        val expectedRatio = if (pet == PetType.Cat) 1.15f else 1.10f
        assertTrue("$label bubble enlargement mismatch: expected=$expectedRatio actual=$widthRatio; $geometry",
            widthRatio in (expectedRatio - .02f)..(expectedRatio + .02f))
        // The old two-line bubble measured about 51dp high on the physical phone.
        // Allow pixel/font metric rounding while checking the per-pet upward
        // change independently of the chosen modifier chain.
        val oldHeight = 51.05f
        val oldCenterY = header.bottom.value - 2f - oldHeight / 2f
        val newCenterY = (bubble.top.value + bubble.bottom.value) / 2f
        val raisedBy = oldCenterY - newCenterY
        // The dog keeps its lower anchor to avoid its right ear; growth alone
        // moves its center slightly upward. The cat has the stronger upward move.
        val expectedRise = if (pet == PetType.Cat) 17f..24f else 0f..6f
        assertTrue("$label center rise mismatch: expected=$expectedRise actual=$raisedBy; $geometry", raisedBy in expectedRise)

        val textNode = rule.onNodeWithTag("pet_bubble_text", useUnmergedTree = true).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        textNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val text = layouts.single()
        assertEquals("毛孩子的\n心声来啦！", text.layoutInput.text.text)
        assertEquals(PetFontFamilies.Title, text.layoutInput.style.fontFamily)
        assertFalse("$label bubble text height overflow", text.didOverflowHeight)
        assertEquals("$label bubble text must retain both complete lines", 2, text.lineCount)
        assertEquals(text.layoutInput.text.length, text.getLineEnd(text.lineCount - 1))
        for (line in 0 until text.lineCount) {
            assertFalse("$label bubble text is ellipsized", text.isLineEllipsized(line))
            assertTrue("$label line exceeds measured width", text.getLineRight(line) - text.getLineLeft(line) <= text.size.width + 1f)
        }
        val textBounds = textNode.getUnclippedBoundsInRoot()
        val textDetail = "$geometry; text=$textBounds; textLayout=${text.size}, font=${text.layoutInput.style.fontSize}, lines=${text.lineCount}"
        File(folder, "measured-$label.txt").appendText("\n$textDetail\n")
        assertTrue("$label text leaves the bubble: $textDetail", textBounds.left + onePixel >= bubble.left && textBounds.right <= bubble.right + onePixel &&
            textBounds.top + onePixel >= bubble.top && textBounds.bottom <= bubble.bottom + onePixel)
        report.appendLine("$label header=$header infoTop=${info.top}; bubble=$bubble; oldWidth=${oldWidth}dp " +
            "widthRatio=$widthRatio; oldCenterY=${oldCenterY}dp newCenterY=${newCenterY}dp raisedBy=${raisedBy}dp; " +
            "text=$textBounds font=${text.layoutInput.style.fontFamily}, lines=${text.lineCount}")
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureToImage()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun emphasizedBubbleFitsPhysicalCatAndDogHeaders() {
        rule.mainClock.autoAdvance = false
        var pet by mutableStateOf(PetType.Cat)
        rule.setContent { Screen(pet, width = 0, scale = 1f) }
        for (current in PetType.entries) {
            rule.runOnIdle { pet = current }
            awaitContent()
            val name = "phone-result-${pet.name.lowercase()}"
            // Retain a real screenshot even if the geometry assertion fails.
            capture(name)
            verify(pet, name)
        }
        File(folder, "physical-result-bubble-geometry.txt").writeText(report.toString())
    }

    @Test fun emphasizedBubbleRemainsContainedAtSmallWidthsAndLargeFont() {
        rule.mainClock.autoAdvance = false
        var pet by mutableStateOf(PetType.Cat)
        var width by mutableIntStateOf(360)
        var scale by mutableFloatStateOf(1f)
        rule.setContent { Screen(pet, width, scale) }
        for (requestedWidth in listOf(360, 320)) for (requestedScale in listOf(1f, 2f)) for (requestedPet in PetType.entries) {
            rule.runOnIdle { width = requestedWidth; scale = requestedScale; pet = requestedPet }
            awaitContent()
            val name = "result-${pet.name.lowercase()}-${width}dp-font$scale"
            capture(name)
            verify(pet, name)
        }
        File(folder, "responsive-result-bubble-geometry.txt").writeText(report.toString())
    }
}
