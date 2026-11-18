# B3.2.2 首版转换接入交付（真机验收未完整通过）

日期：2026-11-18（用户时区）。本轮在 main 完成一次集中实现、一次构建、一轮自动化真机验收。用户明确选择“仅做自动化验证，人工录音试听标为未验证”。未提交、未推送、未切换分支。

**代码与30项单元测试通过；真机在后台恢复后的狗狗重播步骤超时，不能标记为完整验收通过。已停止，未进行第二轮修改、构建或测试。**

## 文件范围

新增生产代码（均在 `app/src/main/java/com/example/pettranslator/ui/voice/conversion/`）：

- `VoiceConversionModels.kt`：能量特征、批准音源和固定序列定义。
- `PcmVoiceFeatureAnalyzer.kt`：后台、可取消的真实PCM特征分析。
- `VoiceSoundSelector.kt`：确定性匹配和同类候选轮换。
- `VoiceConversionPlayback.kt`：资产读取、MediaPlayer队列、AudioFocus、真实进度与释放。

修改已有文件：

- `app/src/main/java/com/example/pettranslator/ui/voice/VoiceTalkViewModel.kt`：分析、匹配、自动播放、固定重播、取消和宠物隔离接线。
- `app/src/main/java/com/example/pettranslator/ui/voice/ExclusiveVoiceAudioCapture.kt`：只在本页增加 `recordAndUse` 文件消费租约；消费结束后清理本会话PCM。
- `app/src/main/java/com/example/pettranslator/ui/voice/VoiceTalkScreen.kt`：RESULT真实数据和操作回调接线，READY/RECORDING布局及权限流程保持。
- `app/src/main/java/com/example/pettranslator/ui/voice/VoiceTalkStateComponents.kt`：真实播放进度与0.1秒计时，空音源禁播。
- `app/src/main/java/com/example/pettranslator/ui/navigation/PetTranslatorApp.kt`：仅为人声页工厂注入分析器、资源库和播放器。
- `app/src/test/java/com/example/pettranslator/VoiceTalkViewModelTest.kt`：旧占位终态改为真实链路断言，并补取消、重播和资源保护测试。
- `app/src/androidTest/java/com/example/pettranslator/VoiceTalkDeviceTest.kt`：更新旧B3.1的直接受影响占位终态断言；本轮未运行该旧测试类。

新增验证：`VoiceConversionCoreTest.kt`、`VoiceConversionDeviceTest.kt`、`tools/b322_voice_acceptance.py` 以及本目录证据。

保护检查见 `scope-check.json`：除以上7个已有文件外，1531个基线文件保持一致，包括用户此前全部未提交更名成果。首页、逗宠主页和音效列表、我的页面、录音器、YAMNet、隐私机制、Manifest、Gradle、全局主题均未改动。

## 六条实际音源与构建隔离

以下文件原样复制至 `app/src/debug/assets/voice_conversion/`，文件名为 `<audio_id>.m4a`：

| audio_id | 用户确认类型 | 优化文件内容时长 |
| --- | --- | ---: |
| cat_fs_362953 | 单叫声 | 0.734秒 |
| cat_fs_618900 | 单叫声 | 1.100秒 |
| cat_fs_728095 | 连续叫声 | 4.282秒 |
| dog_fs_813120 | 极短单叫声 | 0.223秒 |
| dog_fs_9032 | 连续叫声 | 2.831秒 |
| dog_fs_807270 | 连续叫声 | 3.137秒 |

