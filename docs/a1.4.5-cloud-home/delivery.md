# A1.4.5 六场景环绕布局

本轮将六个场景一起向中央收拢，保留中央猫狗位置、尺寸及其余全部首页区域。构建、27 项单元测试、12 项模拟器 UI 测试、12 项 S22 真机 UI 测试均通过。猫咪和狗狗真机截图已分别检查，未见按钮碰撞、萌宠遮挡、截字或内容溢出。

## 本轮坐标调整

相对 A1.4.4，在当前 S22 的普通字体布局中采用：

| 场景 | 横向变化 | 纵向变化 |
| --- | --- | --- |
| 门口或窗边 | 右 8dp | 下 18dp |
| 吃饭前后 | 0 | 下 12dp |
| 玩耍 | 左 8dp | 下 18dp |
| 独处 | 右 8dp | 上 12dp |
| 被抱起 | 0 | 上 12dp |
| 不确定 | 左 8dp | 上 12dp |

这是本轮相对上一轮的额外位移，已由同一台 S22 前后运行记录逐项核对，六个按钮宽高变化均为 0，见 `layout-deltas.json`。较小可用区域按云朵宽、高限制位移幅度：额外横移为 `min(8dp, 宽×0.023)`；上方两侧额外下移 `min(18dp, 高×0.054)`；中轴和下方额外纵移 `min(12dp, 高×0.036)`。最终坐标会按物理像素取整。大字体原有的可滚动加长云朵分支保持不变。

六个按钮形成近似椭圆：上侧两个靠近头部两侧，下侧两个靠近身体两侧，中轴两个从正上、正下靠近，保留错落差异。按钮大小、文字、图标、声明顺序和点击范围均不改变。

## 修改范围

生产代码仅修改：

- `app/src/main/java/com/example/pettranslator/ui/home/HomeResponsiveDimensions.kt`：仅调整最终返回的六个场景坐标。中央图片的原坐标和宽高计算仍在这些平移之前完成，未改变。

另修改两个首页验收测试文件：

- `app/src/androidTest/java/com/example/pettranslator/CloudHomeLayoutTest.kt`
- `app/src/androidTest/java/com/example/pettranslator/HomeVisualEvidenceTest.kt`

验收按圆钮真实圆形外缘加 4dp 空隙，与源图片 alpha≥16 的可见像素检查碰撞，避免把圆形外侧的透明方框角误当作可见按钮；保留六个完整点击矩形互不交叠的检查。六个按钮靠萌宠一侧均使用真实触摸验证，原回调次序、单选状态、ready 门控和导航检查保留。

`HomeScreen.kt` 完全未改，选择宠物区域沿用 A1.4.4。顶部插画、中央萌宠、云朵素材和外框、录音按钮、免责声明、底部导航、所有其他页面、ViewModel、Repository、DataStore、业务状态、录音权限、VIP、Gradle、Manifest、依赖及 App Icon 均未改。

## 验证记录

| 检查 | 结果 |
| --- | --- |
| Debug APK / AndroidTest APK | 构建通过，见 `build-tests.txt`、`build-ui-tests.txt`。 |
| 单元测试 | 27 项通过，0 失败/错误；见 `unit-tests.txt`。 |
| Lint | 0 errors / 9 warnings，见 `lint.txt`。 |
| 几何预检查 | S22、360×720、393×800、411 参考视口、412×900 的猫狗与六钮共 60 组关系通过圆形外缘 +4dp 检查；六个按钮的完整点击矩形互不碰撞。 |
| 模拟器 UI | 12 项通过，耗时 72.918 秒，见 `emulator-tests.txt`。 |
| S22 真机 UI | 12 项通过，耗时 53.178 秒，见 `device-tests.txt`。 |
| 猫狗截图 | 均通过人工视觉复核：六钮比上一轮更集中，形成椭圆环绕，文字和图片没有新增裁切或遮挡。 |
| 六场景真实触摸 | 六个靠萌宠一侧的触摸位置均能选择对应场景，精确回调序列与单选状态检查通过。 |
| 原交互与适配 | 猫狗选择、六环境选择、ready 门控、原 VIP 禁用状态、录音入口、底部导航、重建后选择保留和大字体滚动检查通过。未执行实际录音或外部服务测试。 |

几何预检查使用上一轮记录的视口与图片 Fit 映射，仅用于选择安全位置，不代替本轮运行截图。最紧的 393 视口狗狗 / 独处组合，估算白圆与源像素距离约 5.88dp。

S22 实际运行的 `device-visual-metrics.txt` 与上一轮相比，除六个 `scene_` 坐标外所有其他行完全相同；`device-layout.txt` 中除六场景外的全部布局记录也相同。中央猫狗图片框、alpha 主体尺寸、宠物选项、标题、顶部、云朵及固定录音区均保持原样。

最新图片与记录：

- `device-home-cat.png`：猫咪状态 S22 整屏原图。
- `device-home-dog.png`：狗狗状态 S22 整屏原图。
- `review-device.png`：目标 / 本轮猫咪 / 本轮狗狗对照。
- `before-after-orbit.png`：同一台真机 A1.4.4 / A1.4.5 的猫狗云朵区域裁图对照。
- `device-layout.txt`、`device-visual-metrics.txt`、`layout-deltas.json`：完整坐标及前后差异。
- `device/cloud-home-evidence/`：真机上运行的常见尺寸、参考视口、大字体逐项与点击验证截图。带测试导航占位文字的图为受控 Compose 视口，不是另一台物理设备。
- `emulator-home-cat.png`、`emulator-home-dog.png`、`emulator/`：本轮模拟器证据，明确与真机原图区分。

未发现本轮新增的待解决布局问题。现有素材轮廓与装饰、轻量按钮浮雕、猫咪原素材耳尖贴边等已有特征保持不变。

## 交付与分支

分支始终为 `feature/a1.4-cloud-home`，未切换、提交、推送、合并、重置或清理分支。`main` 和 HEAD 保持 `55948715fc6ca202c34f955ca9f12b538bd80e5f`。前几轮未提交内容保留；本轮以 `artifacts/a1.4.5-cloud-home/*.before` 和 `baseline.json` 核对范围。

除一个布局文件和两个验收测试文件外，108 个受保护文件的 SHA-256 不变。`HomeScreen.kt` 也包含在受保护范围，证据见 `scope-verification.json`。生产源码在最终构建和截图测试后未再修改。

新版已安装到 S22，测试专用包已卸载，App 留在首页；本次启动的只读模拟器已关闭。

APK：`D:/pettranslator/artifacts/pettranslator-a1.4.5-cloud-home-debug.apk`。

SHA-256：`5765F82A0B087AD2B792BED8657B3461C6703E6055EEECB71ADA49C45BC3982E`。
