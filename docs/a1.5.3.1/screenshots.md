# A1.5.3.1 真机截图与对比

本次设备为 Samsung SM-G973U，现有显示设置1080×2280，420dpi。以下 phone- 图片为完整真机视口。预览包名独立，未覆盖手机中原有应用。

## 修复后

| 录音页：原位置主插画 + 叠加云朵 | 分析页：8dp上弧 |
| --- | --- |
| ![录音修复](D:/pettranslator/docs/a1.5.3.1/a1531-evidence/phone-recording-cat.png) | ![分析弧度](D:/pettranslator/docs/a1.5.3.1/a1531-evidence/phone-analysis.png) |

| 猫咪结果页：主视觉208dp | 狗狗结果页：主视觉208dp |
| --- | --- |
| ![猫咪结果](D:/pettranslator/docs/a1.5.3.1/a1531-evidence/phone-result-cat.png) | ![狗狗结果](D:/pettranslator/docs/a1.5.3.1/a1531-evidence/phone-result-dog.png) |

## A1.5.3回归版本对比

左侧为A1.5.3留白版本，右侧为修复后。连接设备不同，分辨率/密度存在差别，请以主视觉与邻接内容的关系和交付记录中的dp测量为准。

| A1.5.3：录音图片被下移 | 本次：恢复原位置 |
| --- | --- |
| ![录音之前](D:/pettranslator/docs/a1.5.3/a153-evidence/recording-cat.png) | ![录音之后](D:/pettranslator/docs/a1.5.3.1/a1531-evidence/phone-recording-cat.png) |

| A1.5.3：结果页顶部约100dp额外占位 | 本次：去除占位 |
| --- | --- |
| ![结果之前](D:/pettranslator/docs/a1.5.3/a153-evidence/result-cat.png) | ![结果之后](D:/pettranslator/docs/a1.5.3.1/a1531-evidence/phone-result-cat.png) |

| A1.5.3：4dp浅弧 | 本次：8dp上弧，容器大小不变 |
| --- | --- |
| ![弧度之前](D:/pettranslator/docs/a1.5.3/a153-evidence/analysis-title.png) | ![弧度之后](D:/pettranslator/docs/a1.5.3.1/a1531-evidence/phone-analysis.png) |

另有320/360dp、1/1.5/2倍字体矩阵截图及READY/RECORDING坐标对照，保存在 a1531-evidence/。

录音截图为注入的录音中UI状态，分析固定初始展示状态，结果输入经原解读引擎生成；全部在实体设备上运行生产Composable采集，非设计预览。
