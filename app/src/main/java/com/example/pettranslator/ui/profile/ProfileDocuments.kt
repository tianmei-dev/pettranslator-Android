package com.example.pettranslator.ui.profile

/** Fixed local assets only: menu titles, routes and files share one explicit mapping. */
enum class LegalDocument(val id: String, val title: String, val assetFile: String) {
    UserAgreement("user-agreement", "用户协议", "user_agreement.txt"),
    PrivacyPolicy("privacy-policy", "隐私政策", "privacy_policy.txt"),
    ChildrenPrivacyPolicy("children-privacy", "儿童隐私政策", "children_privacy_policy.txt"),
    PersonalInfoCollection("personal-info", "个人信息收集清单", "personal_info_collection.txt"),
    ThirdPartyInfoCollection("third-party-info", "第三方信息收集清单", "third_party_info_collection.txt"),
    ThirdPartySdkSharing("third-party-sdk", "第三方 SDK 共享清单", "third_party_sdk_sharing.txt"),
    CollectedPersonalInfo("collected-info", "已收集个人信息清单", "collected_personal_info.txt");

    companion object {
        fun fromId(id: String?): LegalDocument? = entries.firstOrNull { it.id == id }
    }
}

internal object ProfileRoutes {
    const val Permissions = "profile/permissions"
    const val Document = "profile/legal/{documentId}"
    fun document(document: LegalDocument) = "profile/legal/${document.id}"
}
