# Wrench Bridge alpha previews — Forge / NeoForge

Alpha previews of Wrench Bridge, a universal wrench and native-tool compatibility bridge. NeoForge 1.21.1 uses alpha.9; the five additional ports use alpha.10.

## Features

- Share supported dismantling protocols between wrench-like tools.
- Use the universal wrench in either hand and press the configurable **G** key to select a simulated native tool or sub-mode.
- Keep the universal wrench in hand while the carried source tool supplies its data, energy, air, and durability.
- Borrow tools from supported carried containers, including Sophisticated Backpacks. AE2 network statistics and card storage support container sources and full inventories.
- Keep normal block interactions in the default shared mode, and retain native rotation/configuration in explicitly selected modes.
- Restore missing native sounds and block effects, avoid duplicate AnvilCraft swings, and let eligible offhand actions pass an empty main hand.

## Install

For 1.21.1, download `wrenchbridge-neoforge-1.21.1-0.1.0-alpha.9.jar`. Requires **Minecraft 1.21.1, NeoForge 21.1.200+, and Java 21**; tested with NeoForge 21.1.248. Other game versions are listed below. Install the matching file on both the client and server. In single-player, install in the client. Supported third-party mods are optional.

The original 1.21.1 alpha.9 files are retained. Five alpha.10 ports are also available below; choose the file for your exact game version and loader.

## Validation and limits

71 server feedback comparisons across 15 tools and 53 client scenarios passed, alongside existing protection, costs, inventory conservation, offhand, and container-menu checks. Coverage is for specific pinned mod versions and representative actions. In the automated Refined Storage case, the original wrench also refuses dismantling; only matching refusal has been verified there.

Use each mod's native behavior as the reference: Pipez's wrench configures connections, and Pretty Pipes' terminals and pressurizers still require normal mining. Unknown blocks are never force-destroyed as a dismantling fallback.

MIT license. Source and source JAR downloads are provided.

中文说明：1.21.1 / NeoForge 继续使用 alpha.9，另追加下方五个 alpha.10 移植版。G 切换模拟模式，潜行右键共享拆卸；模拟模式需要携带原工具。详细使用、配方和已知限制见仓库中文 README。

## Additional alpha.10 ports

| Minecraft | Loader | Game Java |
| --- | --- | --- |
| 1.12.2 | Forge + **MixinBooter 11.18+** | 8 |
| 1.16.5 | Forge | 8 |
| 1.20.1 | Forge | 17 |
| 26.1.2 | NeoForge | 25 |
| 26.2 | NeoForge | 25 |

These ports include held-tool simulation, real donor costs and mode restoration, carried-container sources and native AE2 menus, offhand routing, the adaptive key-bound palette, native feedback and the mixed-material recipe. The universal wrench stays in its hand; native tools are not converted or selected in its place.

All five passed isolated server checks. Real AE2/Mekanism dismantling was checked on the Forge ports; real AE2 was checked on 26.x. Native AE2 card changes persisted to tools in carried containers with a full player inventory. The new ports still need broader client/overlay playtesting. See [exact versions and validation scope](https://github.com/EienMiku/wrenchbridge/blob/main/docs/alpha10-ports.md).

The additional source archive corresponds to [v0.1.0-alpha.10-ports](https://github.com/EienMiku/wrenchbridge/tree/v0.1.0-alpha.10-ports). `SHA256SUMS-ports-alpha10.txt` covers these new files; the original alpha.9 artifacts and checksums remain unchanged.
