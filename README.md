# 宠物翻译器

当前实现：A1.4（首页、三个 Tab、独立全屏录音页、真实 AudioRecord 采集、本地 YAMNet 声音识别、正式趣味文案库/规则引擎，以及分析页和解读结果页）。尚未实现登录、VIP 购买、支付或服务端。包名暂用开发者此前同意的 `com.example.pettranslator`，发布前需确认。

## 构建

- Kotlin 2.2.10 / Jetpack Compose / Material 3 / MVVM。
- minSdk 26，compileSdk / targetSdk 36，Gradle 8.13，AGP 8.11.0，JDK 17 或 21。
- Android Studio 打开根目录，设置自己的 local.properties 中 sdk.dir（不入库）。
- `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest`
- 连接已解锁设备后：`./gradlew :app:connectedDebugAndroidTest`。
- 多设备时可用 ANDROID_SERIAL 选择测试设备。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。历史 Release 产物不代表当前录音版本，尚无生产签名。

## 技术结构

- `ui/theme`：全局浅色主题、系统字体、粉蓝配色与尺寸。
- `ui/home`：沉浸式首页、宠物/场景选择、HomeViewModel 状态与事件。
- `ui/navigation`：三个 Tab、独立 recording/{pet}/{scene} 目的地、RecordingEntry。
- `ui/recording`：录音页、权限与生命周期适配、RecordingViewModel 状态机；停止与取消按钮固定可见。
- `ui/play` / `ui/profile`：基础占位页。
- `domain`：宠物/场景与偏好接口；`domain/recording`：音频采集接口和结果数据。
- `data`：DataStore；`data/recording`：AndroidPcmRecorder 后台采集。
- `domain/interpretation`：仅接受识别 SUCCESS 的结构化趣味解读、保守声音特征归纳、本地模板和版本化结果编解码。
- `core`：FeatureAccessManager，当前默认放行。
- `PetTranslatorApplication`：每个新进程首次启动时清理本模块遗留 PCM 缓存。

## 录音行为

首页先选择宠物和场景，点击开始录音翻译进入独立页面，录音页没有 Tab 栏。只有点击录音页的开始按钮才请求麦克风权限并启动采集。录音为 16000Hz、单声道、PCM16 little-endian，最长 10 秒，后台 IO 线程采集；计时来自采样数，另用单调时钟及最大采样数双重限制。

声波来自采集到的真实 RMS，无随机波形或非录音实时动画。手动停止与自动停止仅交付一次结果；取消、返回、前后台切换、失焦或重建中断都会释放采集并清理不完整文件。完成页只显示开发占位提示，没有 AI 结论。

音频仅放在 cacheDir/pet_recordings，不上传、不写公共存储。完成音频归录音页所有，重新录音/页面退出时失效；异常进程死亡遗留文件在下次进程启动清理，系统也可能提前回收缓存。下一阶段通过 `RecordingViewModel.completedInput()` 读取 RecordingInput（音频 + 宠物 + 场景），必须在页面释放前消费，并处理缓存被回收的情况。时长和文件存在不代表声音内容有效。

## 测试与交付

- [A1.2.1 录音交付记录](docs/A1.2.1-delivery.md)：状态机、数据格式、权限、生命周期、测试与截图。
- [A1.2.1.1 录音页视觉与声波精修](docs/A1.2.1.1-delivery.md)：房间背景、圆形录音操作、dBFS 声波映射及 Galaxy S10 截图。
- [A1.1 初始工程记录](docs/A1.1-delivery.md)
- [A1.1.1 视觉升级记录](docs/A1.1.1-delivery.md)
- [A1.1.2 首页精修记录](docs/A1.1.2-delivery.md)

素材均使用开发者提供的 PNG；源图有部分毛边和贴边内容，未擅自改变角色。场景常规为三列两行，窄屏或大字体回退两列。录音页沿用首页正式插画与奶油粉/雾霾蓝视觉。
