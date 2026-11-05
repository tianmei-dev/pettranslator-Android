# A1.6.1.2 首页空间重新分配

## 根因

上一版本 HomeScreen 外层 Column 同时设置 heightIn(min = viewportHeight) 和 Arrangement.SpaceBetween。上方功能区与下方操作组是两个子节点，因此视口多余高度全部插入两者之间；此外操作组还有8dp top padding。现已移除最小视口高度和 SpaceBetween，使用内容自然高度的滚动 Column，场景模块到录音操作组保留单层16dp间距，不使用负offset或绝对定位。

## 尺寸对比

以下卡片高度为正常字体倍率1.0的代码布局值；系统字体、字体渲染可导致实测小数差异。

| 项目 | A1.6.1.1 | A1.6.1.2 |
| --- | --- | --- |
| 宠物选项卡高度 | 约52dp | 约62dp（上下padding各+5dp） |
| 场景选项卡高度 | 约63dp | 约74dp（上下padding各+4dp，图文间距+3dp） |
| 场景图文间距 | 1dp | 4dp |
| 场景两行间距 | 8dp | 10dp |
| 顶部主视觉到宠物模块 | 6dp | 8dp |
| 宠物模块到场景模块 | 6dp | 10dp |
| 场景模块到按钮 | 弹性，随剩余空间变化，上一轮Pixel截图约39dp | 固定16dp，设备断言验证 |
| Tab内容区（不含系统安全区） | 68dp | 58dp |

猫狗头像到文字的横向间距6→8dp。录音按钮、艺术标题、主视觉图片、裁切方式及主视觉高度公式均保持上一版本实现。首页仍未显示人声入口。

## 底部导航

使用轻量可选择Tab Column组成58dp高的Row，图标24dp、选中浅粉胶囊64×28dp、文字12sp、图文间距2dp。沿用三个Tab图标、颜色、选中状态与Navigation原有点击逻辑，整列可点击且目标高度58dp。系统底部/水平Insets仅处理一次，继续使用Scaffold innerPadding，未修改系统设置。

## 真机证据

home-after-samsung.png：Samsung SM-G973U / Android 12，当前显示1080×2280、420dpi、系统字体倍率1.1，三键导航。本次未修改这些设备设置。首页全部主要内容一屏可见，无需滚动，场景、宠物卡片更舒展，按钮与Tab均完整显示。

home-before.png 为上一轮Pixel 3 / Android 10验收截图，仅作上一版本布局参考，并非同一Samsung设备的前后对比。Samsung原先未安装可启动的正式应用，因此没有同机修改前截图。

## 检查范围

最终 assembleDebug、lintDebug、testDebugUnitTest、connectedDebugAndroidTest 均通过。Samsung设备执行HomeVisualEvidenceTest与HomeFlowTest共2项、0失败，验证首屏主要内容、16dp间距、猫狗选择、六场景、Tab切换、录音入口和返回恢复。首次旧签名测试包安装冲突在重跑后消除。git diff --check通过。

修改生产文件为 HomeScreen.kt 和 PetTranslatorApp.kt；HomeVisualEvidenceTest.kt 改为检查场景到操作组16dp固定间距和操作组首屏完整可见。其他业务及页面未改动。手势导航模式与更大字体仍需补充设备验收。
