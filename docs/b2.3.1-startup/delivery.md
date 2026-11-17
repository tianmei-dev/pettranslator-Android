# B2.3.1 启动流程与协议弹窗交付

实现已完成，构建与5项JVM测试通过；本轮真机只部分通过，不标记完整验收。

## 实际文件

修改：
- [MainActivity.kt](D:/pettranslator/app/src/main/java/com/example/pettranslator/MainActivity.kt)：授权Loading时用轻量OnPreDraw保留系统首屏；完成后移除监听，销毁时清理。Android12原生Splash退出直接remove；Granted之后接入品牌页。
- [AndroidPrivacyConsent.kt](D:/pettranslator/app/src/main/java/com/example/pettranslator/privacy/AndroidPrivacyConsent.kt)：仅增加declineAndExit，调用原controller.decline，确认Denied、写入完成且无错误后调用原退出方法。
- [PrivacyConsentUi.kt](D:/pettranslator/app/src/main/java/com/example/pettranslator/privacy/PrivacyConsentUi.kt)：移除读取等待文字；更新标题、正文、两个真实链接及纵向按钮；复用原TXT阅读页。

新增：
- [StartupSession.kt](D:/pettranslator/app/src/main/java/com/example/pettranslator/startup/StartupSession.kt)：纯展示时序，进程内保留首次可见时间和完成标记，不是第二套隐私状态。
- [StartupScreen.kt](D:/pettranslator/app/src/main/java/com/example/pettranslator/startup/StartupScreen.kt)：品牌背景/正式Icon/宠物装饰、首次前台绘制后开始的非阻塞2秒计时。
- [StartupSessionTest.kt](D:/pettranslator/app/src/test/java/com/example/pettranslator/StartupSessionTest.kt)：5项假时钟测试。
- [StartupFlowTest.kt](D:/pettranslator/app/src/androidTest/java/com/example/pettranslator/StartupFlowTest.kt)：一轮集中UI验收。
- [b231_startup_acceptance.py](D:/pettranslator/tools/b231_startup_acceptance.py)：拒绝退出、隔离与还原专用隐私文件、UI验收及冷启动检查。

## 素材与流程

正式Icon使用 R.mipmap.ic_launcher，通过原生ImageView呈现AdaptiveIcon；资源链为mipmap-anydpi/ic_launcher.xml→ic_launcher_foreground/background→app_icon.png。装饰复用my_header_pets_cloud_v2.png（2172×724），保持3:1；其余云朵、爪印由Compose Canvas绘制。没有改图标、素材、Manifest、Gradle、主题或依赖。

- 未授权：沿用现有异步隐私读取及同一controller，系统首屏等待Loading结束，然后直接显示协议弹窗；未进入业务或提前计时。
- 首次同意：原DataStore可靠保存后，Granted进入品牌页；前台首个OnDraw后的post回调起算，约2秒后构建业务页面。
- 已授权冷进程：进程共享StartupSession为新实例，品牌页显示一次。使用SystemClock单调时间，不含系统原生首屏耗时。
- 真热恢复：已完成标记留在进程，不在onResume启动等待；原Activity及内容未销毁时直接恢复。
- Activity重建：复用同进程session，未完成时沿用原截止时间，完成后不重播。该规则通过JVM测试，实际重建用例本轮未跑到。

正文两处使用Compose 1.8.3现有LinkAnnotation.Clickable + TextLinkStyles下划线；范围仅《用户协议》《隐私政策》，分别对应UserAgreement/PrivacyPolicy的独立TXT。阅读返回原弹窗，未改变同意状态。按钮“同意并继续”为粉紫渐变，“不同意并退出”为浅色描边。

