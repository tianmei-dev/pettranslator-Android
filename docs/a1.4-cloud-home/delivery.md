# A1.4 首页萌趣云朵 UI 改造

2026-11-12，项目 `D:/pettranslator`。

## 分支和范围

开始时确认工作区干净，分支为 `feature/a1.4-cloud-home`。整个任务未切换分支、未合并、未重置、未清理，也未提交或推送。

`main` 与实验分支的起始 HEAD 均为 `55948715fc6ca202c34f955ca9f12b538bd80e5f`，目前引用保持不变。本轮改动保留在当前实验分支的工作区中。

对除首页两个 UI 文件外的 88 个主代码、素材和构建配置文件进行前后 SHA-256 对比，0 个改变。业务状态、ViewModel、Repository、DataStore、录音/权限/分析/结果、会员、导航、其他页面、Theme、Gradle、Manifest、SDK 与依赖均未修改。详见 `scope-verification.json`。

## 修改文件

| 文件 | 用途 |
| --- | --- |
| `app/src/main/java/com/example/pettranslator/ui/home/HomeScreen.kt` | 首页渐变、云朵装饰、胶囊标题/宠物按钮、错落圆形场景按钮与首页固定操作区的视觉过渡 |
| `app/src/main/java/com/example/pettranslator/ui/home/HomeResponsiveDimensions.kt` | 仅首页布局参数：圆直径、列数、错落位移和剩余空间分配 |
| `app/src/androidTest/java/com/example/pettranslator/CloudHomeLayoutTest.kt` | 新增 6 项首页 UI 验收测试与受控视口截图导出 |
| `docs/a1.4-cloud-home/` | 本记录、全部截图、尺寸、测试日志与范围校验；逐文件清单见 `files.txt` |

没有修改或新生成图片资源。实际扫描后使用已存在的 `drawable-nodpi/pastel_cloud_pet_app_frame.png`，1216×1293 RGBA。复用现有猫狗顶部插画、猫狗选择插画与六个环境图标。

## UI 改动

- 背景改为粉、蓝、浅紫渐变；原名称、应用标识、VIP 和猫狗顶部插画保留，顶部尺寸按可用高度调整，完整显示插画。
- 去掉宠物/环境两块大矩形底卡，标题使用奶油胶囊，猫狗选项改为悬浮圆角胶囊；粉色描边和浅粉底表示选中。
- 原云朵近似正方形，以 `ContentScale.Fit` 放在整个选择区后方，保留比例与轮廓。仅装饰，无点击或语义动作。
- 六个环境均为独立 Compose RadioButton 语义控件，普通字体使用三列两行，第一行中间抬高、第二行中间降低。触摸区域彼此分离；按钮底色保证图标/文字不被背景装饰干扰。
- 名称、ID、声明顺序 Door / Meal / Play / Alone / Held / Unknown、默认状态、ready 限制、选中判断和回调均保留。宠物顺序仍为狗/猫。
- 原粉蓝渐变录音按钮与免责声明保留，仍由未修改的导航 Scaffold 固定在底栏上方。VIP 仍为原禁用占位入口。

## 测试

构建及检查：`assembleDebug`、`lintDebug`、`testDebugUnitTest`、`assembleDebugAndroidTest` 均通过。27 项单元测试无失败；lint 0 errors，9 warnings，完整输出见 `lint.txt`。

当前实际设备：Samsung SM-G973U / Android12，系统报告屏幕 1440×3040，已有 override 为 1080×2280，420dpi。本轮未更改设备分辨率、密度、字体或导航设置。

真机执行的 11 项 instrumentation 检查均通过：

| 检查 | 结果 |
| --- | --- |
| CloudHomeLayoutTest，6 项 | 360×720、393×800、412×900 普通字体一屏展示；六圆形控件方形点击边界且互不重叠；独立回调/选中唯一；选中前后尺寸和位置稳定；宠物/录音 ready 限制；VIP 禁用；320dp/2倍字体滚动和文字溢出检查 |
| HomeFlowTest | 猫狗和全部环境切换；录音页接收狗/吃饭前后参数；底栏切换与返回；Activity 重建恢复 |
| HomeVisualEvidenceTest | 三星实际首页完整可见；操作区在滚动视口外、不覆盖环境区；截图和实际尺寸 |
| HomeResponsiveEvidenceTest#largeFont | 393×800 / 1.3倍字体，场景可滚动到达，固定录音按钮可见 |
| AdaptiveHomeTest#smallPhone / #largeContent | 320dp 与 600dp 宽，环境文字无溢出，全部环境可到达 |

`device-tests.txt` 是直接运行 AndroidJUnitRunner 的原始结果：`OK (11 tests)`。`unit-tests.txt` 与 `build-validation.txt` 为结果摘要，后者不是原始控制台日志。

## 尺寸与视觉验收

以下是同一三星设备上的受控 Compose 视口，底栏明确标为 58dp 测试占位，不代表多台不同真机。坐标相对测试根布局，单位 dp，四舍五入。

| 视口 | 首页滚动视口底部 | 最低环境按钮底部 | 圆直径 | 普通字体初始需滚动 |
| --- | ---: | ---: | ---: | --- |
| 360×720 | 569.5 | 561.5 | 92.2 | 否 |
| 393×800 | 648.8 | 616.3 | 92.1 | 否 |
| 412×900 | 748.8 | 631.1 | 92.1 | 否 |

三星实际首页：环境区底部约 644.6dp，录音按钮顶部约 674.7dp；完整显示六项、免责声明和原底栏。实际尺寸见 `samsung-layout.txt`。

已目视检查实际首页、较矮受控视口、1.3倍字体和窄屏2倍字体：无永久裁切、按钮相互覆盖或不可到达项。大字体采用滚动兜底，部分场景在初始画面下方；滚动不会移动或覆盖固定录音操作区。`font-1.3-log.txt` 中负 sceneToRecord 是未滚动时内容的原始坐标，不表示其覆盖固定操作区。

`comparison.png` 为同设备改版前/后截图的等比例缩小排版；实际原图分别为 `before-samsung.png` 和 `home-samsung.png`。其余受控截图与逐项坐标保存在 `cloud-home-evidence/`。

本次任务未附此前设计效果图，按本条文字要求和已提供云朵素材实现；不声称与缺失参考图逐像素一致。未额外验证横屏、折叠屏或超过已测范围的极端屏幕。当前无未解决的构建或交互阻塞问题。

## 安装包

已覆盖安装到当前三星设备。实验版 Debug APK：`artifacts/pettranslator-a1.4-cloud-home-debug.apk`；SHA-256 见 `scope-verification.json`。该文件和 `artifacts/cloud-home/baseline.json` 属于被 gitignore 排除的本地交付/校验文件。
