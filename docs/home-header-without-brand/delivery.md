# 首页顶部品牌元素移除

已移除首页顶部 App 图标和“猫言狗语翻译官”名称，保留猫狗插画、VIP 入口及原功能。桌面图标、应用名称资源和其他页面未改。

生产修改仅为 `HomeScreen.kt`：删除私有 Brand 组件，HomeHeader 保留右侧 VIP 和普通字体原有的最小 44dp 高度。同步更新 `HomeVisualEvidenceTest.kt` 和 `FinalFontLayoutTest.kt` 中失效的品牌断言，分析页、结果页测试内容保留。

Debug APK 与 AndroidTest APK 构建通过。Pixel 3（1080×2160、440dpi、字体1.0）的 HomeVisualEvidenceTest 通过，1项、9.507秒，验证标题不存在并保留首页内容与猫狗切换；截图为 `home-cat.png`、`home-dog.png`。

仍位于 `feature/a1.4-cloud-home`，main 和 HEAD 未变，没有提交或合并。受保护文件哈希校验见 `scope-verification.json`。测试包已移除，新版 App 已安装并留在首页。

APK：`D:/pettranslator/artifacts/pettranslator-home-no-brand-debug.apk`。
