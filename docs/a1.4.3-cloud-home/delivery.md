# A1.4.3 首页云朵 UI 精修记录

本轮保留第二版结构，仅调整首页六处视觉细节。代码、构建、单元测试、模拟器验收及最终 S22 真机验收已完成。2026-11-13 解锁后补跑的 12 项真机 UI 测试全部通过，并已保存、逐张检查猫咪和狗狗整屏截图。

## 修改范围

当前分支：`feature/a1.4-cloud-home`。没有切换、提交、推送、合并或重置分支，`main` 提交保持 `55948715fc6ca202c34f955ca9f12b538bd80e5f`。

| 文件 | 本轮改动 |
| --- | --- |
| `app/src/main/java/com/example/pettranslator/ui/home/HomeScreen.kt` | 顶部插画增高约 8dp；云朵装饰适配；猫狗装饰视觉校准；圆钮柔和渐变、细外缘、内高光和轻阴影；调整文字在圆内的位置，避免长名称受到圆形裁剪。 |
| `app/src/main/java/com/example/pettranslator/ui/home/HomeResponsiveDimensions.kt` | 云朵加宽、纵向空间分配、标题和六岛位置微调、中央萌宠尺寸及安全间距。保留原有大字体滚动方案。 |
| `app/src/androidTest/java/com/example/pettranslator/CloudHomeLayoutTest.kt` | 首页几何、独立点击、状态回调、可读性、大字体和受控参考视口检查；以图片透明通道测量实际主体，保存证据。 |
| `app/src/androidTest/java/com/example/pettranslator/HomeVisualEvidenceTest.kt` | 保存猫狗截图及实际图片主体尺寸；区分当前设备与参考设备，避免将受控渲染当作真机实测。 |
| `docs/a1.4.3-cloud-home/` | 本报告、截图、测试日志、范围校验和文件清单。 |

工作区包含上一轮尚未提交的首页改动；Git 总 diff 是累计差异。原有 `docs/a1.4-cloud-home/` 和 `docs/a1.4.2-cloud-home/` 保留。应用图标、图片素材、底部导航、其他页面、业务代码、Manifest、Gradle 和依赖未修改。对本轮开始时记录的 88 个受保护生产/资源/配置文件进行 SHA-256 比较，无变化，见 `scope-verification.json` 和 `protected-file-verification.json`。

## 六项视觉调整

1. 云朵布局宽度为可用宽度的 99%，结合素材自身透明边缘，实心外轮廓约占屏宽 96.1%。完整绘制全部素材行与左右边缘；只压缩底部弥散光晕的占位，未裁掉云朵轮廓。
2. 中央猫狗继续复用 `pet_cat_main` / `pet_dog_main` 和原有宠物选择状态；保持 Fit 比例。分别校准可见主体，避免狗狗因透明留白不同而明显偏大。
3. 环境圆钮使用白至极浅粉渐变、细浅粉灰外缘、内侧轻高光和柔和底部阴影。选中仍为粉色描边。
4. 保留原顺序与错落构图，中上按钮较小、位置较高，下中按钮略低。标题移入云朵上部；中央图片区域与六个按钮点击矩形保持间隔。
5. 顶部插画展示高度增加约 8dp，仍使用原素材及入口。
6. 云朵底部向录音区靠近；录音按钮、免责声明继续由原 Scaffold 固定在底部导航上方。

## 视觉测量与截图

参考设备为上一轮 SM-G973U 的约 411×869dp、字体 1.1。验收中连接设备变为 SM-S9010（S22，1080×2340、480dpi、字体 1.0），已在这台 S22 上完成实际 App 截图和交互验证。参考尺寸的放大比例另用受控 Compose 视口验证，**不表示重新在 S10 真机拍摄**。

| 指标 | 411×869dp / 字体 1.1 受控渲染结果 |
| --- | --- |
| 云朵实心外轮廓 / 可用屏宽 | 96.05% |
| 猫咪实际主体 | 131.77×107.52dp，相对上一轮参考机约增大 30.3% |
| 狗狗实际主体 | 141.82×104.78dp，相对上一轮参考机约增大 25.5% |
| 猫狗实际主体面积比 | 1.049，约相差 4.9% |
| 云朵实心轮廓底部到录音按钮 | 17.91dp，上一轮约 35dp |

上表为 S22 上运行 411×869dp / 字体 1.1 的受控参考视口结果。实际主体使用源图片 alpha≥240 的边界计算，排除大面积透明留白和极淡光晕。猫咪放大略高于 30%，是结合留白和猫狗视觉面积后保留的微调。模拟器上同一视口因像素取整得到猫咪约 31.2%、狗狗约 25.7%，原始记录一并保留。

S22 实际 App 视口宽 360dp、字体 1.0：云朵实心轮廓占屏宽 96.05%，轮廓到录音按钮间距 17.61dp；猫咪主体 102.50×83.64dp，狗狗主体 110.23×81.44dp，面积约相差 4.7%。两者在上下排之间均无按钮遮挡，六个名称完整可见，首页一屏展示。不同屏幕会按可用空间缩放，不能把 S10 旧截图的 dp 尺寸直接用于 S22 增长率结论。

