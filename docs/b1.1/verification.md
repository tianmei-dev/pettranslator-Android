# B1.1 逗宠首页UI与基础交互

## 文件与组件

- ui/play/PlayScreen.kt：替换占位页，组件包括FunHeader、VoiceCard、PetSwitch、SoundRow、FunWave和FunMicrophone。
- ui/play/PetFunSoundItem.kt：稳定ID、宠物类型、名称、说明、展示图标；无音频文件、播放器或播放生命周期字段。
- ui/navigation/PetTranslatorApp.kt：只将逗宠目的地内容替换为PlayScreen()，不修改导航体系与Tab样式。
- androidTest/PetFunFlowTest.kt：猫狗列表切换、占位提示、列表末条、Tab返回与页面重建回归。
- androidTest/PetFunLayoutTest.kt：360×720、393×800、412×900与393×800/1.3字体受控Compose布局检查及截图。
- androidTest/HomeFlowTest.kt：旧逗宠占位文案与返回按钮断言更新为本次正式入口和底部首页Tab。
- drawable-nodpi/pet_fun_header.png、pet_fun_microphone.png：开发者提供的新增素材，仅引用，未修改文件内容。

## 素材检查与位置

| 资源 | 解码尺寸 | 格式 | Alpha范围 | 实际使用 |
| --- | --- | --- | --- | --- |
| pet_fun_header.png | 1016×648 | 32位ARGB | 0～255 | 横幅右侧，Fit完整展示猫狗 |
| pet_fun_microphone.png | 724×745 | 32位ARGB | 0～255 | 主卡右侧，Fit保留麦克风与光环 |

两图具有真实透明通道。顶部图497761个非全透明像素，麦克风363466个；非零Alpha包围盒覆盖全画布，说明柔光/装饰延伸至边缘，不能依靠简单Alpha裁边缩去“空白”。本次保持PNG原样，通过独立图片容器缩放。视觉检查主体完整，无标题或按钮覆盖。

## 数据清单

| 猫咪ID | 狗狗ID | 名称 | 说明 |
| --- | --- | --- | --- |
| cat_call | dog_call | 快来 | 召唤它到你身边 |
| cat_sit | dog_sit | 坐下 | 引导它乖乖坐下 |
| cat_paw | dog_paw | 握手 | 和它友好互动，击掌握手 |
| cat_praise | dog_praise | 夸夸我 / 夸夸你 | 给它满满的夸奖与鼓励 / 给它一点温柔鼓励 |
| cat_meal | dog_meal | 开饭啦 | 呼唤它来吃饭 |

全部是UI预览标签，不代表宠物能够理解这些指令，也不表示已具备音频资源。

## 状态与交互

猫狗切换使用逗宠页自己的rememberSaveable状态，首次为猫咪，不使用首页PetPreferences/DataStore；页面重建可恢复。高亮是650ms的条目点击反馈，不是播放状态。新点击替换原提示，避免累积提示队列。

“开始说话”显示“趣味说话功能即将上线～”。条目整行或预览按钮显示“音效资源将在下一阶段接入～”。未创建AudioRecord、未申请权限、未查询或消耗体验资格、未触发购买、未创建播放器或下载素材。HomeViewModel原有humanVoice入口不再被此页调用，后续正式流程可在这里接入统一权益接口。

## 滚动与适配

整个页面使用单个LazyColumn，顶部、主卡、选择控件和五条列表均在其中，稳定ID作为列表key。父级Scaffold继续提供contentPadding与消费Insets；页面不重复添加系统底部Insets，最后一条可完整滚动到Tab上方。58dp Tab与导航逻辑保持现有实现。

标题/图片分别位于Row的独立权重容器，不重叠；顶部图片容器按宽度计算并限制110～174dp，主麦克风容器保持比例、限制高度。大字体时文字自然换行、内容高度增长，列表滚动。使用原霞鹜臻楷标题字体及系统功能文字，无新增字体或UI依赖。

## 证据说明与设计差异

assembleDebug、lintDebug、testDebugUnitTest均通过。Samsung设备上的4项受控布局、1项HomeFlowTest回归通过；PetFunFlowTest在调整测试操作时序后单独通过，覆盖主功能提示、音效提示、猫狗两组数据、列表末条、Tab切换和重建。共6项不同测试分次完成。早期失败来自测试在上一提示仍覆盖屏幕时点击靠近底部的条目，现改为等待前一提示结束并滚入完整条目后操作；无需修改业务代码。

samsung-top.png及samsung-bottom.png为Samsung SM-G973U / Android12实际页面截图。底部截图在提示消失后补拍，五条狗狗音效均可见，最后一条未被Tab遮挡。evidence/play-compose-*为受控Compose视口截图，底部有明确标记的58dp测试Tab占位，不是不同型号手机的实机截图。

与设计稿仍有差异：主标题使用项目原始霞鹜臻楷，没有烘焙字效；主麦克风在小宽度上比例更小，标题和副标题允许换行；Tab保留当前项目图标样式；呼唤/爱心使用系统Emoji，坐下/握手复用已有托爪插画，与设计稿各不相同的立体小图标尚有差距。音效行使用独立轻质卡片，未叠加额外厚重外层，默认首屏不强制显示全部五条。

本次止于UI及基础交互，正式音效、播放器、人声录制与转换均未接入。
