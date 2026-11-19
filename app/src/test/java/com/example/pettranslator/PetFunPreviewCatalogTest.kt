package com.example.pettranslator

import com.example.pettranslator.audio.PetClipType
import com.example.pettranslator.audio.PetSoundCatalog
import com.example.pettranslator.domain.PetType
import com.example.pettranslator.ui.play.PetFunPreviewCatalog
import com.example.pettranslator.ui.play.PetFunSoundCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetFunPreviewCatalogTest {
    @Test fun allTenEntriesHaveDistinctContinuousSoundsForTheirPet() {
        val items = PetType.entries.flatMap(PetFunSoundCatalog::items)
        val assignments = PetFunPreviewCatalog.audioIdsBySoundId
        assertEquals(10, items.size)
        assertEquals(items.map { it.id }.toSet(), assignments.keys)
        assertEquals(10, assignments.values.toSet().size)

        for (item in items) {
            val audio = PetSoundCatalog.byId.getValue(assignments.getValue(item.id))
            assertEquals(item.petType, audio.petType)
            assertEquals(PetClipType.CONTINUOUS, audio.clipType)
            assertTrue(audio.available)
            assertTrue(audio.durationMs in 1_000L..9_000L)
            assertTrue(audio.overlaps.none { it in assignments.values })
        }
    }
}
