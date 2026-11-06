package com.example.pettranslator.ui.home

/** All values are dp. Dimensions are stable across selection changes. */
internal data class HomeResponsiveDimensions(
    val petCard: Float, val sceneCard: Float,
    val topGap: Float, val moduleGap: Float, val bottomGap: Float,
    val rowGap: Float, val iconTextGap: Float,
) {
    companion object {
        // Minimum / ideal / maximum at font scale 1. Text can always grow beyond these minima.
        val petRange = listOf(62f, 72f, 84f)
        val sceneRange = listOf(74f, 84f, 100f)
        val gapRange = listOf(8f, 14f, 48f)

        fun resolve(available: Float, fontScale: Float): HomeResponsiveDimensions {
            val scale = fontScale.coerceAtLeast(1f)
            val petMin = if (scale >= 1.3f) 64f + 40f * scale else 20f + 42f * scale
            val sceneMin = 56f + 18f * scale
            // Section padding + title/content gap; wrapping remains natural and scrollable.
            val headers = 2f * (21f + 25f * scale)
            var budget = (available - headers - petMin - 2f * sceneMin - 8f - 10f - 12f - 10f).coerceAtLeast(0f)
            fun take(limit: Float): Float = minOf(budget, limit).also { budget -= it }
            val comfort = take((petRange[1] - petRange[0]) + 2f * (sceneRange[1] - sceneRange[0])) / 3f
            val gaps = take(18f) / 3f
            val iconSpace = take(8f) / 2f
            val expansion = take(36f) / 3f
            val balanced = take(90f) / 3f
            return HomeResponsiveDimensions(petMin + comfort + expansion,
                sceneMin + comfort + expansion + iconSpace,
                8f + gaps + balanced, 10f + gaps + balanced, 12f + gaps + balanced,
                10f, 4f + iconSpace)
        }
    }
}
