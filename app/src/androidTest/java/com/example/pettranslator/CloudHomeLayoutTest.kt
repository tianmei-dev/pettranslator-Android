package com.example.pettranslator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pettranslator.domain.PetScene
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.ui.home.HomeRecordActionBar
import com.example.pettranslator.ui.home.HomeScreen
import com.example.pettranslator.ui.home.HomeUiState
import com.example.pettranslator.ui.theme.PetTranslatorTheme
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.PI
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Homepage-only fixtures; the 58dp test navigation area does not change app navigation. */
class CloudHomeLayoutTest {
    @get:Rule val rule = createComposeRule()
    @Before fun wake() = wakeTestDevice()

    private val sceneMatcher = PetScene.entries.map { hasTestTag("scene_${it.name}") }
        .reduce { previous, matcher -> previous or matcher }
    private val folder get() = File(
        InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
        "cloud-home-evidence",
    ).apply { mkdirs() }

    private class Fixture {
        var pet by mutableStateOf(PetType.Cat)
        var scene by mutableStateOf(PetScene.Unknown)
        var ready by mutableStateOf(true)
        val petCalls = mutableListOf<PetType>()
        val sceneCalls = mutableListOf<PetScene>()
        var recordings = 0
    }

    private fun show(width: Int, height: Int, fontScale: Float = 1f, fixture: Fixture = Fixture(),
        referenceSystemInsets: Boolean = false): Fixture {
        rule.setContent {
            val physical = LocalDensity.current
            BoxWithConstraints {
                val density = minOf(physical.density,
                    constraints.maxWidth.toFloat() / width, constraints.maxHeight.toFloat() / height)
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                    PetTranslatorTheme {
                        Scaffold(Modifier.size(width.dp, height.dp),
                            contentWindowInsets = if (referenceSystemInsets)
                                WindowInsets(0.dp, 42.6667.dp, 0.dp, 0.dp) else ScaffoldDefaults.contentWindowInsets,
                            bottomBar = {
                            Column {
                                HomeRecordActionBar(fixture.ready) { fixture.recordings++ }
                                Surface { Box(Modifier.fillMaxWidth().height(58.dp)) {
                                    Text("测试导航区域（58dp）")
                                } }
                                if (referenceSystemInsets) {
                                    Surface { Box(Modifier.fillMaxWidth().height(47.2381.dp)) {
                                        Text("参考系统导航占位")
                                    } }
                                }
                            }
                        }) { padding ->
                            Box(Modifier.padding(padding)) {
                                HomeScreen(HomeUiState(fixture.pet, fixture.scene, fixture.ready),
                                    onPet = { fixture.petCalls += it; fixture.pet = it },
                                    onScene = { fixture.sceneCalls += it; fixture.scene = it })
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        return fixture
    }

    private fun bounds(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun sceneBounds() = PetScene.entries.associateWith { bounds("scene_${it.name}") }
    private fun centerX(area: DpRect) = (area.left + area.right) / 2
    private fun centerY(area: DpRect) = (area.top + area.bottom) / 2
    private fun width(area: DpRect) = area.right - area.left

    private fun overlaps(a: DpRect, b: DpRect) =
        a.left < b.right - .5.dp && b.left < a.right - .5.dp &&
            a.top < b.bottom - .5.dp && b.top < a.bottom - .5.dp

    private fun verifyDecorativeOnly(tag: String) {
        rule.onNodeWithTag(tag).assert(SemanticsMatcher("Decorative image has no click action") {
            !it.config.contains(SemanticsActions.OnClick)
        })
        rule.onAllNodes(hasAnyAncestor(hasTestTag(tag)) and hasClickAction(),
            useUnmergedTree = true).assertCountEquals(0)
    }

    private fun verifyIndependentSceneControls() {
        rule.onNodeWithTag("scene_orbit").assertExists()
        rule.onAllNodesWithTag("scene_grid_3").assertCountEquals(0)
        rule.onAllNodes(sceneMatcher).assertCountEquals(6)
        assertEquals("Scene declaration/accessibility order must stay unchanged",
            PetScene.entries.map { "scene_${it.name}" },
            rule.onAllNodes(sceneMatcher).fetchSemanticsNodes().map {
                it.config[SemanticsProperties.TestTag]
            })
        PetScene.entries.forEach { scene ->
            rule.onNodeWithTag("scene_${scene.name}")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .assertHasClickAction()
        }
        // Decorative clouds and pets must not add or intercept a seventh action.
        rule.onAllNodes(hasAnyAncestor(hasTestTag("home_section_2")) and hasClickAction(),
            useUnmergedTree = true).assertCountEquals(6)
    }

    private fun verifyVipUnchanged() {
        rule.onNodeWithContentDescription("VIP 入口，暂不可用").assertIsNotEnabled()
            .assert(SemanticsMatcher("VIP has no click action") {
                !it.config.contains(SemanticsActions.OnClick)
            })
    }

    private fun verifyHeaderAndPetSwitch() {
        rule.onNodeWithText("选择宠物").assertDoesNotExist()
        rule.onNodeWithTag("home_pet_title").assertDoesNotExist()
        val brand = rule.onNodeWithTag("home_brand").assertTextEquals("猫狗心声翻译官")
        val brandBounds = brand.getUnclippedBoundsInRoot()
        val vipBounds = rule.onNodeWithContentDescription("VIP 入口，暂不可用").getUnclippedBoundsInRoot()
        assertFalse("App name and unchanged VIP must not overlap", overlaps(brandBounds, vipBounds))
        val hero = bounds("home_hero")
        assertTrue("App name stays inside the existing hero", brandBounds.left >= hero.left &&
            brandBounds.right <= hero.right && brandBounds.top >= hero.top && brandBounds.bottom <= hero.bottom)
        brand.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            val results = mutableListOf<TextLayoutResult>()
            action(results)
            assertTrue("App name exposes its complete text layout", results.isNotEmpty())
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
            File(folder, "brand-layout.txt").writeText("brand=$brandBounds\nvip=$vipBounds\nhero=$hero\n$diagnostics\n")
            assertFalse("App name must not overflow or ellipsize: $diagnostics", results.any { result ->
                result.hasVisualOverflow || (0 until result.lineCount).any { result.isLineEllipsized(it) }
            })
        }
        val toggle = bounds("home_pet_switch")
        val viewport = bounds("home_viewport")
        assertTrue("The shared pet switch stays centered",
            abs((centerX(toggle) - centerX(viewport)).value) <= 1f)
        assertTrue("The shared pet switch stays inside the available width",
            toggle.left >= viewport.left && toggle.right <= viewport.right)
        rule.onAllNodes(hasAnyAncestor(hasTestTag("home_pet_switch")) and hasClickAction(),
            useUnmergedTree = true).assertCountEquals(2)
        PetType.entries.forEach { type ->
            val option = rule.onNodeWithTag("pet_${type.name}")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .assertHasClickAction()
            val area = option.getUnclippedBoundsInRoot()
            assertTrue("$type keeps its complete touch target inside the shared switch", area.left >= toggle.left &&
                area.right <= toggle.right && area.top >= toggle.top && area.bottom <= toggle.bottom)
            // Scaled viewport fixtures can round a logical 48dp down by a fraction of a dp.
            assertTrue("$type keeps its 48dp touch target within pixel rounding: area=$area",
                area.right - area.left >= 47.5.dp && area.bottom - area.top >= 47.5.dp)
            rule.onNodeWithText(type.title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val results = mutableListOf<TextLayoutResult>()
                action(results)
                assertTrue("$type exposes its text layout", results.isNotEmpty())
                assertFalse("$type label must not overflow", results.any { it.hasVisualOverflow })
            }
        }
        assertFalse("Pet options remain independent touch targets", overlaps(bounds("pet_Dog"), bounds("pet_Cat")))
        rule.onAllNodes(hasAnyAncestor(hasTestTag("home_pet_switch")) and isSelected()).assertCountEquals(1)
        val title = bounds("home_scene_title")
        assertTrue("The shared pet switch must not cover the scene title", toggle.bottom <= title.top)
    }

    private fun verifyCircleHitAreas(areas: Map<PetScene, DpRect>) {
        areas.forEach { (scene, area) ->
            val width = area.right - area.left
            val height = area.bottom - area.top
            assertTrue("$scene must keep its square hit area around the cloud outline: $area",
                abs(width.value - height.value) <= 1f)
            assertTrue("$scene must keep a usable touch target", width >= 48.dp)
        }
        val entries = areas.entries.toList()
        for (first in entries.indices) for (second in first + 1 until entries.size) {
            val a = entries[first].value
            val b = entries[second].value
            assertFalse("Scene hit areas overlap: ${entries[first].key} $a / ${entries[second].key} $b",
                overlaps(a, b))
        }
    }

    private fun verifyPetPixelsAvoidControls(type: PetType, areas: Map<PetScene, DpRect>) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resource = if (type == PetType.Cat) R.drawable.pet_cat_main else R.drawable.pet_dog_main
        val image = bounds("home_center_pet_${type.name}")
        val cloud = bounds("home_cloud")
        val clearance = if ((cloud.bottom - cloud.top) <= (cloud.right - cloud.left) * 1.4f) 0f else 4f
        areas.forEach { (scene, area) ->
            assertFalse("Visible $type pixels must avoid $scene's circular outline ($clearance dp clearance): $image / $area",
                HomeArtworkAudit.visiblePixelsOverlapButtonOutline(context, resource, image, area, clearance))
        }
    }

    private fun verifyReferenceComposition(areas: Map<PetScene, DpRect>) {
        verifyHeaderAndPetSwitch()
        val viewport = bounds("home_viewport")
        val cloud = bounds("home_cloud")
        val pet = bounds("home_center_pet")
        verifyDecorativeOnly("home_center_pet")
        verifyDecorativeOnly("home_cloud_artwork")
        rule.onNodeWithTag("home_center_pet_Cat").assertIsDisplayed()
        val artwork = bounds("home_cloud_artwork")
        val alpha = HomeArtworkAudit.alphaBounds(
            InstrumentationRegistry.getInstrumentation().targetContext, R.drawable.pastel_cloud_pet_app_frame)
        val outline = HomeArtworkAudit.cloudOutline(artwork, alpha)
        val outlineRatio = width(outline).value / width(viewport).value
        assertTrue("Cloud opaque outline must occupy 95–98% of the available width: $outlineRatio",
            outlineRatio >= .95f - .003f && outlineRatio <= .98f + .003f)
        assertTrue("The complete decorative source stays inside the visible width",
            artwork.left >= viewport.left - .5.dp && artwork.right <= viewport.right + .5.dp)
        areas.forEach { (scene, area) ->
            assertTrue("$scene must stay inside the complete cloud: $area in $cloud",
                area.left >= cloud.left - .5.dp && area.right <= cloud.right + .5.dp &&
                    area.top >= cloud.top - .5.dp && area.bottom <= cloud.bottom + .5.dp)
        }
        // Transparent corners may surround the circular controls; rendered pet pixels may not cover them.
        verifyPetPixelsAvoidControls(PetType.Cat, areas)
        assertTrue("Central pet stays inside the cloud",
            pet.left >= cloud.left && pet.right <= cloud.right && pet.top >= cloud.top && pet.bottom <= cloud.bottom)
        HomeAnchorAudit.assertOrbit(cloud, pet, areas)
        val door = areas.getValue(PetScene.Door)
        val meal = areas.getValue(PetScene.Meal)
        val play = areas.getValue(PetScene.Play)
        val alone = areas.getValue(PetScene.Alone)
        val held = areas.getValue(PetScene.Held)
        val unknown = areas.getValue(PetScene.Unknown)
        assertTrue("Upper center island is visibly smaller than the side islands",
            width(meal) < width(door) - 2.dp && width(meal) < width(play) - 2.dp)
        assertTrue("Upper islands have a raised middle, rather than a level grid row",
            centerY(meal) < centerY(door) - 4.dp && centerY(meal) < centerY(play) - 4.dp)
        assertTrue("Lower center island sits below the side islands",
            centerY(held) > centerY(alone) + 2.dp && centerY(held) > centerY(unknown) + 2.dp)
        assertTrue("Upper/lower declaration order remains left to right",
            centerX(door) < centerX(meal) && centerX(meal) < centerX(play) &&
                centerX(alone) < centerX(held) && centerX(held) < centerX(unknown))
        PetType.entries.forEach { type ->
            val pill = bounds("pet_${type.name}")
            assertTrue("Pet selection must stay a compact pill: $pill",
                width(pill) <= width(viewport) * .4f && pill.bottom - pill.top < 60.dp)
        }
        listOf("home_pet_switch", "home_scene_title").forEach { tag ->
            assertTrue("$tag must be centered",
                abs((centerX(bounds(tag)) - centerX(viewport)).value) <= 1f)
        }
        val title = bounds("home_scene_title")
        assertTrue("Environment title belongs inside the upper cloud",
            title.top >= outline.top - 1.dp && title.bottom <= cloud.bottom)
        listOf("1", "2", "选择你想翻译的毛孩子", "不同场景下，TA的含义可能不同哦").forEach {
            rule.onAllNodesWithText(it).assertCountEquals(0)
        }
    }

    private fun normalViewport(width: Int, height: Int, fontScale: Float = 1f) {
        show(width, height, fontScale)
        verifyIndependentSceneControls()
        verifyVipUnchanged()
        val viewport = bounds("home_viewport")
        val actions = bounds("home_actions")
        val areas = sceneBounds()
        verifyCircleHitAreas(areas)
        verifyReferenceComposition(areas)
        areas.forEach { (scene, area) ->
            rule.onNodeWithTag("scene_${scene.name}").assertIsDisplayed()
            assertTrue("$scene must fit before any scrolling: $area in $viewport",
                area.left >= viewport.left - .5.dp && area.right <= viewport.right + .5.dp &&
                    area.top >= viewport.top - .5.dp && area.bottom <= viewport.bottom + .5.dp)
        }
        assertTrue("Record actions stay outside the scrolling viewport", actions.top >= viewport.bottom)
        rule.onNodeWithTag("home_record").assertIsDisplayed()
        rule.onNodeWithText("趣味声音模拟，不代表宠物真实语言翻译").assertIsDisplayed()
        capture("viewport-${width}x$height", areas)
        rule.onNodeWithTag("pet_Dog").performClick().assertIsSelected()
        assertEquals("Pet switching keeps every scene position and size", areas, sceneBounds())
        verifyPetPixelsAvoidControls(PetType.Dog, areas)
        capture("viewport-${width}x$height-dog", areas)
    }

    @Test fun shortViewportFitsEveryScene() = normalViewport(360, 720)
    @Test fun standardViewportFitsEveryScene() = normalViewport(393, 800)
    @Test fun tallViewportFitsEveryScene() = normalViewport(412, 900)
    @Test fun narrowOrdinaryFontKeepsArtworkClear() = normalViewport(320, 900, 1.15f)
    @Test fun wideOrdinaryViewportKeepsArtworkClear() = normalViewport(440, 900)

    @Test fun controlledReferenceViewportKeepsBothPetsFullSized() {
        show(411, 869, fontScale = 1.1f, referenceSystemInsets = true)
        verifyIndependentSceneControls()
        verifyVipUnchanged()
        val viewport = bounds("home_viewport")
        val initialAreas = sceneBounds()
        val decoration = bounds("home_center_pet")
        verifyCircleHitAreas(initialAreas)
        verifyReferenceComposition(initialAreas)
        initialAreas.forEach { (scene, area) ->
            rule.onNodeWithTag("scene_${scene.name}").assertIsDisplayed()
            assertTrue("Reference layout must fit before scrolling: $scene $area in $viewport",
                area.left >= viewport.left - .5.dp && area.right <= viewport.right + .5.dp &&
                    area.top >= viewport.top - .5.dp && area.bottom <= viewport.bottom + .5.dp)
        }
        assertTrue("Reference cloud must fit before scrolling", bounds("home_cloud").bottom <= viewport.bottom + .5.dp)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outline = HomeArtworkAudit.cloudOutline(bounds("home_cloud_artwork"),
            HomeArtworkAudit.alphaBounds(context, R.drawable.pastel_cloud_pet_app_frame))
        val outlineRatio = width(outline).value / width(viewport).value
        val gap = bounds("home_record").top - outline.bottom
        val report = StringBuilder().apply {
            appendLine("CONTROLLED reference viewport, rendered on ${android.os.Build.MODEL}; not a physical SM-G973U measurement.")
            appendLine("411x869dp, font1.1, reference status inset42.6667dp, system navigation inset47.2381dp, app navigation placeholder58dp.")
            appendLine("Prior A1.4.5 controlled reference alpha-derived pet sizes provide the no-shrink baseline.")
            appendLine("viewport=$viewport, cloud=${bounds("home_cloud")}, opaqueOutline=$outline")
            appendLine("opaqueCloudWidthRatio=$outlineRatio, opaqueCloudToRecordGap=$gap")
        }
        File(folder, "controlled-reference-visual-metrics.txt").writeText(report.toString())
        assertTrue("Reference cloud outline width must reach95–98%: $outlineRatio",
            outlineRatio >= .95f - .003f && outlineRatio <= .98f + .003f)
        assertTrue("Reference cloud-to-record gap should be compact: $gap", gap >= 0.dp && gap <= 25.5.dp)
        val areas = mutableMapOf<PetType, Float>()
        listOf(PetType.Cat, PetType.Dog).forEach { type ->
            rule.onNodeWithTag("pet_${type.name}").performClick().assertIsSelected()
            rule.onNodeWithTag("home_center_pet_${type.name}").assertIsDisplayed()
            assertEquals("Pet selection keeps the decorative region stable", decoration, bounds("home_center_pet"))
            assertEquals("Pet selection must not move environment controls", initialAreas, sceneBounds())
            verifyDecorativeOnly("home_center_pet")
            val resource = if (type == PetType.Cat) R.drawable.pet_cat_main else R.drawable.pet_dog_main
            val source = HomeArtworkAudit.alphaBounds(context, resource)
            val visible = HomeArtworkAudit.fitOpaqueBounds(bounds("home_center_pet_${type.name}"), source)
            val visibleWidth = width(visible).value
            val visibleHeight = (visible.bottom - visible.top).value
            val oldWidth = if (type == PetType.Cat) 131.7f else 141.8f
            val oldHeight = if (type == PetType.Cat) 107.5f else 104.7f
            val widthGrowth = visibleWidth / oldWidth - 1f
            val heightGrowth = visibleHeight / oldHeight - 1f
            areas[type] = visibleWidth * visibleHeight
            report.appendLine("$type: alphaSource=$source, visible=$visible")
            report.appendLine("$type: visibleWidth=${visibleWidth}dp, visibleHeight=${visibleHeight}dp, " +
                "visibleArea=${areas[type]}dp2, priorA145WidthGrowth=$widthGrowth, priorA145HeightGrowth=$heightGrowth")
            File(folder, "controlled-reference-visual-metrics.txt").writeText(report.toString())
            assertTrue("Visible $type must not shrink from A1.4.5: ${visibleWidth}x${visibleHeight}dp",
                visibleWidth >= oldWidth - 1f && visibleHeight >= oldHeight - 1f)
            assertTrue("Visible pet stays inside its decorative region",
                visible.left >= decoration.left - .5.dp && visible.right <= decoration.right + .5.dp &&
                    visible.top >= decoration.top - .5.dp && visible.bottom <= decoration.bottom + .5.dp)
            verifyPetPixelsAvoidControls(type, initialAreas)
            HomeAnchorAudit.assertOrbit(bounds("home_cloud"), decoration, initialAreas)
            capture("controlled-reference-${type.name.lowercase()}", sceneBounds())
        }
        val areaRatio = areas.values.max() / areas.values.min()
        report.appendLine("catDogOpaqueAreaRatio=$areaRatio")
        File(folder, "controlled-reference-visual-metrics.txt").writeText(report.toString())
        assertTrue("Reference cat/dog visual mass should remain comparable: $areaRatio", areaRatio <= 1.15f)
        rule.onNodeWithTag("pet_Cat").performClick().assertIsSelected()
    }

    @Test fun callbacksSelectionAndGeometryRemainStable() {
        val fixture = show(393, 800)
        verifyHeaderAndPetSwitch()
        val initialAreas = sceneBounds()
        val initialPet = bounds("home_center_pet")
        val catImage = rule.onNodeWithTag("home_center_pet").captureToImage().asAndroidBitmap()
        rule.onNodeWithText("猫咪").assertIsSelected()
        rule.onNodeWithTag("scene_Unknown").assertIsSelected()
        verifyPetPixelsAvoidControls(PetType.Cat, initialAreas)
        capture("central-cat", initialAreas)
        listOf(PetType.Dog, PetType.Cat).forEach { pet ->
            rule.onNodeWithText(pet.title).performClick().assertIsSelected()
            rule.onNodeWithTag("home_center_pet_${pet.name}").assertIsDisplayed()
            assertEquals("Switching pets must not move the central decoration", initialPet, bounds("home_center_pet"))
            assertEquals("Switching pets must not move environment controls", initialAreas, sceneBounds())
            verifyDecorativeOnly("home_center_pet")
            verifyPetPixelsAvoidControls(pet, initialAreas)
            if (pet == PetType.Dog) {
                val dogImage = rule.onNodeWithTag("home_center_pet").captureToImage().asAndroidBitmap()
                assertEquals(catImage.width, dogImage.width)
                assertEquals(catImage.height, dogImage.height)
                var changed = 0
                var sampled = 0
                for (y in 0 until catImage.height step 3) for (x in 0 until catImage.width step 3) {
                    sampled++
                    if (catImage.getPixel(x, y) != dogImage.getPixel(x, y)) changed++
                }
                assertTrue("The central decoration must visibly switch from the cat to the dog", changed > sampled / 10)
                capture("central-dog", sceneBounds())
            }
        }
        PetScene.entries.forEach { scene ->
            rule.onNodeWithTag("scene_${scene.name}").performClick().assertIsSelected()
            rule.onAllNodes(sceneMatcher and isSelected()).assertCountEquals(1)
            assertEquals("Selection must not move or resize buttons", initialAreas, sceneBounds())
        }
        val orbitTouches = listOf(
            PetScene.Door to Offset(1f, 1f), PetScene.Meal to Offset(0f, 1f),
            PetScene.Play to Offset(-1f, 1f), PetScene.Alone to Offset(1f, -1f),
            PetScene.Held to Offset(0f, -1f), PetScene.Unknown to Offset(-1f, -1f),
        )
        orbitTouches.forEach { (scene, direction) ->
            // Real pointer events near the pet-facing side prove transparent decoration does not intercept taps.
            rule.onNodeWithTag("scene_${scene.name}").performTouchInput {
                click(Offset(center.x * (1f + .20f * direction.x),
                    center.y * (1f + .88f * direction.y)))
            }.assertIsSelected()
            assertEquals("Pointer selection must not move controls", initialAreas, sceneBounds())
        }
        rule.onNodeWithTag("home_record").performClick()
        rule.runOnIdle {
            assertEquals(listOf(PetType.Dog, PetType.Cat), fixture.petCalls)
            assertEquals(PetScene.entries + orbitTouches.map { it.first }, fixture.sceneCalls)
            assertEquals(PetType.Cat, fixture.pet)
            assertEquals(PetScene.Unknown, fixture.scene)
            assertEquals(1, fixture.recordings)
        }
        capture("callbacks-and-selection", sceneBounds())
    }

    @Test fun readinessKeepsRecordAndPetGating() {
        val fixture = Fixture().apply { ready = false }
        show(393, 800, fixture = fixture)
        rule.onNodeWithTag("home_record").assertIsNotEnabled().performTouchInput { click() }
        rule.onNodeWithText("狗狗").assertIsNotEnabled().performTouchInput { click() }
        rule.runOnIdle {
            assertEquals(0, fixture.recordings)
            assertTrue(fixture.petCalls.isEmpty())
            fixture.ready = true
        }
        rule.onNodeWithTag("home_record").assertIsEnabled().performClick()
        rule.onNodeWithText("狗狗").assertIsEnabled().performClick().assertIsSelected()
        rule.runOnIdle {
            assertEquals(1, fixture.recordings)
            assertEquals(listOf(PetType.Dog), fixture.petCalls)
        }
        verifyVipUnchanged()
        capture("readiness-gating", sceneBounds())
    }

    @Test fun narrowLargeFontKeepsTextAndFixedActionsReachable() {
        show(320, 720, fontScale = 2f)
        verifyHeaderAndPetSwitch()
        val originalActions = bounds("home_actions")
        verifyCircleHitAreas(sceneBounds())
        HomeAnchorAudit.assertOrbit(bounds("home_cloud"), bounds("home_center_pet"), sceneBounds())
        verifyPetPixelsAvoidControls(PetType.Cat, sceneBounds())
        PetScene.entries.forEach { scene ->
            rule.onNodeWithTag("scene_${scene.name}").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText(scene.title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val results = mutableListOf<TextLayoutResult>()
                action(results)
                assertFalse("Scene label must not overflow at large font: ${scene.title}",
                    results.any { it.hasVisualOverflow })
            }
            assertEquals("Home scroll must not move the recording actions", originalActions, bounds("home_actions"))
            rule.onNodeWithTag("home_record").assertIsDisplayed()
            rule.onNodeWithText("趣味声音模拟，不代表宠物真实语言翻译").assertIsDisplayed()
            // Retain each scrolled position for visual checks of cloud clipping, beyond text layout overflow.
            capture("narrow-320-font2-${scene.name.lowercase()}", sceneBounds())
        }
        capture("narrow-320-font2", sceneBounds())
    }

    private fun capture(name: String, areas: Map<PetScene, DpRect>) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(folder, "$name.txt").writeText(buildString {
            appendLine("Controlled Compose viewport; 58dp navigation placeholder, not a separate physical device.")
            appendLine("viewport=${bounds("home_viewport")}")
            appendLine("actions=${bounds("home_actions")}")
            appendLine("cloud=${bounds("home_cloud")}")
            appendLine("centralPet=${bounds("home_center_pet")}")
            append(HomeAnchorAudit.report(bounds("home_cloud"), bounds("home_center_pet"), areas))
            appendLine("brand=${bounds("home_brand")}")
            appendLine("petSwitch=${bounds("home_pet_switch")}")
            PetType.entries.forEach { type -> appendLine("pet${type.name}=${bounds("pet_${type.name}")}") }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val cloudSource = HomeArtworkAudit.alphaBounds(context, R.drawable.pastel_cloud_pet_app_frame)
            val artwork = bounds("home_cloud_artwork")
            val bodyAndTail = (artwork.bottom - artwork.top).value / width(artwork).value < 1.4f
            val outline = HomeArtworkAudit.cloudOutline(artwork, cloudSource, bodyAndTail)
            appendLine("cloudRendering=${if (bodyAndTail) "body-and-tail" else "expanded-fit"}")
            appendLine("cloudAlpha240Source=$cloudSource")
            appendLine("cloudOpaqueOutline=$outline")
            appendLine("cloudOpaqueWidthRatio=${width(outline).value / width(bounds("home_viewport")).value}")
            appendLine("cloudOpaqueToRecordGap=${bounds("home_record").top - outline.bottom}")
            PetType.entries.forEach { type ->
                val tag = "home_center_pet_${type.name}"
                if (rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) {
                    val resource = if (type == PetType.Cat) R.drawable.pet_cat_main else R.drawable.pet_dog_main
                    val source = HomeArtworkAudit.alphaBounds(context, resource)
                    appendLine("central${type.name}Image=${bounds(tag)}")
                    appendLine("central${type.name}Source=$source")
                    appendLine("central${type.name}Visible=${HomeArtworkAudit.fitOpaqueBounds(bounds(tag), source)}")
                }
            }
            areas.forEach { (scene, area) -> appendLine("${scene.name}: $area") }
        })
    }
}

/** Measurements of rendered centers, independent of the production coordinate calculation. */
internal object HomeAnchorAudit {
    fun normalizedCenter(cloud: DpRect, element: DpRect): Pair<Float, Float> =
        ((element.left.value + element.right.value) / 2f - cloud.left.value) /
            (cloud.right - cloud.left).value to
            ((element.top.value + element.bottom.value) / 2f - cloud.top.value) /
                (cloud.bottom - cloud.top).value

    fun assertOrbit(cloud: DpRect, pet: DpRect, scenes: Map<PetScene, DpRect>) {
        val petCenter = normalizedCenter(cloud, pet)
        val centers = scenes.mapValues { normalizedCenter(cloud, it.value) }
        scenes.forEach { (scene, area) ->
            assertTrue("$scene retains a usable full click area", area.right - area.left >= 48.dp && area.bottom - area.top >= 48.dp)
            assertTrue("$scene stays inside the cloud", area.left >= cloud.left - .5.dp &&
                area.right <= cloud.right + .5.dp && area.top >= cloud.top - .5.dp && area.bottom <= cloud.bottom + .5.dp)
        }
        val entries = scenes.entries.toList()
        for (first in entries.indices) for (second in first + 1 until entries.size) {
            val a = entries[first].value
            val b = entries[second].value
            val overlap = a.left < b.right - .5.dp && b.left < a.right - .5.dp &&
                a.top < b.bottom - .5.dp && b.top < a.bottom - .5.dp
            assertFalse("Full click areas must not overlap: ${entries[first].key}/${entries[second].key}", overlap)
        }
        assertEquals("The pet is horizontally centered in the cloud", .50f, petCenter.first, .006f)
        assertEquals("The pet uses the 54% vertical anchor", .54f, petCenter.second, .006f)
        listOf(PetScene.Meal, PetScene.Held).forEach { scene ->
            assertEquals("$scene stays on the pet's vertical axis", .50f, centers.getValue(scene).first, .006f)
        }
        listOf(PetScene.Door to PetScene.Play, PetScene.Alone to PetScene.Unknown).forEach { (left, right) ->
            val a = centers.getValue(left)
            val b = centers.getValue(right)
            assertEquals("$left and $right mirror around the pet", 1f, a.first + b.first, .008f)
            assertTrue("$left and $right keep paired diagonal heights, allowing contour safety",
                abs(a.second - b.second) <= .02f)
            val wideX = if (left == PetScene.Door) .24f else .26f
            assertEquals("$left uses the restored horizontal spread", wideX, a.first, .006f)
            assertEquals("$right uses the restored horizontal spread", 1f - wideX, b.first, .006f)
        }
        val door = centers.getValue(PetScene.Door)
        val meal = centers.getValue(PetScene.Meal)
        val alone = centers.getValue(PetScene.Alone)
        val held = centers.getValue(PetScene.Held)
        val ordinaryMode = (cloud.bottom - cloud.top) <= (cloud.right - cloud.left) * 1.4f
        val upperRange = if (ordinaryMode) .38f.. .416f else .33f.. .38f
        val lowerRange = if (ordinaryMode) .680f.. .740f else .72f.. .79f
        assertTrue("Upper diagonal anchors follow the mode's orbit", door.second in upperRange)
        assertTrue("Lower diagonal anchors follow the mode's orbit", alone.second in lowerRange)
        assertTrue("The meal anchor remains above both upper diagonals", meal.second < door.second - .01f)
        assertTrue("Upper diagonals stay above the central pet", door.second < petCenter.second - .10f)
        assertTrue("Lower diagonals stay below the central pet", alone.second > petCenter.second + .10f)
        assertTrue("The held anchor closes the orbit below the lower diagonals", held.second > alone.second + .025f)
        assertEquals("The held anchor is at 82%", .82f, held.second, .006f)

        // Clockwise traversal must visit six distinct sectors around the actual rendered pet center.
        val clockwise = listOf(PetScene.Meal, PetScene.Play, PetScene.Unknown,
            PetScene.Held, PetScene.Alone, PetScene.Door)
        fun angle(scene: PetScene): Float {
            val center = centers.getValue(scene)
            return atan2(center.first - petCenter.first, petCenter.second - center.second)
        }
        val fullTurn = (2 * PI).toFloat()
        val origin = angle(clockwise.first())
        val angles = clockwise.map { ((angle(it) - origin) + fullTurn) % fullTurn }
        angles.zipWithNext().forEachIndexed { index, (a, b) ->
            assertTrue("Orbit sectors must follow ${clockwise[index]} then ${clockwise[index + 1]}: $angles",
                b > a + .10f)
        }
    }

    fun report(cloud: DpRect, pet: DpRect, scenes: Map<PetScene, DpRect>): String = buildString {
        appendLine("anchorCoordinates=element centers normalized to the rendered cloud bounds")
        appendLine("centralPet.anchor=${normalizedCenter(cloud, pet)}")
        scenes.forEach { (scene, bounds) -> appendLine("${scene.name}.anchor=${normalizedCenter(cloud, bounds)}") }
    }
}

/** Source-derived visual bounds distinguish the artwork from its transparent canvas. */
internal data class HomeSourceAlpha(
    val width: Int, val height: Int,
    val left: Int, val top: Int, val right: Int, val bottom: Int,
)

internal object HomeArtworkAudit {
    private val cache = mutableMapOf<Int, HomeSourceAlpha>()
    private data class SourcePixels(val width: Int, val height: Int, val colors: IntArray)
    private val pixelCache = mutableMapOf<Int, SourcePixels>()

    private fun sourcePixels(context: Context, resource: Int): SourcePixels = pixelCache.getOrPut(resource) {
        val bitmap = checkNotNull(BitmapFactory.decodeResource(context.resources, resource,
            BitmapFactory.Options().apply { inScaled = false }))
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            SourcePixels(bitmap.width, bitmap.height, pixels)
        } finally {
            bitmap.recycle()
        }
    }

    /** Fit-map visible source cells against the circular button and the requested clearance. */
    fun visiblePixelsOverlapButtonOutline(context: Context, resource: Int, image: DpRect, clickArea: DpRect,
        clearance: Float = 4f): Boolean {
        val source = sourcePixels(context, resource)
        val imageWidth = (image.right - image.left).value
        val imageHeight = (image.bottom - image.top).value
        val fit = minOf(imageWidth / source.width, imageHeight / source.height)
        check(fit > 0f)
        val left = image.left.value + (imageWidth - source.width * fit) / 2f
        val top = image.top.value + (imageHeight - source.height * fit) / 2f
        val centerX = (clickArea.left.value + clickArea.right.value) / 2f
        val centerY = (clickArea.top.value + clickArea.bottom.value) / 2f
        val radius = minOf((clickArea.right - clickArea.left).value,
            (clickArea.bottom - clickArea.top).value) / 2f + clearance
        val xStart = floor((centerX - radius - left) / fit).toInt().coerceIn(0, source.width)
        val xEnd = ceil((centerX + radius - left) / fit).toInt().coerceIn(0, source.width)
        val yStart = floor((centerY - radius - top) / fit).toInt().coerceIn(0, source.height)
        val yEnd = ceil((centerY + radius - top) / fit).toInt().coerceIn(0, source.height)
        for (y in yStart until yEnd) for (x in xStart until xEnd) {
            // Include low-opacity fur edges, rather than only the alpha>=240 measurement bounds.
            if (Color.alpha(source.colors[y * source.width + x]) >= 16) {
                val dx = centerX - centerX.coerceIn(left + x * fit, left + (x + 1) * fit)
                val dy = centerY - centerY.coerceIn(top + y * fit, top + (y + 1) * fit)
                if (dx * dx + dy * dy < radius * radius) return true
            }
        }
        return false
    }

    fun alphaBounds(context: Context, resource: Int): HomeSourceAlpha = cache.getOrPut(resource) {
        val bitmap = checkNotNull(BitmapFactory.decodeResource(context.resources, resource,
            BitmapFactory.Options().apply { inScaled = false }))
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            var left = bitmap.width
            var top = bitmap.height
            var right = -1
            var bottom = -1
            pixels.forEachIndexed { index, pixel ->
                if (Color.alpha(pixel) >= 240) {
                    val x = index % bitmap.width
                    val y = index / bitmap.width
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
            check(right >= left && bottom >= top)
            HomeSourceAlpha(bitmap.width, bitmap.height, left, top, right + 1, bottom + 1)
        } finally {
            bitmap.recycle()
        }
    }

    fun fitOpaqueBounds(image: DpRect, source: HomeSourceAlpha): DpRect {
        val imageWidth = (image.right - image.left).value
        val imageHeight = (image.bottom - image.top).value
        val fit = minOf(imageWidth / source.width, imageHeight / source.height)
        val left = image.left + ((imageWidth - source.width * fit) / 2f).dp
        val top = image.top + ((imageHeight - source.height * fit) / 2f).dp
        return DpRect(left + (source.left * fit).dp, top + (source.top * fit).dp,
            left + (source.right * fit).dp, top + (source.bottom * fit).dp)
    }

    fun cloudOutline(image: DpRect, source: HomeSourceAlpha, bodyAndTail: Boolean = true): DpRect {
        if (!bodyAndTail) return fitOpaqueBounds(image, source)
        val width = (image.right - image.left).value
        val height = (image.bottom - image.top).value
        // Normal cloud rendering retains every source row, with the faint bottom tail in 8dp.
        val split = minOf(1210, source.height - 1)
        val tail = minOf(8f, height / 2f)
        fun y(row: Int): Float = if (row <= split) row.toFloat() / split * (height - tail)
            else height - tail + (row - split).toFloat() / (source.height - split) * tail
        return DpRect(image.left + (source.left.toFloat() / source.width * width).dp,
            image.top + y(source.top).dp,
            image.left + (source.right.toFloat() / source.width * width).dp,
            image.top + y(source.bottom).dp)
    }
}
