package com.example.pettranslator.core

enum class Feature { RecordingTranslation, HumanVoice }
fun interface FeatureAccessManager { fun canAccess(feature: Feature): Boolean }
class DefaultFeatureAccessManager : FeatureAccessManager {
    override fun canAccess(feature: Feature) = true
}
