# A1.5.3 艺术标题与趣味气泡：第一轮试装

以开发者三联图红框为目标，升级录音提示、分析主标题和结果主视觉气泡。此次没有更换字体、插画或背景文件；首页、独白、结果页内部卡片、导出/分享和业务实现均未修改。本目录保留 reference.png。

## 组件与接入

- PetSpeechBubble：公共气泡，Cloud 和 Dialogue 两种不规则多段贝塞尔轮廓，奶油白 #FFF9FC、98% 不透明度、0.5dp 浅粉描边和 1dp 柔影。背景非圆角矩形；文字仍为 Compose Text。
- RecordingListeningBubble：录音页独立入口，复用 Cloud，文案「正在听\n毛孩子的声音…」。仅录音中出现；原下方重复的常规“正在听”提示移到气泡；安静环境的原靠近提示仍保留。
- ResultCelebrationBubble：结果页独立入口，复用 Dialogue，文案「毛孩子的\n心声来啦！」。
- PetArtisticTitle：分析页独立浅弧标题。使用 TextMeasurer 的真实字形度量和小数 advance，逐字水平排列，字形不旋转，中心基线较边缘最高 4dp；保留整句 Text 的语义及无障碍读取。

字体均为现有霞鹜臻楷 Slab GB Regular，FontWeight.Normal、FontSynthesis.None。三处没有制作文字 PNG，也未新增字体、图片或动画库。

## 字号和几何配置

| 位置 | 字号 / 行高 | 文字颜色 | 旋转 / 弧度 |
| --- | --- | --- | --- |
| 录音气泡 | 常规15sp，小于340dp宽时14sp；1.35倍行高 | #EF4F9C | 文字 -5° |
| 分析主标题 | 22sp，1.35倍行高 | 保留 Ink 深蓝 | 边缘比中央低最多4dp |
| 结果气泡 | 16sp，1.35倍行高 | #EF4F9C | 文字 -3° |

气泡 letterSpacing 为 0sp；原版 Regular 字重避免额外合成加粗。分析标题普通单行时绘制浅弧；小屏/大字体导致多行、逐字宽度不足、组合字符或 Emoji 序列时回退为普通居中文字，保留用户字体缩放。标题下仅增加必要的4dp绘制余量，副标题、进度、阶段和页脚结构不变。

录音气泡宽为主视觉可用宽度42%，右侧6dp。结果气泡常规为40%，大字体时根据最长一行5个字的实际sp宽度增加，最多可用宽度减12dp；右侧6dp，位于导航下方2dp。气泡高度由实际排版及旋转后的高度决定，不设固定 maxLines。

## 素材限制与第一轮视觉差异

已检查 pet_cat_main / pet_dog_main、result_cat_header / result_dog_header：均没有自带气泡或文字，不存在重复绘制。

当前猫狗主体比参考设计更居中，顶部右侧空白不足。为避免气泡盖住耳朵/脸部、同时保留原图片大小及裁切，气泡宿主按测量结果增加主视觉上方空间，原照片在下方保持原尺寸绘制。结果页原208dp照片裁切保持不变。

这会造成结果页顶部比参考图更疏，导航后多一块浅粉留白：常规360dp下，猫咪主视觉总高度约307dp、狗狗约305dp（原208dp）。录音常规猫咪主视觉约271dp、狗狗约250dp（原约232dp）。这是本轮为了保护完整主体作出的明确取舍；不是与原设计完全相同的重叠位置。若开发者要求同时保持原208dp总高与参考图气泡位置，需要另行提供主体左偏、右上留白更多的插画，或明确允许调整图片显示位置。本轮未重裁、拉伸或生成替代资源。

气泡及原照片均属于各自主视觉节点，结果页滚动时一起移动。其他结果卡片的内部布局、内容和底部操作区没有改动。

## 修改清单

在A1.5.2工作区基础上，本次新增/修改：

- app/src/main/java/com/example/pettranslator/ui/components/PetSpeechBubble.kt（新增）。
- app/src/main/java/com/example/pettranslator/ui/components/PetArtisticTitle.kt（新增）。
- app/src/main/java/com/example/pettranslator/ui/recording/RecordingScreen.kt：气泡宿主和显示位置，原房间绘制抽为函数。
- app/src/main/java/com/example/pettranslator/ui/analysis/AnalysisScreen.kt：仅用艺术标题组件替换指定标题。
- app/src/main/java/com/example/pettranslator/ui/result/InterpretationResultScreen.kt：仅结果气泡及主视觉宿主。
- app/src/androidTest/java/com/example/pettranslator/ArtisticDecorationTest.kt（新增）：实际页面截图、适配和滚动验证。
- docs/a1.5.3/：截图、测量、日志与本交付说明。

工作区仍包含A1.5.2尚未提交的变更，不属于本轮新增范围。

## 真机截图与适配

设备：Samsung SM-S9010，1080×2340，480dpi。常规设备截图和320dp容器、1 / 1.5 / 2倍字体截图见 [截图索引](D:/pettranslator/docs/a1.5.3/screenshots.md)。

录音截图使用注入的录音中UI状态（3秒）展示正式RecordingScreen，未自动打开麦克风；分析页固定初始进度仅为截图；结果来自受控识别输入经正式解读引擎生成。所有截图来自实体设备，非模拟预览。

自动验证气泡不超出所属主视觉/容器，文字无省略和排版溢出，返回和首页按钮不被气泡覆盖，结果气泡随顶部主视觉滚动。猫狗耳朵和脸部由真机截图人工核对。小屏大字体下允许气泡增高/加宽及自然换行，页面保持可滚动。

## 构建和测试

- assembleDebug、lintDebug、testDebugUnitTest、assembleDebugAndroidTest：均通过，见 build.txt。
- 27项单元测试全部通过，正式96条文案哈希保护和录音状态测试保留。
- lint：0 errors，5个既有 warnings。
- 真机共11项测试通过，124.105秒，见 device-tests.txt：ArtisticDecorationTest 3项、FinalFontLayoutTest 4项、AnalysisLayoutTest 2项、RecordingFlowTest 2项。
- 覆盖四处指定截图（额外狗狗录音）、320dp和1/1.5/2倍字体、文字完整性、导航避让、气泡随图滚动、最终字体与192组正式文案布局、分享PNG及授权准备、分析页页脚、真实麦克风录音和取消/重建资源释放。
- 本轮没有重复运行导出字体专用测试，因为 ResultCardExporter 未修改；真实分享按钮回归仍通过，未实际向收件人发送。
- git diff --check：通过。

## 验收重点与限制

常规和受约束小屏下没有发现气泡越界、文字被截断或气泡遮住宠物脸部/耳朵、导航按钮。小屏两倍字体下，录音气泡会自然换为更多行并增加主视觉高度，需滚动查看完整插画；分析标题会使用普通多行居中排版。这些为保持字号与内容完整的降级方式。

第一轮最明显的待验收差异是结果页新增约100dp顶部安全留白。其原因和保留原始208dp照片裁切的取舍已明确说明；本轮不宣称与参考图位置完全一致。仅测试一台实体设备，320dp通过容器约束模拟可用宽度，未覆盖全部OEM/平板。

A1.5.3 第一轮试装完成，等待开发者视觉验收；未继续其他UI任务。

