# A1.4 首页三项局部视觉优化交付

已在 `feature/a1.4-cloud-home` 完成左上角名称、一体式宠物切换及场景云朵外圈。生产代码仅修改 `HomeScreen.kt`；`HomeResponsiveDimensions.kt` 字节未变。未提交 Git，未切换或合并分支，保留了任务开始前全部未提交改动。

## 实际修改文件

| 文件绝对路径 | 本轮修改 |
| --- | --- |
| `D:\pettranslator\app\src\main\java\com\example\pettranslator\ui\home\HomeScreen.kt` | 三项首页展示改动及纯绘制路径函数 |
| `D:\pettranslator\app\src\androidTest\java\com\example\pettranslator\CloudHomeLayoutTest.kt` | 更新标题/品牌/切换组件断言，增加文字、语义、独立点击与间距检查 |
| `D:\pettranslator\app\src\androidTest\java\com\example\pettranslator\HomeVisualEvidenceTest.kt` | 更新名称及切换组件的验收、截图和布局测量 |
| `D:\pettranslator\app\src\androidTest\java\com\example\pettranslator\FinalFontLayoutTest.kt` | 仅更新首页名称存在、旧宠物标题不存在的断言 |

其余新增内容为本报告目录的截图/日志/审计数据，以及 `D:\pettranslator\artifacts\home-local-polish` 中的修改前快照和本地 APK，不是业务源码改动。

## 三项实现

1. **顶部名称**：复用 `R.string.app_name`，16sp Bold、`#B64D91`，使用系统现有字体。左侧文字占用原 Header 空间，右侧 VIP 代码原样保留；没有恢复 App 图标、增加独立标题栏或改变 Hero 高度。窄屏大字体按完整字形固有宽度适配，确保七字完整、单行且不覆盖 VIP。
2. **宠物切换**：删除“选择宠物”及标题胶囊，两个独立选项共用浅色胶囊底板。选中项浅粉渐变、粉色描边和柔和阴影。保留原 PNG、`state.pet == pet`、`state.ready`、`onPet(pet)`、RadioButton 及原测试标记；没有新增本地选择状态。原宠物区高度预算仍保留为布局占位，切换组件在其中居中，节省的标题空间没有交给云朵尺寸计算。
3. **场景云朵外圈**：使用单一 Compose 路径生成函数绘制轻微不规则圆润云瓣，阴影、裁切、边框和内高光共用轮廓；内高光不再绘制完整圆圈。路径和所有控制点均在原圆内，因此原圆形避碰仍为保守边界。文字所在下半区保留原圆弧安全带，保护大字号及换行文字。没有修改 `sizes.islands`、锚点、直径、图标、文案、资源映射、回调及中央萌宠。

## 同一 Pixel 3 前后测量

设备：Pixel 3，1080×2160、440dpi、正常字体 1.0。修改前证据是任务开始时保存的同一设备前版首页测量；最终证据来自本轮最终 APK 的实际运行。

| 测量项 | 修改前 | 修改后 |
| --- | ---: | ---: |
| Hero 高度 | 173.818dp | 173.818dp |
| 云朵宽×高 | 388.727×340.364dp | 388.727×340.364dp |
| 云朵顶部绝对 Y | 252.000dp | 252.000dp |
| 中央萌宠容器 | 190.545×91.273dp | 190.545×91.273dp |
| 场景标题顶部 Y | 290.182dp | 290.182dp |
| 录音按钮顶部 Y | 600.364dp | 600.364dp |
| 录音按钮宽×高 | 352.727×51.273dp | 352.727×51.273dp |
| 宠物选项整体宽度 | 约264.364dp，两个分离胶囊 | 232.000dp，共用底板 |
| 普通模式宠物切换高度 | 48dp | 48dp |

六场景尺寸依次约为 **72.00 / 61.45 / 70.55 / 68.73 / 65.45 / 70.55dp**，全部保持不变；完整位置和宽高也全部相同。归一化锚点文件与修改前字节一致。`home_section_1` 从85.818dp变为84dp，是删除标题后固定占位的测量变化，没有传导到云朵、场景或底部区域。

