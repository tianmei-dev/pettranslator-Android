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
import com.example.pettranslator.ui.home.*
import com.example.pettranslator.ui.theme.PetTranslatorTheme
import org.junit.*

/** Controlled Compose viewport evidence, not screenshots of different physical devices. */
class HomeResponsiveEvidenceTest {
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
                            Column {
                                HomeRecordActionBar(true, {})
                                Surface { Box(Modifier.fillMaxWidth().height(58.dp)) { Text("测试 Tab 区（58dp）") } }
                            }
                        }) { padding ->
                            Box(Modifier.padding(padding)) { HomeScreen(HomeUiState(ready = true), {}, {}) }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val tags = listOf("home_viewport", "home_hero", "home_section_1", "home_section_2", "home_record")
        val metrics = tags.associateWith { rule.onNodeWithTag(it).getUnclippedBoundsInRoot() }
        val viewport = metrics.getValue("home_viewport")
        val scene = metrics.getValue("home_section_2")
        val pet = metrics.getValue("home_section_1")
        val record = metrics.getValue("home_record")
        val scrolling = scene.bottom > viewport.bottom
        val report = "${width}x$height font=$scale content=${viewport.height.value} pet=${pet.height.value} scene=${scene.height.value} gap=${scene.top.value-pet.bottom.value} sceneToRecord=${record.top.value-scene.bottom.value} scroll=$scrolling"
        println("A162 $report")
        android.util.Log.i("A162", report)
        Assert.assertTrue(record.top >= viewport.bottom)
        if (scale == 1f) Assert.assertFalse("Normal viewport must fit: $report", scrolling)
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "compose-${width}x$height-$scale.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/A162")
        }))
        checkNotNull(resolver.openOutputStream(uri)).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        rule.onNodeWithText("不确定").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("开始录音翻译").assertIsDisplayed()
    }
    @Test fun shortViewport() = verify(360, 720, 1f)
    @Test fun standardViewport() = verify(393, 800, 1f)
    @Test fun tallViewport() = verify(412, 900, 1f)
    @Test fun largeFont() = verify(393, 800, 1.3f)
}
