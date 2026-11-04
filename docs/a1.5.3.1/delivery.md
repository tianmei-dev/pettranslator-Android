# A1.5.3.1 留白根因修复与弧度调整

## 留白根因

A1.5.3 的 PetRoom 和 ResultHeroWithBubble 用 SubcomposeLayout 先测量气泡，再计算 headroom。最终 layout 高度写成原图片高度 + headroom，并通过 art.place(0, headroom) 把原插画下移。因此额外留白来自实际布局占位，不是图片透明像素，也不是 Scaffold 重复安全区。

本次彻底移除这两个宿主的 SubcomposeLayout、headroom 和图片下移逻辑，恢复固定尺寸 Box；图片与气泡共享同一块主视觉。没有使用 Modifier.offset、负 padding 或页面绝对偏移补偿。

## 修复结果与尺寸对比

360dp 宽度下，以下数值为dp，像素取整有不到1dp差异：

| 区域 | A1.5.3之前 | A1.5.3回归状态 | 本次 |
| --- | ---: | ---: | ---: |
| 录音猫咪主视觉 | 360/1.55≈232.26 | ≈270.67 | ≈232.38 |
| 录音狗狗主视觉 | 360/1.55≈232.26 | ≈250.33 | ≈232.38 |
| 猫咪结果页主视觉 | 208 | ≈307 | 208 |
| 狗狗结果页主视觉 | 208 | ≈305 | 208 |

录音页原 .74 宽度的宠物、原宽高比、底部2dp间距和原 ContentScale 均保留；READY与RECORDING下原图片的宽高和相对位置完全一致。结果页原208dp照片和裁切恢复在主视觉顶端，宠物信息卡紧接主视觉底端，下方所有卡片内部布局未修改。

## 气泡适配

复用原 PetSpeechBubble、云朵/对话路径、奶油白、粉色 #EF4F9C、臻楷与小尾巴。新增紧凑参数，仅收紧内边距与行高，避免原大气泡挤占图片角落。

- 录音：右上叠加，宽度42%，end4dp，顶部0；基准14sp、1.15倍行高、-5°文字旋转。云朵轮廓水平翻转，使尾巴朝外，避开猫耳；文字不翻转。
- 结果：仍在顶部主视觉右侧，但移至右侧较低的安全位置，bottom2dp / end4dp，宽度约27%、最小94dp，基准14sp、1.15倍行高、-3°。该位置避开居中猫狗的脸和耳朵，可能覆盖围巾/尾巴旁的背景。
- 气泡是固定主视觉中的装饰标签，其文字按14sp基准视觉尺寸显示，不随系统大字体继续膨胀；完整文字语义保留。其余功能文字和页面正常响应系统字体缩放。这是为满足固定主视觉高度和不遮挡主体所作的局部约束，不是全局字体修改。

## 分析标题

沿用原字形测量与绘制机制，最大基线差从4dp改为8dp。保留22sp、深蓝色、文字内容、居中方式、原行高及底部4dp绘制空间。

通过解析实际字体并测量字形墨迹边界，在既有绘制范围内分配纵向位置，优先使用中央上移4dp、边缘下移4dp的连续二次曲线。空间不足或文本换行时继续用普通文本回退。没有增加标题容器高度，因此副标题、进度条、步骤和提示卡片的布局位置不因弧度变化而移动。

## 修改文件

- app/src/main/java/com/example/pettranslator/ui/recording/RecordingScreen.kt：恢复原尺寸Box，右上叠加气泡。
- app/src/main/java/com/example/pettranslator/ui/result/InterpretationResultScreen.kt：恢复208dp Box，图片/导航/气泡叠加。
- app/src/main/java/com/example/pettranslator/ui/components/PetSpeechBubble.kt：紧凑间距参数与原轮廓尾巴方向配置。
- app/src/main/java/com/example/pettranslator/ui/components/PetArtisticTitle.kt：8dp弧度和实际墨迹边界保护。
- app/src/androidTest/java/com/example/pettranslator/BubbleLayoutRegressionTest.kt：新增固定尺寸和相对位置回归检查、完整视口截图。
- docs/a1.5.3.1/：截图、尺寸记录和测试日志。

录音波形、计时器/操作按钮、AudioRecord、YAMNet、分析时间轴、正式文案、独白及其字体、AI分析、互动建议、保存分享、权益和其他页面均未修改；没有替换或生成图片素材。

## 真机与测试说明

本次连接设备为 Samsung SM-G973U，系统物理尺寸1440×3040，已有显示覆盖1080×2280，密度420dpi。未更改设备显示设置。另测试320/360dp宽度及1/1.5/2倍系统字体。

设备中已有应用签名与本地Debug签名不同。为保留原应用和数据，使用一次性Gradle初始化脚本生成独立包 com.example.pettranslator.a1531，显示名称「宠物翻译器·布局预览」，进行真机回归。正式工程 applicationId 仍为 com.example.pettranslator，最终常规构建已恢复正式包名。

临时预览构建脚本和manifest位于被忽略的 artifacts/a1531-preview.init.gradle、artifacts/a1531-preview-manifest.xml；预览APK为 artifacts/pettranslator-a1531-preview.apk。不进入正式应用配置。

截图来自真机运行生产Composable。录音截图为受控录音中UI状态，不会自动打开麦克风；分析页时间固定用于截图；结果由测试输入经原解读引擎生成。全视口截图以 phone- 开头，320/360dp图为真机中受约束容器。旧A1.5.3截图来自另一台三星设备，故对比恢复程度以dp测量为准，不宣称逐像素一致。

[查看截图与对比](D:/pettranslator/docs/a1.5.3.1/screenshots.md)。

## 构建与测试结果

- 正式包名的 assembleDebug、lintDebug、testDebugUnitTest 全部通过，日志 build.txt；Debug APK 位于 app/build/outputs/apk/debug/app-debug.apk。
- 单元测试27项通过；lint为0 errors / 5个既有warnings。
- 独立预览包回归15项通过，日志 device-tests.txt，覆盖新增几何回归4项、原艺术组件3项、分析布局2项、结果字体/滚动/分享4项、真实录音与取消/资源释放2项。
- 录音尾巴朝外的最终轮廓调整后，重新执行新增4项几何/截图测试全部通过，日志 final-layout-tests.txt（41.095秒）。
- 新增回归验证320/360dp × 1/1.5/2倍字体 × 猫狗：READY与RECORDING主视觉高度、宠物宽高和相对坐标一致；结果页208dp与信息卡邻接关系一致；弧形标题容器保持原Text高度+4dp。
- 完整视口的猫狗录音、猫狗结果和分析页共5张截图已人工核对；录音气泡尾巴朝外，脸部和耳朵无遮挡，导航按钮保留。
- 正式包和预览包使用同一生产源码，仅包名/启动器标签不同。正式applicationId恢复检查通过。
- git diff --check通过。未发现本轮范围内的未解决布局问题。

## 验收边界

气泡为适应不可增高的主视觉，采用更紧凑的14sp装饰文字；结果气泡位于主视觉右侧较低区域，避免头部遮挡。系统大字体只影响正常功能文本，装饰标签的视觉字号保持基准尺寸。当前实体设备与之前Samsung SM-S9010不同，未声称重新在同一台1080×2340设备上采集。

仅完成本次三项回归修复，已停止，等待开发者验收。

