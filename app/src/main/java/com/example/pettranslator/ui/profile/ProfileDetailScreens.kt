package com.example.pettranslator.ui.profile

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val DetailInk = Color(0xFF554164)
private val DetailMuted = Color(0xFF8B7C9B)
private val DetailPurple = Color(0xFFA379CF)
private val DetailBackground = Brush.verticalGradient(
    listOf(Color(0xFFF0E6FC), Color(0xFFFDF0F8), Color(0xFFEDF2FF)))

private data class PermissionDescription(val permission: String, val title: String, val purpose: String, val tag: String)

private fun appPermissions(): List<PermissionDescription> = buildList {
    add(PermissionDescription(Manifest.permission.RECORD_AUDIO, "麦克风",
        "用于录制宠物叫声，支持本机声音分析和趣味解读。", "permission_microphone_status"))
    // The manifest and result exporter request storage access only on Android 8–9.
    if (Build.VERSION.SDK_INT <= 28) {
        add(PermissionDescription(Manifest.permission.WRITE_EXTERNAL_STORAGE, "存储",
            "用于将结果卡片图片保存到系统相册，仅 Android 8–9 需要此项授权。", "permission_storage_status"))
    }
}

@Composable
fun PermissionDetailsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val permissions = remember { appPermissions() }
    fun readGrants() = permissions.associate { item ->
        item.permission to (ContextCompat.checkSelfPermission(context, item.permission) == PackageManager.PERMISSION_GRANTED)
    }
    var grants by remember(context) { mutableStateOf(readGrants()) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) grants = readGrants()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize().background(DetailBackground).testTag("permission_details")) {
        DetailHeader("权限详情", onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("守护你的隐私", fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = DetailInk)
            Text("以下权限用于支持相应功能，你可以随时前往系统设置管理授权。",
                fontSize = 14.sp, lineHeight = 23.sp, color = DetailMuted)
            permissions.forEach { item ->
                Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFFFFFAFE),
                    tonalElevation = 0.dp, shadowElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(item.title, color = DetailInk, fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
                        Text(item.purpose, color = DetailMuted, fontSize = 14.sp, lineHeight = 23.sp)
                        Text(if (grants[item.permission] == true) "已授权" else "未授权",
                            modifier = Modifier.testTag(item.tag), fontSize = 14.sp, lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold, color = DetailPurple)
                    }
                }
            }
            Text("此页面仅展示授权状态，不会主动申请权限。系统麦克风开关也可能影响录音可用性。",
                color = DetailMuted, fontSize = 12.sp, lineHeight = 20.sp)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            settingsError?.let { Text(it, color = DetailInk, fontSize = 13.sp, lineHeight = 21.sp,
                modifier = Modifier.testTag("permission_settings_error")) }
            Button(onClick = { settingsError = openAppSettings(context) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("manage_system_permissions"),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DetailPurple),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
                Text("管理系统权限", fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun openAppSettings(context: Context): String? = try {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    null
} catch (_: ActivityNotFoundException) {
    "无法打开系统设置，请在手机设置中找到本应用并管理权限。"
} catch (_: SecurityException) {
    "系统暂不允许打开设置，请在手机设置中找到本应用并管理权限。"
}

private sealed interface DocumentContent {
    data object Loading : DocumentContent
    data class Ready(val text: String) : DocumentContent
    data object Failed : DocumentContent
}

@Composable
fun LegalDocumentScreen(documentId: String?, onBack: () -> Unit) {
    // A new document gets fresh loading and scroll state; never reuse a previous document's text.
    key(documentId) {
        val document = LegalDocument.fromId(documentId)
        val context = LocalContext.current.applicationContext
        val content by produceState<DocumentContent>(DocumentContent.Loading, document, context) {
            value = if (document == null) DocumentContent.Failed else try {
                val text = withContext(Dispatchers.IO) {
                    context.assets.open("legal/${document.assetFile}").bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
                if (text.isBlank()) DocumentContent.Failed else DocumentContent.Ready(text)
            } catch (_: IOException) {
                DocumentContent.Failed
            } catch (_: SecurityException) {
                DocumentContent.Failed
            }
        }
        Column(Modifier.fillMaxSize().background(DetailBackground).testTag("legal_document_screen")) {
            DetailHeader(document?.title ?: "文档阅读", onBack)
            when (val current = content) {
                DocumentContent.Loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DetailPurple, modifier = Modifier.testTag("legal_document_loading"))
                }
                DocumentContent.Failed -> Column(Modifier.fillMaxWidth().padding(20.dp)
                    .testTag("legal_document_error"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("文档读取失败，请返回后重试。", color = DetailInk, fontSize = 16.sp, lineHeight = 25.sp)
                    TextButton(onClick = onBack) { Text("返回", color = DetailPurple) }
                }
                is DocumentContent.Ready -> Column(Modifier.fillMaxWidth().weight(1f)
                    .verticalScroll(rememberScrollState()).testTag("legal_document_scroll")
                    .padding(horizontal = 20.dp, vertical = 16.dp)) {
                    SelectionContainer {
                        Text(current.text, modifier = Modifier.fillMaxWidth().testTag("legal_document_body"),
                            color = DetailInk, fontSize = 15.sp, lineHeight = 26.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onBack, modifier = Modifier.size(48.dp).testTag("profile_detail_back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = DetailInk)
        }
        Text(title, modifier = Modifier.weight(1f).padding(end = 12.dp), color = DetailInk,
            fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
    }
}