- `review-device.png`：目标参考 / 本轮 S22 真机猫咪 / 本轮 S22 真机狗狗对照。
- `device-home-cat.png`、`device-home-dog.png`：最终 S22 实际 App 整屏截图。
- `device-visual-metrics.txt`、`device-layout.txt`：S22 实际 App 的测量记录。
- `device/cloud-home-evidence/`：在 S22 上运行的受控参考视口、其他尺寸、大字体和点击回调证据。
- `device/cloud-home-evidence/narrow-320-font2-{door,meal,play,alone,held,unknown}.png`：320dp / 2 倍字体逐项滚动截图。已分别检查当前目标按钮的字形，未见圆形边缘截字；同屏其他非目标按钮可以处于滚动边缘。
- `review-emulator.png`：目标参考 / 本轮模拟器猫咪 / 本轮模拟器狗狗对照。
- `emulator-home-cat.png`、`emulator-home-dog.png`：实际运行 App 的模拟器整屏截图，包含原有底部导航和免责声明。
- `emulator/cloud-home-evidence/controlled-reference-cat.png`、`controlled-reference-dog.png`：参考视口受控截图，底部测试占位文字明确保留。
- `emulator/cloud-home-evidence/controlled-reference-visual-metrics.txt`：参考视口测量原始记录。
- `emulator/cloud-home-evidence/viewport-360x720.png`、`viewport-393x800.png`、`viewport-412x900.png`：普通字体常见尺寸检查。
- `emulator/cloud-home-evidence/narrow-320-font2.png`：320dp 窄屏、2 倍字体滚动后截图；通过滚动逐一检查六个环境，录音和免责声明固定可见。
- `before-cat.png`、`before-dog.png`：上一轮真机截图，仅作前后对照。

## 构建及行为检查

| 检查 | 结果与证据 |
| --- | --- |
| Debug APK、AndroidTest APK 构建 | 通过；`build-tests.txt`、`build-ui-tests.txt`。 |
| 单元测试 | 27 项通过，0 失败/错误；`unit-tests.txt`。 |
| Android Lint | 0 errors、9 warnings；`lint.txt`。 |
| API 35 模拟器 UI 测试 | 12 项通过；`emulator-tests.txt`。 |
| S22 真机 UI 测试 | 12 项通过；`device-tests.txt`，耗时 45.778 秒。 |
| 大字体逐项截图补充 | 单独重跑 1 项既有大字体用例通过，补拍六个滚动位置；`device-font-evidence-test.txt`，耗时 9.253 秒。只增加测试截图，生产代码未变。 |
| 猫狗切换 | 回调和值保持原样，中央装饰对应变化，六个环境位置不变。 |
| 六环境选择 | 六个真实独立 Compose 控件，原 ID、顺序、名称和单选反馈保留，点击矩形互不重叠。 |
| 录音入口与 ready 门控 | 进入原录音页面并保留宠物/环境参数；未就绪时保持禁用。未进行真实录音或外部服务验收。 |
| VIP | 保持当前“暂不可用”的原有禁用语义及无点击行为，没有新增 VIP 逻辑。 |
| 底部导航、选择持久化 | 原有首页 / 逗宠 / 我的切换及 Activity 重建后的选择状态检查通过；未修改这些页面或导航实现。 |
| 字体与适配 | 普通字体 360×720、393×800、412×900 和参考视口可一屏显示六环境；320dp / 字体 2 保留滚动，每项文字无 TextLayout 溢出且可达。 |
| 最终真机截图检查 | 猫狗分别通过：云朵左右轮廓完整、按钮文字未裁剪、中央图片与点击区不重叠、录音及免责声明和导航完整可见。 |

真机和模拟器通过相同的用例：`CloudHomeLayoutTest`（7 项）、`HomeFlowTest`、`HomeVisualEvidenceTest`、`HomeResponsiveEvidenceTest.largeFont`、`AdaptiveHomeTest.smallPhone`、`AdaptiveHomeTest.largeContent`。S22 系统为 Android 15 / API 35。构建产物与最后生产源码一致，测试后未再修改生产源码。补充逐项截图的测试包构建见 `build-font-evidence.txt`。`device-tests-locked.txt` 保留上次锁屏时失败的历史尝试，本轮解锁后已全部补跑通过。

## 仍存在的差异 / 待办

- 当前真机为 S22；原参考 S10 未重新连接，因此 S10 的最终显示仍通过匹配尺寸、字体和系统占位的受控渲染检查。
- 原云朵素材轮廓和自带装饰位置与参考图不同。本轮沿用素材，仅做布局适配；圆钮边缘和阴影保持轻量，仍比参考图的明显浮雕边缘简洁。
- S22 的可用布局宽度比旧参考设备更小，中央萌宠仍略小于目标图的视觉比例，优先保证六个点击区和底部录音区完整。猫咪原素材耳尖贴着图片顶部的轻微平截保留，本轮未新增图片裁剪。
- 保留现有应用图标、正式环境名称“玩耍”、免责声明和底部导航；这些与参考图中旧爪印、错误重复标签、无免责声明的呈现存在预期差异。
- 大字体模式采用可滚动的加长云朵组合，以保留文字与点击区域，不承诺与普通字体构图完全相同。

APK：`D:/pettranslator/artifacts/pettranslator-a1.4.3-cloud-home-debug.apk`。

SHA-256：`45200586C99B956D450F1ABE618B9ACD21FE0112ACDD111FA681B5788AAD3B54`。
