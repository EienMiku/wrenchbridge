# 用户扳手的逐像素微调

原图为用户绘制的 `common/src/main/resources/assets/wrenchbridge/textures/item/universal_wrench_2.png`。`source-original.png` 为原文件备份，`source.sha256` 记录原文件摘要。

当前柔和版 `soft` 从原图重新微调，保留原有中性灰及灰阶分布，仅压低最亮的三个像素、轻微提亮扳口内侧阴影，并将三个青色像素统一为稍暗的青色。没有添加额外棱面或改变轮廓。原文件及此前版本均保留。

`universal_wrench_2_soft.sprite.json` 保存精确网格与配色替换记录。运行 `python scripts/refine_user_wrench.py --variant soft` 可重建柔和版 `universal_wrench_2_soft.png` 和 `before-after-soft.png` 对比图。游戏尺寸为 16×16；预览均为最近邻放大。

首轮 `refined` 采用 9 色及较强的冷色明暗，保存在 `universal_wrench_2_refined.sprite.json`；可用 `--variant refined` 重建，作为历史稿。

修改版均另存于原图同目录，原图和当前启用的材质保持原样。未使用图片生成。
