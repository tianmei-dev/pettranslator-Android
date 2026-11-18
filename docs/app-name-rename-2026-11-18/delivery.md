# 应用名称统一更新（2026-11-18）

应用正式显示名称已统一为「猫狗心声翻译官」。在 main 开发，开始前工作区干净；未创建或切换分支，未提交、未推送。

实际修改文件：

- `app/src/main/res/values/strings.xml`：更新唯一 `app_name`。
- `app/src/main/java/com/example/pettranslator/privacy/PrivacyConsentUi.kt`：隐私授权欢迎正文改用 `app_name`，同意/拒绝/撤销及文档链接逻辑不变。
- `app/src/main/java/com/example/pettranslator/ui/profile/AboutScreen.kt`：介绍正文也使用页面原有的 `appName`。
- `app/src/main/assets/legal/user_agreement.txt`：更换正文应用名称，其他内容不变。
- `app/src/androidTest/java/com/example/pettranslator/CloudHomeLayoutTest.kt`
- `app/src/androidTest/java/com/example/pettranslator/FinalFontLayoutTest.kt`
- `app/src/androidTest/java/com/example/pettranslator/HomeVisualEvidenceTest.kt`
- `app/src/androidTest/java/com/example/pettranslator/ProfileSettingsFlowTest.kt`
- `README.md`：项目标题更新为正式新名称。

自动同步的新名称展示位置：桌面/系统应用标签、首页左上角、启动页、关于我们名称及图标无障碍描述、结果分享卡品牌。它们原本已引用 `app_name`，无需修改布局或功能实现。Manifest 继续引用 `@string/app_name`，没有单独的 Activity 标签覆盖，也没有其他语言版本的旧名称。

隐私协议覆盖：隐私授权弹窗和用户协议中实际存在的旧名称均已替换；其余六份 TXT 本来没有旧应用名称，无需改写法律内容。扫描 `app/src` 的代码、字符串和 TXT 后，没有旧名称残留。历史验收报告、旧截图和原始界面快照保留历史原文，不用于当前 App 显示。

验证结果：

- 一次执行 `./gradlew.bat --offline :app:assembleDebug :app:assembleDebugAndroidTest`，`BUILD SUCCESSFUL in 1m 11s`。
- 更新后的 UI 测试代码编译通过；没有执行完整 instrumentation 回归。
- APK 的实际 `application-label` 为「猫狗心声翻译官」，所有打包语言的回退标签一致。
- APK 中七份 legal TXT 均无旧名称，`user_agreement.txt` 含新名称。
- `git diff --check` 通过。
- 已在连接的 Samsung SM-G973U 上覆盖安装成功。设备保持锁屏，未做新页面截图，没有改变隐私授权状态来触发弹窗。

包名/applicationId 继续为 `com.example.pettranslator`，版本号、图标、权限、持久化和业务逻辑不变。

APK：`D:\pettranslator\app\build\outputs\apk\debug\app-debug.apk`

日志及检查：本目录 `build.txt`、`install.txt`、`apk-badging.txt`、`apk-check.json`、`rename-files.json`。