来源为 `D:\CodexData\PetTranslatorAudio\output\processed\cat\` 和 `processed\dog\` 对应文件。没有再转码、裁剪或调音；没有启用其余4条。复制前后和APK内字节SHA-256一致，六条在Debug APK中均为未压缩资产，可直接使用AssetFileDescriptor；没有在同一APK中复制到第二处。逐条来源URL、作者和哈希见 `debug-audio-manifest.json`。

Release代码编译通过，实际目录 `app/build/intermediates/assets/release/mergeReleaseAssets` 存在且不含 `voice_conversion` 或任何M4A。没有将未完成最终许可审核的候选放入main/Release。本轮未生成或安装新的Release APK；Release缺资源的真实错误路径以资源库返回空、ViewModel开麦前提示“转换音源尚未就绪，请使用包含开发音效的 Debug 版本”处理，该路径有单元测试，未做Release真机验证。

## 实现行为

- 输入沿用16kHz单声道PCM_S16LE、最长10秒、最少800ms。分析在后台读取实际字节，严格检查格式、样本数、文件长度与时长，按50ms窗口计算RMS、相对变化、自适应低位能量参考、活动段和停顿。至少150ms累计活动才可通过；静音、文件损坏和取消不生成结果。能量活动不能证明是人声，更不识别词义或情绪。
- 短猫句在两条单叫声中依据活动时长/变化选择；长或多段猫句优先728095。狗极短活动可选813120一次，其余句子选择9032或807270完整连续片段。轮换仅在同一合格类别内进行，不跨宠物，不使用随机延时或随机无规则选择。
- 首版实际默认完整播放一条自然音源，没有启用两条拼接策略。数据结构与播放器最多接受两条不同音源并支持非阻塞自然停顿；不循环极短狗叫，不复制连续音源强凑录音长度，不变速、不变调、不叠加。
- CONVERTING只覆盖真实分析/匹配工作。AudioRecord释放后，PCM在该页消费租约内供分析读取；分析退出、失败或取消后finally仅清理本会话文件，清理完成后才自动进入播放。没有修改首页PCM生命周期，也不长期保存原始人声。
- RESULT保存独立固定序列。重播沿用同一序列，播放/暂停与时间来自MediaPlayer实际状态及currentPosition/duration，完成后停留RESULT。再说一次、退出、后台和宠物切换取消旧任务、停止音频并等待清理；旧回调受会话身份、目标宠物、前台和PrivacyGate共同约束。
- 播放前申请AudioFocus，拒绝时不启动；焦点丢失停止，退出释放MediaPlayer与焦点，不改系统音量。使用既有PrivacyGate和HumanVoice权益接口，不消耗首页免费翻译机会。
- 当前逗宠固定条目本来仍是待接入提示，未因本功能改成AVAILABLE，未修改10个soundId或原列表行为。

## 构建及单元测试

一次Gradle调用：

`./gradlew.bat --offline :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest --tests com.example.pettranslator.VoiceConversionCoreTest --tests com.example.pettranslator.VoiceTalkViewModelTest :app:compileReleaseKotlin :app:mergeReleaseAssets`

结果：`BUILD SUCCESSFUL in 1m 47s`。核心分析/选择13项、ViewModel17项，共30项通过，0失败。覆盖静音、损坏、短录音、取消、少量确定性匹配、白名单、宠物隔离、缺资源、固定重播以及清理屏障。XML原始报告和 `build-validation.json` 已保存。本轮没有发生编译错误，也没有重新构建。

## 唯一真机轮次与实际结果

预检时是S22，安装前连接已更换为Pixel 3。针对旧S22序列号的首次安装命令未执行成功，没有启动测试；随后在当前Pixel 3（API29）成功安装应用和测试APK各一次，只调用一轮instrumentation。

自动化使用显式合成的PCM测试夹具，但调用真实分析器、匹配器和Android MediaPlayer；没有向生产代码注入假分析结果或假播放器。它不等于真实人声录音或人工试听。

| 用例 | 本轮证据 |
| --- | --- |
| V01 短猫句 | 真机夹具通过，选单叫声、自动播放完成 |
| V02 长猫句 | 真机夹具通过，选728095，完成并验证暂停/恢复 |
| V03 短狗句 | 真机夹具通过，813120只播放一次 |
| V04 长狗句 | 选807270并真实开始播放；后台中断后重播未通过 |
| V05 静音 | 单元测试通过；后续真机步骤未执行 |
| V06 自动分析播放 | 猫短/长、狗短已真实完成；狗长已开始 |
| V07 同序列重播 | 猫短句通过；后台恢复后的狗重播超时 |
| V08 再说一次 | 已完成播放后的复位可用；播放中复位有单测，后续真机未完成 |
| V09 后台停止释放 | 狗长句停止并释放有实际日志；后续恢复重播失败 |
| V10 转换中退出 | 单元测试通过，等待分析取消清理；未做真机时序验证 |
| V11 缺音源 | 单元测试通过，开麦前报未就绪；未做Release真机测试 |
| V12 采集播放无冲突 | 软件清理屏障及合成PCM集成通过；物理麦克风步骤未执行 |
| V13 宠物隔离 | 确定性测试及猫/狗夹具选择正确；播放中换宠物真机步骤未执行 |
| V14 逗宠回归 | 源码和ID未变；计划的真机列表回归步骤未执行 |

明确失败：`VoiceConversionDeviceTest.kt:137`，Activity从CREATED回到RESUMED后点击狗狗“重新播放”，8秒内未进入isPlaying，日志中没有新的播放器prepare/start。此前RESULT已恢复，旧音频已停止释放，没有自动后台续播。**具体根因尚未证明**；窗口焦点/生命周期时序可能相关，不能直接认定只是测试问题，也不能宣称生产重播已通过。没有修改或重测以掩盖失败。

因此instrumentation总结果为1个集成测试、1个失败。后续物理麦克风采集和静音UI等步骤未到达。遵照用户选择，人工说话、听感自然度、拼接感和主观响度未验证。本轮短时日志未显示本功能的FATAL异常，但不能据此宣称完成Crash/ANR/长期资源泄漏验收。

实际被原生播放器prepare/start的4条为cat618900、cat728095、dog813120、dog807270；其余2条在APK中哈希与可读性检查通过，但本轮没有原生播放记录。Pixel 3原生播放器报告时长可能含AAC约21ms编码填充，UI使用播放器真实时长，不强行使用素材清单近似值或截断声音。

## 证据、数据保护与剩余风险

截图（真实MediaPlayer状态，输入是测试PCM而非人工人声）：

- `D:\pettranslator\docs\b3.2.2-voice-conversion\device\b322-evidence\01-cat-result-fixture.png`
- `D:\pettranslator\docs\b3.2.2-voice-conversion\device\b322-evidence\02-dog-result-fixture.png`

日志：`device/instrumentation.txt`、`device/logcat.txt`、`device/b322-evidence/device-results.txt`、`device-run.txt`。所选实际输出音频可按 `debug-audio-manifest.json` 对应到原样Debug M4A文件，没有导出或长期保存用户原始人声。

`device/state-protection.json` 确认临时麦克风授权及全部权限标志准确恢复；测试未写用户DataStore，前后哈希清单一致，未清空App数据。自动化故障后已停止App并释放测试会话。

剩余风险：后台恢复后的重播失败需下一轮定位；本轮未完成的物理采集、录音期间生命周期、播放中换宠物、静音UI、逗宠真机回归仍需后续验收；人工录音和听感未验证；六条只获用户开发试听认可，最终发布授权仍待专项复核。当前资源仅Debug可用，不能作为已审核Release资源。

可供复核的同一构建APK：`D:\pettranslator\artifacts\pettranslator-b3.2.2-debug.apk`，SHA-256见 `validation.json`。未自动提交或推送Git。
