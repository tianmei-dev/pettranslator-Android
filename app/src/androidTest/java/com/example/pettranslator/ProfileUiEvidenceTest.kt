package com.example.pettranslator

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pettranslator.ui.profile.ProfileScreen
import com.example.pettranslator.ui.theme.PetTranslatorTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** One real-device pass; retain original screen PNGs and concise measured evidence. */
class ProfileUiEvidenceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Before fun wake() = wakeTestDevice()

    @Test fun captureProfileUiEvidence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val folder = File(context.getExternalFilesDir(null), "b21-profile-evidence").apply { mkdirs() }
        val log = File(folder, "profile-ui.txt").apply { writeText("") }
        fun note(message: String) {
            log.appendText("$message\n")
            android.util.Log.i("B21Profile", message)
        }
        fun capture(name: String) {
            rule.waitForIdle()
            instrumentation.waitForIdleSync()
            val activePackage = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
            if (activePackage != null) {
                assertEquals("Capture only the actual app foreground", context.packageName, activePackage)
            }
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
        fun tab(title: String) = rule.onNode(hasText(title) and hasClickAction())
        fun assertCompleteText(title: String) {
            rule.onNodeWithText(title, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    action(layouts)
                    assertTrue("Text layout is available for $title", layouts.isNotEmpty())
                    layouts.forEach { layout ->
                        val ellipsized = (0 until layout.lineCount).any(layout::isLineEllipsized)
                        note("text=$title lines=${layout.lineCount} size=${layout.size} " +
                            "overflow=${layout.hasVisualOverflow} ellipsized=$ellipsized")
                        assertFalse("Complete text must fit: $title", layout.hasVisualOverflow || ellipsized)
                    }
                }
        }

        note("device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT} " +
            "fontScale=${context.resources.configuration.fontScale}")
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("home_record").fetchSemanticsNodes().isNotEmpty()
        }
        tab("我的").performClick()
        rule.onNodeWithTag("profile_viewport").assertIsDisplayed()
        rule.onNodeWithTag("home_actions").assertDoesNotExist()
        rule.onNodeWithTag("profile_header_artwork").assertIsDisplayed()
        rule.onNodeWithTag("profile_avatar", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("profile_login").assert(hasClickAction()).assertIsDisplayed()
        listOf("立即登录", "未登录", "会员中心", "未开通会员", "会员有效期：—").forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
            assertCompleteText(it)
        }
        rule.onNodeWithTag("profile_vip").assertIsDisplayed()
        rule.onNode(hasText("开通会员") and hasClickAction()).assertIsDisplayed()
        rule.onNodeWithTag("profile_scroll")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)).assertCountEquals(1)
        rule.onNodeWithTag("profile_privacy_card").assertExists()
        rule.onNodeWithTag("profile_settings_card").assertExists()

        val tabNames = listOf("首页", "逗宠", "我的")
        val tabBounds: Map<String, DpRect> = tabNames.associateWith {
            tab(it).assertIsDisplayed().getUnclippedBoundsInRoot()
        }
        val viewport = rule.onNodeWithTag("profile_viewport").getUnclippedBoundsInRoot()
        tabBounds.forEach { (title, bounds) ->
            assertTrue("$title tab stays below the scrolling viewport", bounds.top.value >= viewport.bottom.value - .5f)
        }
        note("viewport=$viewport fixedTabs=$tabBounds")
        fun assertFixedTabs() {
            tabNames.forEach { title ->
                assertEquals("$title tab must remain fixed while Profile scrolls", tabBounds.getValue(title),
                    tab(title).assertIsDisplayed().getUnclippedBoundsInRoot())
            }
        }
        capture("profile-top.png")

        val privacyTitles = listOf(
            "系统权限管理", "隐私政策授权撤销", "用户协议", "隐私政策", "儿童隐私政策",
            "个人信息收集清单", "第三方信息收集清单", "第三方 SDK 共享清单", "已收集个人信息清单",
        )
        privacyTitles.forEachIndexed { index, title ->
            rule.onNodeWithTag("profile_menu_$index").performScrollTo()
                .assertIsDisplayed().assert(hasText(title)).assert(hasClickAction())
            assertCompleteText(title)
            assertFixedTabs()
            note("reachable=profile_menu_$index $title")
        }
        capture("profile-privacy.png")

        val settingTitles = listOf("关于我们", "清除缓存", "当前版本号", "联系我们")
        @Suppress("DEPRECATION")
        val installedVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        assertFalse("The installed package exposes a real version", installedVersion.isNullOrBlank())
        settingTitles.forEachIndexed { index, title ->
            val row = rule.onNodeWithTag("profile_setting_$index").performScrollTo()
                .assertIsDisplayed().assert(hasText(title) or hasAnyDescendant(hasText(title)))
            row.assert(if (index == 2) !hasClickAction() else hasClickAction())
            assertCompleteText(title)
            if (index == 2) {
                row.assert(hasAnyDescendant(hasText(checkNotNull(installedVersion), substring = true)))
                note("displayedVersion=$installedVersion source=PackageManager")
            }
            assertFixedTabs()
            note("reachable=profile_setting_$index $title")
        }
        capture("profile-bottom.png")
        rule.onNodeWithTag("profile_setting_3").performClick()
        rule.onNodeWithTag("profile_contact_dialog").assertIsDisplayed()
        rule.onNode(hasText("联系我们") and hasAnyAncestor(hasTestTag("profile_contact_dialog")))
            .assertIsDisplayed()
        rule.onNodeWithText("客服在线时间：08:00-20:00").assertIsDisplayed()
        rule.onNodeWithText("联系QQ：594841675").assertIsDisplayed()
        capture("profile-contact.png")
        rule.onNodeWithText("我知道了").performClick()
        rule.onNodeWithTag("profile_contact_dialog").assertDoesNotExist()
        assertFixedTabs()
        note("contactDialog=opened, exact content verified, closed")

        fun assertPlaceholder(tag: String) {
            rule.onNodeWithTag(tag).performScrollTo().performClick()
            rule.onNodeWithTag("profile_placeholder_dialog").assertIsDisplayed()
            rule.onNodeWithText("功能暂未开放").assertIsDisplayed()
            rule.onNodeWithText("当前仅展示入口，相关功能尚未接入。").assertIsDisplayed()
            rule.onNodeWithText("我知道了").performClick()
            rule.onNodeWithTag("profile_placeholder_dialog").assertDoesNotExist()
            rule.onNodeWithTag("profile_viewport").assertIsDisplayed()
            assertFixedTabs()
            note("placeholder=$tag explicit, dismissible, Profile remains active")
        }
        rule.onNodeWithTag("profile_menu_1").performScrollTo().performClick()
        rule.onNodeWithTag("privacy_revoke_dialog").assertIsDisplayed()
        rule.onNodeWithTag("privacy_revoke_cancel").performClick()
        rule.onNodeWithTag("privacy_revoke_dialog").assertDoesNotExist()
        assertFixedTabs()
        val cacheSentinel = File.createTempFile("b21-profile-", ".sentinel", context.cacheDir)
        try {
            cacheSentinel.writeText("B2.4 unknown cache must remain untouched")
            rule.onNodeWithTag("profile_setting_1").performScrollTo().performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithTag("profile_cache_empty").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("profile_cache_cancel").performClick()
            assertTrue("Clear-cache UI must not delete real cache files", cacheSentinel.isFile)
            assertEquals("B2.4 unknown cache must remain untouched", cacheSentinel.readText())
            note("cacheInspection=no eligible deletions; unknown sentinel preserved")
        } finally {
            cacheSentinel.delete()
        }

        tab("首页").performClick()
        rule.onNodeWithTag("home_record").assertIsDisplayed()
        tab("我的").performClick()
        rule.onNodeWithTag("profile_viewport").assertIsDisplayed()
        rule.onNodeWithTag("home_actions").assertDoesNotExist()
        assertFixedTabs()
        note("navigation=Profile -> Home (home_record visible) -> Profile; PASS")
    }
}

