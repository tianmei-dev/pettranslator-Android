# A1.6.1.3 首页固定底部操作区与展示名称

## 实现

PetTranslatorApp 的唯一 Scaffold.bottomBar 通过 Column 承载首页专属 HomeRecordActionBar 和原有58dp Tab栏。只有home目的地显示操作区，切换逗宠、我的或进入录音流程时隐藏。按钮仍调用HomeViewModel.startRecording，权益策略及导航逻辑保持原实现。

HomeScreen 仅保留主视觉、宠物选择和六场景，并在扣除Scaffold innerPadding的空间内滚动。不存在靠weight Spacer、offset或大块占位来固定操作区。底部安全区由Tab栏处理一次，操作区只处理水平安全边距。

操作区采用首页极浅粉色底，按钮沿用原样式。声明宽度填满、TextAlign.Center、允许换行；按钮与声明间距7dp，声明下方到Tab内容区10dp。

删除Compose顶部副标题Text；按后续要求进一步删除“今天想听懂谁？”主标题。用户随后明确顶部总高度必须恢复到删除主标题之前：将原主标题36sp行高及8dp间距分配给图片显示区域，正常字体下图片区域110→154dp，顶部总高度恢复约206dp，文字仍删除。背景及角色资源不变，使用Fit保持比例。顶部品牌使用20sp、26sp行高，允许自然换行。

## 品牌名称

- strings.xml：app_name = 猫言狗语翻译官。
- AndroidManifest：沿用android:label=@string/app_name，无需改动Manifest。
- 首页：通过stringResource(R.string.app_name)显示。
- ResultCardExporter：通过app_name与share_card_brand字符串资源显示导出卡片品牌。
- aapt dump badging确认最终APK application-label为“猫言狗语翻译官”，package仍为com.example.pettranslator。
- applicationId、包名、DataStore名称/Key、签名配置、模型和权益标识没有修改。既有已安装数据的实际升级保留尚未单独演练，工程身份与数据路径保持兼容。

## 文件清单

生产文件：HomeScreen.kt、PetTranslatorApp.kt、strings.xml、ResultCardExporter.kt。

设备测试：HomeVisualEvidenceTest.kt更新固定操作组断言；HomeFlowTest.kt验证其他Tab隐藏首页操作组并保持录音导航及Activity重建；AdaptiveHomeTest.kt在分离内容/操作区后验证320/360/411/600dp及320dp双倍字体。

## 验证

assembleDebug、lintDebug、testDebugUnitTest、connectedDebugAndroidTest全部通过。

删除主标题前，实际连接设备为Pixel 3 / Android 10，7项设备测试全部通过。覆盖主内容与操作组不遮挡、操作组不随内容滚动、两个副标题、品牌名、顶部副标题已移除、六场景、猫狗选择、录音入口、Tab切换、返回状态和Activity重建。适配测试使用设备上的受控Compose宽度与字体环境，并非真实修改系统设置。删除主标题后尚未补跑设备测试，相关旧标题断言已同步更新。

本轮未连接Samsung，手势导航模式、实际旋转、桌面启动器长名称显示尚需补验。首次导出截图被其他前台应用打断，已请求保持测试手机空闲；这些非本项目画面不作为验收证据。

home-before-samsung.png为上一轮Samsung截图，只作布局参考，不是同机前后对比。
