# A1.4.8 首页六场景环绕布局定向修正

已在 `feature/a1.4-cloud-home` 完成普通模式的指定垂直聚拢。保持现有 BoxWithConstraints 相对定位；没有重新设计布局。`adjustSceneOrbit()` 原样使用中心 `.54f`、系数 `.72f`，执行在 `spreadHorizontally(safeAnchors, width)` 之后。扩展模式分支保持逐段相同。

本轮实际源码修改只有：

- `D:\pettranslator\app\src\main\java\com\example\pettranslator\ui\home\HomeResponsiveDimensions.kt`：指定公式，以及仅在四侧按钮与现有萌宠轮廓相交时执行的 Y 安全退让。
- `D:\pettranslator\app\src\androidTest\java\com\example\pettranslator\CloudHomeLayoutTest.kt`：适配新的普通模式 Y 范围；用实际像素重叠判定普通模式；补测窄屏 1.15 字号、宽屏及每种普通视口的狗狗状态。扩展模式继续检查 4dp 轮廓间距。

其余新增文件为 `D:\pettranslator\docs\a1.4.8-cloud-home` 的截图、日志和报告，以及 `D:\pettranslator\artifacts\a1.4.8-cloud-home` 的本轮修改前快照。工作区原有其他未提交修改均保留。

## 最终真机坐标

设备为本轮实际连接的 **Pixel 3**，1080×2160、440dpi、字体 1.0。S22 当前没有连接，本轮未声称在 S22 复拍。以下为元素中心相对云朵容器的实测百分比，含像素取整；猫狗切换时按钮坐标完全相同。

| 场景 | 修改前 X/Y | 最终 X/Y | 实测移动 |
| --- | --- | --- | --- |
| 门口或窗边 | 24.0412% / 36.0043% | **24.0412% / 41.0256%** | 下移 17.0909dp |
| 玩耍 | 76.0524% / 36.0043% | **76.0524% / 41.0256%** | 下移 17.0909dp |
| 独处 | 25.9588% / 73.9850% | **25.9588% / 68.4295%** | 上移 18.9091dp |
| 不确定 | 73.9944% / 74.0385% | **73.9944% / 68.3761%** | 上移 19.2727dp |

代码 X 目标仍为 `.24/.76/.26/.74`。同台设备的修改前证据来自 A1.4.7 水平展开及此前已完成顶部品牌移除后的首页，不将旧 S22 截图与当前 Pixel 3 原始像素直接比较。

中轴和萌宠坐标原样保持：吃饭前后约 `(50%, 29.0064%)`，被抱起约 `(49.9532%, 81.9444%)`，中央萌宠约 `(50.0468%, 54.0064%)`。吃饭按钮已有的标题安全下限仍有效，没有强行覆盖为 22%。云朵保持 `388.7273 × 340.3637dp`，中央萌宠容器保持 `190.5454 × 91.2727dp`。顶部、宠物选择、标题、录音区及免责声明均未改变。

精确测量见 `D:\pettranslator\docs\a1.4.8-cloud-home\device\anchor-metrics.txt`、`device\layout.txt` 和 `layout-verification.json`。比较结果：只有四个侧边按钮的 top/bottom 改变，所有 X、宽高及其他测量区域均相同。

## 必要的碰撞保护

先按指定公式构建并测试。当前 Pixel 3 的猫狗候选均无实际碰撞，最终保留指定公式结果；独立轮廓检查最紧间距约 4.26dp。

初次适配测试有 4 项失败。进一步按原图 alpha≥16 像素单元检查，确认部分较大普通视口的下侧按钮确实与萌宠重叠，最深约 9dp；窄屏普通模式 1.15 字号也有上侧碰撞。没有用统一新系数或新坐标去替换用户公式。

安全处理只作用于普通模式四侧按钮：使用两张现有猫狗图片 alpha≥16 像素单元的凸包，按现有 ContentScale.Fit 和图片尺寸映射；仅当指定候选与轮廓相交时，沿 Y 在候选位置与原来的安全位置之间二分，取最近的安全位置，并保留 1dp 取整余量。两种宠物同时参与判定，所以切换宠物不会导致按钮跳动。X、中轴按钮、萌宠位置/尺寸、按钮直径和云朵大小均不参与调整。

轮廓数组只是从未修改 PNG 提取的碰撞边界，不是新图片素材、业务状态或交互逻辑。凸包会对原图局部凹陷稍保守；Pixel 3 与 S22 的既有普通视口离线候选均不触发避让。复核的已知碰撞尺寸组合在避让后保留至少约 1dp 的轮廓间距。

## 构建与验收

- 离线 `assembleDebug`、`assembleDebugAndroidTest`、`testDebugUnitTest`、`lintDebug` 通过。
- 单元测试 **27/27** 通过；lint **0 errors / 9 项既有 warnings**，没有为修复这些无关项修改其他文件。
- 最终 Pixel 3 instrumentation **14/14** 通过，耗时 72.695 秒。覆盖正常窄/标准/高/宽视口、1.15 字号普通模式、参考视口、字体 2.0 滚动、猫狗切换、六场景完整点击区域、宠物朝向一侧的实际触摸、原回调、就绪禁用状态、VIP 状态、底部导航和录音/免责声明可达性。
- PNG alpha≥16 可见像素不得覆盖场景圆形边缘；六个完整方形点击区域两两无交集，保留至少 48dp；标题正常显示，文字无截断。猫狗分别验证、截图。
- 相同设备前后测量确认水平展开范围未缩小，其他 UI 区域和所有尺寸不变。
- 109 个受保护文件哈希全部一致；业务、其他页面、HomeScreen、资源、Gradle、Manifest、底部导航均没有本轮修改。
- 分支未切换、未合并、未提交。HEAD 与 main 仍为 `55948715fc6ca202c34f955ca9f12b538bd80e5f`。未执行重置或清理 Git 操作。
- 已清理测试包，保留本轮 App 安装并打开首页。

日志：`D:\pettranslator\docs\a1.4.8-cloud-home\build-final.txt`、`build-ui-final.txt`、`unit-tests.txt`、`lint.txt`、`device-tests.txt`、`scope-verification.json`。初次失败日志保存在 `device-tests-initial.txt`，没有覆盖为成功结果。`device-layout` 是受控 Compose 视口截图，不能当作对应型号的真实设备截图。

## 真机截图与安装包

- 猫咪：`D:\pettranslator\docs\a1.4.8-cloud-home\device-home-cat.png`
- 狗狗：`D:\pettranslator\docs\a1.4.8-cloud-home\device-home-dog.png`
- 修改前同设备：`D:\pettranslator\docs\a1.4.8-cloud-home\before-cat.png`、`before-dog.png`
- APK：`D:\pettranslator\artifacts\pettranslator-a1.4.8-cloud-home-debug.apk`
- APK SHA256：`F03F6119F471F7085BB7E0DBBD9413791D458959ACCF1F4D01CF6666AEE48491`

已测试范围未发现剩余重叠、裁切或点击问题。S22 本轮仅做历史测量数据的离线轮廓复核，最新真机截图为 Pixel 3；没有将受控视口或历史图片当作本轮 S22 实测。
