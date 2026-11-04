# 结果页顶部气泡强化微调交付

最终采用按宠物插画分别配置的版本，保持208dp主视觉、原背景裁切及所有下方卡片的位置与布局。

## 最终参数

| 项目 | 猫咪 | 狗狗 |
| --- | --- | --- |
| 整体缩放 | +15% | +10% |
| 文字字号 | 14→16.1sp | 14→15.4sp |
| 气泡底边距 | 2→18dp | 保持2dp |
| 右边距 | 4→2dp | 4→1dp |
| 实测中心上移 | 约19.5dp | 约2.6dp |
| 实测上缘上移 | 约23.1dp | 约5.2dp |

猫咪采用本次建议范围内的上移与放大。狗狗右耳在当前右侧区域延伸较低，若同样上移20dp会覆盖耳缘，因此采用较小上移和10%放大；仍比原版本稍高、文字更大。

白底、粉色 #EF4F9C、臻楷、轮廓和小尾巴均保留。内边距、字号、描边和柔影随缩放同步增加；文字使用新的实际字号重新排版绘制，不用位图放大。猫咪描边约0.575dp、柔影1.15dp；狗狗约0.55dp、1.1dp，保持轻盈。

气泡仍为固定主视觉Box内的叠加元素，未增加容器高度、移动背景或改变信息卡的起点。系统大字体下继续采用上一版装饰标签的固定视觉字号规则，字体接入和普通功能文字没有更改。

## 修改文件

- app/src/main/java/com/example/pettranslator/ui/result/InterpretationResultScreen.kt：结果气泡的缩放和猫狗位置参数。
- app/src/main/java/com/example/pettranslator/ui/components/PetSpeechBubble.kt：新增可选 visualScale 参数，默认1；只有结果页传入放大值，其他页面调用和视觉不变。
- app/src/androidTest/java/com/example/pettranslator/ResultBubbleEmphasisTest.kt：位置/尺寸、文字完整性、主视觉208dp和信息卡位置验证。
- docs/result-bubble-emphasis/：截图、测量及日志。

本次未修改其他页面、背景插画、信息栏、独白、AI分析、互动建议、底部操作、保存分享逻辑或字体配置文件。工作区中的A1.5.3.1修改仍保留，不属于本次新增范围。

## 真机截图

设备Samsung SM-G973U，现有1080×2280显示覆盖，420dpi；采用上次已安装的独立包「宠物翻译器·布局预览」，原应用和数据未覆盖。

| 猫咪最终效果 | 狗狗最终效果 |
| --- | --- |
| ![猫咪气泡](D:/pettranslator/docs/result-bubble-emphasis/result-bubble-emphasis-evidence/phone-result-cat.png) | ![狗狗气泡](D:/pettranslator/docs/result-bubble-emphasis/result-bubble-emphasis-evidence/phone-result-dog.png) |

[猫咪调整前截图](D:/pettranslator/docs/a1.5.3.1/a1531-evidence/phone-result-cat.png)，[狗狗调整前截图](D:/pettranslator/docs/a1.5.3.1/a1531-evidence/phone-result-dog.png)。前后为同型号设备、相同显示尺寸和密度。

截图来自实体设备运行生产结果页；识别输入为受控测试数据，经原引擎生成同一组正式独白。目录内另有320/360dp、1/2倍字体的截图和测量记录。

## 验证

- 2项真机测试通过，包含真实视口、320/360dp与1/2倍字体、猫狗共10种页面状态。
- 自动检查气泡不越界、不与顶部返回/首页按钮相交；文字完整、保持两行、无省略或排版溢出。
- 自动检查主视觉固定208dp，宠物信息卡top==主视觉bottom，位置不变。
- 人工核对猫咪耳朵、眼睛和嘴部，以及狗狗右耳，没有被气泡覆盖。
- 实际边界见 physical-result-bubble-geometry.txt、responsive-result-bubble-geometry.txt及measured-*.txt；测试日志 device-tests.txt。

最终工程检查结果见 build.txt。

assembleDebug、lintDebug、testDebugUnitTest 全部通过；27项单元测试通过，lint为0 errors / 5个既有warnings。仅完成本次气泡微调。

