# 手绘 16×16 通用扳手

当前材质直接用 `universal_wrench.sprite.json` 中的 16 行像素网格绘制，使用 9 种不透明颜色及全透明背景。没有使用图片生成或将大图缩小。轮廓、钢色扳口、深色握柄、青色点缀和黄铜调节件均为原创像素排列。

运行 `python scripts/draw_wrench16.py` 可从网格重建游戏材质。`universal-wrench-16.png` 为原生尺寸；`preview.png` 和 `inventory-scales.png` 仅使用最近邻放大，供检查像素轮廓。

运行时文件：`common/src/main/resources/assets/wrenchbridge/textures/item/universal_wrench.png`。此前 32×32 生成图保留在 `art/texture-refresh/`，作为历史稿。

像素稿和脚本遵循项目 MIT 许可证。
