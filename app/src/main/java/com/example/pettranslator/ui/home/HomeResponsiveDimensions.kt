package com.example.pettranslator.ui.home

/** Independent center anchors in the cloud's local coordinate space; diameter remains in dp. */
internal data class HomeCloudIsland(val xFraction: Float, val yFraction: Float, val diameter: Float)

internal data class HomeResponsiveDimensions(
    val petButtonWidth: Float,
    val petButtonHeight: Float,
    val cloudWidth: Float,
    val cloudHeight: Float,
    val cloudTop: Float,
    val titleY: Float,
    val islands: List<HomeCloudIsland>,
    val centerPetYFraction: Float,
    val centerPetWidth: Float,
    val centerPetHeight: Float,
    val expandedCloud: Boolean,
) {
    companion object {
        const val cloudAspect = 1216f / 1293f

        fun resolve(available: Float, fontScale: Float, contentWidth: Float): HomeResponsiveDimensions {
            val scale = fontScale.coerceAtLeast(1f)
            val buttonHeight = maxOf(48f, 22f * scale + 16f)
            val buttonWidth = minOf(70f + 44f * scale, (contentWidth - 44f) / 2f)
            val petSectionHeight = 22f * scale + 8f + 6f + buttonHeight
            val titleHeight = 26f * scale
            val maxWidth = (contentWidth * .99f).coerceAtLeast(180f)
            val minimumWidth = maxOf(296f, ((60f * scale + 12f + 48f * scale + 12f) / 2f + 8f) / .27f)
            val fittedHeight = ((available - 4f - petSectionHeight - 6f - titleHeight / 2f) / .85f)
                .coerceAtLeast(1f)
            val width = maxWidth
            val expanded = minimumWidth > maxWidth || scale > 1.15f
            val height = maxOf(minOf(fittedHeight, width / cloudAspect * 1.10f), minimumWidth / cloudAspect)
            val islandUnit = minOf(width, height * cloudAspect)
            val fractions = listOf(.22f, .18f, .22f, .215f, .205f, .22f)
            val labelLengths = listOf(5, 4, 2, 2, 3, 3)
            val diameters = fractions.mapIndexed { index, fraction ->
                maxOf(islandUnit * fraction, labelLengths[index] * 12f * scale + 12f,
                    (16f * scale + 16f) / .52f, 48f)
            }

            // Preserve the existing artwork size budget independently of all scene positions.
            val artworkBudget = height * .665f - titleHeight / 2f -
                (diameters[0] + diameters[1] + diameters[5]) / 2f - 20f
            val petHeight = if (expanded) maxOf(width * .29f, minOf(height * .29f, artworkBudget))
                else minOf(height * .29f, artworkBudget.coerceAtLeast(1f))
            val coverage = ((petHeight / width - .25f) / .03f).coerceIn(0f, 1f)
            val anchors = listOf(
                HomeCloudIsland(.30f - .01f * coverage, .36f - .015f * coverage, diameters[0]),
                HomeCloudIsland(.50f, .22f, diameters[1]),
                HomeCloudIsland(.70f + .01f * coverage, .36f - .015f * coverage, diameters[2]),
                HomeCloudIsland(.30f - .02f * coverage, .74f + .02f * coverage, diameters[3]),
                HomeCloudIsland(.50f, .82f, diameters[4]),
                HomeCloudIsland(.70f + .02f * coverage, .74f + .02f * coverage, diameters[5]),
            )

            if (expanded) {
                // Keep the same orbit for accessibility fonts, extending only the scrolling canvas.
                val titleY = 24f + titleHeight / 2f
                var canvasHeight = maxOf(height,
                    (titleY + titleHeight / 2f + 4f + diameters[1] / 2f) / anchors[1].yFraction)
                for (first in anchors.indices) for (second in first + 1 until anchors.size) {
                    val a = anchors[first]
                    val b = anchors[second]
                    val separation = (a.diameter + b.diameter) / 2f + 8f
                    if (kotlin.math.abs(a.xFraction - b.xFraction) * width < separation) {
                        val verticalSpan = kotlin.math.abs(a.yFraction - b.yFraction)
                        if (verticalSpan > 0f) canvasHeight = maxOf(canvasHeight, separation / verticalSpan)
                    }
                }
                return HomeResponsiveDimensions(buttonWidth, buttonHeight, width, canvasHeight,
                    petSectionHeight + 12f, titleY, spreadHorizontally(anchors, width), .54f, width * .49f, petHeight, true)
            }

            val titleY = height * .15f
            val headSideX = minOf(anchors[0].xFraction,
                .5f - ((maxOf(diameters[0], diameters[2]) + diameters[1]) / 2f + 4f) / width)
            val pawSideX = minOf(anchors[3].xFraction,
                .5f - ((maxOf(diameters[3], diameters[5]) + diameters[4]) / 2f + 4f) / width)
            val safeAnchors = anchors.mapIndexed { index, anchor ->
                when (index) {
                    0 -> anchor.copy(xFraction = headSideX)
                    2 -> anchor.copy(xFraction = 1f - headSideX)
                    3 -> anchor.copy(xFraction = pawSideX)
                    5 -> anchor.copy(xFraction = 1f - pawSideX)
                    1 -> anchor.copy(yFraction = maxOf(anchor.yFraction,
                        (titleY + titleHeight / 2f + 4f + anchor.diameter / 2f) / height))
                    else -> anchor
                }
            }
            val cloudTop = petSectionHeight + 6f - height * .15f + titleHeight / 2f
            val orbit = adjustSceneOrbit(spreadHorizontally(safeAnchors, width))
            return HomeResponsiveDimensions(buttonWidth, buttonHeight, width, height,
                maxOf(cloudTop, available - 4f - height), titleY,
                avoidArtworkOverlap(orbit, safeAnchors, width, height, petHeight),
                .54f, width * .49f, petHeight, false)
        }

        private fun adjustSceneOrbit(
            anchors: List<HomeCloudIsland>
        ): List<HomeCloudIsland> {
            val centerY = .54f
            val factor = .72f

            return anchors.mapIndexed { index, island ->
                if (index in listOf(0, 2, 3, 5)) {
                    island.copy(
                        yFraction = centerY +
                            (island.yFraction - centerY) * factor
                    )
                } else {
                    island
                }
            }
        }

        // Ordinary-mode safety only: retain the requested anchors unless a pet outline intersects.
        // Both existing images are checked so switching pets never moves a scene button.
        private fun avoidArtworkOverlap(
            orbit: List<HomeCloudIsland>, original: List<HomeCloudIsland>,
            width: Float, height: Float, petHeight: Float,
        ): List<HomeCloudIsland> {
            fun fitOutline(source: IntArray, sourceWidth: Float, heightScale: Float): FloatArray {
                val fit = minOf(width * .49f / sourceWidth, petHeight * heightScale / 426f)
                return FloatArray(source.size) { index ->
                    if (index % 2 == 0) width * .5f + (source[index] - sourceWidth / 2f) * fit
                    else height * .54f + (source[index] - 213f) * fit
                }
            }
            val outlines = listOf(fitOutline(catOutline, 620f, 1f), fitOutline(dogOutline, 697f, .96f))
            fun intersects(island: HomeCloudIsland, y: Float, clearance: Float): Boolean {
                val x = island.xFraction * width
                val centerY = y * height
                val radius = island.diameter / 2f + clearance
                return outlines.any { points ->
                    (points.indices step 2).any { index ->
                        val next = (index + 2) % points.size
                        val dx = points[next] - points[index]
                        val dy = points[next + 1] - points[index + 1]
                        val projection = (((x - points[index]) * dx +
                            (centerY - points[index + 1]) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
                        val gapX = x - points[index] - projection * dx
                        val gapY = centerY - points[index + 1] - projection * dy
                        gapX * gapX + gapY * gapY < radius * radius
                    }
                }
            }
            return orbit.mapIndexed { index, island ->
                if (index !in listOf(0, 2, 3, 5) || !intersects(island, island.yFraction, 0f)) {
                    island
                } else {
                    // Search only between the requested Y and the previous safe Y; keep 1dp for rounding.
                    var colliding = island.yFraction
                    var safe = original[index].yFraction
                    repeat(16) {
                        val candidate = (colliding + safe) / 2f
                        if (intersects(island, candidate, 1f)) colliding = candidate else safe = candidate
                    }
                    island.copy(yFraction = safe)
                }
            }
        }

        // Convex outlines of the unchanged pet_cat_main / pet_dog_main alpha>=16 pixel cells.
        // Source coordinates preserve their Fit aspect ratio; these are collision bounds, not artwork.
        private val catOutline = intArrayOf(
            66,293, 67,281, 70,265, 117,81, 120,74, 370,0, 427,0, 428,1,
            561,145, 569,157, 574,168, 575,173, 575,191, 573,197, 556,241,
            499,379, 493,386, 492,387, 489,389, 487,390, 463,397, 458,398,
            350,410, 333,410, 302,409, 144,393, 128,391, 112,386, 107,384,
            102,379, 80,348, 71,330, 67,315, 66,300,
        )
        private val dogOutline = intArrayOf(
            79,353, 83,142, 84,130, 86,126, 95,109, 104,96, 114,83, 117,80,
            127,71, 140,62, 208,16, 214,12, 227,5, 229,4, 234,3, 269,0,
            404,0, 417,5, 434,16, 446,24, 495,60, 508,73, 514,80, 638,243,
            640,246, 641,248, 643,254, 651,281, 651,292, 650,316, 643,342,
            636,353, 625,367, 618,373, 613,375, 607,377, 603,378, 598,379,
            374,413, 343,415, 332,415, 213,414, 184,411, 178,410, 174,409,
            104,386, 101,385, 91,381, 85,376, 84,375, 81,370, 79,354,
        )

        // Apply after canvas sizing so only the four horizontal centers change.
        private fun spreadHorizontally(anchors: List<HomeCloudIsland>, width: Float) =
            anchors.mapIndexed { index, anchor ->
                val targetX = when (index) {
                    0 -> .24f
                    2 -> .76f
                    3 -> .26f
                    5 -> .74f
                    else -> return@mapIndexed anchor
                }
                val edgeInset = ((anchor.diameter / 2f + 4f) / width).coerceAtMost(.5f)
                anchor.copy(xFraction = targetX.coerceIn(edgeInset, 1f - edgeInset))
            }
    }
}
