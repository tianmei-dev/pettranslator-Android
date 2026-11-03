package com.example.pettranslator

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pettranslator.domain.*
import com.example.pettranslator.domain.interpretation.*
import com.example.pettranslator.domain.recognition.*
import com.example.pettranslator.ui.analysis.AnalysisScreen
import com.example.pettranslator.ui.navigation.PetTranslatorApp
import com.example.pettranslator.ui.result.InterpretationResultScreen
import com.example.pettranslator.ui.theme.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Final pairing, scrolling, and quote adaptation on a physical device. */
class FinalFontLayoutTest {
    @get:Rule val rule = createComposeRule()
    @Before fun wake() = wakeTestDevice()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val folder get() = File(instrumentation.targetContext.getExternalFilesDir(null), "a152-evidence").apply { mkdirs() }
    private val metrics = StringBuilder()
    private enum class Page { Home, Analysis, Result }
    private class Preferences : PetPreferences {
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
            quality = AudioQuality(3000, -25.0, 0.0, .05, 3, true), modelVersion = "final-font-ui-fixture", ruleVersion = "final-font-ui-fixture")
        val engine = if (template == null) PetInterpretationEngine(now = { 1_800_000_000_000L })
            else PetInterpretationEngine(now = { 1_800_000_000_000L }, templates = listOf(template))
        return checkNotNull(engine.interpret(recognition, "a".repeat(64)))
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureToImage()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun awaitContent(tag: String) {
        rule.waitForIdle()
        rule.waitUntil(5_000) {
            // The test Activity can be resumed just before its first Compose root
            // is registered, especially for the first test after APK installation.
            runCatching { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        rule.waitForIdle()
    }

    private fun layout(node: SemanticsNodeInteraction, label: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val value = layouts.single()
        val detail = "$label size=${value.size} lines=${value.lineCount} font=${value.layoutInput.style.fontFamily} " +
            "sizeSp=${value.layoutInput.style.fontSize} lineHeight=${value.layoutInput.style.lineHeight} text=${value.layoutInput.text}"
        metrics.appendLine(detail)
        assertFalse(detail, value.didOverflowHeight)
        for (line in 0 until value.lineCount) {
            assertFalse(detail, value.isLineEllipsized(line))
            // Centered-text semantics may use paragraph offsets from a wider parent.
            assertTrue(detail, value.getLineRight(line) - value.getLineLeft(line) <= value.size.width + 1f)
        }
        return value
    }

    private fun quote(label: String): TextLayoutResult {
        val node = rule.onNodeWithTag("font_trial_monologue").performScrollTo().assertIsDisplayed()
        val value = layout(node, label)
        assertEquals("Monologue must use the approved WenKai family", PetFontFamilies.Monologue, value.layoutInput.style.fontFamily)
        assertEquals(18.sp, value.layoutInput.style.fontSize)
        assertEquals(25.sp, value.layoutInput.style.lineHeight)
        val text = node.getUnclippedBoundsInRoot()
        val art = rule.onNodeWithTag("result_quote_art").getUnclippedBoundsInRoot()
        val overlaps = text.left < art.right && text.right > art.left && text.top < art.bottom && text.bottom > art.top
        assertFalse("$label: text and pet illustration overlap", overlaps)
        val card = rule.onNodeWithTag("result_quote").getUnclippedBoundsInRoot()
        assertTrue("$label: text leaves card horizontally", text.left >= card.left && text.right <= card.right)
        assertTrue("$label: text leaves card vertically", text.top >= card.top && text.bottom <= card.bottom)
        assertTrue("$label: illustration leaves card", art.left >= card.left && art.right <= card.right && art.top >= card.top && art.bottom <= card.bottom)
        fixedActions()
        return value
    }

    private fun fixedActions() {
        val viewport = rule.onNodeWithTag("result_scroll").fetchSemanticsNode().boundsInRoot
        val actions = rule.onNodeWithTag("result_actions").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Scrolling viewport overlaps fixed actions: $viewport vs $actions", viewport.bottom <= actions.top + 1f)
        listOf("再翻译一次", "保存结果", "分享心声").forEach { text ->
            val label = rule.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
            layout(label, "action-$text")
        }
    }

    @Test fun approvedDefaultPairingAndPageScreenshots() {
        var page by mutableStateOf(Page.Home)
        var current by mutableStateOf(result())
        val preferences = Preferences()
        rule.setContent {
            PetTranslatorTheme {
                key(page) {
                    when (page) {
                        Page.Home -> PetTranslatorApp(preferences)
                        Page.Analysis -> Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                            // Snapshot fixture only: identical stable initial analysis state.
                            AnalysisScreen(PetType.Cat, 3, Long.MAX_VALUE, 5_000L, {})
                        }
                        Page.Result -> InterpretationResultScreen(current, {}, {}, {})
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.waitUntil(5_000) {
            runCatching { rule.onAllNodesWithTag("font_trial_home").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        val home = layout(rule.onNodeWithTag("font_trial_home").assertIsDisplayed(), "home-title")
        assertEquals(PetFontFamilies.Title, home.layoutInput.style.fontFamily)
        val body = layout(rule.onNodeWithText("选择它和场景，听听毛孩子的小心思～"), "home-system-body")
        assertNotEquals(PetFontFamilies.ZhenKai, body.layoutInput.style.fontFamily)
        assertNotEquals(PetFontFamilies.WenKai, body.layoutInput.style.fontFamily)
        capture("final-home")
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { page = Page.Analysis }
        rule.mainClock.advanceTimeBy(160)
        val analysis = layout(rule.onNodeWithTag("font_trial_analysis").assertIsDisplayed(), "analysis-title")
        assertEquals(PetFontFamilies.Title, analysis.layoutInput.style.fontFamily)
        assertEquals(1, analysis.lineCount)
        capture("final-analysis")
        rule.runOnIdle { page = Page.Result }
        rule.mainClock.autoAdvance = true
        val entries = PetInterpretationTemplateCatalog.entries
        for (pet in PetType.entries) {
            val petEntries = entries.filter { it.petType == pet }
            for ((name, template) in listOf("short" to petEntries.minBy { it.innerMonologue.length },
                "longest-formal" to petEntries.maxBy { it.innerMonologue.length })) {
                rule.runOnIdle { current = result(template.petType, template.scene, template) }
                quote("${pet.name}-$name-${template.templateId}")
                capture("final-${pet.name.lowercase()}-$name")
            }
        }
        val stress = "今天我有好多悄悄话想告诉你，窗外的阳光暖暖的，最喜欢靠近你一起看看小鸟，再慢慢说说我的小心思！👀✨"
        assertTrue(stress.length > 35)
        for (pet in PetType.entries) {
            rule.runOnIdle {
                current = result(pet, PetScene.Alone).copy(innerMonologue = stress,
                    interpretationId = "synthetic-normal-width-font-layout-only")
            }
            quote("normal-width-${pet.name}-synthetic-${stress.length}chars")
            capture("final-${pet.name.lowercase()}-synthetic-normal")
        }
        File(folder, "final-page-layouts.txt").writeText(metrics.toString())
    }

    @Test fun allApprovedCopyAndLargeFontStressLayout() {
        var current by mutableStateOf(result())
        var width by mutableIntStateOf(360)
        var fontScale by mutableFloatStateOf(1f)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                PetTranslatorTheme {
                    Box(Modifier.width(width.dp).fillMaxHeight()) {
                        InterpretationResultScreen(current, {}, {}, {})
                    }
                }
            }
        }
        awaitContent("result_scroll")
        val entries = PetInterpretationTemplateCatalog.entries
        assertEquals(96, entries.size)
        val longest = entries.maxBy { it.innerMonologue.length }
        metrics.appendLine("Formal catalog longest=${longest.templateId}, UTF16 length=${longest.innerMonologue.length}; >35 characters tested only in synthetic fixture below.")
        for (requestedWidth in listOf(360, 320)) {
            rule.runOnIdle { width = requestedWidth }
            for (template in entries) {
                rule.runOnIdle { current = result(template.petType, template.scene, template) }
                quote("${requestedWidth}dp-1x-${template.templateId}")
            }
        }
        val stress = "喵呜～今天我有好多悄悄话想告诉你，窗外的阳光暖暖的，最喜欢靠近你一起看看小鸟，再慢慢说说我的小心思！👀✨"
        assertTrue(stress.length > 35)
        for (scale in listOf(1.5f, 2f)) {
            rule.runOnIdle { width = 320; fontScale = scale }
            for (pet in PetType.entries) {
                val petEntries = entries.filter { it.petType == pet }
                val petLongest = petEntries.maxBy { it.innerMonologue.length }
                val emoji = petEntries.firstOrNull { it.innerMonologue.codePoints().anyMatch { code -> code > 0xffff } }
                    ?: petEntries.first { it.innerMonologue.contains('✨') }
                for (template in listOf(petEntries.minBy { it.innerMonologue.length }, petLongest, emoji).distinct()) {
                    rule.runOnIdle { current = result(template.petType, template.scene, template) }
                    quote("320dp-${scale}x-${template.templateId}")
                }
                capture("final-${pet.name.lowercase()}-320dp-font$scale")
                rule.runOnIdle { current = result(pet, PetScene.Alone).copy(innerMonologue = stress, interpretationId = "synthetic-font-layout-only") }
                quote("320dp-${scale}x-${pet.name}-synthetic-${stress.length}chars")
                capture("final-${pet.name.lowercase()}-synthetic-font$scale")
            }
        }
        File(folder, "final-copy-layouts.txt").writeText(metrics.toString())
    }

    @Test fun completePageScrollsWhileActionsStayFixedAndCallbacksWork() {
        var backs = 0
        var homes = 0
        var agains = 0
        // Ensure content is taller than this device's viewport. A short result may
        // fit almost entirely, so it cannot scroll the full header out of view.
        val scrollResult = result().copy(innerMonologue =
            "喵呜～想和你一起看看窗外，聊聊今天的小心思！".repeat(12))
        rule.setContent {
            PetTranslatorTheme {
                InterpretationResultScreen(scrollResult, { backs++ }, { homes++ }, { agains++ })
            }
        }
        awaitContent("result_scroll")
        rule.onNodeWithText("翻译结果").assertIsDisplayed()
        val before = rule.onNodeWithTag("result_actions").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithText("趣味解读仅供娱乐参考，请结合宠物实际表现判断。").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("翻译结果").assertIsNotDisplayed()
        val after = rule.onNodeWithTag("result_actions").fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f)
        assertEquals(before.bottom, after.bottom, 1f)
        fixedActions()
        capture("final-result-scrolled-bottom")
        rule.onNodeWithText("再翻译一次").performClick()
        rule.runOnIdle { assertEquals(1, agains) }
        rule.onNodeWithTag("recording_back").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, backs) }
        rule.onNodeWithText("⌂").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, homes) }
        File(folder, "scroll-and-callbacks.txt").writeText("Header scrolls out; actions remain fixed; viewport does not overlap actions. Again/Back/Home each fired exactly once.\n$metrics")
    }

