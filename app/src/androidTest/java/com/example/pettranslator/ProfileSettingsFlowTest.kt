package com.example.pettranslator

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pettranslator.ui.profile.SafeCacheInspector
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** One device pass; the host backs up/restores consent. Cache deletion is not enabled or claimed. */
class ProfileSettingsFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Before fun wake() = wakeTestDevice()

    @Test fun profileSettingsFlow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val folder = File(checkNotNull(context.getExternalFilesDir(null)),
            "b24-settings-evidence").apply { check(isDirectory || mkdirs()) }
        val log = File(folder, "settings-flow.txt").apply { writeText("") }
        val createdFixtures = mutableListOf<File>()
        val tabNames = listOf("首页", "逗宠", "我的")
        val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        var screenshotCount = 0

        fun note(message: String) {
            log.appendText("$message\n")
            android.util.Log.i("B24Settings", message)
        }
        @Suppress("DEPRECATION")
        fun activePackage(): String? {
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
            return try { root.packageName?.toString() } finally { root.recycle() }
        }
        fun capture(name: String) {
            check(name == "about.png" || name == "cache-check.png")
            rule.waitForIdle()
            instrumentation.waitForIdleSync()
            rule.waitUntil(10_000) { activePackage() == context.packageName }
            assertEquals("Only capture the app in the foreground", context.packageName, activePackage())
            val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                File(folder, name).outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                screenshotCount++
                note("screenshot=$name ${bitmap.width}x${bitmap.height} foreground=${context.packageName}")
            } finally { bitmap.recycle() }
        }
        fun tab(title: String) = rule.onNode(tabRole and hasText(title) and hasClickAction())
        fun assertProfile() {
            rule.onNodeWithTag("profile_viewport").assertIsDisplayed()
            rule.onAllNodes(tabRole).assertCountEquals(3)
            tabNames.forEach { tab(it).assertIsDisplayed() }
            tab("我的").assertIsSelected()
            rule.onNodeWithTag("home_actions").assertDoesNotExist()
        }
        fun scrollValue(): Float = rule.onNodeWithTag("profile_scroll")
            .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        fun snapshot(file: File): ByteArray? {
            if (!file.exists()) return null
            assertTrue("Snapshot only a regular file: ${file.name}", file.isFile)
            return file.readBytes()
        }
        fun assertSnapshotsUnchanged(expected: Map<File, ByteArray?>, phase: String) {
            expected.forEach { (file, bytes) ->
                assertEquals("$phase preserves existence: ${file.name}", bytes != null, file.exists())
                if (bytes != null) {
                    assertTrue("$phase preserves a regular file: ${file.name}", file.isFile)
                    assertArrayEquals("$phase preserves exact bytes: ${file.name}", bytes, file.readBytes())
                }
                note("preserved=$phase file=${file.name} exists=${bytes != null} " +
                    "bytes=${bytes?.size ?: 0} sha256=${bytes?.let(::sha256) ?: "absent"}")
            }
        }

        try {
            note("device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT} " +
                "fontScale=${context.resources.configuration.fontScale}")
            note("cachePolicy=retain recording/share/unknown; safeFiles=0 clearedBytes=0; " +
                "本轮实际删除路径未验证/未启用")
            // The host preserves the dedicated consent file. Never seed, edit, or restore it here.
            rule.waitUntil(10_000) {
                rule.onAllNodesWithTag("privacy_consent_dialog").fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithTag("home_record").fetchSemanticsNodes().isNotEmpty()
            }
            if (rule.onAllNodesWithTag("privacy_consent_dialog").fetchSemanticsNodes().isNotEmpty()) {
                rule.onNodeWithTag("privacy_agree").assertIsDisplayed().performClick()
                note("privacyGate=agreed through visible UI; host owns original-file restore")
            } else note("privacyGate=existing consent; no consent action")
            rule.waitUntil(10_000) {
                rule.onAllNodesWithTag("home_record").fetchSemanticsNodes().isNotEmpty()
            }
            tab("我的").performClick()
            assertProfile()
            val fixedTabs = tabNames.associateWith { tab(it).getUnclippedBoundsInRoot() }
            fun assertTabsFixed() {
                assertProfile()
                tabNames.forEach { title ->
                    assertEquals("$title retains its bottom-tab position", fixedTabs.getValue(title),
                        tab(title).getUnclippedBoundsInRoot())
                }
            }

            @Suppress("DEPRECATION")
            val actualVersion = checkNotNull(context.packageManager
                .getPackageInfo(context.packageName, 0).versionName).also { check(it.isNotBlank()) }
            fun assertVersionRow() {
                rule.onNodeWithTag("profile_setting_2").performScrollTo().assertIsDisplayed()
                    .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
                // The read-only row does not merge descendants; its Text children own these values.
                rule.onNode(hasAnyAncestor(hasTestTag("profile_setting_2")) and hasText(actualVersion),
                    useUnmergedTree = true).assertIsDisplayed().assertTextEquals(actualVersion)
                rule.onNode(hasAnyAncestor(hasTestTag("profile_setting_2")) and hasText("当前版本号"),
                    useUnmergedTree = true).assertIsDisplayed().assertTextEquals("当前版本号")
            }
            assertVersionRow()
            note("installedPackageVersion=$actualVersion versionRow=childTextExact readOnly=true")

            rule.onNodeWithTag("profile_setting_0").performScrollTo().assertIsDisplayed()
            rule.waitForIdle()
            val beforeAboutScroll = scrollValue()
            rule.onNodeWithTag("profile_setting_0").performClick()
            rule.onNodeWithTag("about_screen").assertIsDisplayed()
            rule.onAllNodes(tabRole).assertCountEquals(0)
            rule.onNodeWithTag("about_icon").assertIsDisplayed()
            rule.onNodeWithTag("about_version").assertIsDisplayed().assertTextEquals("版本 $actualVersion")
            rule.onNodeWithText(context.getString(R.string.app_name)).assertIsDisplayed()
                .assertTextEquals(context.getString(R.string.app_name))
            rule.onNodeWithText("猫狗心声翻译官是一款萌趣宠物互动应用，提供猫狗声音趣味解读、宠物互动音效等功能，让你与宠物的日常生活多一点欢乐。")
                .assertTextEquals("猫狗心声翻译官是一款萌趣宠物互动应用，提供猫狗声音趣味解读、宠物互动音效等功能，让你与宠物的日常生活多一点欢乐。")
            rule.onNodeWithText("本应用提供娱乐性声音解读，不代表真实动物语言翻译或专业动物行为判断。")
                .assertTextEquals("本应用提供娱乐性声音解读，不代表真实动物语言翻译或专业动物行为判断。")
            capture("about.png")
            rule.onNodeWithTag("about_back").assertIsDisplayed().performClick()
            rule.waitForIdle()
            assertTabsFixed()
            val afterAboutScroll = scrollValue()
            assertTrue("About back restores Profile scroll within one pixel: $beforeAboutScroll/$afterAboutScroll",
                abs(beforeAboutScroll - afterAboutScroll) <= 1f)
            note("about=icon/name/twoParagraphs/versionExact back=Profile selected=我的 " +
                "scrollBefore=$beforeAboutScroll scrollAfter=$afterAboutScroll deltaPx=${abs(beforeAboutScroll - afterAboutScroll)}")

            val protectedState = listOf(
                File(context.filesDir, "datastore/privacy_consent.preferences_pb"),
                File(context.filesDir, "datastore/pet_preferences.preferences_pb"),
            ).associateWith(::snapshot)
            val fixtures = linkedMapOf(
                File(context.cacheDir, "pet_recordings/b24-protected.pcm") to byteArrayOf(0, 1, 127, -128, -1),
                File(context.cacheDir, "share/b24-protected-share.png") to "B24 protected share bytes".toByteArray(),
                File(context.cacheDir, "b24-unknown.tmp") to "B24 unknown cache bytes".toByteArray(),
                File(context.filesDir, "b24-persistent.txt") to "B24 persistent file bytes".toByteArray(),
            )
            fixtures.keys.forEach { file -> check(!file.exists()) { "Never overwrite an existing fixture: $file" } }
            fixtures.forEach { (file, bytes) ->
                check(file.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
                check(file.createNewFile()) { "Never overwrite an existing fixture: $file" }
                createdFixtures += file
                file.writeBytes(bytes)
            }
            note("fixturesCreated=4; cache=recording/share/unknown; persistent=files; " +
                "no deletable safe-cache directory fabricated")
            val unchangedState: Map<File, ByteArray?> = protectedState + fixtures

            fun openCacheInspection(phase: String) {
                rule.onNodeWithTag("profile_setting_1").performScrollTo().assertIsDisplayed().performClick()
                rule.onNodeWithTag("profile_cache_dialog").assertIsDisplayed()
                rule.waitUntil(10_000) {
                    rule.onAllNodesWithTag("profile_cache_loading").fetchSemanticsNodes().isEmpty() &&
                        (rule.onAllNodesWithTag("profile_cache_empty").fetchSemanticsNodes().isNotEmpty() ||
                            rule.onAllNodesWithTag("profile_cache_error").fetchSemanticsNodes().isNotEmpty())
                }
                rule.onNodeWithTag("profile_cache_loading").assertDoesNotExist()
                rule.onNodeWithTag("profile_cache_error").assertDoesNotExist()
                rule.onNodeWithTag("profile_cache_empty").assertIsDisplayed().assertTextEquals("暂无可清理缓存")
                rule.onNodeWithTag("profile_cache_size").assertIsDisplayed().assertTextEquals(
                    "当前可清理缓存约 ${String.format(Locale.getDefault(), "%.2f", 0.0)} MB")
                val report = SafeCacheInspector(listOfNotNull(context.cacheDir, context.externalCacheDir)).inspect()
                assertEquals("No currently safe deletion lifecycle exists", 0L, report.cleanableBytes)
                assertEquals("The real cache inspection completes without errors", 0, report.inspectionErrors)
                assertTrue("Recording, share and unknown fixture files are retained", report.retainedFiles >= 3)
                rule.onNodeWithText("已保留 ${report.retainedFiles} 项缓存。").assertExists()
                rule.onNodeWithTag("profile_cache_cancel").assertIsDisplayed().assertTextEquals("取消")
                rule.onNodeWithTag("profile_cache_close").assertIsDisplayed().assertTextEquals("我知道了")
                rule.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("profile_cache_dialog")))
                    .assertCountEquals(2)
                note("cache=$phase cleanableBytes=${report.cleanableBytes} safeFiles=0 clearedBytes=0 " +
                    "retainedFiles=${report.retainedFiles} retainedBytes=${report.retainedBytes} " +
                    "inspectionErrors=${report.inspectionErrors}; 本轮实际删除路径未验证/未启用")
                assertSnapshotsUnchanged(unchangedState, "$phase-inspection")
            }

            openCacheInspection("first")
            rule.onNodeWithTag("profile_cache_cancel").performClick()
            rule.onNodeWithTag("profile_cache_dialog").assertDoesNotExist()
            assertTabsFixed()
            assertSnapshotsUnchanged(unchangedState, "cancel")
            openCacheInspection("second")
            capture("cache-check.png")
            rule.onNodeWithTag("profile_cache_close").performClick()
            rule.onNodeWithTag("profile_cache_dialog").assertDoesNotExist()
            assertTabsFixed()
            assertSnapshotsUnchanged(unchangedState, "close")
            assertVersionRow()

            rule.onNodeWithTag("profile_setting_3").performScrollTo().assertIsDisplayed().performClick()
            rule.onNodeWithTag("profile_contact_dialog").assertIsDisplayed()
            rule.onNodeWithText("客服在线时间：08:00-20:00").assertIsDisplayed()
                .assertTextEquals("客服在线时间：08:00-20:00")
            rule.onNodeWithText("联系QQ：594841675").assertIsDisplayed().assertTextEquals("联系QQ：594841675")
            rule.onNode(hasText("我知道了") and hasAnyAncestor(hasTestTag("profile_contact_dialog")))
                .performClick()
            rule.onNodeWithTag("profile_contact_dialog").assertDoesNotExist()
            assertTabsFixed()
            assertSnapshotsUnchanged(unchangedState, "settings-complete")
            assertEquals("Retain exactly the two requested original screenshots", 2, screenshotCount)
            note("contact=QQ594841675 time08:00-20:00 closed; threeTabs=positionsUnchanged; " +
                "version=$actualVersion consistent; PASS B2.4; originals=about.png,cache-check.png")
        } catch (failure: Throwable) {
            note("FAIL: ${failure.javaClass.simpleName}: ${failure.message}")
            throw failure
        } finally {
            // Delete only files this test atomically created, never pre-existing files or directories.
            createdFixtures.asReversed().forEach { file ->
                val deleted = !file.exists() || file.delete()
                note("fixtureCleanup=${file.name} deleted=$deleted")
                check(deleted) { "Could not remove this test's own fixture: $file" }
            }
            note("fixtureCleanupComplete; productionCacheDeletion=notEnabled/notVerified; " +
                "safeFiles=0 clearedBytes=0; privacy/currentPet=no direct file writes; host owns consent restore")
        }
    }
}
