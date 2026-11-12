# 桌面图标白边修复

2026-11-12。

## 问题与修改

首轮将完整的带白底圆角源图缩进到 adaptive icon 内，又叠加白色背景，形成用户截图中的白色外框。首轮验收只核对主体可见性，没有拦截该缺陷。

- 保留原始 `source.png`。使用内置 image_gen 工具修补四周白色区域，生成无预置圆角外框、彩色背景铺到四边的 `source-full-bleed.png`；猫狗与声波保持原构图。
- `source-full-bleed.png` 的 SHA-256：`7CBCEFD58DC64A69022800800A888E12FCCB0479E016B04430604806030AFCA8`。
- adaptive icon 的图片由 66dp 扩展到完整的 72dp 可见区域。108dp 图层余下的 18dp/边为系统动效区域，底层改为粉蓝渐变，不再提供白底。
- PNG 后备图标直接铺满对应蒙版，删除原来的白色填充与额外 padding。
- 首页和分享卡片共用修复后的 512px 图片，由页面绘制圆角，不再使用图片自身的白色外框。
- `tools/GenerateAppIcons.ps1` 默认读取新的 full-bleed 源文件，可重建所有尺寸资源。

## 本轮验收

实际连接设备为 Pixel 3 / Android10 / Launcher3。本轮没有连接三星手机，因此不将本轮结果表述为三星实测。

- `assembleDebug`、`lintDebug`、`assembleDebugAndroidTest` 通过，lint 为 0 errors。
- 真机 4 项 instrumentation 检查通过：两项 `AppIconRenderingTest`，首页 `HomeVisualEvidenceTest`，分享导出长文回归。
- 新增图标回归直接加载实际的 AdaptiveIconDrawable，在设备提供的蒙版内侧采样边缘，检查白色占比、连续白环和透明缺口，并保存渲染 PNG。
- 普通/round 图标分别在 96px 和 192px 下采样 720 点，结果均为 `white=0.0, longestWhiteRun=0.0, nonOpaque=0`。
- 应用内共用图外圈 2044 个像素采样，结果同样全部为 0。
- 在同一启动器、相同位置，覆盖安装前后截图并放大比对四边：白色外框已消失，图像填满圆角图标；声波、猫狗面部和项圈保留。圆形 PNG 另做目视检查，主体可见，无白色外圈。
- 首页和分享卡片已实际渲染、截图核对。

证据：

- `white-border-comparison.png`：从同设备前后截图的相同像素区域裁切；仅放大和标注，不合成图标。
- `white-border-before.png`、`white-border-after.png`：完整启动器画面。
- `icon-border-evidence/`：实际 Android drawable 渲染及逐项指标。
- `white-border-device-tests.txt`：4 项设备检查原始输出。
- `white-border-home.png`、`white-border-share-card.png`：内部展示与实际导出。

修复 APK 已覆盖安装到 Pixel 3，另保存为 `artifacts/pettranslator-icon-no-white-border-debug.apk`。

## 图像编辑提示词

使用内置 `image_gen`，输入为原始 `source.png`，输出已复制到本项目 `docs/app-icon/source-full-bleed.png`。最终提示词如下：

```text
Edit target: the attached existing pet translator app icon. Use case: precise-object-edit. Create an Android adaptive-icon-ready FULL-BLEED SQUARE version of this exact illustration. Remove ONLY the outer white/off-white matte and the pre-rendered rounded-square outline: extend the existing pastel pink/lilac/blue gradient naturally all the way to all four square edges and corners. There must be NO white border, NO white surround, NO transparent border, NO rounded-square mask or mockup, NO inset framing, NO outer highlight rim, NO padding. Keep the cat, dog, facial expressions, poses, fur details, collars and medals, speech bubble, all seven sound-wave bars, and white decorative sound ticks in exactly the same locations, sizes, colors and style as the supplied artwork. Do not zoom the composition or crop any important subject features. Preserve the entire illustration; change only the narrow white outer matte and corner areas into continuation of the existing colorful art. This is a production asset replacement, not a redesign. Deliver a flat opaque square raster icon with colorful artwork reaching every outer pixel.
```
