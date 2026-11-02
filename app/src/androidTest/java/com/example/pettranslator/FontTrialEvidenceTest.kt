package com.example.pettranslator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
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
import com.example.pettranslator.ui.navigation.PetTranslatorApp
import com.example.pettranslator.ui.result.InterpretationResultScreen
import com.example.pettranslator.ui.result.ResultCardExporter
import com.example.pettranslator.ui.theme.*
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real-device screenshots of production Composables; all font switching is test-local. */
class FontTrialEvidenceTest {
    @get:Rule val rule = createComposeRule()
    @Before fun wake() = wakeTestDevice()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val folder get() = File(instrumentation.targetContext.getExternalFilesDir(null), "a151-evidence").apply { mkdirs() }
    private val metrics = StringBuilder()
    private enum class Page { Home, Analysis, Result }
    private class TrialPreferences : PetPreferences {
        override val petType = MutableStateFlow(PetType.Cat)
        override suspend fun setPetType(type: PetType) { petType.value = type }
    }

    private fun result(pet: PetType = PetType.Cat, scene: PetScene = PetScene.Alone,
                       template: PetInterpretationTemplate? = null): PetInterpretationResult {
        val event = SoundEvent(if (pet == PetType.Cat) 78 else 70, if (pet == PetType.Cat) "Meow" else "Bark", .74f)
        val recognition = PetRecognitionResult(selectedPet = pet, scene = scene, detectedPet = pet,
            status = RecognitionStatus.SUCCESS, catScore = if (pet == PetType.Cat) .74f else .05f,
            dogScore = if (pet == PetType.Dog) .74f else .05f, otherScore = .08f,
            primarySound = event, soundEvents = listOf(event),
            quality = AudioQuality(3000, -25.0, 0.0, .05, 3, true), modelVersion = "font-ui-fixture", ruleVersion = "font-ui-fixture")
        val engine = if (template == null) PetInterpretationEngine(now = { 1_800_000_000_000L })
            else PetInterpretationEngine(now = { 1_800_000_000_000L }, templates = listOf(template))
        return checkNotNull(engine.interpret(recognition, "a".repeat(64)))
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureToImage() // Synchronize with the actual window draw.
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun checkText(tag: String, label: String, scroll: Boolean = false): TextLayoutResult {
        val node = rule.onNodeWithTag(tag)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val lines = (0 until layout.lineCount).joinToString { line ->
            "line$line left=${layout.getLineLeft(line)} right=${layout.getLineRight(line)} " +
                "extent=${layout.getLineRight(line) - layout.getLineLeft(line)} ellipsis=${layout.isLineEllipsized(line)}"
        }
        val detail = "$label: size=${layout.size}px node=$bounds paragraphWidth=${layout.multiParagraph.width} " +
            "constraints=${layout.layoutInput.constraints} lines=${layout.lineCount}; $lines; text=${layout.layoutInput.text}"
        metrics.appendLine(detail)
        // GetTextLayoutResult can reconstruct a centered paragraph using the parent
        // maxWidth while reporting the Text node's intrinsic size. Its line offsets
        // then belong to that wider paragraph, so compare glyph extents, not right
        // offsets, with the actual Text width. Persist both coordinate systems.
        val error = when {
            layout.didOverflowHeight -> "$label: height overflow"
            (0 until layout.lineCount).any { layout.isLineEllipsized(it) } -> "$label: unexpected ellipsis"
            (0 until layout.lineCount).any {
                layout.getLineRight(it) - layout.getLineLeft(it) > layout.size.width + 1f
            } -> "$label: glyph extent exceeds Text width"
            else -> null
        }
        if (error != null) {
            val safeName = label.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            File(folder, "failure-$safeName.txt").writeText(metrics.toString())
            capture("failure-$safeName")
            fail("$error; $detail")
        }
        return layout
    }

    @Test fun threeFontsOnThreeProductionPages() {
        var font by mutableStateOf(PetTrialFont.System)
        var page by mutableStateOf(Page.Home)
        val preferences = TrialPreferences()
        val sameResult = result()
        rule.setContent {
            CompositionLocalProvider(LocalPetFontTrial provides PetFontTrial(font, font, font)) {
                PetTranslatorTheme {
                    key(page, font) {
                        when (page) {
                            Page.Home -> PetTranslatorApp(preferences)
                            Page.Analysis -> Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                                // Fixed fixture at the start of analysis keeps every capture at the same state.
                                AnalysisScreen(PetType.Cat, 3, Long.MAX_VALUE, 5_000L, {})
                            }
                            Page.Result -> InterpretationResultScreen(sameResult, {}, {}, {})
                        }
                    }
                }
            }
        }
        for (choice in PetTrialFont.entries) {
            rule.mainClock.autoAdvance = true
            rule.runOnIdle { page = Page.Home; font = choice }
            rule.waitForIdle()
            rule.waitUntil(5_000) {
                runCatching { rule.onAllNodesWithTag("font_trial_home").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
            }
            checkText("font_trial_home", "$choice-home")
            capture("${choice.name.lowercase()}-home")
            rule.mainClock.autoAdvance = false
            rule.runOnIdle { page = Page.Analysis }
            rule.mainClock.advanceTimeBy(160)
            val analysis = checkText("font_trial_analysis", "$choice-analysis")
            assertEquals("Normal-size analysis title should stay on one line", 1, analysis.lineCount)
            capture("${choice.name.lowercase()}-analysis")
            rule.runOnIdle { page = Page.Result }
            rule.mainClock.autoAdvance = true
            checkText("font_trial_monologue", "$choice-result", scroll = true)
            capture("${choice.name.lowercase()}-result")
        }
        File(folder, "page-layouts.txt").writeText(metrics.toString())
    }

    @Test fun approvedCopyAndLargeFontRemainReadable() {
        var font by mutableStateOf(PetTrialFont.System)
        var current by mutableStateOf(result())
        var scale by mutableFloatStateOf(1f)
        var narrow by mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalPetFontTrial provides PetFontTrial(font, font, font),
                LocalDensity provides Density(LocalDensity.current.density, scale)) {
                PetTranslatorTheme {
                    Box(if (narrow) Modifier.width(320.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                        InterpretationResultScreen(current, {}, {}, {})
                    }
                }
            }
        }
        // Every approved line is rendered through the actual result card for each font.
        for (choice in PetTrialFont.entries) {
            for (template in PetInterpretationTemplateCatalog.entries) {
                rule.runOnIdle { font = choice; current = result(template.petType, template.scene, template) }
                checkText("font_trial_monologue", "$choice-${template.templateId}", scroll = true)
                val textBounds = rule.onNodeWithTag("font_trial_monologue").fetchSemanticsNode().boundsInRoot
                val artBounds = rule.onNodeWithContentDescription("${template.petType.title}插画").fetchSemanticsNode().boundsInRoot
                assertFalse("$choice-${template.templateId}: quote overlaps artwork", textBounds.overlaps(artBounds))
            }
            val longest = PetInterpretationTemplateCatalog.entries.maxBy { it.innerMonologue.length }
            rule.runOnIdle { narrow = true; scale = 2f; font = choice; current = result(longest.petType, longest.scene, longest) }
            checkText("font_trial_monologue", "$choice-320dp-font2-${longest.templateId}", scroll = true)
            capture("${choice.name.lowercase()}-long-font2")
            rule.runOnIdle { narrow = false; scale = 1f }
        }
        File(folder, "copy-layouts.txt").writeText(metrics.toString())
    }

