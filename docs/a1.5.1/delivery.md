# A1.5.1 字体试装交付

本次仅对首页主标题、分析页主标题、结果页核心独白接入字体试装。三个位置默认均为 System，最终字体方案等待开发者验收。

## 官方字体、授权与资源

| 方案 | 实际字体 | TTF 大小 | 官方版本 |
| --- | --- | ---: | --- |
| A | LXGW ZhenKai Slab GB Regular（霞鹜臻楷 Slab GB） | 17,453,376 bytes / 16.65 MiB | v0.827 |
| B | LXGW WenKai Regular（霞鹜文楷） | 25,928,940 bytes / 24.73 MiB | v1.530 |
| C | Android 当前系统字体 | 无新增文件 | 设备系统版本 |

原始 TTF 从官方 Releases 获取，Android 文件名分别为 pet_title_zhenkai.ttf、pet_title_wenkai.ttf。实际文件 SHA-256 与官方 Release digest 一致，内部名称、版本及 weight=400 已核查；没有修改字形、内部字体名称或做子集化。

- 来源、固定版本下载地址与完整授权说明见 [THIRD_PARTY_FONTS.md](D:/pettranslator/docs/a1.5.1/THIRD_PARTY_FONTS.md)。
- 字体内部元数据及实际哈希见 [font-metadata.json](D:/pettranslator/docs/a1.5.1/font-metadata.json)。
- 臻楷原始许可证：[OFL.txt](D:/pettranslator/app/src/main/assets/licenses/lxgw_zhenkai/OFL.txt)。
- 文楷原始许可证：[OFL.txt](D:/pettranslator/app/src/main/assets/licenses/lxgw_wenkai/OFL.txt)。

两份 OFL 连同版权声明已验证进入 APK assets/licenses。字体以原版形式随应用分发，不单独出售。两个版本的保留名称附加许可将子集化例外限定于网页字体用途；后续若为 Android 子集化，需重新核对保留名称及修改要求。本次未进行此操作。

## 试装配置与手动真机切换

统一配置位于 [PetFontFamilies.kt](D:/pettranslator/app/src/main/java/com/example/pettranslator/ui/theme/PetFontFamilies.kt)。FontFamily 仅在该处创建；使用 Android res/font 资源和平台缺字回退，不引入渲染库。

修改 PetFontTrial 的三个默认值即可独立试装，例如：

```kotlin
data class PetFontTrial(
    val homeTitle: PetTrialFont = PetTrialFont.ZhenKai,
    val analysisTitle: PetTrialFont = PetTrialFont.ZhenKai,
    val monologue: PetTrialFont = PetTrialFont.WenKai,
)
```

候选值为 PetTrialFont.ZhenKai / WenKai / System。修改后 assembleDebug 并重新安装 APK。当前交付仍将三个默认值设为 System；无设置页面、持久化或用户入口。截图测试通过 CompositionLocalProvider 在测试进程内切换三种方案。

候选字体使用原版 Regular，不合成粗体；系统方案保留原来的 Bold。字号、颜色、行高、字间距和页面布局未调整。三个位置以外的文本保持原有样式。

## 真机截图与排版

[打开九张对比截图](D:/pettranslator/docs/a1.5.1/comparison.md)。

设备：三星 SM-S9010，1080×2340、480dpi。同一设备、相同字号/分辨率，逐字体使用相同页面状态。首页固定猫咪和不确定场景；分析页固定同一初始状态；结果页固定猫咪/独处、相同正式模板文案及结果时间。

截图来自真实设备运行生产 Composable。结果使用测试构造的成功识别输入，经现有 PetInterpretationEngine 生成正式内容，并非本次实际录音识别。分析页初始时间由测试固定，仅为保持三张截图可比，生产 3～5 秒时间轴没有改变。

正式 96 条文案在每种字体下通过实际独白卡片逐条渲染，共 288 组；包括长句、短句、Emoji 和引号。另测试 320dp 容器、两倍字体及最长文案。布局检查文字无省略、无高度截断、与右侧宠物插画不重叠。

中文、数字、中文标点及全部正式文案共 422 个不同码点通过字体/Android fallback 的 hasGlyph 检查。三种方案的猫咪 Emoji 均检测到 3658 个彩色像素，原有彩色 Emoji 回退正常。字符和导出检查见 a151-evidence/glyph-and-export.txt；逐条布局见 copy-layouts.txt。

