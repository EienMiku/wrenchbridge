# Alpha.12: client configuration / 客户端配置

Open **Mods → Wrench Bridge → Config** from the title screen or in game. No held tool is required. In Controls, assign **Open Wrench Bridge settings** to a key to open the same screen empty-handed in game. This separate binding defaults to unbound; G keeps its existing held-wrench mode-palette behavior. The palette's settings button remains available.

从主菜单或游戏内的「模组 → Wrench Bridge → 配置」进入，无需手持工具。在控制设置中绑定独立的「打开扳手互通设置」快捷键，即可在游戏内空手打开同一页面，默认未绑定。G 继续用于手持通用扳手时的模式菜单，其底部也保留配置入口。

## Options

| Option | Default | Effect |
| --- | --- | --- |
| Other wrenches / 其他扳手互通 | ON | Controls Wrench Bridge's added sharing for other physical tools; synchronized independently per player. Native compatibility and the universal wrench remain available. |
| Simulated configuration overlay / 模拟配置叠层 | ON | Shows the face configuration overlay while simulating Mekanism's Configurator. The real tool's overlay and actions retain their native behavior. |
| Mode menu icons / 模式菜单图标 | ON | Shows tool icons beside mode entries. Turning it off lets the text buttons use that space. |
| Mode instructions / 模式说明提示 | ON | Includes usage instructions in mode-entry tooltips. Full entry names remain available in modern menu tooltips. |

All changes save immediately to `config/wrenchbridge-client.properties`. Existing alpha.11 sharing preferences are preserved; the three added display options default to ON. Reset restores these four options while preserving Minecraft's key bindings and unrelated configuration properties. Save failures retain the previous values and show an error in the screen.

更改会立即保存；旧版拆卸互通开关的值会保留，新增界面选项默认开启。「恢复默认」重置以上四项，保留按键绑定。保存失败时保持原值，并在界面提示。

## Verification

- Shared Java tests cover old-config migration, independent options, saving/reloading, reset, unknown-property preservation, failed saves and the existing per-player synchronization rules.
- All six platforms build and pass their dedicated-server interaction fixtures with the same pinned native mods as the [alpha.10 port record](alpha10-ports.md) and [1.21.1 verification record](verification.md).
- The 1.21.1 client opens the actual NeoForge mod list, selects Wrench Bridge and presses its enabled configuration button with no world loaded. It toggles all four options and resets them there.
- In a separate test world, the client binds and invokes the settings key with both hands empty. It exercises saving, actual C2S synchronization of the sharing choice, a second launch restoring all four saved values, reset, mode-menu icon layout, and the simulated Configurator render view switching OFF/ON without changing the source tool.
- Client checks use isolated game directories and a 2 GiB heap cap. Other platform screens compile against their respective APIs; the full visual/client interaction fixture runs on 1.21.1.

## Build fix

The 1.16.5 cold-cache build failure was caused by applying Jabel's `--release 8` option to ForgeGradle's temporary Java 8 compilation tasks. These options now apply only to this project's source-set tasks. ForgeGradle keeps its native compiler settings, and the mod still compiles with Java 17/Jabel to Java 8 bytecode. The CI fix passed all seven jobs before the client-settings update and is included here.

To reproduce the new client checks in `versions/neoforge-1.21.1`, run `gradlew runSettingsOfflineClient`, then prepare an isolated `run-sharing-client/saves/Sharing-Check` world and run `gradlew runSharingClient`, followed by `gradlew runSharingClient -PverifySharingResume=true`. Check `settings-offline-result.txt`, `sharing-client-result.txt` and `sharing-client-resume-result.txt` for `PASS:` in their respective run directories. Release JARs exclude these test fixtures.
