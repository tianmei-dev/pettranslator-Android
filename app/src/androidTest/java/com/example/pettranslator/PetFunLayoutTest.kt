package com.example.pettranslator

import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pettranslator.ui.play.PlayScreen
import com.example.pettranslator.ui.theme.PetTranslatorTheme
import org.junit.*

/** Controlled Compose viewport evidence, not screenshots of different physical devices. */
class PetFunLayoutTest {
    @get:Rule val rule = createComposeRule()
    @Before fun wake() = wakeTestDevice()
    private fun verify(width: Int, height: Int, scale: Float) {
        rule.setContent {
            val physical = LocalDensity.current
            BoxWithConstraints {
                val density = minOf(physical.density, constraints.maxWidth.toFloat()/width, constraints.maxHeight.toFloat()/height)
                CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                    PetTranslatorTheme {
                        Scaffold(Modifier.size(width.dp, height.dp), bottomBar = {
                            Surface { Box(Modifier.fillMaxWidth().height(58.dp)) { Text("测试 Tab 区（58dp）") } }
                        }) { padding -> Box(Modifier.padding(padding)) { PlayScreen() } }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("逗宠乐园").assertIsDisplayed()
        rule.onNodeWithText("开始说话").assertIsDisplayed()
        val art = rule.onNodeWithTag("fun_header_art").getUnclippedBoundsInRoot()
        val title = rule.onNodeWithTag("fun_header_text").getUnclippedBoundsInRoot()
        val card = rule.onNodeWithTag("fun_voice_card").getUnclippedBoundsInRoot()
        Assert.assertTrue("Art must not cover banner text", art.left >= title.right)
        Assert.assertTrue("Cloud must overlap card edge", art.bottom > card.top)
        Assert.assertTrue("Header text must stay above card", title.bottom <= card.top)
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "play-compose-${width}x$height-$scale.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/B11")
        }))
        checkNotNull(resolver.openOutputStream(uri)).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        rule.onNodeWithTag("play_list").performScrollToNode(hasTestTag("fun_sound_cat_meal"))
        rule.onNodeWithText("开饭啦").assertIsDisplayed()
    }
    @Test fun shortViewport() = verify(360, 720, 1f)
    @Test fun standardViewport() = verify(393, 800, 1f)
    @Test fun tallViewport() = verify(412, 900, 1f)
    @Test fun largeFont() = verify(393, 800, 1.3f)
}
