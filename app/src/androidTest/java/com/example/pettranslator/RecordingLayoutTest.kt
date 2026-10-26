package com.example.pettranslator

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.pettranslator.domain.*
import com.example.pettranslator.ui.recording.*
import com.example.pettranslator.ui.theme.PetTranslatorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Before

class RecordingLayoutTest {
    @Before fun wake() = wakeTestDevice()
    @get:Rule val rule = createComposeRule()
    private fun verify(width: Int, scale: Float) {
        val state = mutableStateOf(RecordingUiState())
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                PetTranslatorTheme {
                    Box(Modifier.width(width.dp)) {
                        RecordingScreen(PetType.Cat, PetScene.Door, state.value, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        rule.onNodeWithText("门口或窗边").assertExists()
        rule.onNodeWithContentDescription("猫咪 · 门口或窗边").assertExists()
        rule.onNodeWithTag("recording_waveform_idle").assertHeightIsEqualTo(112.dp)
        rule.onNodeWithContentDescription("开始录音").assertIsDisplayed()
        rule.runOnIdle { state.value = RecordingUiState(RecordingPhase.RECORDING, 5000) }
        rule.onNodeWithContentDescription("停止录音").assertIsDisplayed()
        rule.onNodeWithContentDescription("取消录音").assertIsDisplayed()
        rule.runOnIdle { state.value = RecordingUiState(RecordingPhase.SUCCESS, 5000) }
        rule.onNodeWithText("趣味解读功能即将接入").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("重新录音").assertIsDisplayed()
        rule.runOnIdle { state.value = RecordingUiState(permissionBlocked = true, message = "需要麦克风权限") }
        rule.onNodeWithText("前往应用设置").assertIsDisplayed()
    }
    @Test fun smallLargeFont() = verify(320, 2f)
    @Test fun regularPhone() = verify(360, 1f)
}