    @Suppress("DEPRECATION")
    @Test fun shareButtonCreatesPngAndChooserWithoutSendingToRecipient() {
        val chooser = AtomicReference<Intent>()
        val launches = AtomicInteger()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_CHOOSER) return null
                chooser.set(Intent(intent))
                launches.incrementAndGet()
                // Block the chooser here: no recipient app opens and no message is sent.
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, Intent())
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            rule.setContent {
                PetTranslatorTheme { InterpretationResultScreen(result(), {}, {}, {}) }
            }
            awaitContent("result_scroll")
            rule.onNodeWithText("分享心声").performClick()
            rule.waitUntil(10_000) { chooser.get() != null }
            rule.onNodeWithText("分享卡片已准备好").assertIsDisplayed()
            val send = checkNotNull(chooser.get().getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals("image/png", send.type)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            val uri = checkNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            assertEquals("content", uri.scheme)
            assertEquals(1, send.clipData?.itemCount)
            val signature = instrumentation.targetContext.contentResolver.openInputStream(uri).use { stream ->
                checkNotNull(stream)
                ByteArray(8).also { assertEquals(8, stream.read(it)) }
            }
            assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a), signature)
            rule.runOnIdle { assertEquals(1, launches.get()) }
            fixedActions()
            capture("final-result-share-prepared")
            File(folder, "share-button.txt").writeText(
                "Production share button: generated a readable PNG, ACTION_SEND image/png with content URI, ClipData and read grant. " +
                    "Exactly one ACTION_CHOOSER was intercepted and canceled by the test. No recipient app opened; no content sent.\n")
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}
