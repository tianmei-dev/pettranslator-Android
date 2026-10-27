package com.example.pettranslator

import com.example.pettranslator.domain.PetScene
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.domain.interpretation.*
import com.example.pettranslator.domain.recognition.*
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class PetInterpretationEngineTest {
    private val engine = PetInterpretationEngine(now = { 1_800_000_000_000L })

    private fun recognition(pet: PetType, scene: PetScene, status: RecognitionStatus = RecognitionStatus.SUCCESS,
                            usable: Boolean = true): PetRecognitionResult {
        val cat = pet == PetType.Cat
        val labelIndex = if (cat) 78 else 70
        val label = if (cat) "Meow" else "Bark"
        val event = SoundEvent(labelIndex, label, .74f)
        val windowEvidence = listOf(
            PetWindowEvidence(0, if (cat) .74f else .05f, if (cat) .05f else .74f, event),
            PetWindowEvidence(1, if (cat) .62f else .04f, if (cat) .04f else .62f, event),
            PetWindowEvidence(2, if (cat) .05f else .05f, if (cat) .05f else .05f, null),
        )
        return PetRecognitionResult(
            selectedPet = pet, scene = scene, detectedPet = if (status == RecognitionStatus.UNCERTAIN) null else pet,
            status = status, catScore = if (cat) .74f else .05f, dogScore = if (cat) .05f else .74f,
            otherScore = .08f, primarySound = event, soundEvents = listOf(event), windowEvidence = windowEvidence,
            quality = AudioQuality(3000, -25.0, 0.0, .05, 3, usable),
            modelVersion = "yamnet-test-1", ruleVersion = "decision-test-1",
        )
    }

    @Test fun allTwelvePetAndScenePairsProduceStructuredResults() {
        val themes = mapOf(
            PetType.Cat to mapOf(PetScene.Door to CoreTheme.CatDoorWindow, PetScene.Meal to CoreTheme.CatFood,
                PetScene.Play to CoreTheme.CatPlay, PetScene.Alone to CoreTheme.CatAlone,
                PetScene.Held to CoreTheme.CatHeld, PetScene.Unknown to CoreTheme.CatUnknown),
            PetType.Dog to mapOf(PetScene.Door to CoreTheme.DogDoorWindow, PetScene.Meal to CoreTheme.DogFood,
                PetScene.Play to CoreTheme.DogPlay, PetScene.Alone to CoreTheme.DogAlone,
                PetScene.Held to CoreTheme.DogHeld, PetScene.Unknown to CoreTheme.DogUnknown),
        )
        for (pet in PetType.entries) for (scene in PetScene.entries) {
            val result = engine.interpret(recognition(pet, scene), "f".repeat(64))!!
            assertEquals(pet, result.petType)
            assertEquals(scene, result.scene)
            assertEquals(themes.getValue(pet).getValue(scene), result.coreThemeId)
            assertEquals(RecognitionStatus.SUCCESS, result.recognitionStatus)
            assertTrue(result.innerMonologue.isNotBlank())
            assertTrue(result.interactionSuggestion.isNotBlank())
            assertEquals(pet, result.soundFeatures.petType)
            if (pet == PetType.Cat) {
                assertEquals(SoundCategory.CatMeow, result.soundFeatures.soundCategory)
                assertTrue(result.title.contains("猫咪"))
                assertFalse(result.title.contains("狗狗"))
            } else {
                assertEquals(SoundCategory.DogBark, result.soundFeatures.soundCategory)
                assertTrue(result.title.contains("狗狗"))
                assertFalse(result.title.contains("猫咪"))
            }
        }
    }

    @Test fun onlySuccessWithUsableMatchingPetAndFingerprintCanBeInterpreted() {
        assertNull(engine.interpret(recognition(PetType.Cat, PetScene.Play, RecognitionStatus.UNCERTAIN), "a"))
        assertNull(engine.interpret(recognition(PetType.Cat, PetScene.Play, RecognitionStatus.PET_MISMATCH), "a"))
        assertNull(engine.interpret(null, "a")) // Analysis errors have no accepted recognition result.
        assertNull(engine.interpret(recognition(PetType.Cat, PetScene.Play, usable = false), "a"))
        assertNull(engine.interpret(recognition(PetType.Cat, PetScene.Play), null))
        assertNull(engine.interpret(recognition(PetType.Cat, PetScene.Play), ""))
        val wrongSpecies = recognition(PetType.Cat, PetScene.Play).copy(detectedPet = PetType.Dog)
        assertNull(engine.interpret(wrongSpecies, "a"))
    }

    @Test fun sameFingerprintProducesStableThemeToneEvidenceAndCopy() {
        val input = recognition(PetType.Dog, PetScene.Alone)
        val first = engine.interpret(input, "0123456789abcdef".repeat(4))!!
        repeat(100) {
            val repeated = engine.interpret(input, "0123456789abcdef".repeat(4))!!
            assertEquals(first.interpretationId, repeated.interpretationId)
            assertEquals(first.coreThemeId, repeated.coreThemeId)
            assertEquals(first.emotionTone, repeated.emotionTone)
            assertEquals(first.evidenceSummary, repeated.evidenceSummary)
            assertEquals(first.interactionSuggestion, repeated.interactionSuggestion)
            assertEquals(first.templateId, repeated.templateId)
            assertEquals(first.innerMonologue, repeated.innerMonologue)
        }
        val second = engine.interpret(input, "0123456789abcdef".repeat(4))!!
        assertEquals(first.coreThemeId, second.coreThemeId)
        assertEquals(first.emotionTone, second.emotionTone)
        assertEquals(first.explanation, second.explanation)
        assertEquals(first.interactionSuggestion, second.interactionSuggestion)
        assertEquals(first.templateId, second.templateId)
    }

    @Test fun factsComeOnlyFromYamnetLabelsAndWindowBreakdown() {
        val input = recognition(PetType.Cat, PetScene.Unknown).copy(
            soundEvents = listOf(SoundEvent(76, "Cat", .72f)),
            windowEvidence = listOf(
                PetWindowEvidence(0, .72f, .04f, SoundEvent(76, "Cat", .72f)),
                PetWindowEvidence(1, .60f, .02f, SoundEvent(76, "Cat", .60f)),
                PetWindowEvidence(2, .50f, .05f, SoundEvent(76, "Cat", .50f)),
            ),
        )
        val result = engine.interpret(input, "b".repeat(64))!!
        assertEquals(SoundCategory.CatBroadCall, result.soundFeatures.soundCategory)
        assertEquals(listOf("Cat"), result.soundFeatures.detectedModelLabels)
        assertEquals(3, result.soundFeatures.windowsWithPetEvidence)
        assertEquals(3, result.soundFeatures.longestAdjacentEvidenceRun)
        assertTrue(result.explanation.contains("不代表 AI 识别了宠物真实心理"))
        assertTrue(result.evidenceSummary.contains("不是概率"))
        assertFalse(result.explanation.contains("喵叫类别"))
        assertEquals(CoreTheme.CatUnknown, result.coreThemeId)
        assertEquals(ThemeBasis.ScenePlayfulAssociation, result.themeBasis)
    }

    @Test fun sceneChangesThemeButNeverOverridesRecognizedSoundFacts() {
        val input = recognition(PetType.Dog, PetScene.Meal)
        val meal = engine.interpret(input, "c".repeat(64))!!
        val play = engine.interpret(input.copy(scene = PetScene.Play), "c".repeat(64))!!
        assertEquals(SoundCategory.DogBark, meal.soundFeatures.soundCategory)
        assertEquals(meal.soundFeatures, play.soundFeatures)
        assertEquals(CoreTheme.DogFood, meal.coreThemeId)
        assertEquals(CoreTheme.DogPlay, play.coreThemeId)
    }

    @Test fun broadSpeciesOnlyEvidenceFallsBackToGeneralThemeAcrossScenes() {
        for (pet in PetType.entries) for (scene in PetScene.entries) {
            val broadIndex = if (pet == PetType.Cat) 76 else 69
            val broadLabel = if (pet == PetType.Cat) "Cat" else "Dog"
            val input = recognition(pet, scene).copy(soundEvents = listOf(SoundEvent(broadIndex, broadLabel, .72f)))
            val expected = when (pet) {
                PetType.Cat -> mapOf(PetScene.Door to CoreTheme.CatDoorWindow, PetScene.Meal to CoreTheme.CatFood,
                    PetScene.Play to CoreTheme.CatPlay, PetScene.Alone to CoreTheme.CatAlone,
                    PetScene.Held to CoreTheme.CatHeld, PetScene.Unknown to CoreTheme.CatUnknown)
                PetType.Dog -> mapOf(PetScene.Door to CoreTheme.DogDoorWindow, PetScene.Meal to CoreTheme.DogFood,
                    PetScene.Play to CoreTheme.DogPlay, PetScene.Alone to CoreTheme.DogAlone,
                    PetScene.Held to CoreTheme.DogHeld, PetScene.Unknown to CoreTheme.DogUnknown)
            }
            val result = engine.interpret(input, "e".repeat(64))!!
            assertEquals(expected.getValue(scene), result.coreThemeId)
            assertEquals(TemplateEvidenceTier.BroadClassOnly, result.soundFeatures.templateEvidenceTier)
            assertEquals(ThemeBasis.ScenePlayfulAssociation, result.themeBasis)
        }
    }

    @Test fun resultHasStableVersionedRoundTrip() {
        val original = engine.interpret(recognition(PetType.Cat, PetScene.Held), "d".repeat(64))!!
        val restored = PetInterpretationCodec.decode(PetInterpretationCodec.encode(original))
        assertEquals(original, restored)
        assertEquals("yamnet-test-1", restored.modelVersion)
        assertEquals("decision-test-1", restored.recognitionRuleVersion)
        assertEquals(PetInterpretationEngine.RULE_VERSION, restored.interpretationRuleVersion)
        assertEquals(PetInterpretationEngine.TEMPLATE_VERSION, restored.templateVersion)
        assertEquals(.05, restored.soundFeatures.silentFrameFraction!!, 0.0)
        assertEquals(0.0, restored.soundFeatures.clippedFraction!!, 0.0)
        assertTrue(restored.soundFeatures.audioQualityUsable)
    }

    @Test fun formalCatalogHasExactly96UniqueVerbatimEntriesAcrossTwelveGroups() {
        val catalog = PetInterpretationTemplateCatalog.entries
        assertEquals(96, catalog.size)
        assertEquals(96, catalog.map { it.templateId }.distinct().size)
        assertEquals(48, catalog.count { it.petType == PetType.Cat })
        assertEquals(48, catalog.count { it.petType == PetType.Dog })
        for (pet in PetType.entries) for (scene in PetScene.entries) {
            val group = catalog.filter { it.petType == pet && it.scene == scene }
            assertEquals("$pet/$scene", 8, group.size)
            assertTrue(group.all { it.innerMonologue.isNotBlank() })
            assertTrue(group.all { it.templateVersion == PetInterpretationEngine.TEMPLATE_VERSION })
            assertEquals(8, group.map { it.templateId }.distinct().size)
        }
        assertTrue(catalog.any { it.templateId == "CAT_DOOR_WINDOW_01" && it.innerMonologue == "咦？外面是不是有新情况！本喵踮脚侦查一下👀" })
        assertTrue(catalog.any { it.templateId == "DOG_UNKNOWN_07" && it.innerMonologue == "主人！一条小狗语音消息，请查收📩" })
        val expectedHashes = javaClass.getResourceAsStream("/approved_pet_copy_v1.sha256")!!.bufferedReader().useLines { lines ->
            lines.associate { line -> line.substringBefore(' ') to line.substringAfter(' ') }
        }
        assertEquals(96, expectedHashes.size)
        catalog.forEach { template ->
            val actual = MessageDigest.getInstance("SHA-256").digest(template.innerMonologue.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            assertEquals("Approved copy changed: ${template.templateId}", expectedHashes[template.templateId], actual)
        }
    }

    @Test fun missingSceneUsesUnknownAndMissingGroupFallsBackWithinSameSpecies() {
        val missingScene = engine.interpret(recognition(PetType.Cat, PetScene.Play), "1".repeat(64), null)!!
        assertEquals(PetScene.Unknown, missingScene.scene)
        assertEquals(CoreTheme.CatUnknown, missingScene.coreThemeId)
        assertFalse(missingScene.templateFallbackUsed)

        val withoutCatDoor = PetInterpretationTemplateCatalog.entries.filterNot {
            it.petType == PetType.Cat && it.scene == PetScene.Door
        }
        val fallbackEngine = PetInterpretationEngine({ 1_800_000_000_000L }, withoutCatDoor)
        val fallback = fallbackEngine.interpret(recognition(PetType.Cat, PetScene.Door), "2".repeat(64))!!
        assertEquals(PetScene.Unknown, fallback.scene)
        assertEquals(CoreTheme.CatUnknown, fallback.coreThemeId)
        assertTrue(fallback.templateFallbackUsed)
        assertTrue(fallback.templateId.startsWith("CAT_UNKNOWN_"))
    }

    @Test fun emptySameSpeciesFallbackFailsInsteadOfBorrowingAnotherSpeciesCopy() {
        val dogOnly = PetInterpretationTemplateCatalog.entries.filter { it.petType == PetType.Dog }
        val noCatTemplates = PetInterpretationEngine({ 1_800_000_000_000L }, dogOnly)
        assertThrows(IllegalStateException::class.java) {
            noCatTemplates.interpret(recognition(PetType.Cat, PetScene.Play), "3".repeat(64))
        }
    }
}