普通字体页面一屏完整显示。大字体继续使用原扩展布局与滚动策略；受控参考视口中逻辑48dp宠物触摸高度实测可因像素取整为47.835dp，测试仅对这种取整保留0.5dp容差，生产布局仍为原48dp。

## 构建与验证结果

- 离线 `assembleDebug`、`assembleDebugAndroidTest`、`testDebugUnitTest`、`lintDebug` 全部成功。
- 单元测试 **27/27** 通过；lint **0错误、9项原有警告**。
- 最终 Pixel 3 instrumentation **15/15** 通过，耗时 **91.886秒**。
- 覆盖普通360/393/412/440dp视口、320dp/font1.15普通模式、411dp/font1.1参考视口、320dp/font2扩展滚动、320/600dp适配，以及真实 MainActivity 的猫狗截图、切换恢复、六场景选择、就绪门控、录音入口、VIP状态、底部导航和字体回归。
- `HomeFlowTest` 验证狗狗选择在 Activity 重建后恢复，录音入口收到原宠物/场景组合，返回首页及导航保持正常；没有清除应用数据来强行获得默认选中状态。
- 品牌完整七字、无溢出或省略，与 VIP 无重叠；普通字体下 Hero 测量未增加。
- 六个独立场景完整点击区域无互相重叠，原靠萌宠一侧的真实触摸采样均通过，切换猫狗不移动场景或萌宠容器。
- 场景图标/文字与中央萌宠无新增可见重叠；录音、免责声明及导航位置原样。

早期候选在大字体下曾发生名称末字截断，已按完整字形固有宽度修正。进一步 PNG 像素审计发现云朵凹边裁去“边”“后”的部分笔画，已通过文字安全弧修复：原先217/25个强墨迹像素丢失已消除。六张大字体截图、共17个可见标签按多阈值对照；少数“定/起”字缘剩余1–3个抗锯齿像素覆盖率变化，仍为蓝色边缘，没有整笔画缺失。完整客观计数保留在 `glyph-verification.json`，未把差异全部写成零。

113个保护文件哈希无变化，包含 `HomeResponsiveDimensions.kt`、业务状态/持久化、导航、录音、权限相关代码、其他页面、全部资源、Manifest、Gradle和全局主题。原11个冻结展示函数/映射文本也相同。Git HEAD和main仍为 `55948715fc6ca202c34f955ca9f12b538bd80e5f`，没有自动提交。

## 最终交付路径

- 猫咪真机截图：`D:\pettranslator\docs\home-local-polish\device-home-cat.png`
- 狗狗真机截图：`D:\pettranslator\docs\home-local-polish\device-home-dog.png`
- 最终实际首页截图及原始测量：`D:\pettranslator\docs\home-local-polish\evidence\home`
- 最终受控视口截图及原始测量：`D:\pettranslator\docs\home-local-polish\evidence\layout`。这些是在Pixel 3上渲染的受控Compose视口，不能当作其他型号真机截图。
- 构建日志：`D:\pettranslator\docs\home-local-polish\build-final.txt`
- 最终真机日志：`D:\pettranslator\docs\home-local-polish\device-tests.txt`
- 单元测试、lint：本目录 `unit-tests.txt`、`lint.txt`
- 审计：本目录 `scope-verification.json`、`layout-verification.json`、`glyph-verification.json`
- APK：`D:\pettranslator\artifacts\pettranslator-home-local-polish-debug.apk`
- APK SHA256：`BBCF0CC9776C9E83B3AB1E8B969F5BA64C1CA19C2ED02EF1F50535060C26CD86`

本目录其他 `device`、`final-device`、`final-layout` 目录属于中间候选诊断；**最终交付以 `device-home-cat.png`、`device-home-dog.png`、`evidence` 和最终日志为准**。测试包已清理，最终 App 已保留安装并打开首页。

已验证范围内未发现剩余布局或功能问题。云瓣是按现有几何约束绘制的Compose轮廓，保留原主题和一屏构图；字缘存在上述极少抗锯齿差异。