    @Test fun characterFallbackEmojiAndExistingExport() {
        val context = instrumentation.targetContext
        val sample = "中文0123456789，。！？；：‘’“”（）《》……～—👀✨🐱🐶"
        val allText = PetInterpretationTemplateCatalog.entries.joinToString("") { it.innerMonologue } + sample
        val codePoints = allText.codePoints().toArray().distinct().filterNot { Character.isWhitespace(it) }
        val report = StringBuilder()
        for (choice in PetTrialFont.entries) {
            val typeface = when (choice) {
                PetTrialFont.ZhenKai -> context.resources.getFont(R.font.pet_title_zhenkai)
                PetTrialFont.WenKai -> context.resources.getFont(R.font.pet_title_wenkai)
                PetTrialFont.System -> Typeface.DEFAULT
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = 64f; color = Color.BLACK }
            val missing = codePoints.filterNot { paint.hasGlyph(String(Character.toChars(it))) }
            assertTrue("$choice fallback missing codepoints: $missing", missing.isEmpty())
            // A colored emoji must retain non-grey pixels under the selected font's fallback.
            val emoji = Bitmap.createBitmap(110, 110, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(emoji)
            canvas.drawColor(Color.WHITE)
            canvas.drawText("🐱", 8f, 80f, paint)
            var colorPixels = 0
            for (y in 0 until emoji.height) for (x in 0 until emoji.width) {
                val pixel = emoji.getPixel(x, y)
                if (maxOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)) -
                    minOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)) > 25) colorPixels++
            }
            assertTrue("$choice emoji lost color", colorPixels > 30)
            File(folder, "${choice.name.lowercase()}-emoji.png").outputStream().use { emoji.compress(Bitmap.CompressFormat.PNG, 100, it) }
            emoji.recycle()
            report.appendLine("$choice: ${codePoints.size} distinct codepoints including all 96 approved lines supported with Android fallback; cat emoji colored pixels=$colorPixels")
        }
        // Production export intentionally keeps its existing system-font rendering.
        val longTemplate = PetInterpretationTemplateCatalog.entries.maxBy { it.innerMonologue.length }
        val longResult = result(longTemplate.petType, longTemplate.scene, longTemplate)
        val exported = ResultCardExporter.render(context, longResult)
        assertTrue(exported.width == 1080 && exported.height >= 1400)
        val shareFile = ResultCardExporter.writeShareFile(context, exported)
        assertTrue(shareFile.exists() && shareFile.length() > 1000)
        File(folder, "existing-system-font-export.png").outputStream().use { exported.compress(Bitmap.CompressFormat.PNG, 100, it) }
        exported.recycle()
        report.appendLine("Unmodified ResultCardExporter.render/writeShareFile: passed, PNG bytes=${shareFile.length()}; no gallery write or share intent launched.")
        File(folder, "glyph-and-export.txt").writeText(report.toString())
    }
}
