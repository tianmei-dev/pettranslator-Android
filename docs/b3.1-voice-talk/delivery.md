# B3.1 交付记录（2026-11-17）

代码已完成一次实现和构建，但本轮真机录音验收未完成，不能标记为完整通过。已按约定停止，不再修改、构建、安装或补拍。全部工作保留在 main，未提交、未推送。

1. **文件范围**

   新增生产代码：
   - `app/src/main/java/com/example/pettranslator/ui/voice/VoiceTalkScreen.kt`：独立页面、权限请求及生命周期接线。
   - `app/src/main/java/com/example/pettranslator/ui/voice/VoiceTalkViewModel.kt`：页面专用真实录音状态管理。
   - `app/src/main/java/com/example/pettranslator/ui/voice/ExclusiveVoiceAudioCapture.kt`：本页面采集实例互斥、会话清理与有限诊断。
   - `app/src/main/java/com/example/pettranslator/ui/voice/VoiceTalkStateComponents.kt`：波形、转换中和结果预留组件。

   修改现有文件：
   - `app/src/main/java/com/example/pettranslator/ui/play/PlayScreen.kt`：仅将主入口回调由提示改为打开录音页。
   - `app/src/main/java/com/example/pettranslator/ui/navigation/PetTranslatorApp.kt`：新增独立目的地、门控检查及返回接线；录音页隐藏底部 Tab。
   - `app/src/androidTest/java/com/example/pettranslator/PetFunFlowTest.kt`：更新直接受入口变化影响的断言。

   新增验证文件：
   - `app/src/test/java/com/example/pettranslator/VoiceTalkViewModelTest.kt`
   - `app/src/androidTest/java/com/example/pettranslator/VoiceTalkDeviceTest.kt`
   - `tools/b31_voice_acceptance.py`
   - 本目录构建、验证日志及交付记录。

   用户预先放入的两张 PNG 保持原文件不变：`voice_talk_header_listening.png` 用于顶部等比插画；`voice_talk_converting_paw.png` 仅用于转换中装饰。没有生成或下载图片。

2. **录音及状态实现**

   复用现有 `AndroidPcmRecorder`，未修改首页录音器。参数沿用 16kHz、单声道、PCM16、最长 10 秒。计时和波形来自真实 `CaptureFrame` 的样本时长及 RMS；波形复用项目已有 `rmsToWaveLevel()`。最短 800ms 仅判断总时长，不声称检测到了人声。

   生产状态为 READY → STARTING → RECORDING → STOPPING → RECORDED，异常进入 ERROR。首个真实帧到来后才显示正在录音。有效结束仅显示“录音完成啦！趣味转换功能正在准备中～”，可以重新录制或返回。

   复用现有 `PetPreferences` 持久化状态，初次读取前不建立另一套默认宠物；录音或宠物写入期间禁止切换。保留逗宠页原来的局部音效分类选择逻辑。

   页面检查现有 PrivacyGate、FeatureAccessManager 及麦克风权限。重复开始受状态和会话互斥保护；返回等待停止清理，后台或页面离开中断录音且不自动恢复。临时 PCM 只处理当前委托采集器自己的会话，采集释放后清理；没有修改原录音文件生命周期或其他模块文件。

   CONVERTING、RESULT 仅提供独立预览/隔离测试组件，生产流程没有接入。结果组件预留音源列表与播放状态输入；缺少有效音源时禁用播放/重播，不展示转换成功。本轮没有音频分析、合成或播放转换结果。

3. **一次构建及针对性单元测试**

   命令：`./gradlew.bat --offline :app:assembleDebug :app:testDebugUnitTest --tests com.example.pettranslator.VoiceTalkViewModelTest :app:assembleDebugAndroidTest`

   一次构建成功，用时 1m23s，没有重新构建。10 项新状态机单元测试全部通过，0 失败、0 跳过。覆盖真实帧驱动、短录音、有效结束、中断、权限错误、门控、宠物持久化及清理等待等软件路径；这些测试使用替身，不等同于真机麦克风验收。

   日志：`build.txt`、`unit-tests.xml`。

   APK：`D:\pettranslator\artifacts\pettranslator-b3.1-voice-talk-debug.apk`

   SHA-256：`85FAC5DD118BCDA1FB8298C1A3D1B7F2320C8DF2F36ADEFE9790ED51AFB564C0`

4. **一轮真机验收及明确限制**

   设备：Samsung SM-G973U，Android API 31，字体比例 1.1。主 APK 安装一次成功。测试 APK 部署前 ADB 服务曾中断，恢复后部署成功，没有重新构建或重新安装主 APK。设备不支持预检使用的 `pm clear-permission-flags`，已在正式测试开始前移除该命令并核对原始权限恢复；随后只启动了一轮、共两个 instrumentation 测试。

   - 已验证：逗宠主入口进入新页面；狗狗、猫咪切换写入既有持久化状态流。
   - 主流程失败：拍摄首张待录音截图前，UiAutomation 前台窗口持续为 null，保护检查超时停止。此时尚未点击录音，尚未执行系统权限按钮测试。
   - **未验证：生产页面完整视觉、真实权限拒绝/允许、真实麦克风采集与波形、手动停止、10 秒自动停止，以及硬件上的后台/返回资源释放。**不能据此宣称真实录音已验收。
   - 隔离 UI 测试通过：狗语转换中装饰、空音源结果禁播。两张截图明确标注“隔离UI预览，不执行转换或播放”，不是生产录音或真实转换截图。
   - 汇总：2 个真机测试，1 通过、1 失败；没有再次测试。

   截图实际保存位置：
   - `D:\pettranslator\docs\b3.1-voice-talk\device\b31-voice-evidence\voice-converting-isolated.png`
   - `D:\pettranslator\docs\b3.1-voice-talk\device\b31-voice-evidence\voice-result-unavailable-isolated.png`

   **没有获得待录音、录音中或录音完成的有效生产页面截图。**

   原始记录：`device/instrumentation.txt`、`device/voice-logcat.txt`、`device/b31-voice-evidence/voice-flow.txt`、`device/b31-voice-evidence/voice-isolated.txt`、`device-run.txt`。未产生真实采集事件，因此不能用日志证明硬件录音成功。

5. **数据保护及剩余问题**

   只在验收隔离期间备份/恢复指定的隐私同意和宠物偏好文件；原始文件字节校验一致，原有麦克风授权及用户权限标志已恢复，未清空 App 数据。详见 `device/state-restoration.json`。

   对启动时记录的 145 个受保护文件完成哈希核对，未发现范围外变更，包括用户 PNG 素材、首页、我的页面、现有录音/分析、隐私机制及配置。没有修改 Manifest、Gradle、全局主题或业务权益接口。核对结果见 `validation.json`、`scope-check.json`。

   剩余阻塞是本轮真机自动化前台窗口检查失败，尚未完成核心录音和主页面视觉验收。按执行轮次限制保留当前成果并停止，未擅自修复或开始下一轮验收。B3.2 分析、转换、真实音源播放仍待后续开发。
