# B2.1「我的」主页面 UI 交付

本轮按一次实现、一次构建、一次实际真机验收完成。工作分支为 `feature/my-page-ui`；未提交、推送或合并。main保持任务开始时的提交 `fc007fa2946caf87ba0b47095648953bde1e794b`。

## 本轮源码文件

| 文件 | 变更 |
| --- | --- |
| `D:\pettranslator\app\src\main\java\com\example\pettranslator\ui\profile\ProfileScreen.kt` | 从占位页替换为完整可滚动主页面，所有私有展示组件仍在本文件 |
| `D:\pettranslator\app\src\androidTest\java\com\example\pettranslator\HomeFlowTest.kt` | 仅适配原「我的」占位页返回按钮被新UI替换后的断言；通过原首页Tab返回，不改其他流程 |
| `D:\pettranslator\app\src\androidTest\java\com\example\pettranslator\ProfileUiEvidenceTest.kt` | 新增本轮真实页面及320dp/font2适配测试，生成原始截图和简短验证日志 |

另外生成本目录的报告/截图/日志，以及 `D:\pettranslator\artifacts\b2.1-my-page-ui` 的修改前快照和一个调试APK；未修改业务模块。

## 素材与页面

三张指定PNG均直接复用，内容哈希未变，保留原未跟踪状态：

- `D:\pettranslator\app\src\main\res\drawable-nodpi\my_header_pets_cloud.png`：顶部完整Fit展示，按图片原始比例布局。
- `D:\pettranslator\app\src\main\res\drawable-nodpi\my_avatar_paw_default.png`：通用头像，与首页宠物选择无关联。
- `D:\pettranslator\app\src\main\res\drawable-nodpi\my_vip_crown.png`：会员卡皇冠。

任务开始时main无已跟踪代码改动，但有四张未跟踪PNG。额外的中文/空格原图已由用户处理并移出资源目录，本轮没有移动、改名、删除或改写任何素材。随后创建独立分支，保留三张正式素材。

页面依次包含：顶部猫狗云朵、立即登录/未登录个人卡、会员中心/未开通会员/会员有效期「—」卡、准确排序的九项隐私菜单、四项设置菜单。粉紫与雾霾蓝图标、柔和渐变、白色圆角卡片及浅色分割线延续现有主题。普通菜单至少52dp，长文字可换行；大字体会员入口改为纵向排列，不压缩文字强行一屏显示。

页面仅一个纵向滚动容器。原Scaffold、Tab、路由与Insets代码保持原样，内容底部留24dp空间，不重复叠加系统安全区。原 `ProfileScreen(onBack)` 调用签名保留；新页面使用既有底部Tab导航。

## 功能边界

| 入口 | 当前行为 |
| --- | --- |
| 立即登录、开通会员 | 统一明确提示「功能暂未开放／当前仅展示入口，相关功能尚未接入」 |
| 九项隐私菜单 | 同样为可关闭的UI占位，不打开系统权限、不撤销授权、不读取TXT、不控制SDK |
| 关于我们 | UI占位提示 |
| 清除缓存 | UI占位提示，不删除用户数据；测试确认缓存探针在点击后仍保留 |
| 当前版本号 | PackageManager只读取得真实安装版本，当前为 `1.0.0`；点击仅显示版本信息 |
| 联系我们 | Compose原生可关闭弹窗，显示「客服在线时间：08:00-20:00」「联系QQ：594841675」与「我知道了」；不打开QQ、不连接客服系统 |

没有新增登录、会员、权限、隐私、支付等业务状态，也没有假账号、昵称、会员或期限。后续业务接入仍待下一阶段实现。

## 构建和本轮验证

- 唯一Gradle构建：离线 `:app:assembleDebug :app:assembleDebugAndroidTest`，**BUILD SUCCESSFUL in 1m 31s**，没有第二轮修正或构建。
- 当前实际验收设备：三星 **SM-G973U（S10）**，Android API31，字体1.1，1080×2280。
- 原S22序列号在准备安装时已经失效，相关ADB请求未在手机执行安装；发现当前S10后，主APK和测试包分别一次安装成功，没有重新构建。
- 唯一一次instrumentation验收：**2项通过，20.359秒**。没有执行无关功能测试。
- 真实MainActivity页面：全部九项隐私、四项设置按顺序可滚动查看，文字无溢出或省略，只有一个滚动容器，底部三个Tab保持原bounds；未登录/未开通文案及版本来源准确。
- 联系弹窗内容准确、可打开关闭；权限菜单和清缓存占位明确可关闭；可切回首页看到原录音入口，再回「我的」。
- 同一设备上的320×740dp、字体2.0受控布局：13项均可到达，长名称自然换行，版本信息分行，底栏占位不随滚动，联系弹窗内容完整且可关闭。它不是第二台真机截图，图外留白与「底部导航占位」属于测试容器。
- 118个保护文件SHA256保持一致，包含首页、逗宠、导航、业务状态、录音/识别/结果、三张素材、全局主题、Manifest及Gradle。详见 `scope-verification.json`。
- 测试包已清理；主App保留安装，没有清除原有应用数据。

## 原始截图与日志

| 内容 | 本地绝对路径 |
| --- | --- |
| 页面顶部、头像和会员卡 | `D:\pettranslator\docs\b2.1-my-page-ui\device\profile-top.png` |
| 隐私菜单滚动后 | `D:\pettranslator\docs\b2.1-my-page-ui\device\profile-privacy.png` |
| 页面底部和全部设置 | `D:\pettranslator\docs\b2.1-my-page-ui\device\profile-bottom.png` |
| 联系我们弹窗 | `D:\pettranslator\docs\b2.1-my-page-ui\device\profile-contact.png` |
| 320dp大字体受控验证 | `D:\pettranslator\docs\b2.1-my-page-ui\device\controlled-320-font2-bottom.png` |
| 构建原始日志 | `D:\pettranslator\docs\b2.1-my-page-ui\build.txt` |
| 验收原始日志 | `D:\pettranslator\docs\b2.1-my-page-ui\device-tests.txt` |
| 逐项测量和滚动记录 | 本目录 `device\profile-ui.txt`、`device\profile-controlled-320-font2.txt` |
| APK | `D:\pettranslator\artifacts\pettranslator-b2.1-my-page-ui-debug.apk` |

APK SHA256保存在本目录 `apk-sha256.txt`，没有改包名或版本配置。

## 已记录的差异和待办

未发现菜单遮挡、文字异常截断、底栏随内容滚动或插画主体裁切。联系弹窗原始截图有轻微背景透显；代码容器颜色为不透明浅粉，疑似捕获到系统淡入帧。按单轮限制不补拍、不二次打磨，保留原图供用户验收。

参考图中的额外装饰和缓存数值没有伪造加入，底部导航保持项目原样。登录、会员/支付、隐私权限及文档二级页、真实清缓存、关于我们内容均为后续待办。
