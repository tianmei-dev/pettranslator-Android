# B2.4 设置模块交付

关于我们、真实版本展示及安全缓存检查已实现；实际缓存删除未启用，不能将B2.4的删除功能标记为完成。

## 文件

新增：
- [AboutScreen.kt](D:/pettranslator/app/src/main/java/com/example/pettranslator/ui/profile/AboutScreen.kt)：新的Compose二级页，正式Icon、名称、实际版本和用户指定两段介绍；单滚动主体。
- [InstalledAppVersion.kt](D:/pettranslator/app/src/main/java/com/example/pettranslator/ui/profile/InstalledAppVersion.kt)：关于页与设置列表共用PackageManager版本读取。
- [SafeCacheInspector.kt](D:/pettranslator/app/src/main/java/com/example/pettranslator/ui/profile/SafeCacheInspector.kt)：只检查缓存元数据，不跟随符号链接，无删除API；无已证明安全对象时cleanableBytes=0。
- [ProfileCacheDialog.kt](D:/pettranslator/app/src/main/java/com/example/pettranslator/ui/profile/ProfileCacheDialog.kt)：IO线程检查，明确空状态/保留说明；检查错误另报，不能把失败说成缓存为空。
- [SafeCacheInspectorTest.kt](D:/pettranslator/app/src/test/java/com/example/pettranslator/SafeCacheInspectorTest.kt)
- [ProfileSettingsFlowTest.kt](D:/pettranslator/app/src/androidTest/java/com/example/pettranslator/ProfileSettingsFlowTest.kt)
- [b24_settings_acceptance.py](D:/pettranslator/tools/b24_settings_acceptance.py)：一次集中验收和专用隐私状态原样恢复。

修改：
- [ProfileScreen.kt](D:/pettranslator/app/src/main/java/com/example/pettranslator/ui/profile/ProfileScreen.kt)：关于入口、缓存检查入口、共享版本helper；版本行移除点击和箭头。其他布局与联系人弹窗原样。
- [PetTranslatorApp.kt](D:/pettranslator/app/src/main/java/com/example/pettranslator/ui/navigation/PetTranslatorApp.kt)：仅新增About路由及详情页insets/底栏条件，复用popBackStack。
- [ProfileUiEvidenceTest.kt](D:/pettranslator/app/src/androidTest/java/com/example/pettranslator/ProfileUiEvidenceTest.kt)：直接受影响的版本只读、缓存非占位断言。

## 关于我们与版本

正式图标为 R.mipmap.ic_launcher，原生ImageView呈现AdaptiveIcon并保持比例；未改任何Icon资源。名称来自R.string.app_name，两段介绍按用户原文。版本由当前安装包PackageManager.getPackageInfo(...).versionName读取，共用一个helper，未硬编码或修改Gradle。此次设备安装版本实际为1.0.0。

## 缓存安全结论与未完成部分

生产代码只发现：
1. cacheDir/pet_recordings/capture_*.pcm：原录音生命周期管理，明确排除，不改其规则。
2. cacheDir/share/pet-result.png：ResultCardExporter生成后通过FileProvider授予外部读取；没有外部消费结束或可靠闲置记录。文件年龄、当前页面不在结果页、进程重启都不能证明外部不再使用，因此保留。
3. 其余内部/外部缓存：用途或使用状态不能证明，保留。

所以当前安全删除白名单为空，没有删除任何生产缓存目录或文件，也未创建无生产者的虚构安全目录。界面如实显示0.00MB、暂无可清理缓存与保留说明；没有“清理成功”、没有虚报释放空间。统计失败会显示“缓存检查未完成，未删除任何数据”。

当前只实现安全检查、取消、关闭和无可清理缓存状态。确认清理、重新统计实际删除结果的非空分支未启用/未验证。若需要启用真实删除，应先获得可证明的缓存使用生命周期；现有分享导出管理属于本轮禁止修改的结果页范围，本轮没有扩大范围。

## 构建和一轮真机

- 一次构建：BUILD SUCCESSFUL in 1m5s；无修复重建。
- 5项JVM测试：4通过、0失败、1因Windows符号链接创建权限而跳过；不把跳过算通过。
- 一次安装、一轮集中UI验收：Samsung SM-G973U / API31 / 字体1.1，1 test通过。
- 关于页Icon、名称、两段文案、版本正确；返回我的页scroll值930→930（0px差异）。
- 设置列表1.0.0与关于页1.0.0及PackageManager一致，版本行无点击行为。
- 缓存统计、取消、关闭：本轮3个自建缓存fixture共53B全部保留，safeFiles=0、clearedBytes=0；另有1个files持久化fixture，及当前隐私/宠物DataStore快照，操作前后字节不变。
- fixture为排他创建的测试文件：模拟PCM、分享、未知缓存及持久化文件；测试finally仅删除其自己创建的4个fixture，不是生产功能清理用户文件。截图中的“已保留3项”来自这些测试fixture。
- 联系我们原QQ594841675、时间08:00-20:00和关闭行为通过；三个底部Tab位置保持。
- 原隐私状态存在且已备份，结束后逐字节恢复；未清空App数据。
- 实际生产缓存删除路径未启用，也未制造读目录失败和部分删除失败；符号链接分支只读代码检查但JVM用例被平台跳过。不能声称“清除安全缓存”的非空功能已完整验收。

136个保护文件哈希未变，B2.3.1未提交成果、首页、逗宠、结果、权限详情、TXT、隐私门控/撤销/持久化、启动、全局主题、Manifest和Gradle均保留。没有修改录音和分享生产者。

## 本地证据

- [关于我们截图](D:/pettranslator/docs/b2.4-settings/device/b24-settings-evidence/about.png)
- [缓存检查截图](D:/pettranslator/docs/b2.4-settings/device/b24-settings-evidence/cache-check.png)
- [真机逐步日志](D:/pettranslator/docs/b2.4-settings/device/b24-settings-evidence/settings-flow.txt)
- [Instrumentation原始结果](D:/pettranslator/docs/b2.4-settings/device/instrumentation.txt)
- [授权恢复记录](D:/pettranslator/docs/b2.4-settings/device/state-restoration.json)
- [构建日志](D:/pettranslator/docs/b2.4-settings/build.txt)、[单测结果](D:/pettranslator/docs/b2.4-settings/unit-tests.xml)
- [范围检查](D:/pettranslator/docs/b2.4-settings/scope-check.json)、[最终验证记录](D:/pettranslator/docs/b2.4-settings/validation.json)
- [Debug APK](D:/pettranslator/artifacts/pettranslator-b2.4-settings-debug.apk)

全程main，HEAD未变，无自动commit/push、分支操作、清理或重置；已有B2.3.1工作保留。本轮按限制停止，等待用户验收。
