package com.example.pettranslator

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pettranslator.domain.PetScene
import com.example.pettranslator.domain.PetType
import java.io.File
import kotlin.math.abs
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
        rule.onNodeWithText("选择宠物").assertDoesNotExist()
        rule.onNodeWithTag("home_pet_title").assertDoesNotExist()
        rule.onNodeWithTag("home_pet_switch").assertIsDisplayed()
        val brand = rule.onNodeWithTag("home_brand").assertTextEquals("猫言狗语翻译官").assertIsDisplayed()
        val brandBounds = brand.getUnclippedBoundsInRoot()
        val vip = rule.onNodeWithContentDescription("VIP 入口，暂不可用").assertIsNotEnabled()
        val vipBounds = vip.getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue("App name must stay left of the unchanged VIP", brandBounds.right <= vipBounds.left)
        brand.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            val results = mutableListOf<TextLayoutResult>()
            action(results)
            org.junit.Assert.assertTrue("App name exposes its complete text layout", results.isNotEmpty())
            val diagnostics = results.joinToString("\n") { result ->
                "size=${result.size}, paragraph=${result.multiParagraph.width}x${result.multiParagraph.height}, " +
                    "lines=${result.lineCount}, didExceedMaxLines=${result.multiParagraph.didExceedMaxLines}, " +
                    "hasVisualOverflow=${result.hasVisualOverflow}, didOverflowWidth=${result.didOverflowWidth}, " +
                    "didOverflowHeight=${result.didOverflowHeight}, constraints=${result.layoutInput.constraints}, " +
                    "fontSize=${result.layoutInput.style.fontSize}, lineHeight=${result.layoutInput.style.lineHeight}, " +
                    "lineEnd=${if (result.lineCount > 0) result.getLineEnd(0, visibleEnd = true) else -1}, " +
                    "textLength=${result.layoutInput.text.length}, " +
                    "lineRight=${if (result.lineCount > 0) result.getLineRight(0) else -1f}, " +
                    "maxIntrinsicWidth=${result.multiParagraph.maxIntrinsicWidth}, density=${result.layoutInput.density}, " +
                    "letterSpacing=${result.layoutInput.style.letterSpacing}, fontFamily=${result.layoutInput.style.fontFamily}"
            }
            File(folder, "brand-layout.txt").writeText("brand=$brandBounds\nvip=$vipBounds\n$diagnostics\n")
            org.junit.Assert.assertFalse("App name must not overflow or ellipsize: $diagnostics", results.any { result ->
                result.hasVisualOverflow || (0 until result.lineCount).any { result.isLineEllipsized(it) }
            })
        }
        rule.onNodeWithText("它现在在哪里？").assertIsDisplayed()
        listOf("1", "2", "选择你想翻译的毛孩子", "不同场景下，TA的含义可能不同哦").forEach {
            rule.onAllNodesWithText(it).assertCountEquals(0)
        }
        PetScene.entries.forEach { rule.onNodeWithTag("scene_${it.name}").assertIsDisplayed() }
        rule.onNodeWithText("开始录音翻译").assertIsDisplayed()
        rule.onNodeWithText("趣味声音模拟，不代表宠物真实语言翻译").assertIsDisplayed()
        rule.onAllNodesWithText("把我的话说给它听").assertCountEquals(0)
        val viewport = rule.onNodeWithTag("home_viewport").getUnclippedBoundsInRoot()
        val actions = rule.onNodeWithTag("home_actions").getUnclippedBoundsInRoot()
        val scenes = rule.onNodeWithTag("home_section_2").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue("Fixed actions must sit outside scrolling content", actions.top >= viewport.bottom)
        org.junit.Assert.assertTrue("Actions must not cover scenes", actions.top >= scenes.bottom)
        val context = instrumentation.targetContext
        val artwork = rule.onNodeWithTag("home_cloud_artwork").getUnclippedBoundsInRoot()
        val cloudSource = HomeArtworkAudit.alphaBounds(context, R.drawable.pastel_cloud_pet_app_frame)
        val cloudOutline = HomeArtworkAudit.cloudOutline(artwork, cloudSource)
        val viewportWidth = (viewport.right - viewport.left).value
        val outlineWidthRatio = (cloudOutline.right - cloudOutline.left).value / viewportWidth
        val recordTop = rule.onNodeWithTag("home_record").getUnclippedBoundsInRoot().top
        val outlineToRecordGap = recordTop - cloudOutline.bottom
        val referenceDevice = Build.MODEL == "SM-G973U" &&
            abs(viewportWidth - 411.4286f) < 2f && abs(context.resources.configuration.fontScale - 1.1f) < .02f
        val s22BaselineViewport = Build.MODEL == "SM-S9010" && abs(viewportWidth - 360f) < 2f &&
            abs(context.resources.configuration.fontScale - 1f) < .02f
        val cloudBounds = rule.onNodeWithTag("home_cloud").getUnclippedBoundsInRoot()
        val sceneBounds = PetScene.entries.associateWith {
            rule.onNodeWithTag("scene_${it.name}").getUnclippedBoundsInRoot()
        }
        val initialPet = rule.onNodeWithTag("home_center_pet").getUnclippedBoundsInRoot()
        HomeAnchorAudit.assertOrbit(cloudBounds, initialPet, sceneBounds)
        val anchorMetrics = HomeAnchorAudit.report(cloudBounds, initialPet, sceneBounds)
        File(folder, "anchor-metrics.txt").writeText("device=${Build.MODEL}\n$anchorMetrics")
        val visualMetrics = StringBuilder().apply {
            appendLine("device=${Build.MODEL}, fontScale=${context.resources.configuration.fontScale}, referenceDevice=$referenceDevice")
            appendLine("Cloud metrics use source alpha >= 240, excluding faint transparent glow.")
            appendLine("cloudAlphaSource=$cloudSource")
            appendLine("cloudArtwork=$artwork")
            appendLine("cloudOpaqueOutline=$cloudOutline")
            appendLine("cloudOpaqueWidthRatio=$outlineWidthRatio")
            appendLine("cloudOpaqueToRecordGap=$outlineToRecordGap")
            listOf("home_brand", "home_pet_switch", "pet_Dog", "pet_Cat", "home_scene_title", "home_center_pet").forEach { tag ->
                appendLine("$tag=${rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()}")
            }
            PetScene.entries.forEach { scene ->
                appendLine("scene_${scene.name}=${rule.onNodeWithTag("scene_${scene.name}").getUnclippedBoundsInRoot()}")
            }
            append(anchorMetrics)
        }
        org.junit.Assert.assertTrue("Cloud visual outline must reach 95–98% screen width: $outlineWidthRatio",
            outlineWidthRatio >= .95f - .003f && outlineWidthRatio <= .98f + .003f)
        if (referenceDevice) {
            org.junit.Assert.assertTrue("Reduce the cloud-to-record visual gap on the reference phone: $outlineToRecordGap",
                outlineToRecordGap >= 0.dp && outlineToRecordGap <= 25.5.dp)
        }
        rule.onNodeWithText("猫言狗语翻译官").assertIsDisplayed()
        rule.onAllNodesWithText("选择它和场景，听听毛孩子的小心思～").assertCountEquals(0)
        rule.onNodeWithText("不确定").performScrollTo()
        org.junit.Assert.assertEquals(actions, rule.onNodeWithTag("home_actions").getUnclippedBoundsInRoot())
        val metrics = (listOf("home_hero", "home_section_1", "home_section_2", "home_cloud", "home_center_pet", "home_record",
            "home_brand", "home_pet_switch", "pet_Dog", "pet_Cat", "home_scene_title") + PetScene.entries.map { "scene_${it.name}" }).joinToString("\n") { tag ->
            val bounds = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            "$tag: top=${bounds.top.value}dp height=${bounds.bottom.value - bounds.top.value}dp width=${bounds.right.value - bounds.left.value}dp\n$tag.bounds=$bounds"
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
        fun selectPet(pet: PetType) {
            rule.onNodeWithTag("pet_${pet.name}").performClick()
            rule.waitUntil(5_000) {
                rule.onAllNodesWithTag("home_center_pet_${pet.name}").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag("pet_${pet.name}").assertIsSelected()
            rule.onNodeWithTag("home_center_pet_${pet.name}").assertIsDisplayed()
        }
        val visibleAreas = mutableMapOf<PetType, Float>()
        var centralDecoration: DpRect? = null
        fun measurePet(pet: PetType) {
            val decoration = rule.onNodeWithTag("home_center_pet").getUnclippedBoundsInRoot()
            centralDecoration?.let { org.junit.Assert.assertEquals("Pet switching keeps the decorative region stable", it, decoration) }
            centralDecoration = decoration
            val image = rule.onNodeWithTag("home_center_pet_${pet.name}").getUnclippedBoundsInRoot()
            val resource = if (pet == PetType.Cat) R.drawable.pet_cat_main else R.drawable.pet_dog_main
            val source = HomeArtworkAudit.alphaBounds(context, resource)
            val visible = HomeArtworkAudit.fitOpaqueBounds(image, source)
            val visibleWidth = (visible.right - visible.left).value
            val visibleHeight = (visible.bottom - visible.top).value
            val priorSize = when {
                referenceDevice -> if (pet == PetType.Cat) 131.7f to 107.5f else 141.8f to 104.7f
                s22BaselineViewport -> if (pet == PetType.Cat) 102.5f to 83.6f else 110.2f to 81.4f
                else -> null
            }
            visibleAreas[pet] = visibleWidth * visibleHeight
            visualMetrics.appendLine("${pet.name}: alphaSource=$source, imageLayout=$image, visible=$visible")
            visualMetrics.appendLine("${pet.name}: visibleWidth=${visibleWidth}dp, visibleHeight=${visibleHeight}dp, " +
                "visibleArea=${visibleWidth * visibleHeight}dp2")
            priorSize?.let { (oldWidth, oldHeight) ->
                visualMetrics.appendLine("${pet.name}: priorA145Size=${oldWidth}x${oldHeight}dp, " +
                    "widthDelta=${visibleWidth - oldWidth}dp,heightDelta=${visibleHeight - oldHeight}dp")
                org.junit.Assert.assertTrue("Visible $pet must not shrink from A1.4.5 in the same viewport",
                    visibleWidth >= oldWidth - 1f && visibleHeight >= oldHeight - 1f)
            }
            File(folder, "visual-metrics.txt").writeText(visualMetrics.toString())
            org.junit.Assert.assertTrue("Pet artwork stays inside its noninteractive region",
                visible.left >= decoration.left - .5.dp && visible.right <= decoration.right + .5.dp &&
                    visible.top >= decoration.top - .5.dp && visible.bottom <= decoration.bottom + .5.dp)
            PetScene.entries.forEach { scene ->
                val area = rule.onNodeWithTag("scene_${scene.name}").getUnclippedBoundsInRoot()
                org.junit.Assert.assertFalse("Visible $pet pixels must keep 4dp clear of ${scene.name}'s circular outline",
                    HomeArtworkAudit.visiblePixelsOverlapButtonOutline(context, resource, image, area))
            }
            HomeAnchorAudit.assertOrbit(cloudBounds, decoration, sceneBounds)
        }
        selectPet(PetType.Cat)
        measurePet(PetType.Cat)
        capture("home-top.png")
        capture("home-cat.png")
        selectPet(PetType.Dog)
        measurePet(PetType.Dog)
        val areaRatio = visibleAreas.values.max() / visibleAreas.values.min()
        visualMetrics.appendLine("catDogOpaqueAreaRatio=$areaRatio")
        File(folder, "visual-metrics.txt").writeText(visualMetrics.toString())
        if (referenceDevice) org.junit.Assert.assertTrue("Cat and dog should have comparable visual mass: $areaRatio", areaRatio <= 1.15f)
        PetScene.entries.forEach { rule.onNodeWithTag("scene_${it.name}").assertIsDisplayed() }
        capture("home-dog.png")
        selectPet(PetType.Cat)
        rule.onNodeWithText("不确定").performScrollTo().assertIsDisplayed()
        capture("home-scenes.png")
        rule.onNodeWithText("趣味声音模拟，不代表宠物真实语言翻译").assertIsDisplayed()
        rule.onNodeWithText("开始录音翻译").assertIsDisplayed()
        rule.onNodeWithText("首页", useUnmergedTree = true).assertIsDisplayed()
        capture("home-record.png")
    }
}
