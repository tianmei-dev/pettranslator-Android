package com.example.pettranslator.domain

enum class PetType(val title: String, val subtitle: String) {
    Cat("猫咪", "喵喵的小心声"), Dog("狗狗", "汪汪的小心声")
}
enum class PetScene(val title: String, val symbol: String) {
    Door("门口或窗边", "▥"), Meal("吃饭前后", "♨"), Play("玩耍", "◉"),
    Alone("独处", "⌂"), Held("被抱起", "♡"), Unknown("不确定", "？")
}
interface PetPreferences {
    val petType: kotlinx.coroutines.flow.Flow<PetType>
    suspend fun setPetType(type: PetType)
}
