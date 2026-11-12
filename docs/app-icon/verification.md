# 应用图标替换

> 首轮交付漏检了桌面白边。本文件保留首轮记录；当前资源与验收结果以 [白边修复记录](white-border-fix.md) 为准。

2026-11-12，使用用户提供的 `E:/Download/pettranslator-icon.png`。

原图备份为 `source.png`，1254×1254，SHA-256：
`3BB90E89FDD499E2FCF366B8F34608A8FCB5FDBEF17DA23AA6CFFE43E519BF0C`。
备份与用户原文件校验值一致。`tools/GenerateAppIcons.ps1` 可重新生成所有 PNG 资源。

## 替换范围

- Manifest 改为标准 mipmap 应用图标，并配置 roundIcon。删除旧粉色爪印 launcher vector。
- 常规和圆形 adaptive icon 共用新图片前景与白色背景；四边使用 19.444444% 的相对留白，108dp 图层内保留 66dp 原图。比例留白随实际绘制大小缩放。
- 五档 PNG 后备资源为 48、72、96、144、192px，分别使用圆角和圆形 launcher 蒙版。项目最低 API26，正常展示使用 adaptive icon。
- 应用内共用 512px PNG；首页名称前的品牌标识保持原 30dp 大小。
- 导出/分享卡片品牌区加入同款 72px 图标，品牌文字右移，正文布局不变。
- 系统启动页、最近任务、应用信息等读取 Manifest 应用图标的位置随资源同步。没有额外自定义启动页或通知图标。
- 保留宠物资料爪印、导航、麦克风、场景和宠物插画；这些图不承担应用品牌标识功能。

本次保留彩色原图，没有另外设计 Android 主题单色图标；lint 的 monochrome 建议不影响彩色图标显示。

## 验证

- `assembleDebug`、`lintDebug`、`testDebugUnitTest` 通过；27 项单元测试，0 失败。
- Samsung SM-S9010 / Android15：`HomeFlowTest`、`HomeVisualEvidenceTest` 和 `ResultCardFontExportTest#longTextAndColoredEmojiRemainInsideExpandedExport`，共 3 项设备测试通过。原始结果见 `device-tests.xml`。
- 使用实际生产导出方法重新输出压力测试分享卡片并目视核对品牌区，1 项测试通过；卡片正文是测试夹具，不是新增正式文案。
- 最终整理后重新构建与运行 lint，确认 PNG 后备资源和目录配置可编译。
- 新 APK 覆盖安装到三星测试机。

截图：`home-samsung.png` 为实际首页；`launcher-samsung.png` 为三星启动器搜索推荐区；`share-card.png` 为实际导出的长文压力测试卡片。均已目视检查，新图中的猫狗面部、声波和品牌文字可见，无拉伸或文字重叠。圆形资源已生成，未另切换设备启动器蒙版。

构建期间工作区新增了 `drawable-nodpi/Pastel Cloud Pet App Frame.png`，该文件名不符合 Android 资源规则。保留图片内容，仅将其重命名为 `pastel_cloud_pet_app_frame.png`，未将它接入任何页面。

安装包：`app/build/outputs/apk/debug/app-debug.apk`（Debug 签名）。