Android12原生Splash与自定义品牌页是两段不同计时；系统首屏仍可能额外短暂出现，不承诺系统启动总耗时正好2秒。[Android官方Splash说明](https://developer.android.com/develop/ui/views/launch/splash-screen)

## 构建与本轮验证

- 一次构建成功：BUILD SUCCESSFUL in 1m21s，App和AndroidTest包均生成；没有修复重建。
- 5项JVM测试全部通过：授权等待不计时、2秒边界、重建沿用截止时间、完成后不重播、新进程重新展示。
- 一次安装/一轮实际UI验收：Samsung SM-G973U / API31 / 字体1.1。
- 验收准备阶段曾发现exec-out对不存在的cat文件仍返回0，脚本误触发备份保护；当时尚未隔离隐私状态或进入场景，仅修正存在检测后继续同一轮，未重建、重装。原准备日志保留preflight-run.txt/preflight-result.json。

| 场景 | 结果 |
| --- | --- |
| 无隐私记录启动 | 通过：显示新协议弹窗，无读取文字；原设备无隐私记录 |
| 不同意并退出 | 通过：读取到Denied已持久化、进程退出 |
| 两个真实文本链接 | 通过：点击字形坐标，分别核对完整TXT，返回仍Denied且gate关闭 |
| 首次同意后的品牌页 | 通过：正式Icon、正在进入文案可见，然后进入首页；实际日志durationMs=2002 |
| 回前台保留我的页面 | 未通过：HOME后am start被系统标记LaunchState=WARM；等待profile_viewport 8秒超时 |
| 真正原Activity热恢复 | 未完成有效验证：不能把上述WARM启动当作真实原Activity恢复 |
| 已授权普通冷启动 | 尚未执行：集中UI失败后按一轮限制停止，未跑后续host冷启动步骤 |
| Activity实际重建 | 尚未执行：位于失败步骤之后；JVM共享时序测试通过 |
| 测试状态恢复 | 通过：原隐私文件状态精确恢复（原本不存在，测试文件已移除），宠物偏好保留，未清空业务数据 |

回前台现象需进一步区分测试启动命令与真实热启动行为，不能据此宣称App热恢复通过或已定位生产缺陷。按用户限制没有重跑，也没有自行更改launchMode、导航或页面视觉。

133个保护文件哈希未变，包括PrivacyConsentController/PrivacyGate、DataStore读写原逻辑、撤销流程、受控录音/推理、首页/逗宠/我的/结果主UI、七份TXT及阅读页。HEAD/main引用未变，无commit/push。

## 截图与原始日志

- [首次同意后的品牌页](D:/pettranslator/docs/b2.3.1-startup/device/b231-startup-evidence/startup-after-agree.png)
- [协议弹窗](D:/pettranslator/docs/b2.3.1-startup/device/b231-startup-evidence/consent-dialog.png)
- [链接打开的隐私政策](D:/pettranslator/docs/b2.3.1-startup/device/b231-startup-evidence/privacy-policy.png)
- [UI逐步日志](D:/pettranslator/docs/b2.3.1-startup/device/b231-startup-evidence/startup-flow.txt)
- [Instrumentation结果](D:/pettranslator/docs/b2.3.1-startup/device/instrumentation.txt)
- [启动与授权日志](D:/pettranslator/docs/b2.3.1-startup/device/startup-logcat.txt)
- [状态隔离恢复记录](D:/pettranslator/docs/b2.3.1-startup/device/acceptance.json)
- [构建日志](D:/pettranslator/docs/b2.3.1-startup/build.txt)、[单测结果](D:/pettranslator/docs/b2.3.1-startup/unit-tests.xml)
- [范围核验](D:/pettranslator/docs/b2.3.1-startup/scope-check.json)、[验证摘要](D:/pettranslator/docs/b2.3.1-startup/validation.json)
- [Debug APK](D:/pettranslator/artifacts/pettranslator-b2.3.1-startup-debug.apk)

视觉记录：使用现有插画和少量Canvas装饰，未复制参考截图；底部宠物装饰延伸到透明系统导航栏背景。未发现弹窗文字或按钮被遮挡；未做第二轮视觉微调。系统首屏到自定义页的连续动态衔接未录屏完整量化，不能声称所有设备无闪屏。

下一步如获确认，应先用返回原任务/原Activity的方式验证真实热恢复，再完成普通已授权冷启动与实际重建。本轮停止等待用户根据截图验收。
