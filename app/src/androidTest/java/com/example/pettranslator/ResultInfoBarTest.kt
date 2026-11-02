package com.example.pettranslator

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pettranslator.domain.*
import com.example.pettranslator.domain.interpretation.*
import com.example.pettranslator.domain.recognition.*
import com.example.pettranslator.ui.result.*
import com.example.pettranslator.ui.theme.PetTranslatorTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import org.junit.*
import org.junit.Assert.*

class ResultInfoBarTest {
    @get:Rule val rule = createComposeRule()
    @Before fun wake() = wakeTestDevice()
    private val timestamp = 1_800_000_000_000L
    private fun result(pet: PetType, scene: PetScene): PetInterpretationResult {
        val event = SoundEvent(if (pet == PetType.Cat) 78 else 70, if (pet == PetType.Cat) "Meow" else "Bark", .74f)
        val recognition = PetRecognitionResult(selectedPet = pet, scene = scene, detectedPet = pet,
            status = RecognitionStatus.SUCCESS, catScore = .74f, dogScore = .74f, otherScore = .08f,
            primarySound = event, soundEvents = listOf(event),
            quality = AudioQuality(3000, -25.0, 0.0, .05, 3, true), modelVersion = "ui-fixture", ruleVersion = "ui-fixture")
        return checkNotNull(PetInterpretationEngine(now = { timestamp }).interpret(recognition, "a".repeat(64)))
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "a145-evidence").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun bothPetsAllScenesAndResponsiveText() {
        var current by mutableStateOf(result(PetType.Cat, PetScene.Alone))
        var scale by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(0)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                PetTranslatorTheme {
                    if (width == 0) InterpretationResultScreen(current, {}, {}, {})
                    else Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        Box(Modifier.width(width.dp).padding(horizontal = 14.dp)) { PetInfoBar(current) }
                    }
                }
            }
        }
        for (pet in PetType.entries) {
            for (scene in PetScene.entries) {
                rule.runOnIdle { current = result(pet, scene) }
                rule.onNodeWithText(pet.title).assertIsDisplayed()
                rule.onNodeWithText(pet.subtitle).assertIsDisplayed()
                rule.onNodeWithTag("result_scene").assertHasNoClickAction()
                rule.onNodeWithText(scene.title).assertIsDisplayed()
                verifyDate()
                if (scene == PetScene.Alone) capture("result-${pet.name.lowercase()}")
            }
        }
        for (font in listOf(1f, 1.5f, 2f)) {
            rule.runOnIdle { width = 320; scale = font; current = result(PetType.Dog, PetScene.Door) }
            rule.onNodeWithText("汪汪的小心声").assertIsDisplayed()
            rule.onNodeWithText("门口或窗边").assertIsDisplayed()
            verifyDate()
            val petBounds = rule.onNodeWithText("汪汪的小心声").fetchSemanticsNode().boundsInRoot
            val sceneBounds = rule.onNodeWithTag("result_scene").fetchSemanticsNode().boundsInRoot
            assertFalse(petBounds.overlaps(sceneBounds))
            capture("small-font-$font")
        }
    }
    private fun verifyDate() {
        val node = rule.onNodeWithTag("result_created_at").assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        // With softWrap=false Compose can flag paragraph-width overflow even when
        // every glyph fits. Check the actual line and intrinsic text extents.
        assertTrue(layout.getLineRight(0) <= layout.size.width)
        assertTrue(layout.multiParagraph.intrinsics.maxIntrinsicWidth <= layout.size.width)
        assertFalse(layout.didOverflowHeight)
        assertEquals(1, layout.lineCount)
        val expected = listOf("yyyy年MM月dd日 HH:mm", "MM月dd日 HH:mm").map {
            SimpleDateFormat(it, Locale.CHINA).format(Date(timestamp))
        }
        assertTrue(layout.layoutInput.text.text in expected)
        val card = rule.onNodeWithTag("result_pet_info").fetchSemanticsNode().boundsInRoot
        val date = node.fetchSemanticsNode().boundsInRoot
        assertTrue(date.left >= card.left && date.right <= card.right)
    }
}
