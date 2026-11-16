package com.example.pettranslator.privacy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.pettranslator.R
import com.example.pettranslator.ui.profile.LegalDocument
import com.example.pettranslator.ui.profile.LegalDocumentScreen

private val ConsentInk = Color(0xFF554164)
private val ConsentMuted = Color(0xFF8B7C9B)
private val ConsentPurple = Color(0xFFA379CF)
private val ConsentBackground = Brush.verticalGradient(
    listOf(Color(0xFFFDF0F8), Color(0xFFF0E6FC), Color(0xFFEDF2FF)),
)

/** Only the coordinator can grant access; local state holds the document reader mode. */
@Composable
fun PrivacyConsentHost(
    state: ConsentUiState,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onRetryRevocation: () -> Unit,
    onExit: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (state.phase == ConsentPhase.Granted) {
        content()
        return
    }

    var readingPolicy by rememberSaveable { mutableStateOf(false) }
    val canReadPolicy = state.phase == ConsentPhase.Required || state.phase == ConsentPhase.Denied
    if (canReadPolicy && readingPolicy) {
        Box(Modifier.fillMaxSize().background(ConsentBackground).safeDrawingPadding()) {
            LegalDocumentScreen(LegalDocument.PrivacyPolicy.id, onBack = { readingPolicy = false })
        }
        // Registered only while reading, so system Back returns to the consent dialog first.
        BackHandler(onBack = { readingPolicy = false })
        return
    }

    BackHandler(onBack = onExit)
    Box(Modifier.fillMaxSize().background(ConsentBackground).safeDrawingPadding(),
        contentAlignment = Alignment.Center) {
        when (state.phase) {
            ConsentPhase.Loading -> ConsentStatus("正在读取隐私授权状态…", state.error)
            ConsentPhase.Required, ConsentPhase.Denied -> {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.app_name), color = ConsentInk, fontSize = 24.sp,
                        lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                    Text("请先阅读并确认隐私政策", color = ConsentMuted,
                        fontSize = 15.sp, lineHeight = 24.sp)
                }
                ConsentDialog(state, onAgree, onDecline,
                    onReadPolicy = { readingPolicy = true }, onExit = onExit)
            }
            ConsentPhase.Revoking -> Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(24.dp)
                    .verticalScroll(rememberScrollState()).testTag("privacy_revoking"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.busy || state.error == null) CircularProgressIndicator(color = ConsentPurple)
                Text("正在撤销隐私政策授权…", color = ConsentInk, fontSize = 18.sp,
                    lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("已暂停新的相关操作。撤销完成后，应用将退出。", color = ConsentMuted,
                    fontSize = 14.sp, lineHeight = 23.sp)
                state.error?.let { error ->
                    ConsentError(error)
                    Button(onClick = onRetryRevocation, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ConsentPurple)) {
                        Text("重试撤销")
                    }
                }
            }
            ConsentPhase.Revoked -> ConsentStatus("授权已撤销，正在退出应用…", state.error)
            ConsentPhase.Granted -> Unit // Granted content is composed only by the branch above.
        }
    }
}

@Composable
private fun ConsentDialog(
    state: ConsentUiState,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onReadPolicy: () -> Unit,
    onExit: () -> Unit,
) {
    AlertDialog(
        // Back exits through the host callback; outside taps cannot remove the gate.
        onDismissRequest = onExit,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        modifier = Modifier.testTag("privacy_consent_dialog"),
        shape = RoundedCornerShape(26.dp),
        containerColor = Color(0xFFFFF7FC),
        titleContentColor = ConsentInk,
        textContentColor = ConsentMuted,
        title = { Text("隐私政策说明", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("本应用使用麦克风录制宠物叫声，并在本机进行声音分析与趣味解读。请先阅读隐私政策，了解相关数据的使用方式。")
                Text("隐私政策同意与 Android 系统权限授权相互独立。同意隐私政策不会自动授予麦克风等系统权限，使用相关功能时仍需单独授权。")
                Text("当前隐私政策为开发草稿，内容仍需完善。")
                if (state.phase == ConsentPhase.Denied) {
                    Text("你尚未同意隐私政策，暂时无法进入应用。你仍可阅读政策、重新同意，或退出应用。",
                        color = ConsentInk)
                }
                state.error?.let { ConsentError(it) }
                TextButton(onClick = onReadPolicy, enabled = !state.busy,
                    modifier = Modifier.testTag("privacy_read_policy"),
                    colors = ButtonDefaults.textButtonColors(contentColor = ConsentPurple)) {
                    Text("阅读隐私政策")
                }
                TextButton(onClick = onExit, enabled = !state.busy,
                    modifier = Modifier.testTag("privacy_exit"),
                    colors = ButtonDefaults.textButtonColors(contentColor = ConsentMuted)) {
                    Text("退出应用")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAgree, enabled = !state.busy,
                modifier = Modifier.testTag("privacy_agree"),
                colors = ButtonDefaults.textButtonColors(contentColor = ConsentPurple)) {
                Text("同意并继续")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline, enabled = !state.busy,
                modifier = Modifier.testTag("privacy_decline"),
                colors = ButtonDefaults.textButtonColors(contentColor = ConsentMuted)) {
                Text("不同意")
            }
        },
    )
}

@Composable
private fun ConsentStatus(message: String, error: String?) {
    Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator(color = ConsentPurple)
        Text(message, color = ConsentInk, fontSize = 16.sp, lineHeight = 25.sp)
        error?.let { ConsentError(it) }
    }
}

@Composable
private fun ConsentError(error: String) {
    Text(error, modifier = Modifier.fillMaxWidth().testTag("privacy_error"),
        color = ConsentInk, fontSize = 14.sp, lineHeight = 23.sp)
}
