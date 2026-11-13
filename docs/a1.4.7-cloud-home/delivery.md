# A1.4.7 六场景环绕范围修正

已仅展开四个侧边按钮的水平位置，恢复宽展的椭圆环绕范围。新版已安装到 S22，猫咪、狗狗真机截图均已验证，未见新增重叠、截字或越界。

## 实际锚点

已先核实 A1.4.6 的实际值，当前 S22 原来为约 30% / 70%，并非 32% / 68%。本轮坐标表示按钮中心相对于云朵容器的比例。

| 场景 | A1.4.6 实际 X | A1.4.7 实际 X | Y 变化 | 实际水平位移 |
| --- | --- | --- | --- | --- |
| 门口或窗边 | 30.03% | 24.04% | 0 | 左 21.33dp |
| 玩耍 | 70.02% | 76.01% | 0 | 右 21.33dp |
| 独处 | 30.03% | 26.01% | 0 | 左 14.33dp |
| 不确定 | 70.02% | 74.04% | 0 | 右 14.33dp |

源码目标精确采用 24% / 76%、26% / 74%；实测小数差异来自像素取整。上侧两个按钮的横向中心距约增加 30%，下侧约增加 20%。

“吃饭前后”“被抱起”的完整坐标完全不变。中央猫狗的坐标、宽高和资源完全不变；所有按钮 Y、宽高均与 A1.4.6 相同。

## 修改范围

生产文件仅修改：

- `app/src/main/java/com/example/pettranslator/ui/home/HomeResponsiveDimensions.kt`

在原尺寸和全部坐标计算完成后，只复制四个 `xFraction`。大字体的画布高度仍用原锚点计算，避免横向展开连带缩短画布、改变 Y 或移动中轴。极大字体保留半径 +4dp 的容器边缘限制；当前 S22 及 320dp / 2 倍字体都无需触发限制，均采用目标 X。

验收文件仅更新 `app/src/androidTest/java/com/example/pettranslator/CloudHomeLayoutTest.kt` 的四个 X 期望值；原 Y、中心、完整点击矩形、像素避让与真实触摸断言保留。

`HomeScreen.kt`、顶部插画、宠物选择区、云朵背景及容器尺寸、录音按钮、免责声明、底部导航、全部业务逻辑和其他页面均未修改。未改 Gradle、Manifest、依赖、App Icon 或素材，也未进行业务重构。

## 验收结果

- Debug / AndroidTest 构建通过，见 `build-tests.txt`。
- 27 项单元测试通过，0 失败/错误，见 `unit-tests.txt`。
- Lint：0 errors / 9 warnings，见 `lint.txt`。
- S22 真机 12 项 UI 测试通过，耗时 54.596 秒，见 `device-tests.txt`。
- 猫狗切换、六场景选择、六个靠萌宠侧的真实触摸、原 ready 门控、VIP 禁用语义、录音入口、底部导航及选择保留检查通过。未执行实际录音或外部服务测试。
- 六个完整点击矩形独立、≥48dp、不交叠；圆形边缘外扩 4dp 与猫狗可见像素无碰撞。

`layout-verification.json` 逐项对比同一 S22 的前后记录：除了四个侧钮 X，其他运行指标完全一致。另比较 360×720、393×800、412×900、411 参考视口与 320dp / 字体 2 的六个滚动位置共 10 组记录，云朵、宠物、固定录音区、所有场景 Y 和尺寸均不变。

## 图片与交付

- `device-home-cat.png`：最新猫咪真机整屏原图。
- `device-home-dog.png`：最新狗狗真机整屏原图。
- `before-after-orbit.png`：A1.4.6 / A1.4.7 同机猫狗云朵区域对照。
- `device-anchor-metrics.txt`、`device-visual-metrics.txt`、`device-layout.txt`：实际锚点及完整布局记录。
- `device/cloud-home-evidence/`：运行在 S22 上的受控尺寸、猫狗、大字体和触摸证据；含测试导航占位的截图为受控 Compose 视口。

未发现本轮新增的未解决问题。既有风格、装饰和素材特征保持不变。

始终在 `feature/a1.4-cloud-home`，未切换、提交、推送、合并、重置或清理分支。main 和 HEAD 保持 `55948715fc6ca202c34f955ca9f12b538bd80e5f`。本轮开始时的备份及范围基线位于 `artifacts/a1.4.7-cloud-home/`，最终校验见 `scope-verification.json`。

测试专用包已移除，手机保留新版 App 首页。最终测试后生产源码未再修改。

APK：`D:/pettranslator/artifacts/pettranslator-a1.4.7-cloud-home-debug.apk`。

SHA-256：`8EBF8FE11740493E1D19298EFA8723C85B361E36D776FED7A2701DD8997CBCEE`。
