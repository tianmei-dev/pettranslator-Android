# A1.4.3 交付记录

日期：2026-10-29。真机：Pixel 3（1080×2160）。

## 本任务修改文件

生产代码：

- `ui/recording/RecordingViewModel.kt`：UNCERTAIN 等待本次已有随机展示时长；增加幂等 `dismissUncertain()`，消费本次未识别结果并清理缓存。
- `ui/recording/RecordingScreen.kt`：仅修改 RecordingRoute 的状态呈现，UNCERTAIN 使用待录音页作为背景并显示新 BottomSheet；SUCCESS / PET_MISMATCH / ANALYSIS_ERROR 继续原路径。RecordingScreen 本体及 RecordingWaveform 未因本任务修改。
- `ui/recording/UncertainRecordingSheet.kt`：新增 Material 3 ModalBottomSheet，正式标题、副标题、粉色声音图标及重录按钮。
- `ui/home/HomeScreen.kt`：移除人声入口及相应回调参数、原卡片实现，无占位空白。
- `ui/play/PlayScreen.kt`：保留原占位标题、说明、返回首页行为，新增人声入口卡片。
- `ui/navigation/PetTranslatorApp.kt`：将原 HomeViewModel.humanVoice 回调接到逗宠页，三 Tab 导航结构不变。

测试：RecordingViewModelTest、UncertainSheetFlowTest（新增）、HomeFlowTest、RecordingFlowTest、AdaptiveHomeTest（仅适配 HomeScreen 参数）。

工作区中 AnalysisScreen.kt、analysis_cloud_background.png、AnalysisLayoutTest.kt 以及较早的录音波形改动，均为本任务开始前已有工作，未在本任务中继续修改。YAMNet、阈值、96 条模板、结果页、保存分享和权益接口无本次改动。

## UNCERTAIN 新旧流程

旧：录音完成 → 分析 → UNCERTAIN → 独立“声音分析”失败界面。

新：录音完成并释放 AudioRecord → 分析 → 等待该会话已有 3～5 秒目标时长 → UNCERTAIN → 原录音页面 + 一次性底部提示。不是新的导航目的地，没有结果重复导航。

现有代码原先只对成功解释等待展示时长，UNCERTAIN 会提前结束。本次仅对 UNCERTAIN 补足同一会话的剩余时间，不重新随机时长、不重复推理；分析页 UI、进度计算和阶段上限未变。未识别不伪装成完整解释，阶段证据仍停留于原失败阶段。PET_MISMATCH 和 ANALYSIS_ERROR 原有结束时机保持不变。

弹窗显示由录音目的地的 ViewModel 终态驱动，没有全局单例事件。组合重组不会重复提交结果；尚未关闭时 Activity 重建可以恢复此会话的弹窗，关闭后状态变为 READY，重建不再弹出。进程死亡后新会话从 READY 开始，不重放遗留失败弹窗。

“重新录音”复用原 prepareAnotherRecording() 的权益检查，只重置状态，不启动采集。遮罩、下滑及系统返回均调用无权益副作用的 dismissUncertain()，清理上一段临时音频并回到待录音状态；重复调用不再产生作用。随后可正常返回首页。AudioRecord 的 stop/release 仍由原采集实现 finally 在推理前完成，未更改采集实现。

## 弹窗视觉

奶油粉背景、30dp 顶部圆角、柔和遮罩、声音图标和粉色圆角按钮。内容最小高度按窗口高度 25% 计算，加上 Material 拖动区及系统安全区域后，当前真机可见主体约占可用高度 34%。文字或大字体需要更多空间时自然增高，内容可滚动，按钮可达。系统导航区域由 Material 3 默认 Insets 处理。

真实麦克风截图：

![真实录音未识别弹窗](real-mic-sheet.png)

首页移除入口后（滚动至录音入口，六场景和 Tab 保留）：

![首页](a143-evidence/home.png)

逗宠新增入口：

![逗宠](a143-evidence/play.png)

`a143-evidence/uncertain-sheet.png` 是同机使用受控识别结果的交互测试截图；`real-mic-sheet.png` 来自真实 AudioRecord + 本地 YAMNet 的录音回归，二者已区分。

## 免费体验检查结论

当前 FeatureAccessManager 仅提供 canAccess()，DefaultFeatureAccessManager 恒定放行。项目中未发现已实现的首次免费完整解释次数、成功消费接口、已使用标记或本地权益持久化；DataStore 仅管理宠物选择。因此不存在“录音开始扣次数”的实现可迁移。本次没有新增扣减、发放、重置、VIP 或支付逻辑，也未修改接口。UNCERTAIN 不生成解释，不消费任何免费机会；重录复用已有访问检查，关闭弹窗不发放权益。

若未来接入免费次数持久化，成功生成完整解释后消费需要由商业化任务明确设计；本次不能宣称验证了尚不存在的持久化规则。

## 验证

- assembleDebug：通过。
- lintDebug：通过；最终日志 final-lint.txt。
- testDebugUnitTest：27 项通过，0 failure/error。包括成功解释、10 次 3～5 秒会话、缓存识别物种切换、录音中断和新增 UNCERTAIN 延迟/一次消费/不自动录音/拒绝权益及退出取消延迟。
- 真机共 5 项流程测试通过（device-tests.txt）：
  - 受控结果下正常成功结果页、物种不匹配切换（推理仅一次）、分析异常及重试仍走独立异常流程。
  - 弹窗重录、重组、返回键、点击遮罩、下滑关闭；每次关闭不增加采集或推理次数；关闭后返回页面。
  - 首页不存在入口，逗宠存在正式标题和副标题，点击仍出现“人声变宠物语开发中”；六场景、宠物选择、三个 Tab、返回及重建保留。
  - 真正麦克风手动停止、取消、10 秒自动结束、UNCERTAIN BottomSheet、Activity 重建后关闭；录音前后台和重建中断资源清理。
- 两倍字体弹窗可达性另 1 项通过（font-test.txt）：标题、副标题和重录按钮可通过滚动完整显示。
- git diff --check：通过。

构建及单元测试见 build.txt。旧录音截图辅助代码曾假设只有一个 Compose 根节点，BottomSheet 引入第二个窗口后失败；已去掉多余的单根截图预热，保留原整屏截图，全部录音回归已重跑通过。

## 尚未解决或未覆盖的范围

无已知阻塞。免费次数持久化尚未实现，已明确记录；未新增商业化实现。未对多台不同物理小屏设备逐一实测，已检查当前真机及两倍字体可滚动显示。真实宠物声音的 SUCCESS 准确率不属于本次测试：成功和物种不匹配边界采用受控识别输出验证原 UI 路径，真实环境录音返回 UNCERTAIN。

本任务完成后停止，不进入其他模块。

最终窗口适配复核：弹窗高度使用 LocalWindowInfo 实际容器高度计算，可随窗口变化更新，没有 Configuration.screenHeightDp 警告。最终 assembleDebug / lintDebug / assembleDebugAndroidTest 再次通过，lint 仅余 5 条既有警告。最终 UncertainSheetFlowTest 共 3 项重新通过，见 final-sheet-tests.txt，包含两倍字体、其他结果分支、重录/遮罩/下滑/返回的一次性消费。a143-evidence/uncertain-sheet.png 已更新为最终安装版本截图。
