# A1.4.9 云朵轮廓微调交付

源码仅修改 `D:\pettranslator\app\src\main\java\com\example\pettranslator\ui\home\HomeScreen.kt` 中 `sceneCloudPath()` 的一行：

```kotlin
// 修改前
val ripple = .95 + .04 * cos(8.0 * angle + PI) + .01 * cos(3.0 * angle - .65)
// 修改后
val ripple = .96 + .032 * cos(8.0 * angle + PI) + .008 * cos(3.0 * angle - .65)
```

新半径系数等于 `1 + 0.8 × (旧系数 − 1)`。相对原圆的采样内缩量减少20%，不会把整个按钮缩小20%。频率、相位、96个采样点、二次曲线、文字安全弧均保持原样；阴影、裁切、描边及内高光继续共用这一路径。按钮外接尺寸、坐标、资源、文案、点击语义、颜色及其他UI不变。116个保护文件哈希全部一致。

本任务只做一次定向源码修改、一次Gradle构建。2026-11-13原构建成功（40秒），续作未重新构建。2026-11-16在S22（SM-S9010、字体1.0）执行一次安装，结果Success。

**有效真机截图与视觉验收未完成。** 截图点选前没有再次确认前台，实际拍到文件管理器及打开方式面板。这组图片不能证明首页猫狗状态或轮廓效果，已明确标记为无效。用户随后明确选择“不补拍，先交付现有结果”，因此停止手机操作，没有补拍、重新安装或再次修改。

文件与日志：

- APK：`D:\pettranslator\artifacts\pettranslator-a1.4.9-cloud-contour-debug.apk`
- 构建日志：`D:\pettranslator\docs\a1.4.9-cloud-contour\build.txt`
- 安装日志：本目录 `install.txt`
- 审计和验收状态：本目录 `validation.json`
- 无效原始截图：本目录 `invalid-cat-attempt.png`、`invalid-dog-attempt.png`，仅保留操作记录，不作为验收截图。
- 原始UI记录：本目录 `home-before.xml`、`home-cat.xml`、`home-dog.xml`、`ui-dump.txt`。

没有触及禁止修改的源码范围，没有新增依赖或修改配置。分支保持 `feature/a1.4-cloud-home`，没有执行自动提交或合并main。续作开始时HEAD已为 `351d9ae`，保留该既有提交；main与原基线一致。

当前成果等待用户视觉验收；不能把构建和安装成功表述为视觉验收通过。
