package com.example.pettranslator.ui.play

import com.example.pettranslator.domain.PetType

/** UI catalogue only; no audio asset or player state is implied. */
data class PetFunSoundItem(val id: String, val petType: PetType, val title: String,
    val subtitle: String, val icon: FunSoundIcon)
enum class FunSoundIcon { Call, Sit, Paw, Praise, Meal }
object PetFunSoundCatalog {
    fun items(pet: PetType): List<PetFunSoundItem> {
        val prefix = if (pet == PetType.Cat) "cat" else "dog"
        return listOf(
            PetFunSoundItem("${prefix}_call", pet, "快来", "召唤它到你身边", FunSoundIcon.Call),
            PetFunSoundItem("${prefix}_sit", pet, "坐下", "引导它乖乖坐下", FunSoundIcon.Sit),
            PetFunSoundItem("${prefix}_paw", pet, "握手", "和它友好互动，击掌握手", FunSoundIcon.Paw),
            PetFunSoundItem("${prefix}_praise", pet, if (pet == PetType.Cat) "夸夸我" else "夸夸你",
                if (pet == PetType.Cat) "给它满满的夸奖与鼓励" else "给它一点温柔鼓励", FunSoundIcon.Praise),
            PetFunSoundItem("${prefix}_meal", pet, "开饭啦", "呼唤它来吃饭", FunSoundIcon.Meal))
    }
}
