package com.example.pettranslator.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pettranslator.R

private val ProfileInk = Color(0xFF554164)
private val ProfileMuted = Color(0xFF8B7C9B)
private val ProfilePurple = Color(0xFFA379CF)
private val ProfilePink = Color(0xFFEE86B7)
private val ProfileBlue = Color(0xFF7BA9EA)

private val PrivacyMenus = listOf(
    "系统权限管理" to Icons.Default.Settings,
    "隐私政策授权撤销" to Icons.Default.CheckCircle,
    "用户协议" to Icons.Default.Info,
    "隐私政策" to Icons.Default.Lock,
    "儿童隐私政策" to Icons.Default.Face,
    "个人信息收集清单" to Icons.AutoMirrored.Filled.List,
    "第三方信息收集清单" to Icons.Default.AccountBox,
    "第三方 SDK 共享清单" to Icons.Default.Build,
    "已收集个人信息清单" to Icons.Default.Person,
)
private val SettingsMenus = listOf(
    "关于我们" to Icons.Default.Info,
    "清除缓存" to Icons.Default.Refresh,
    "当前版本号" to Icons.Default.Info,
    "联系我们" to Icons.Default.Email,
)

/** Presentation only; the existing app Scaffold continues to own navigation and system insets. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ProfileScreen(
    onPermissions: () -> Unit = {},
    onDocument: (LegalDocument) -> Unit = {},
    onRevokeConsent: () -> Unit = {},
    onAbout: () -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val version = remember(context) { installedAppVersion(context) }
    var dialogEntry by remember { mutableStateOf<String?>(null) }
    var showRevokeConfirmation by remember { mutableStateOf(false) }
    var showCacheInspection by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().testTag("profile_viewport")
        .background(Brush.verticalGradient(listOf(Color(0xFFF0E6FC), Color(0xFFFDF0F8), Color(0xFFEDF2FF))))) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().align(Alignment.TopCenter)
            .verticalScroll(rememberScrollState()).testTag("profile_scroll").padding(bottom = 24.dp),
            // Overlap only the header and card group; keep the cards' internal spacing unchanged.
            verticalArrangement = Arrangement.spacedBy((-16).dp)) {
            ProfileHeader()
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileIdentity { dialogEntry = "立即登录" }
                ProfileMembership { dialogEntry = "开通会员" }
                ProfileCard(Modifier.testTag("profile_privacy_card")) {
                    ProfileSectionTitle("隐私设置", Icons.Default.Lock)
                    PrivacyMenus.forEachIndexed { index, (title, icon) ->
                        ProfileMenuRow(title, icon, index, Modifier.testTag("profile_menu_$index")) {
                            val document = LegalDocument.entries.firstOrNull { it.title == title }
                            when {
                                index == 0 -> onPermissions()
                                index == 1 -> showRevokeConfirmation = true
                                document != null -> onDocument(document)
                                else -> dialogEntry = title
                            }
                        }
                        if (index < PrivacyMenus.lastIndex) ProfileDivider()
                    }
                    Spacer(Modifier.height(4.dp))
                }
                ProfileCard(Modifier.testTag("profile_settings_card")) {
                    ProfileSectionTitle("设置", Icons.Default.Settings)
                    SettingsMenus.forEachIndexed { index, (title, icon) ->
                        ProfileMenuRow(title, icon, index, Modifier.testTag("profile_setting_$index"),
                            value = if (index == 2) version ?: "版本信息暂不可用" else null,
                            onClick = when (index) {
                                0 -> onAbout
                                1 -> ({ showCacheInspection = true })
                                2 -> null
                                else -> ({ dialogEntry = title })
                            })
                        if (index < SettingsMenus.lastIndex) ProfileDivider()
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
    if (showCacheInspection) ProfileCacheDialog { showCacheInspection = false }
    if (showRevokeConfirmation) {
        AlertDialog(
            onDismissRequest = { showRevokeConfirmation = false },
            modifier = Modifier.testTag("privacy_revoke_dialog"),
            shape = RoundedCornerShape(26.dp), containerColor = Color(0xFFFFF7FC),
            titleContentColor = ProfileInk, textContentColor = ProfileMuted,
            title = { Text("撤销隐私政策授权", fontWeight = FontWeight.Bold) },
            text = {
                Text("撤销后，我们将停止基于你此前隐私同意而进行的相关数据收集与处理。部分功能可能暂时无法使用。\n\n确认撤销后，应用将退出。下次打开时，你需要重新确认隐私政策。",
                    modifier = Modifier.verticalScroll(rememberScrollState()))
            },
            confirmButton = {
                TextButton(onClick = {
                    showRevokeConfirmation = false
                    onRevokeConsent()
                }, modifier = Modifier.testTag("privacy_revoke_confirm"),
                    colors = ButtonDefaults.textButtonColors(contentColor = ProfilePurple)) {
                    Text("确认撤销")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeConfirmation = false },
                    modifier = Modifier.testTag("privacy_revoke_cancel"),
                    colors = ButtonDefaults.textButtonColors(contentColor = ProfileMuted)) {
                    Text("取消")
                }
            },
        )
    }
    dialogEntry?.let { entry ->
        val contact = entry == "联系我们"
        AlertDialog(
            onDismissRequest = { dialogEntry = null },
            modifier = Modifier.testTag(if (contact) "profile_contact_dialog" else "profile_placeholder_dialog"),
            shape = RoundedCornerShape(26.dp), containerColor = Color(0xFFFFF7FC),
            titleContentColor = ProfileInk, textContentColor = ProfileMuted,
            title = { Text(if (contact) "联系我们" else entry, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (contact) {
                        Text("客服在线时间：08:00-20:00", modifier = Modifier.fillMaxWidth())
                        Text("联系QQ：594841675", modifier = Modifier.fillMaxWidth())
                    } else {
                        Text("功能暂未开放", color = ProfileInk, fontWeight = FontWeight.SemiBold)
                        Text("当前仅展示入口，相关功能尚未接入。", modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogEntry = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = ProfilePurple)) { Text("我知道了") }
            },
        )
    }
}

@Composable
private fun ProfileHeader() {
    val painter = painterResource(R.drawable.my_header_pets_cloud_v2)
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            Image(painter, contentDescription = null, contentScale = ContentScale.Fit,
                // Keep the original image container ratio when swapping artwork.
                modifier = Modifier.fillMaxWidth().aspectRatio(1774f / 600f)
                    .testTag("profile_header_artwork"))
        }
    }
}

@Composable
private fun ProfileIdentity(onClick: () -> Unit) {
    ProfileCard(Modifier.testTag("profile_login").clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.my_avatar_paw_default), "通用爪印头像",
                contentScale = ContentScale.Fit, modifier = Modifier.size(58.dp).testTag("profile_avatar"))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("立即登录", color = ProfileInk, fontSize = 18.sp, lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                Text("未登录", color = ProfileMuted, fontSize = 13.sp, lineHeight = 19.sp,
                    modifier = Modifier.fillMaxWidth())
            }
            ProfileArrow()
        }
    }
}

@Composable
private fun ProfileMembership(onClick: () -> Unit) {
    ProfileCard(Modifier.testTag("profile_vip"),
        listOf(Color(0xFFFFF1CA), Color(0xFFFFE0D4), Color(0xFFF1DCFA))) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(14.dp)) {
            val stack = maxWidth < 280.dp || LocalDensity.current.fontScale > 1.3f
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.my_vip_crown), contentDescription = null,
                        contentScale = ContentScale.Fit, modifier = Modifier.size(44.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("会员中心", color = ProfileInk, fontSize = 18.sp, lineHeight = 24.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                        Text("未开通会员", color = ProfileMuted, fontSize = 12.sp, lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth())
                        Text("会员有效期：—", color = ProfileMuted, fontSize = 12.sp, lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth())
                    }
                    if (!stack) ProfileMembershipButton(onClick)
                }
                if (stack) ProfileMembershipButton(onClick, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ProfileMembershipButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Button(onClick, modifier.heightIn(min = 48.dp).clip(shape)
        .background(Brush.horizontalGradient(listOf(ProfilePink, ProfilePurple))),
        shape = shape, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)) {
        Text("开通会员", color = Color.White, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProfileCard(modifier: Modifier = Modifier,
    colors: List<Color> = listOf(Color(0xFFFFFCFF), Color(0xFFF8F4FF)),
    content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.fillMaxWidth().shadow(2.dp, shape,
        ambientColor = ProfilePink.copy(alpha = .10f), spotColor = ProfileBlue.copy(alpha = .10f))
        .clip(shape).background(Brush.linearGradient(colors)).border(1.dp, Color.White.copy(alpha = .9f), shape),
        content = content)
}

@Composable
private fun ProfileSectionTitle(title: String, icon: ImageVector) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = ProfilePurple, modifier = Modifier.size(22.dp))
        Text(title, color = ProfileInk, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ProfileMenuRow(title: String, icon: ImageVector, index: Int, modifier: Modifier,
    value: String? = null, onClick: (() -> Unit)? = null) {
    val accent = if (index % 3 == 0) ProfileBlue else if (index % 3 == 1) ProfilePink else ProfilePurple
    val largeFont = LocalDensity.current.fontScale > 1.3f
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp)
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(30.dp).background(accent.copy(alpha = .10f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = ProfileInk, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.fillMaxWidth())
            if (largeFont && value != null) {
                Text(value, color = ProfileMuted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.fillMaxWidth())
            }
        }
        if (!largeFont && value != null) Text(value, color = ProfileMuted, fontSize = 12.sp, lineHeight = 18.sp)
        if (onClick != null) ProfileArrow()
    }
}

@Composable
private fun ProfileArrow() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
        tint = ProfileMuted, modifier = Modifier.size(18.dp))
}

@Composable
private fun ProfileDivider() {
    HorizontalDivider(Modifier.padding(start = 54.dp, end = 14.dp), thickness = .5.dp,
        color = Color(0xFFE9E1F2))
}
