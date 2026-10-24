# 通用扳手材质更新

此目录保留此前的 32×32 历史稿。当前运行时材质已改为手绘原生 16×16，像素网格与重建方法见 `../handdrawn16/README.md`。

采用内置 imagegen 生成原创图，再用最近邻缩放转换为游戏实际使用的 32×32 RGBA PNG，并将透明度规范为 0/255。

参考 AE2 石英扳手及 Mekanism 配置器扳手模式的像素轮廓，参考 Create 的工业材料配色；没有复用这些模组的图案。参考文件仅留在本机开发缓存，不随源码或模组分发。

设计：斜向钢制活动扳手，明确的固定钳口与活动钳口，黄铜调节螺杆、深色握柄和少量青色点缀。使用 Minecraft `item/handheld` 模型。

运行时文件：`common/src/main/resources/assets/wrenchbridge/textures/item/universal_wrench.png`。

`universal-wrench-generated.png` 是生成原图；`universal-wrench-32.png` 是实际材质；`universal-wrench-preview.png` 为最近邻放大预览。`universal-wrench-before.png` 保留此前版本以便回退。

本次仅改变材质，模组行为与网络协议沿用 0.1.0-alpha.1。已安装体验包通过客户端资源包覆盖，可在游戏内启用；新导入体验包包含新版材质。
