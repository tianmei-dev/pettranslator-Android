package com.example.pettranslator.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import com.example.pettranslator.R

/** Original Regular fonts. OptionalLocal falls back to the platform if loading fails. */
@OptIn(ExperimentalTextApi::class)
object PetFontFamilies {
    val ZhenKai = FontFamily(Font(R.font.pet_title_zhenkai, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.OptionalLocal))
    val WenKai = FontFamily(Font(R.font.pet_title_wenkai, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.OptionalLocal))
    val System = FontFamily.Default
    val Title = ZhenKai
    val Monologue = WenKai
    val Body = System
    val TitleStyle = TextStyle(fontFamily = Title, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None)
    val MonologueStyle = TextStyle(fontFamily = Monologue, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None)

    /** Android Canvas export shares the same original Regular resource as Compose. */
    fun monologueTypeface(context: Context): Typeface = try {
        context.resources.getFont(R.font.pet_title_wenkai)
    } catch (_: RuntimeException) {
        Typeface.create("sans-serif", Typeface.NORMAL)
    }
}
