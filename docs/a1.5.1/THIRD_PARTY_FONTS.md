# A1.5.1 第三方字体来源与授权记录

核查日期：2026 年 11 月 2 日。字体仅用于本轮三个指定文字位置的开发试装，方案尚未确定。

## 官方版本与来源

| 项目 | 本次选用的官方字体 | 正式版本 | 原始发布文件名 | Android 资源路径 |
| --- | --- | --- | --- | --- |
| 霞鹜臻楷 / LXGW ZhenKai | 霞鹜臻楷 Slab GB Regular | v0.827，发行页日期 2026-10-13 | `LXGWZhenKaiSlabGB-Regular.ttf` | `app/src/main/res/font/pet_title_zhenkai.ttf` |
| 霞鹜文楷 / LXGW WenKai | LXGW WenKai Regular | v1.530，发行页标题日期 2026-10-18 | `LXGWWenKai-Regular.ttf` | `app/src/main/res/font/pet_title_wenkai.ttf` |

仅为满足 Android 资源命名规则调整磁盘文件名；本次不修改字体内部名称、字形、字体表或许可证，不进行字体子集化及格式转换。文件须与官方 Release 的原始 TTF 字节及下列 SHA-256 一致。

### 霞鹜臻楷

- 官方项目：<https://github.com/lxgw/LxgwZhenKai>
- 固定版本发行页：<https://github.com/lxgw/LxgwZhenKai/releases/tag/v0.827>
- 官方 TTF 下载：<https://github.com/lxgw/LxgwZhenKai/releases/download/v0.827/LXGWZhenKaiSlabGB-Regular.ttf>
- 发布资产与官方 SHA-256：<https://github.com/lxgw/LxgwZhenKai/releases/expanded_assets/v0.827>
- 文件大小：17,453,376 字节。
- 官方 SHA-256：`bf4ea8c39b51784469020120125715d3075137233fe337b5d0a7327f9dfc03bd`。

v0.827 发行说明明确表示，从该版本起仅维护 Slab 版；原采用 Klee 西文的版本及 GBK 版本不再更新维护，需要时应回退至 v0.825。因此本次采用最新官方发布的 **Slab GB** 变体，名称中保留该变体信息。它是臻楷项目的正式资产，不是第三方改版。

### 霞鹜文楷

- 官方项目：<https://github.com/lxgw/LxgwWenKai>
- 固定版本发行页：<https://github.com/lxgw/LxgwWenKai/releases/tag/v1.530>
- 官方 TTF 下载：<https://github.com/lxgw/LxgwWenKai/releases/download/v1.530/LXGWWenKai-Regular.ttf>
- 发布资产与官方 SHA-256：<https://github.com/lxgw/LxgwWenKai/releases/expanded_assets/v1.530>
- 文件大小：25,928,940 字节。
- 官方 SHA-256：`2d73eea94ad808999d90c0000c239a84a33b09d4bc6ab6c5b8bbf1eda8a7fc8b`。

使用比例西文 Regular 版本，未使用 Mono、Light 或 Medium 版本。官方资产 UTC 发布时间为 2026-10-17，发行页版本标题采用 2026-10-18。

## 随 App 分发的原始许可证

两份许可证均为 **SIL Open Font License 1.1**，分别从对应固定版本标签的官方仓库获取，完整保留在 Android assets 中：

| 字体 | 项目内许可证 | 官方固定版本原文 |
| --- | --- | --- |
| 臻楷 | `app/src/main/assets/licenses/lxgw_zhenkai/OFL.txt` | <https://raw.githubusercontent.com/lxgw/LxgwZhenKai/v0.827/OFL.txt> |
| 文楷 | `app/src/main/assets/licenses/lxgw_wenkai/OFL.txt` | <https://raw.githubusercontent.com/lxgw/LxgwWenKai/v1.530/OFL.txt> |

臻楷许可证包含 LXGW、Klee Project Authors、Batang and Gungsuh Project Authors 的版权声明；文楷许可证包含 LXGW 和 Klee Project Authors 的版权声明。应保留全部声明，不使用本说明替代原始 OFL.txt。

根据两份原始许可证的许可与条件，字体可随软件嵌入、捆绑和分发，包括商用软件，前提是保留相关版权声明与许可证全文；不得将字体文件单独出售。字体本身继续按 OFL 1.1 分发；App 中生成的截图或分享图片无需改用 OFL。

## 保留名称与未来子集化边界

两份许可证指定的保留字体名称（Reserved Font Names）均为：

- `霞鹜`
- `霞鶩`
- `落霞孤鹜`
- `落霞孤鶩`
- `LXGW`

修改字体时，OFL 第 3 条对保留名称的使用有限制。两份许可证还带有附加许可：未经字体源代码修改的重新编译，以及符合所述条件、**仅用于网页字体分发**的子集化或格式转换，可继续使用保留名称。该网页字体例外不能直接视为 Android 安装包内子集字体沿用保留名称的许可。

本次使用官方原版完整 TTF，**未进行子集化**。字体最终选定后，若要制作 Android 子集，应重新核查保留名称条款、必要的更名或作者授权，并验证汉字、标点、数字、Emoji 回退及后续动态文案覆盖；此项不在本轮实施范围内。
