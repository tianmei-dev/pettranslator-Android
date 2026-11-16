# B2.2 权限详情与隐私文档交付

代码已实现，Debug 构建通过；本轮真机验收未完整通过，不能作为发布验收完成。

修改文件：

- [ProfileScreen.kt](D:/pettranslator/app/src/main/java/com/example/pettranslator/ui/profile/ProfileScreen.kt)：仅接通权限与七文档点击事件，主页面布局样式原样；登录/VIP/撤销/设置与联系人弹窗不变。
- [PetTranslatorApp.kt](D:/pettranslator/app/src/main/java/com/example/pettranslator/ui/navigation/PetTranslatorApp.kt)：复用原 NavHost 新增两类 profile 二级路由，详情页隐藏底部 Tab 并使用现有安全区；三个主 Tab 的导航算法和显示不变。
- [ProfileUiEvidenceTest.kt](D:/pettranslator/app/src/androidTest/java/com/example/pettranslator/ProfileUiEvidenceTest.kt)：原权限占位断言改为仍未开发的隐私撤销占位断言。

新增文件：

- [ProfileDetailScreens.kt](D:/pettranslator/app/src/main/java/com/example/pettranslator/ui/profile/ProfileDetailScreens.kt)：权限详情、系统设置跳转、统一纯文本阅读页及错误态。
- [ProfileDocuments.kt](D:/pettranslator/app/src/main/java/com/example/pettranslator/ui/profile/ProfileDocuments.kt)：固定七份文档的标题、ID、TXT 一对一映射与二级路由配置。
- [ProfilePrivacyFlowTest.kt](D:/pettranslator/app/src/androidTest/java/com/example/pettranslator/ProfilePrivacyFlowTest.kt)：一轮集中真机验收。
- [user_agreement.txt](D:/pettranslator/app/src/main/assets/legal/user_agreement.txt)
- [privacy_policy.txt](D:/pettranslator/app/src/main/assets/legal/privacy_policy.txt)
- [children_privacy_policy.txt](D:/pettranslator/app/src/main/assets/legal/children_privacy_policy.txt)
- [personal_info_collection.txt](D:/pettranslator/app/src/main/assets/legal/personal_info_collection.txt)
- [third_party_info_collection.txt](D:/pettranslator/app/src/main/assets/legal/third_party_info_collection.txt)
- [third_party_sdk_sharing.txt](D:/pettranslator/app/src/main/assets/legal/third_party_sdk_sharing.txt)
- [collected_personal_info.txt](D:/pettranslator/app/src/main/assets/legal/collected_personal_info.txt)

权限核实：

- RECORD_AUDIO：实际录制宠物叫声，供本机声音分析、趣味解读。权限页首次进入及 ON_RESUME 均读取真实授权状态，不发起授权申请。
- WRITE_EXTERNAL_STORAGE：Manifest maxSdkVersion=28，实际仅 Android 8–9 保存结果 PNG 时申请；本页仅在该版本范围展示。Android 10+ 导出走现有 MediaStore 分支。
- 不增加照片读取、相机等未使用权限。合并 Manifest 的 AndroidX signature 动态接收器权限不是用户运行时授权，不列入授权列表。未发现 Manifest 与实际调用不一致；未修改 Manifest。
- 系统设置采用 ACTION_APPLICATION_DETAILS_SETTINGS + 当前包 URI，处理无可用 Activity 和安全异常。

文档实现与核验：

- 七份 UTF-8 TXT 均首行标注【开发阶段草稿｜正式发布前需审核完善】，第二行对应各自标题。
- 七个入口各自对应独立文件，已验证实际 Debug APK 内七份文本与源码原文逐字一致（apk-assets-check.json）。这是打包静态核验，不等于七入口真机已通过。
- IO 协程读取 APK assets，原生 Compose 可选择正文、竖向滚动与字体缩放；以文档 ID 重建加载和滚动状态，避免旧内容串页。未知 ID/缺失文件/空文档显示明确失败及返回入口，不回退到另一篇文档。
- 复用原 NavHost/popBackStack 返回我的页面，保留其原有滚动状态机制。
- 草稿依据实际本机录音/分析、偏好、结果图片保存与分享实现；明确人声转换/音效播放/登录/会员为尚未接入，已收集清单为静态说明而非实时个人数据查询。
- 【待核实】包括运营主体/隐私负责人、正式权利请求渠道、生效日期、统一数据期限与处理地点、未成年人监护人机制、SDK 提供方完整法律信息/隐私链接、传递依赖及最终发布包真实行为。正式发布前需专项审核。

构建与唯一一轮真机结果：

- 首次构建因新返回按钮误写 onClick（应为 onBack）编译失败；只修正该明确错误后，按允许次数重新构建，BUILD SUCCESSFUL in 37s。App 及 AndroidTest APK 均生成。
- Samsung SM-G973U / API31 / 字体1.1，一次 App 安装、一次测试包安装、一次 instrumentation run。
- 已通过的运行时步骤：我的→权限详情；麦克风实际 -1/未授权与页面一致；没有发起权限请求；成功进入 com.android.settings；保存一张有效权限详情原图。
- 测试在从系统设置按 Back 返回后等待本 App 前台时超时（10秒），1 test / 1 failure。取日志时 showing=true、secure=false；不推断锁屏之前的具体触发原因。
- 因此“设置返回后的状态刷新、七文档打开/滚动/返回、后续占位/联系/Tab 回归”均未完成真机验证；旧系统存储权限分支也未真机覆盖。没有文档阅读页截图，不把构建或 assets 核验称为完整真机验收。
- 按一次验收限制没有重跑、没有额外视觉修改。

原始证据：

- [权限详情真机截图](D:/pettranslator/docs/b2.2-profile-privacy/device/b22-profile-privacy-evidence/permission-details.png)
- [集中验收逐步日志](D:/pettranslator/docs/b2.2-profile-privacy/device/b22-profile-privacy-evidence/profile-privacy-flow.txt)
- [Instrumentation 原始结果](D:/pettranslator/docs/b2.2-profile-privacy/instrumentation.txt)
- build.txt、build-fix.txt、install-main.txt、install-test.txt、device-logcat.txt、post-test-lock-state.txt 均在本目录。
- [完整状态记录](D:/pettranslator/docs/b2.2-profile-privacy/validation.json)
- [修改范围核验](D:/pettranslator/docs/b2.2-profile-privacy/scope-check.json)：119 个保护文件哈希未变，首页、逗宠、录音/分析/结果、业务状态、素材、Manifest、Gradle、全局主题保持原样。
- [Debug APK](D:/pettranslator/artifacts/pettranslator-b2.2-profile-privacy-debug.apk)

开始于干净 main，全程未创建/切换/合并分支，HEAD 未变；未 commit、push 或清理已有文件。尚需用户确认是否安排剩余真机验收；本轮已停止。
