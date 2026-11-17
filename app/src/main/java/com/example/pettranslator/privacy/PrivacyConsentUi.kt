package com.example.pettranslator.privacy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
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
    startupBackground: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(ConsentBackground))
    },
    content: @Composable () -> Unit,
) {
    if (state.phase == ConsentPhase.Granted) {
        content()
        return
    }

    var documentId by rememberSaveable { mutableStateOf<String?>(null) }
    val canReadDocument = state.phase == ConsentPhase.Required || state.phase == ConsentPhase.Denied
    if (canReadDocument && documentId != null) {
        Box(Modifier.fillMaxSize().background(ConsentBackground).safeDrawingPadding()) {
            LegalDocumentScreen(documentId, onBack = { documentId = null })
        }
        // Registered only while reading, so system Back returns to the consent dialog first.
        BackHandler(onBack = { documentId = null })
        return
    }

    BackHandler(onBack = { if (!canReadDocument || !state.busy) onExit() })
    if (state.phase == ConsentPhase.Loading) {
        startupBackground()
        return
    }
    if (canReadDocument) {
        Box(Modifier.fillMaxSize()) {
            startupBackground()
            ConsentDialog(state, onAgree, onDecline,
                onReadDocument = { documentId = it }, onExit = onExit)
        }
        return
    }

    Box(Modifier.fillMaxSize().background(ConsentBackground).safeDrawingPadding(),
        contentAlignment = Alignment.Center) {
        when (state.phase) {
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
            // These phases are composed only by the branches above.
            ConsentPhase.Loading, ConsentPhase.Required, ConsentPhase.Denied, ConsentPhase.Granted -> Unit
        }
    }
}

@Composable
private fun ConsentDialog(
    state: ConsentUiState,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onReadDocument: (String) -> Unit,
    onExit: () -> Unit,
) {
    AlertDialog(
        // Back exits through the host callback; outside taps cannot remove the gate.
        onDismissRequest = { if (!state.busy) onExit() },
        properties = DialogProperties(dismissOnBackPress = !state.busy, dismissOnClickOutside = false),
        modifier = Modifier.testTag("privacy_consent_dialog"),
        shape = RoundedCornerShape(26.dp),
        containerColor = Color(0xFFFFF7FC),
        titleContentColor = ConsentInk,
        textContentColor = ConsentMuted,
        title = {
            Text("隐私政策与用户协议", modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center, fontSize = 21.sp, lineHeight = 30.sp,
                fontWeight = FontWeight.Bold)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val body = buildAnnotatedString {
                    append("欢迎使用「猫言狗语翻译官」！\n\n我们非常重视你的个人信息和隐私保护。在使用本应用前，请仔细阅读并充分理解")
                    appendDocumentLink(LegalDocument.UserAgreement, "user_agreement",
                        enabled = !state.busy, onReadDocument = onReadDocument)
                    append("和")
                    appendDocumentLink(LegalDocument.PrivacyPolicy, "privacy_policy",
                        enabled = !state.busy, onReadDocument = onReadDocument)
                    append("，了解我们对个人信息的收集、使用和保护情况。")
                }
                Text(body, modifier = Modifier.fillMaxWidth().testTag("privacy_consent_body"),
                    color = ConsentInk, fontSize = 15.sp, lineHeight = 25.sp)
                Text("当前文档为开发阶段草稿，正式发布前仍需审核。",
                    color = ConsentMuted, fontSize = 12.sp, lineHeight = 20.sp)
                state.error?.let { ConsentError(it) }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val buttonShape = RoundedCornerShape(16.dp)
                val gradientColors = listOf(Color(0xFFBA65B1), Color(0xFF8F64C2))
                    .map { it.copy(alpha = if (state.busy) 0.45f else 1f) }
                Button(onClick = onAgree, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(buttonShape)
                        .background(Brush.horizontalGradient(gradientColors)).testTag("privacy_agree"),
                    shape = buttonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent,
                        contentColor = Color.White, disabledContainerColor = Color.Transparent,
                        disabledContentColor = Color.White.copy(alpha = 0.7f))) {
                    Text("同意并继续", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(onClick = onDecline, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("privacy_decline"),
                    shape = buttonShape,
                    border = BorderStroke(1.dp, ConsentPurple.copy(alpha = if (state.busy) 0.2f else 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White.copy(alpha = 0.65f), contentColor = ConsentPurple)) {
                    Text("不同意并退出", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
    )
}

private fun AnnotatedString.Builder.appendDocumentLink(
    document: LegalDocument,
    tag: String,
    enabled: Boolean,
    onReadDocument: (String) -> Unit,
) {
    val style = SpanStyle(color = ConsentPurple, textDecoration = TextDecoration.Underline,
        fontWeight = FontWeight.Medium)
    if (enabled) {
        // Compose supplies range-bound hit targets and accessibility links for these annotations.
        withLink(LinkAnnotation.Clickable(tag = tag, styles = TextLinkStyles(style = style),
            linkInteractionListener = { onReadDocument(document.id) })) {
            append("《${document.title}》")
        }
    } else {
        withStyle(style) { append("《${document.title}》") }
    }
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
