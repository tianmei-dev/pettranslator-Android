package com.example.pettranslator

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pettranslator.ui.profile.LegalDocument
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** One device pass. Never grant/revoke permissions; retain just two original screen PNGs. */
class ProfilePrivacyFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Before fun wake() = wakeTestDevice()

    @Test fun profilePrivacyAndSystemSettingsFlow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val folder = File(checkNotNull(context.getExternalFilesDir(null)),
            "b22-profile-privacy-evidence").apply { check(isDirectory || mkdirs()) }
        val log = File(folder, "profile-privacy-flow.txt").apply { writeText("") }
        val initialMicrophonePermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        val tabNames = listOf("首页", "逗宠", "我的")
        val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

        fun note(message: String) {
            log.appendText("$message\n")
            android.util.Log.i("B22ProfilePrivacy", message)
        }
        @Suppress("DEPRECATION")
        fun activePackage(): String? {
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
            return try { root.packageName?.toString() } finally { root.recycle() }
        }
        fun awaitAppForeground() {
            rule.waitUntil(10_000) { activePackage() == context.packageName }
        }
        fun capture(name: String) {
            rule.waitForIdle()
            instrumentation.waitForIdleSync()
            awaitAppForeground()
            assertEquals("Only capture the actual app foreground", context.packageName, activePackage())
            val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                File(folder, name).outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                note("screenshot=$name ${bitmap.width}x${bitmap.height}")
            } finally {
                bitmap.recycle()
            }
        }
        fun tab(title: String) = rule.onNode(tabRole and hasText(title) and hasClickAction())
        fun assertProfile() {
            rule.onNodeWithTag("profile_viewport").assertIsDisplayed()
            rule.onAllNodes(tabRole).assertCountEquals(3)
            tabNames.forEach { tab(it).assertIsDisplayed() }
            tab("我的").assertIsSelected()
            rule.onNodeWithTag("home_actions").assertDoesNotExist()
        }
        fun assertDetailTabsHidden() {
            rule.onAllNodes(tabRole).assertCountEquals(0)
            rule.onNodeWithTag("profile_viewport").assertDoesNotExist()
            rule.onNodeWithTag("home_actions").assertDoesNotExist()
        }
        fun returnToProfile() {
            rule.onNodeWithTag("profile_detail_back").assertIsDisplayed()
                .assert(hasClickAction()).performClick()
            rule.waitForIdle()
            assertProfile()
        }
        fun assertMicrophoneStatus(phase: String) {
            val actual = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            val expected = if (actual == PackageManager.PERMISSION_GRANTED) "已授权" else "未授权"
            rule.waitUntil(10_000) {
                rule.onAllNodes(hasTestTag("permission_microphone_status") and hasText(expected))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag("permission_microphone_status").assertIsDisplayed().assertTextEquals(expected)
            assertEquals("This flow must not change the microphone grant", initialMicrophonePermission, actual)
            note("microphone=$phase actual=$actual displayed=$expected unchanged=true")
        }
        fun scrollToEnd(tag: String): Pair<Float, Float> {
            val scroll = rule.onNodeWithTag(tag)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))
            val initialRange = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            val maximum = initialRange.maxValue()
            assertTrue("$tag exposes a finite scroll extent", maximum.isFinite())
            if (maximum > 0f) {
                scroll.performSemanticsAction(SemanticsActions.ScrollBy) { action -> action(0f, maximum) }
            }
            rule.waitForIdle()
            val endRange = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            val end = endRange.value()
            val endMaximum = endRange.maxValue()
            assertTrue("$tag reaches the complete document/list bottom: $end/$endMaximum",
                abs(endMaximum - end) <= 1f)
            return end to endMaximum
        }

        try {
            note("device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT} " +
                "fontScale=${context.resources.configuration.fontScale} initialMicrophone=$initialMicrophonePermission")
            rule.waitUntil(10_000) {
                rule.onAllNodesWithTag("home_record").fetchSemanticsNodes().isNotEmpty()
            }
            tab("我的").performClick()
            assertProfile()
            val fixedTabs = tabNames.associateWith { tab(it).getUnclippedBoundsInRoot() }
            fun assertTabsFixed() {
                assertProfile()
                tabNames.forEach { title ->
                    assertEquals("$title stays fixed while Profile scrolls", fixedTabs.getValue(title),
                        tab(title).getUnclippedBoundsInRoot())
                }
            }

            rule.onNodeWithTag("profile_menu_0").performScrollTo().assert(hasText("系统权限管理"))
                .performClick()
            rule.onNodeWithTag("permission_details").assertIsDisplayed()
            rule.onNodeWithText("权限详情").assertIsDisplayed()
            assertDetailTabsHidden()
            awaitAppForeground()
            assertMicrophoneStatus("entered")
            capture("permission-details.png")

            rule.onNodeWithTag("manage_system_permissions").assertIsDisplayed()
                .assert(hasClickAction()).performClick()
            var settingsPackage: String? = null
            try {
                rule.waitUntil(10_000) {
                    val foreground = activePackage()
                    if (foreground != null && foreground != context.packageName &&
                        foreground.contains("settings", ignoreCase = true)) {
                        settingsPackage = foreground
                        true
                    } else false
                }
                val foreground = checkNotNull(settingsPackage)
                @Suppress("DEPRECATION")
                val info = context.packageManager.getApplicationInfo(foreground, 0)
                assertTrue("Permission management opens a system Settings package: $foreground",
                    info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
                note("systemSettings=foreground package=$foreground; no permission controls changed")
            } finally {
                // Back is the only system interaction: do not tap any permission switch or prompt.
                instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").use {
                    ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
                }
            }
            awaitAppForeground()
            rule.waitForIdle()
            rule.onNodeWithTag("permission_details").assertIsDisplayed()
            assertDetailTabsHidden()
            assertMicrophoneStatus("returnedFromSettings")
            returnToProfile()
            note("permissions=opened, real grant verified, system Settings opened, returned and refreshed; PASS")

            val documents = listOf(
                LegalDocument.UserAgreement to "用户协议",
                LegalDocument.PrivacyPolicy to "隐私政策",
                LegalDocument.ChildrenPrivacyPolicy to "儿童隐私政策",
                LegalDocument.PersonalInfoCollection to "个人信息收集清单",
                LegalDocument.ThirdPartyInfoCollection to "第三方信息收集清单",
                LegalDocument.ThirdPartySdkSharing to "第三方 SDK 共享清单",
                LegalDocument.CollectedPersonalInfo to "已收集个人信息清单",
            )
            documents.forEachIndexed { index, (document, expectedTitle) ->
                assertEquals("Document title contract", expectedTitle, document.title)
                val expectedBody = context.assets.open("legal/${document.assetFile}")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                assertEquals("Every asset clearly marks its draft status on its first line",
                    "【开发阶段草稿｜正式发布前需审核完善】", expectedBody.lineSequence().first())
                assertTrue("${document.assetFile} contains its own title", expectedBody.contains(expectedTitle))
                rule.onNodeWithTag("profile_menu_${index + 2}").performScrollTo()
                    .assertIsDisplayed().assert(hasText(expectedTitle)).performClick()
                rule.onNodeWithTag("legal_document_screen").assertIsDisplayed()
                assertDetailTabsHidden()
                rule.onNodeWithText(expectedTitle).assertIsDisplayed()
                rule.waitUntil(10_000) {
                    rule.onAllNodesWithTag("legal_document_body").fetchSemanticsNodes().isNotEmpty() ||
                        rule.onAllNodesWithTag("legal_document_error").fetchSemanticsNodes().isNotEmpty()
                }
                rule.onNodeWithTag("legal_document_error").assertDoesNotExist()
                rule.onNodeWithTag("legal_document_body").assertTextEquals(expectedBody)
                if (document == LegalDocument.PrivacyPolicy) capture("legal-document.png")
                val (end, maximum) = scrollToEnd("legal_document_scroll")
                rule.onNodeWithTag("legal_document_body").assertTextEquals(expectedBody)
                note("document=${document.id} title=$expectedTitle asset=${document.assetFile} " +
                    "utf8Exact=true chars=${expectedBody.length} scroll=$end/$maximum")
                returnToProfile()
            }
            note("legalDocuments=7/7 correct entry, title, complete UTF-8 body, bottom reachable, back restores Profile; PASS")

            fun assertPlaceholder(entry: SemanticsNodeInteraction, title: String) {
                entry.performScrollTo().performClick()
                rule.onNodeWithTag("profile_placeholder_dialog").assertIsDisplayed()
                rule.onNode(hasText(title) and hasAnyAncestor(hasTestTag("profile_placeholder_dialog")))
                    .assertIsDisplayed()
                rule.onNodeWithText("功能暂未开放").assertIsDisplayed()
                rule.onNodeWithText("当前仅展示入口，相关功能尚未接入。").assertIsDisplayed()
                rule.onNodeWithText("我知道了").performClick()
                rule.onNodeWithTag("profile_placeholder_dialog").assertDoesNotExist()
                assertTabsFixed()
                note("placeholder=$title opened, explicit, closed; Profile retained")
            }
            assertPlaceholder(rule.onNodeWithTag("profile_login"), "立即登录")
            assertPlaceholder(rule.onNode(hasText("开通会员") and hasClickAction()), "开通会员")
            rule.onNodeWithTag("profile_menu_1").performScrollTo().performClick()
            rule.onNodeWithTag("privacy_revoke_dialog").assertIsDisplayed()
            rule.onNodeWithTag("privacy_revoke_cancel").performClick()
            rule.onNodeWithTag("privacy_revoke_dialog").assertDoesNotExist()
            assertTabsFixed()

            val (profileEnd, profileMaximum) = scrollToEnd("profile_scroll")
            assertTrue("Profile content requires and supports scrolling", profileMaximum > 0f)
            rule.onNodeWithTag("profile_setting_3").assertIsDisplayed().performClick()
            rule.onNodeWithTag("profile_contact_dialog").assertIsDisplayed()
            rule.onNode(hasText("联系我们") and hasAnyAncestor(hasTestTag("profile_contact_dialog")))
                .assertIsDisplayed()
            rule.onNodeWithText("客服在线时间：08:00-20:00").assertIsDisplayed()
            rule.onNodeWithText("联系QQ：594841675").assertIsDisplayed()
            rule.onNodeWithText("我知道了").performClick()
            rule.onNodeWithTag("profile_contact_dialog").assertDoesNotExist()
            assertTabsFixed()
            rule.onNodeWithTag("profile_login").performScrollTo().assertIsDisplayed()
            assertTabsFixed()
            note("profileScroll=$profileEnd/$profileMaximum and back to login; tabs fixed; contact content/close PASS")

            tab("逗宠").performClick()
            rule.onNodeWithTag("play_list").assertIsDisplayed()
            tab("逗宠").assertIsSelected()
            rule.onNodeWithTag("home_actions").assertDoesNotExist()
            tab("首页").performClick()
            rule.onNodeWithTag("home_record").assertIsDisplayed()
            tab("首页").assertIsSelected()
            tab("我的").performClick()
            assertProfile()
            assertEquals("The complete flow preserves the user's microphone grant", initialMicrophonePermission,
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
            note("threeTabs=Profile -> Play -> Home -> Profile; PASS")
            note("PASS: B2.2 one-pass acceptance complete; originals=permission-details.png,legal-document.png")
        } catch (failure: Throwable) {
            note("FAIL: ${failure.javaClass.simpleName}: ${failure.message}")
            throw failure
        }
    }
}
