Open **Mods → Wrench Bridge → Config** without holding a tool, including from the title screen. A separate **Open Wrench Bridge settings** key can be assigned in Controls; it defaults to unbound. G keeps its existing mode-palette behavior.

The configuration page now offers four switches: other-wrench sharing, simulated configuration overlays, mode menu icons and mode instructions. All default to ON. Changes save automatically; reset restores these options while keeping your key bindings. Existing sharing preferences carry over.

Includes the Forge 1.16.5 cold-cache build fix. Six platform builds and server fixtures pass; 1.21.1 additionally checks the actual mod-list button with no loaded world, the bound shortcut with empty hands, saved options across two launches, reset and overlay OFF/ON. [Verification](https://github.com/EienMiku/wrenchbridge/blob/main/docs/alpha12-client-settings.md).

模组列表可直接进入配置页，无需手持工具；在控制设置中可绑定独立的配置快捷键，默认未绑定。新增模拟配置叠层、菜单图标、模式说明三个界面开关，与其它扳手互通一起默认开启。支持自动保存和恢复默认，并保留原有开关选择及按键绑定。

Choose the file for your exact game version and loader. Forge 1.12.2 additionally requires **MixinBooter 11.18+**. Earlier release files remain available.
