package com.example.pettranslator.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ProfileCacheDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val inspection by produceState<CacheInspection?>(null, context) {
        value = withContext(Dispatchers.IO) {
            try { SafeCacheInspector(listOfNotNull(context.cacheDir, context.externalCacheDir)).inspect() }
            catch (_: IOException) { CacheInspection(inspectionErrors = 1) }
            catch (_: SecurityException) { CacheInspection(inspectionErrors = 1) }
        }
    }
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("profile_cache_dialog"),
        shape = RoundedCornerShape(26.dp), containerColor = Color(0xFFFFF7FC),
        titleContentColor = Color(0xFF554164), textContentColor = Color(0xFF8B7C9B),
        title = { Text("清除缓存", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val current = inspection
                if (current == null) {
                    Text("正在检查缓存…", modifier = Modifier.testTag("profile_cache_loading"))
                } else if (current.inspectionErrors > 0) {
                    Text("缓存检查未完成，未删除任何数据，请稍后重试。",
                        modifier = Modifier.testTag("profile_cache_error"))
                } else {
                    Text("当前可清理缓存约 ${String.format(Locale.getDefault(), "%.2f", current.cleanableBytes / (1024.0 * 1024.0))} MB",
                        modifier = Modifier.testTag("profile_cache_size"))
                    Text("暂无可清理缓存", modifier = Modifier.testTag("profile_cache_empty"))
                    if (current.retainedFiles > 0) Text("已保留 ${current.retainedFiles} 项缓存。")
                    Text("为保护你的数据，录音、可能仍在使用的分享文件及用途不明的缓存均已保留。")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("profile_cache_close")) { Text("我知道了") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("profile_cache_cancel")) { Text("取消") }
        })
}
