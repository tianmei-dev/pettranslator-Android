package com.example.pettranslator.audio

import android.content.Context
import com.example.pettranslator.domain.PetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

enum class PetClipType { SINGLE, CONTINUOUS }

data class PetSoundAsset(
    val audioId: String,
    val petType: PetType,
    val clipType: PetClipType,
    val durationMs: Long,
    val sourceGroup: String,
    val overlaps: Set<String> = emptySet(),
    val defaultForShortInput: Boolean = true,
    val available: Boolean = true,
    val assetPath: String = "pet_audio/$audioId.m4a",
)

/**
 * Durations and overlap pairs come from the reviewed B3.2.3 optimization metadata.
 * Availability here permits inclusion in the Debug library; actual playback also
 * requires the matching packaged asset. Release has no pet audio assets.
 */
object PetSoundCatalog {
    val assets: List<PetSoundAsset> = listOf(
        PetSoundAsset("cat_continuous_01", PetType.Cat, PetClipType.CONTINUOUS, 6810, "video01_track01"),
        PetSoundAsset("cat_continuous_02", PetType.Cat, PetClipType.CONTINUOUS, 6008, "video02_track01",
            overlaps = setOf("cat_continuous_03", "cat_single_01", "cat_single_02")),
        PetSoundAsset("cat_continuous_03", PetType.Cat, PetClipType.CONTINUOUS, 1910, "video02_track01",
            overlaps = setOf("cat_continuous_02")),
        PetSoundAsset("cat_continuous_04", PetType.Cat, PetClipType.CONTINUOUS, 8055, "video07_track01",
            overlaps = setOf("cat_single_03", "cat_single_04", "cat_single_05")),
        PetSoundAsset("cat_continuous_05", PetType.Cat, PetClipType.CONTINUOUS, 15310, "video08_track01",
            defaultForShortInput = false),
        PetSoundAsset("cat_continuous_06", PetType.Cat, PetClipType.CONTINUOUS, 1850, "video10_track01",
            overlaps = setOf("cat_continuous_08")),
        PetSoundAsset("cat_continuous_07", PetType.Cat, PetClipType.CONTINUOUS, 5190, "video09_track01"),
        PetSoundAsset("cat_continuous_08", PetType.Cat, PetClipType.CONTINUOUS, 6553, "video10_track01",
            overlaps = setOf("cat_continuous_06", "cat_continuous_09")),
        PetSoundAsset("cat_continuous_09", PetType.Cat, PetClipType.CONTINUOUS, 1893, "video10_track01",
            overlaps = setOf("cat_continuous_08")),
        PetSoundAsset("cat_single_01", PetType.Cat, PetClipType.SINGLE, 790, "video02_track01",
            overlaps = setOf("cat_continuous_02")),
        PetSoundAsset("cat_single_02", PetType.Cat, PetClipType.SINGLE, 910, "video02_track01",
            overlaps = setOf("cat_continuous_02")),
        PetSoundAsset("cat_single_03", PetType.Cat, PetClipType.SINGLE, 1550, "video07_track01",
            overlaps = setOf("cat_continuous_04")),
        PetSoundAsset("cat_single_04", PetType.Cat, PetClipType.SINGLE, 1450, "video07_track01",
            overlaps = setOf("cat_continuous_04")),
        PetSoundAsset("cat_single_05", PetType.Cat, PetClipType.SINGLE, 2135, "video07_track01",
            overlaps = setOf("cat_continuous_04")),
        PetSoundAsset("dog_continuous_01", PetType.Dog, PetClipType.CONTINUOUS, 1410, "video03_track01"),
        PetSoundAsset("dog_continuous_02", PetType.Dog, PetClipType.CONTINUOUS, 1830, "video03_track01"),
        PetSoundAsset("dog_continuous_03", PetType.Dog, PetClipType.CONTINUOUS, 1210, "video03_track01"),
        PetSoundAsset("dog_continuous_04", PetType.Dog, PetClipType.CONTINUOUS, 1610, "video03_track01"),
        PetSoundAsset("dog_continuous_05", PetType.Dog, PetClipType.CONTINUOUS, 2370, "video03_track01"),
        PetSoundAsset("dog_continuous_06", PetType.Dog, PetClipType.CONTINUOUS, 4290, "video03_track01"),
        PetSoundAsset("dog_continuous_07", PetType.Dog, PetClipType.CONTINUOUS, 7450, "video04_track01"),
        PetSoundAsset("dog_continuous_08", PetType.Dog, PetClipType.CONTINUOUS, 6510, "video05_track01"),
        PetSoundAsset("dog_continuous_09", PetType.Dog, PetClipType.CONTINUOUS, 8805, "video06_track01"),
    )
    val byId: Map<String, PetSoundAsset> = assets.associateBy { it.audioId }
}

class AssetPetSoundLibrary(context: Context) {
    private val assets = context.applicationContext.assets

    suspend fun available(): List<PetSoundAsset> = withContext(Dispatchers.IO) {
        PetSoundCatalog.assets.filter { asset ->
            currentCoroutineContext().ensureActive()
            isReadable(asset)
        }
    }

    suspend fun findAvailable(audioId: String): PetSoundAsset? = withContext(Dispatchers.IO) {
        PetSoundCatalog.byId[audioId]?.takeIf(::isReadable)
    }

    private fun isReadable(asset: PetSoundAsset): Boolean =
        asset.available && runCatching { assets.openFd(asset.assetPath).use { it.length > 0 } }
            .getOrDefault(false)
}