保存/分享继续使用原 ResultCardExporter 的系统字体。本次未更改其样式或逻辑；测试验证原 render 和 writeShareFile 能生成完整 PNG，并留存 existing-system-font-export.png。未实际写入系统相册，也未发送分享 Intent。若日后要求导出卡片使用已选字体，应在字体验收后单独调整。

## APK 体积

| Debug universal APK | 字节 | MiB |
| --- | ---: | ---: |
| 试装前基准 | 48,823,633 | 46.56 |
| 同时包含两款字体 | 73,239,818 | 69.85 |
| 实际增加 | 24,416,185 | 23.29 |
| 只保留臻楷（估计） | 60,335,354 | 57.54 |
| 只保留文楷（估计） | 61,633,771 | 58.78 |

单字体估计仅减去另一字体在 APK 中的压缩载荷，ZIP 头、资源表、签名对齐可能略有变化，不等同于实测单字体构建或商店 AAB 下载大小。两款字体在包内压缩载荷分别为臻楷 11,606,047 bytes、文楷 12,904,464 bytes。原始文件合计约 41.37 MiB。

完整记录见 apk-baseline.json 和 apk-sizes.json。本轮保持全字库以验证正确显示；最终确定方案后删除未使用的资源。若体积目标仍无法满足，再评估合法子集化、名称处理和未来动态文案的字符覆盖，不建议直接只保留当前三个固定标题的字。

## 修改文件

- app/src/main/res/font/pet_title_zhenkai.ttf
- app/src/main/res/font/pet_title_wenkai.ttf
- app/src/main/assets/licenses/lxgw_zhenkai/OFL.txt
- app/src/main/assets/licenses/lxgw_wenkai/OFL.txt
- app/src/main/java/com/example/pettranslator/ui/theme/PetFontFamilies.kt
- app/src/main/java/com/example/pettranslator/ui/home/HomeScreen.kt：仅主标题字体配置和测试标记。
- app/src/main/java/com/example/pettranslator/ui/analysis/AnalysisScreen.kt：仅主标题字体配置和测试标记。
- app/src/main/java/com/example/pettranslator/ui/result/InterpretationResultScreen.kt：仅独白字体配置和测试标记。
- app/src/androidTest/java/com/example/pettranslator/FontTrialEvidenceTest.kt
- docs/a1.5.1/：来源、大小、截图和验证记录。

## 验证与建议

构建与测试最终结果见 build.txt、device-tests.txt。验证范围为指定字体位置；录音、识别、权益、导出分享逻辑未改动。

- assembleDebug、lintDebug、testDebugUnitTest、assembleDebugAndroidTest：均通过。
- 单元测试 27 项通过，包括 96 条正式文案的既有哈希保护测试。
- 真机 FontTrialEvidenceTest：3 项通过，耗时 42.579 秒。包含 9 张页面对比、288 组正式文案排版及 3 组窄屏大字体排版、字符/彩色 Emoji、原导出器 PNG 检查。
- lint：0 errors，5 个既有 warnings。
- git diff --check：通过（仅已有文件的 CRLF 归一化提示）。

视觉建议：优先验收“臻楷用于首页/分析标题，文楷用于核心独白”的搭配。臻楷线条更厚、标题更活泼；文楷较轻柔，长句阅读更舒适。系统字体最熟悉清晰，但手写气质较弱。若体积只允许保留一个完整字库，可以先比较文楷用于三个位置的效果。上述均为建议，未自动应用到默认配置。

与设计稿差别：两款候选都是楷体风格，仍有明确的书写笔势，并非设计图中完全圆润的美术字。两款候选的字面和字体行度量与系统字体不同，同字号下会略宽、基线及文字块高度略变；保留了原有布局和字号，未通过拉伸字形或移动插画来对齐。

限制与范围外问题：只测试一台实体设备；字体回退外观依赖设备系统。320dp/两倍字体的极限截图中，原结果页固定高度的底部操作按钮有文字裁切，属于本轮未修改的操作区；指定独白文字完整、可滚动、没有与插画重叠。此次没有扩大范围修改操作按钮。相册实际保存与系统分享面板未操作；验证到现有 PNG 渲染及分享文件生成。