/** Controlled viewport on the same device; this is not evidence from a second physical phone. */
class ProfileLargeFontEvidenceTest {
    @get:Rule val rule = createComposeRule()
    @Before fun wake() = wakeTestDevice()

    @Test fun controlled320dpFont2RemainsReachable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val folder = File(context.getExternalFilesDir(null), "b21-profile-evidence").apply { mkdirs() }
        val log = File(folder, "profile-controlled-320-font2.txt").apply {
            writeText("Controlled Compose viewport=320x740dp fontScale=2; " +
                "same physical device=${Build.MODEL}; footer=58dp placeholder, not production navigation.\n")
        }
        fun note(message: String) {
            log.appendText("$message\n")
            android.util.Log.i("B21ProfileControlled", message)
        }
        rule.setContent {
            val physicalDensity = LocalDensity.current
            BoxWithConstraints {
                val targetDensity = minOf(physicalDensity.density,
                    constraints.maxWidth / 320f, constraints.maxHeight / 740f)
                CompositionLocalProvider(LocalDensity provides Density(targetDensity, 2f)) {
                    PetTranslatorTheme {
                        Column(Modifier.size(320.dp, 740.dp).align(Alignment.Center)) {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                ProfileScreen(onBack = {})
                            }
                            Box(Modifier.fillMaxWidth().height(58.dp).background(Color.White)
                                .testTag("profile_controlled_footer"), contentAlignment = Alignment.Center) {
                                Text("底部导航占位", fontSize = 12.sp, lineHeight = 16.sp)
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val footer = rule.onNodeWithTag("profile_controlled_footer")
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        note("initialFooter=$footer")
        fun assertFooterFixed() {
            assertEquals("The controlled footer stays fixed while Profile scrolls", footer,
                rule.onNodeWithTag("profile_controlled_footer").assertIsDisplayed().getUnclippedBoundsInRoot())
        }
        fun verifyRow(tag: String, title: String) {
            rule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                .assert(hasText(title) or hasAnyDescendant(hasText(title)))
            rule.onNodeWithText(title, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    action(layouts)
                    assertTrue("Text layout is available for $title", layouts.isNotEmpty())
                    layouts.forEach { layout ->
                        val ellipsized = (0 until layout.lineCount).any(layout::isLineEllipsized)
                        note("$tag=$title lines=${layout.lineCount} size=${layout.size} " +
                            "overflow=${layout.hasVisualOverflow} ellipsized=$ellipsized")
                        assertFalse("320dp / fontScale 2 must show complete text: $title",
                            layout.hasVisualOverflow || ellipsized)
                    }
                }
            assertFooterFixed()
        }
        listOf("系统权限管理", "隐私政策授权撤销", "用户协议", "隐私政策", "儿童隐私政策",
            "个人信息收集清单", "第三方信息收集清单", "第三方 SDK 共享清单", "已收集个人信息清单")
            .forEachIndexed { index, title -> verifyRow("profile_menu_$index", title) }
        listOf("关于我们", "清除缓存", "当前版本号", "联系我们")
            .forEachIndexed { index, title -> verifyRow("profile_setting_$index", title) }

        rule.waitForIdle()
        instrumentation.waitForIdleSync()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(folder, "controlled-320-font2-bottom.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            note("screenshot=controlled-320-font2-bottom.png ${bitmap.width}x${bitmap.height}; " +
                "controlled viewport on the same physical device")
        } finally {
            bitmap.recycle()
        }

        rule.onNodeWithTag("profile_setting_3").performClick()
        rule.onNodeWithTag("profile_contact_dialog").assertIsDisplayed()
        rule.onNode(hasText("联系我们") and hasAnyAncestor(hasTestTag("profile_contact_dialog")))
            .assertIsDisplayed()
        listOf("客服在线时间：08:00-20:00", "联系QQ：594841675").forEach { text ->
            rule.onNodeWithText(text).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    action(layouts)
                    assertTrue("Dialog exposes its complete text layout", layouts.isNotEmpty())
                    assertFalse("Contact text must fit at fontScale 2: $text", layouts.any { layout ->
                        layout.hasVisualOverflow || (0 until layout.lineCount).any(layout::isLineEllipsized)
                    })
                }
        }
        rule.onNodeWithText("我知道了").assertIsDisplayed().performClick()
        rule.onNodeWithTag("profile_contact_dialog").assertDoesNotExist()
        assertFooterFixed()
        note("PASS: all 9 privacy + 4 setting rows reached without text overflow; contact dialog complete and dismissible")
    }
}
