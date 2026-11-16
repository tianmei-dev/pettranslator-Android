# A1.4.4 首页局部布局微调

本轮完成两项局部位置调整，已在 S22（SM-S9010，1080×2340、480dpi、字体 1.0）分别验证猫咪、狗狗并保存整屏截图。12 项真机 UI 测试、27 项单元测试通过，Lint 为 0 errors / 9 warnings。

## 实际修改的首页 UI 文件

| 文件 | 本轮差异 |
| --- | --- |
| `app/src/main/java/com/example/pettranslator/ui/home/HomeResponsiveDimensions.kt` | 在原布局基础上仅平移四个侧边场景按钮。先保留原中央萌宠位置和尺寸，再应用四侧位移；吃饭前后、被抱起保留原坐标。 |
| `app/src/main/java/com/example/pettranslator/ui/home/HomeScreen.kt` | 仅将宠物选项的横向间距由固定 12dp 改为自适应 12–36dp。选项宽高、样式、标题和回调均保持原样。 |

另调整两个首页验收测试文件：`CloudHomeLayoutTest.kt`、`HomeVisualEvidenceTest.kt`。补充实际透明通道像素与点击矩形的遮挡检测、靠近萌宠一侧的真实触摸检查，以及完整布局坐标记录。新增证据目录 `docs/a1.4.4-cloud-home/`，构建产物保存在 `artifacts/`。

没有修改 ViewModel、Repository、DataStore、任何业务回调或状态逻辑、录音和权限、VIP、导航、其他页面、云朵/顶部插画等素材、App Icon、Gradle、Manifest 或依赖。未添加素材或装饰。

## 位移说明

普通字体的原错落布局保留，四侧按钮仅作以下平移：

| 场景 | 方向 | 当前 S22 约移动量 |
| --- | --- | --- |
| 门口或窗边 | 右下 | 右 5.7dp、下 8.3dp |
| 玩耍 | 左下 | 左 5.7dp、下 8.3dp |
| 独处 | 右上 | 右 5.7dp、上 8.3dp |
| 不确定 | 左上 | 左 5.7dp、上 8.3dp |
| 吃饭前后、被抱起 | 不移动 | 0dp |

自适应策略：横移 `min(6dp, 云朵宽×0.016)`，纵移 `min(10dp, 云朵高×0.025)`，最终坐标有像素取整。411×869dp / 字体 1.1 受控视口的实测位移约为横 6.09dp、纵 9.89dp；四个常见视口的前后坐标比较见 `layout-deltas.json`。

宠物选项间距在通常宽度由 12dp 增为 36dp，即狗狗整体左移 12dp、猫咪整体右移 12dp。当前 S22 实测左边界分别为 48dp / 198dp；两项宽度仍为 114dp，高度仍为 48dp，外侧边距均为 48dp。

较窄屏幕或大字体时，间距上限同时受 `可用宽−两项总宽−32dp` 约束，两侧至少保留 16dp。320dp / 字体 2 延续原 12dp 间距，避免挤压或贴边。大字体的原可滚动加长云朵布局保持不变。

## 保持不变的几何与交互

- 当前真机中央萌宠框仍为 `(92.67,399.67)–(267.33,487.00)dp`。猫、狗各自图片框、实际主体尺寸与 A1.4.3 的记录完全一致。
- 真机顶部、宠物区域整体高度、云朵、录音区的原六项布局记录逐行一致。四个受控视口中中央萌宠、云朵、可用区域和固定录音区坐标相同；两个中轴场景的完整矩形逐项相同。
- 六个场景仍为独立的 Compose 单选控件，原顺序、ID、图标、文字、选中反馈及点击回调不变。
- 装饰图片的透明父框允许接近按钮；采用源图片 alpha≥16 的真实可见像素检测，确认这些像素不覆盖按钮的完整点击矩形。六个按钮自身的点击矩形仍互不重叠。
- 保留原 ready 门控、VIP 暂不可用状态、录音入口参数、底部导航和 Activity 重建后的选择状态。未执行实际录音或外部服务测试。

## 测试与截图

| 验收 | 结果 |
| --- | --- |
| Debug APK 构建及单元测试 | 通过；27 项单元测试，0 失败/错误。见 `build-unit-tests.txt`、`unit-tests.txt`。 |
| AndroidTest APK / Lint | 构建通过；0 errors / 9 warnings。见 `build-ui-tests-lint.txt`、`lint.txt`。 |
| S22 真机 UI | 12 项通过，耗时 54.621 秒。见 `device-tests.txt`。 |
| 猫狗切换、六环境选择 | 状态对应、回调和互不重叠检查通过。另以真实触摸验证四侧按钮靠萌宠一侧的点击仍生效。 |
| 常见尺寸与大字体 | 360×720、393×800、412×900、参考视口、320dp / 字体 2 的既有用例通过；普通字体一屏完整，大字体可滚动到全部六项，录音和免责声明保持固定。 |
| 截图视觉复核 | 猫狗均无新增文字截断、萌宠遮挡、按钮互相重叠或内容溢出；四侧按钮环绕感增强，宠物选项中间留白增大。 |

最新真机截图：

- `device-home-cat.png`
- `device-home-dog.png`
- `review-device.png`：目标 / 本轮猫咪 / 本轮狗狗对照。
- `device-layout.txt`、`device-visual-metrics.txt`：实际 App 几何记录。
- `device/cloud-home-evidence/`：受控尺寸、两种宠物、点击和大字体逐项截图。带测试导航占位文字的图片为受控 Compose 渲染，不是额外真机型号。
- `before-cat.png`、`before-dog.png`：上一轮 S22 实际截图，用于前后对照。

没有发现本轮新增的未解决布局问题。现有素材轮廓、装饰位置、猫咪原素材耳尖贴边，以及原有轻量浮雕风格均保留。

## Git 和交付

始终在 `feature/a1.4-cloud-home`。没有切换、提交、推送、合并、重置或清理分支，`main` 和 HEAD 保持 `55948715fc6ca202c34f955ca9f12b538bd80e5f`。工作区保留前几轮未提交内容；本轮修改以 `artifacts/a1.4.4-cloud-home/*.before` 为基线核对。88 个受保护生产/资源/配置文件的 SHA-256 均不变，见 `scope-verification.json`。

已在手机安装新版 App，测试专用包已卸载，App 保留在首页。生产代码在最终构建及真机测试后未再修改。

APK：`D:/pettranslator/artifacts/pettranslator-a1.4.4-cloud-home-debug.apk`。

SHA-256：`10FD8B93C1C3AA0CA97C1CFD7DDF0746E8D38097384A6C2F8E7DAC0511A7B91E`。
