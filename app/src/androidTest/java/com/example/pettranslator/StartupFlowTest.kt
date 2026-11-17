package com.example.pettranslator

import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pettranslator.privacy.AndroidPrivacyConsent
import com.example.pettranslator.privacy.ConsentPhase
import com.example.pettranslator.privacy.PrivacyGate
import com.example.pettranslator.ui.profile.LegalDocument
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** One device pass, starting from the host-prepared real Denied decision. */
class StartupFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Before fun wake() = wakeTestDevice()

    @Test fun deniedConsentLinksStartupAndSameProcessRestoration() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val consent = AndroidPrivacyConsent.get(context)
        val folder = File(checkNotNull(context.getExternalFilesDir(null)),
            "b231-startup-evidence").apply { check(isDirectory || mkdirs()) }
        val log = File(folder, "startup-flow.txt").apply { writeText("") }
        val processId = Process.myPid()
        val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

        fun note(message: String) {
            log.appendText("${SystemClock.elapsedRealtime()} $message\n")
            android.util.Log.i("B231StartupFlow", message)
        }
        @Suppress("DEPRECATION")
        fun activePackage(): String? {
            val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
            return try { root.packageName?.toString() } finally { root.recycle() }
        }
        fun shell(command: String): String {
            val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
            return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                .bufferedReader().use { it.readText() }
        }
        fun awaitTag(tag: String) {
            rule.waitUntil(8_000) {
                rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag(tag).assertIsDisplayed()
        }
        fun awaitAppForeground() {
            rule.waitUntil(8_000) { activePackage() == context.packageName }
        }
        fun capture(name: String) {
            rule.waitForIdle()
            instrumentation.waitForIdleSync()
            awaitAppForeground()
            assertEquals("Capture only the app foreground", context.packageName, activePackage())
            val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                assertEquals("Foreground remains the app during capture", context.packageName, activePackage())
                File(folder, name).outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                note("screenshot=$name ${bitmap.width}x${bitmap.height}")
            } finally {
                bitmap.recycle()
            }
        }
        fun assertConsentStillRequired() {
            awaitTag("privacy_consent_dialog")
            assertEquals("Reading documents never changes the persisted refusal",
                ConsentPhase.Denied, consent.state.value.phase)
            assertFalse("The process privacy gate stays closed", PrivacyGate.isGranted())
            rule.onNodeWithTag("home_record").assertDoesNotExist()
            rule.onNodeWithTag("startup_screen").assertDoesNotExist()
        }
        fun profileTab() = rule.onNode(tabRole and hasText("我的") and hasClickAction())
        fun assertProfileRetained() {
            awaitTag("profile_viewport")
            profileTab().assertIsDisplayed().assertIsSelected()
            rule.onNodeWithTag("startup_screen").assertDoesNotExist()
            rule.onNodeWithTag("privacy_consent_dialog").assertDoesNotExist()
            rule.onNodeWithTag("home_record").assertDoesNotExist()
            assertEquals(ConsentPhase.Granted, consent.state.value.phase)
            assertTrue(PrivacyGate.isGranted())
            assertEquals("Background/restoration stays in the original process", processId, Process.myPid())
        }
        fun openExactLink(tag: String, expectedLabel: String) {
            val body = rule.onNodeWithTag("privacy_consent_body").assertIsDisplayed()
            val text = body.fetchSemanticsNode().config[SemanticsProperties.Text].single()
            val links = text.getLinkAnnotations(0, text.length)
            assertEquals("Only the two named legal ranges are links", 2, links.size)
            assertEquals(setOf("user_agreement", "privacy_policy"), links.map {
                (it.item as LinkAnnotation.Clickable).tag
            }.toSet())
            val range = links.single { (it.item as LinkAnnotation.Clickable).tag == tag }
            assertEquals("The hit target is exactly the legal title", expectedLabel,
                text.text.substring(range.start, range.end))
            val layouts = mutableListOf<TextLayoutResult>()
            body.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayout ->
                assertTrue("Consent body exposes its real rendered text layout", getLayout(layouts))
            }
            val layout = layouts.single()
            val tap = layout.getBoundingBox(range.start + 1).center
            note("link=$tag label=$expectedLabel range=${range.start}..${range.end} tap=$tap")
            // Physical input at a rendered glyph proves range hit testing, not a listener shortcut.
            body.performTouchInput { click(tap) }
        }

        try {
            note("device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT} " +
                "fontScale=${context.resources.configuration.fontScale} pid=$processId folder=$folder")
            assertConsentStillRequired()
            rule.onNodeWithText("隐私政策与用户协议").assertIsDisplayed()
            rule.onNodeWithTag("privacy_agree").assertTextEquals("同意并继续")
            rule.onNodeWithTag("privacy_decline").assertTextEquals("不同意并退出")
            capture("consent-dialog.png")

            listOf(
                Triple("user_agreement", "《用户协议》", LegalDocument.UserAgreement),
                Triple("privacy_policy", "《隐私政策》", LegalDocument.PrivacyPolicy),
            ).forEach { (tag, label, document) ->
                val expectedBody = context.assets.open("legal/${document.assetFile}")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                openExactLink(tag, label)
                awaitTag("legal_document_screen")
                rule.onNodeWithText(document.title).assertIsDisplayed()
                rule.waitUntil(8_000) {
                    rule.onAllNodesWithTag("legal_document_body").fetchSemanticsNodes().isNotEmpty() ||
                        rule.onAllNodesWithTag("legal_document_error").fetchSemanticsNodes().isNotEmpty()
                }
                rule.onNodeWithTag("legal_document_error").assertDoesNotExist()
                rule.onNodeWithTag("legal_document_body").assertTextEquals(expectedBody)
                assertFalse(PrivacyGate.isGranted())
                rule.onNodeWithTag("home_record").assertDoesNotExist()
                if (document == LegalDocument.PrivacyPolicy) capture("privacy-policy.png")
                note("document=${document.id} title=${document.title} asset=${document.assetFile} " +
                    "utf8Exact=true chars=${expectedBody.length} gateGranted=false")
                rule.onNodeWithTag("profile_detail_back").assertIsDisplayed().performClick()
                assertConsentStillRequired()
            }
            note("legalLinks=2/2 physical glyph taps, exact assets, Back restored consent; PASS")

            rule.onNodeWithTag("privacy_agree").assertIsEnabled().performClick()
            awaitTag("startup_screen")
            rule.onNodeWithTag("startup_app_icon").assertIsDisplayed()
            rule.onNodeWithText("正在进入…").assertIsDisplayed()
            rule.onNodeWithTag("privacy_consent_dialog").assertDoesNotExist()
            assertEquals(ConsentPhase.Granted, consent.state.value.phase)
            assertTrue(PrivacyGate.isGranted())
            capture("startup-after-agree.png")
            awaitTag("home_record")
            rule.onNodeWithTag("startup_screen").assertDoesNotExist()
            assertEquals(ConsentPhase.Granted, consent.state.value.phase)
            assertTrue(PrivacyGate.isGranted())
            note("agree=persisted Granted; launcher icon and entering text observed; home reached; PASS")

            profileTab().performClick()
            assertProfileRetained()
            note("backgroundCommand=${shell("input keyevent KEYCODE_HOME").trim()}")
            rule.waitUntil(8_000) {
                val foreground = activePackage()
                foreground != null && foreground != context.packageName
            }
            note("background=actual foreground package ${activePackage()}")
            note("resumeCommand=${shell("am start -W -n com.example.pettranslator/.MainActivity").trim()}")
            awaitAppForeground()
            assertProfileRetained()
            note("resume=same process, Profile retained, selected 我的, no startup or consent; PASS")

            rule.activityRule.scenario.recreate()
            awaitAppForeground()
            assertProfileRetained()
            note("recreate=Profile retained, selected 我的, no startup or consent; PASS")
            note("PASS: B2.3.1 single-pass startup flow; originals=" +
                "consent-dialog.png,privacy-policy.png,startup-after-agree.png")
        } catch (failure: Throwable) {
            note("FAIL: ${failure.javaClass.simpleName}: ${failure.message}")
            throw failure
        }
    }
}
