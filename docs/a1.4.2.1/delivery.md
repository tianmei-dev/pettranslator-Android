# A1.4.2.1 录音波形

仅修改 RecordingScreen.kt 内 RecordingWaveform：单 Canvas、41 根连续圆角柱条、最大 240dp 且不超过父容器可用宽度 70%、72dp 高度、14–68dp 对称柱高、统一粉紫蓝水平渐变。动画沿用录音 active 生命周期，以连续正弦行波驱动，不读取 PCM，不生成随机高度。

验证：Debug APK 编译成功；通过 adb 直接执行现有 RecordingFlowTest#catManualDogAutoAndScreenshots，2 次均 OK (1 test)，覆盖真机手动停止、取消、10 秒自动结束及分析结果到达。Gradle connected 测试调度因本机缺少离线 UTP 依赖而未启动，因此使用已构建的测试 APK 直接执行。

截图：waveform-full.png 为本次真机狗狗录音第 8 秒，向上滚动内容后完整展示波形。cat-recording.png / dog-recording.png 为测试自动生成的初始滚动位置截图。现有页面在该设备初始位置会裁切波形下部，滚动后可完整查看；本次依照范围限制未修改页面布局或其他 UI。
