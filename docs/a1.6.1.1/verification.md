# A1.6.1.1 首页垂直布局修正

本次生产代码修改 HomeScreen.kt 与 PetTranslatorApp.kt；设备测试 HomeVisualEvidenceTest.kt 增加底部操作组对齐与不遮挡场景断言。

原首页为自然高度的滚动 Column，剩余空间全部位于娱乐提示下方。改为最小高度等于 Scaffold 内容可用高度的滚动 Column，使用 SpaceBetween 将上方功能区和下方操作组分开。内容不足一屏时仅分配真实剩余空间；内容超过一屏时按自然高度滚动，不压缩场景或遮挡按钮。

底部 NavigationBar 默认内容高度为 80dp。改为白色 Surface + 68dp Row，复用 NavigationBarItem，图标、文字、颜色、选中态及点击导航保持原实现。系统导航栏 Insets 仅由 Row 外侧的 windowInsetsPadding 处理一次；Scaffold innerPadding 继续由 NavHost 消费。Tab 内容高度从 80dp 降至 68dp，减少 12dp（15%），系统导航安全区另外计算。

录音按钮保持原渐变、图标、圆角和高度。按钮与娱乐提示相隔 7dp；娱乐提示底部至 Tab 顶部为单层 12dp 间距。场景两行间距从 5dp 增至 8dp；宠物卡片尺寸不变。顶部主视觉资源、样式和尺寸公式不变。

前后完整真机截图为 home-before.png 和 home-after.png。

最终 assembleDebug、lintDebug、testDebugUnitTest、connectedDebugAndroidTest 均通过。设备执行 HomeVisualEvidenceTest 与 HomeFlowTest 共2项，覆盖一屏主要内容、12dp靠底间距、不遮挡场景、猫狗及六场景选择、三个Tab、录音入口与返回状态。新增测试最初未扣除预留的12dp padding，已按真实语义边界修正并重跑通过。git diff --check 通过。

主要验收设备 Samsung 尚未连接。本次截图与设备回归使用 Pixel 3 / Android 10，正常字体、三键导航；Samsung 与手势导航模式仍需人工补验。未修改系统字体、显示缩放或系统导航设置。
