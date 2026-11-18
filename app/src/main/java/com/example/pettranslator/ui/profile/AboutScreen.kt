package com.example.pettranslator.ui.profile

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pettranslator.R

internal object ProfileSettingsRoutes {
    const val About = "profile/about"
}

private val AboutInk = Color(0xFF554164)
private val AboutMuted = Color(0xFF8B7C9B)
private val AboutBackground = Brush.verticalGradient(
    listOf(Color(0xFFF0E6FC), Color(0xFFFDF0F8), Color(0xFFEDF2FF)),
)

/** The app Scaffold owns system insets; only the page body scrolls. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val version = remember(context) { installedAppVersion(context) }

    Column(Modifier.fillMaxSize().background(AboutBackground).testTag("about_screen")) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("about_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = AboutInk)
            }
            Text(
                "关于我们", modifier = Modifier.weight(1f).padding(end = 12.dp),
                color = AboutInk, fontSize = 20.sp, lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AndroidView(
                factory = { viewContext ->
                    ImageView(viewContext).apply {
                        setImageResource(R.mipmap.ic_launcher)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = "$appName 应用图标"
                    }
                },
                update = { imageView -> imageView.contentDescription = "$appName 应用图标" },
                modifier = Modifier.size(110.dp).testTag("about_icon"),
            )
            Text(
                appName, modifier = Modifier.fillMaxWidth(), color = AboutInk,
                fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                version?.let { "版本 $it" } ?: "版本信息暂不可用",
                modifier = Modifier.fillMaxWidth().testTag("about_version"),
                color = AboutMuted, fontSize = 14.sp, lineHeight = 23.sp,
                textAlign = TextAlign.Center,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                color = Color(0xFFFFFAFE), tonalElevation = 0.dp, shadowElevation = 1.dp,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Text(
                        "${appName}是一款萌趣宠物互动应用，提供猫狗声音趣味解读、宠物互动音效等功能，让你与宠物的日常生活多一点欢乐。",
                        modifier = Modifier.fillMaxWidth(), color = AboutInk,
                        fontSize = 15.sp, lineHeight = 26.sp,
                    )
                    Text(
                        "本应用提供娱乐性声音解读，不代表真实动物语言翻译或专业动物行为判断。",
                        modifier = Modifier.fillMaxWidth(), color = AboutMuted,
                        fontSize = 14.sp, lineHeight = 24.sp,
                    )
                }
            }
        }
    }
}
