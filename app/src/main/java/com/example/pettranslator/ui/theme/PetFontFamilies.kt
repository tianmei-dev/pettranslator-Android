package com.example.pettranslator.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import com.example.pettranslator.R

/** Original release fonts; Android supplies system fallback for missing glyphs and emoji. */
object PetFontFamilies {
    val ZhenKai = FontFamily(Font(R.font.pet_title_zhenkai, FontWeight.Normal))
    val WenKai = FontFamily(Font(R.font.pet_title_wenkai, FontWeight.Normal))
    val System = FontFamily.Default
}

enum class PetTrialFont {
    ZhenKai, WenKai, System;

    val textStyle: TextStyle
        get() = when (this) {
            ZhenKai -> TextStyle(fontFamily = PetFontFamilies.ZhenKai, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None)
            WenKai -> TextStyle(fontFamily = PetFontFamilies.WenKai, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None)
            System -> TextStyle(fontFamily = PetFontFamilies.System, fontWeight = FontWeight.Bold)
        }
}

/** Development trial only. Edit these three defaults independently, then rebuild/install.
 * Keep System until the developer has approved a final pairing. No stored user preference.
 */
data class PetFontTrial(
    val homeTitle: PetTrialFont = PetTrialFont.System,
    val analysisTitle: PetTrialFont = PetTrialFont.System,
    val monologue: PetTrialFont = PetTrialFont.System,
)

internal val LocalPetFontTrial = staticCompositionLocalOf { PetFontTrial() }
