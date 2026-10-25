Simulated modes now show the source tool's complete native tooltip, retaining its colors, and mirror its native durability/energy bar. Modern builds also forward registered item decorations. Sources can be in your inventory, offhand or a supported carried container. The same source-selection order is used for display and actions, with status refreshed after use. Displaying a source does not move it, change its settings or consume it.

The new **Source tool status** client option defaults to ON. Existing settings carry over. Wrench instructions are gray, the actual bound mode-menu key is yellow, and the selected native profile is aqua.

Native last-durability checks confirm that the source tool can break while the universal wrench remains in hand; a backup is used on the next action, and a missing source is refused.

模拟模式现在显示来源工具的完整原生提示并保留颜色，物品栏同步其原生耐久／能量条，现代版本也转发已注册的物品装饰。支持普通物品栏、副手和可识别的随身容器，显示和实际调用沿用同一来源顺序，使用后刷新状态。查看状态不会移动、修改或消耗原工具。

新增默认开启的「原工具状态」客户端选项，保留已有设置。扳手说明为灰色，实际绑定的模式快捷键为黄色，当前模拟模式为青色。

补充验证了原工具最后一点耐久耗尽：真实工具损坏消失，通用扳手保留；下一次才借用备用工具，没有来源时拒绝操作。

Choose the file for your exact game version and loader. Install the matching build on client and server. Forge 1.12.2 additionally requires **MixinBooter 11.18+**. See the [verification record](https://github.com/EienMiku/wrenchbridge/blob/main/docs/alpha13-source-status.md) for coverage and display limits.
