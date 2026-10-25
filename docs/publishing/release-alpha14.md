Simulated Create wrenches can now use Create's own **Open Block Rotation Menu** key binding on Forge 1.20.1 and NeoForge 1.21.1. The universal wrench stays in hand, and the original tool can be in your inventory or a supported carried container. The 1.20.1 integration also fixes missing previews for ordinary blocks in Create 6.0.8's menu.

- Share the native tool view and borrowing transaction across all six builds, preserving real costs and the source tool's settings.
- Bound and coalesce mode, catalog and source-status requests; cache native API discovery and wrench tag identities where supported.
- Improve container return handling and scoped interaction checks. Reject stale or unauthorized native menu actions.
- Fix switching back to universal mode displaying “Air” in the action bar.

模拟机械动力扳手时，现在可使用 Create 自带的「打开方块旋转菜单」快捷键，支持 Forge 1.20.1 和 NeoForge 1.21.1。通用扳手始终留在手中，原工具可放在物品栏或可识别的随身容器里。1.20.1 还修复了 Create 6.0.8 菜单中普通方块预览缺失的问题。

- 六个构建统一接入原工具视图与借用事务，保留真实消耗及原工具设置。
- 合并高频的模式、目录和状态请求，并缓存原生接口探测和适用版本的扳手标签。
- 改进容器归还与交互检查，拒绝过期或未获授权的原生菜单操作。
- 修复切回通用模式时 action bar 显示「空气」。

Install the matching build on client and server. Forge 1.12.2 additionally requires **MixinBooter 11.18+**. Available native menus follow the installed upstream mod version; older Create versions and game versions without a compatible Create release do not gain this menu. [Port verification and coverage](https://github.com/EienMiku/wrenchbridge/blob/main/docs/alpha14-ports.md).
